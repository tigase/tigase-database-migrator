/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2018 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.db.converter;

import tigase.component.DSLBeanConfigurator;
import tigase.conf.ConfigBuilder;
import tigase.db.DataRepository;
import tigase.db.DataSource;
import tigase.db.TigaseDBException;
import tigase.db.jdbc.DataRepositoryImpl;
import tigase.kernel.KernelException;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.core.Kernel;
import tigase.util.ui.console.CommandlineParameter;
import tigase.util.ui.console.ParameterParser;

import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Converter {

	final static String repositoryClassParameter = "repository-class";
	final static String sourceUriParameter = "source-uri";
	final static String serverTypeParameter = "server-type";
	final static String destinationUriParameter = "destination-uri";
	final static String componentsParameter = "components";
	final static String virtualHostParameter = "virtual-host";
	private static final Logger log = Logger.getLogger(Converter.class.getName());
	private static final String defaultRepositoryClass = DataRepositoryImpl.class.getName();
	private static final Logger loggerFor = Logger.getLogger("convertible");

	public enum SERVER {
		ejabberd,
		ejabberd_new;

		public static final String[] strings = EnumSet.allOf(SERVER.class)
				.stream()
				.map(SERVER::name)
				.toArray(String[]::new);

	}

	private final List<String> components;
	private final ConverterProperties converterProperties;
	private final String destinationURI;
	private final String respositoryClassStr;
	private final String sourceURI;
	DataRepoPool dataRepoPool;
	private Set<Class<Convertible>> convertibles;
	private boolean initialised = false;
	private Kernel kernel;

	private static List<CommandlineParameter> getCommandlineOptions() {
		List<CommandlineParameter> options = new ArrayList<>();
		options.add(new CommandlineParameter.Builder("R", repositoryClassParameter).description(
				"Data Repository implementation used for reading data from source; must implement " +
						DataSource.class.getName()).defaultValue(defaultRepositoryClass).required(true).build());
		options.add(new CommandlineParameter.Builder("S", sourceUriParameter).description(
				"URI of the source do the data: `jdbc:xxxx://<host>/<database>…`").required(true).build());
		options.add(new CommandlineParameter.Builder("T", serverTypeParameter).description(
				"Type of the server from which import will be performed")
							.options(SERVER.strings)
							.required(true)
							.build());
		options.add(new CommandlineParameter.Builder("D", destinationUriParameter).description(
				"URI of the destination for the data: `jdbc:xxxx://<host>/<database>…`").required(true).build());
		options.add(new CommandlineParameter.Builder("C", componentsParameter).description(
				"Additional component beans names").required(false).build());

		options.add(new CommandlineParameter.Builder("H", virtualHostParameter).description(
				"Virtual-host / domain name used by installation")
							.required(true)
							.build());
		return options;
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws ClassNotFoundException {
		ConverterUtil.initLogger();

		ParameterParser parser = new ParameterParser(true);
		parser.addOptions(getCommandlineOptions());

		Properties properties = null;

		if (null == args || args.length == 0 || (properties = parser.parseArgs(args)) == null) {
			String usage = "$ java -cp jars/*:. tigase.db.converter.Converter [options]\n" +
					"\t\tif the option defines default then <value> is optional";
			System.out.println(parser.getHelp(usage));
			System.exit(0);
		} else {
			System.out.println("Properties: " + properties);
		}

		Converter converter = null;

		try {
			converter = new Converter(properties);
			converter.init();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Converter initialisation failed: " + e);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Converter initialisation failed: " + e.getMessage(), e);
			}
			System.exit(1);
		}
		converter.convert();
		System.exit(0);
	}

	public Converter(Properties properties) {
		this.sourceURI = properties.getProperty(sourceUriParameter);
		this.destinationURI = properties.getProperty(destinationUriParameter);
		this.respositoryClassStr = properties.getProperty(repositoryClassParameter);

		final String[] split = properties.getProperty(componentsParameter, "").split(",");
		this.components = Arrays.asList(split);

		converterProperties = new ConverterProperties();
		final String virtualHost = properties.getProperty(virtualHostParameter);
		converterProperties.setVHost(virtualHost);
		final SERVER serverType = SERVER.valueOf(properties.getProperty(serverTypeParameter));
		converterProperties.setServerType(serverType);
	}

	private void convert() {

		if (!initialised) {
			throw new IllegalStateException("Converter hasn't been initialised yet");
		}
		convertibles.stream().map(kernel::getInstance).forEach(convertible -> {
			final Optional<String> query = convertible.getMainQuery();

			AtomicInteger failCount = new AtomicInteger();
			AtomicInteger totalCount = new AtomicInteger();
			if (query.isPresent()) {
				PreparedStatement preparedStatement = null;
				ResultSet resultSet = null;
				try {
					dataRepoPool.initPreparedStatement(query.get(), query.get());
					preparedStatement = dataRepoPool.getPreparedStatement(1, query.get());

					resultSet = preparedStatement.executeQuery();

					while (!resultSet.isClosed() && resultSet.next()) {
						Optional<RowEntity> entity = Optional.empty();
						totalCount.incrementAndGet();
						try {
							entity = convertible.processResultSet(resultSet);

							//TODO: add progress / count of rows

							boolean result;
							if (entity.isPresent() && (result = convertible.storeEntity(entity.get()))) {
								log.log(Level.FINEST, "Storing: entity" + entity.get());
								loggerFor.log(Level.INFO, entity.get().getID() + ": OK");
							} else {
								failCount.incrementAndGet();
								log.log(Level.FINE, entity.get() + " : FAILED");
								loggerFor.log(Level.WARNING, entity.get().getID() + " : FAILED");
							}
						} catch (TigaseDBException e) {
							failCount.incrementAndGet();
							log.log(Level.FINE, entity + " : FAILED (" + e.getMessage() + ")", e);
							loggerFor.log(Level.WARNING,
										  (entity.isPresent() ? entity.get().getID() : "n/a") + " : FAILED (" +
												  e.getMessage() + ")");
						}
					}
				} catch (Exception e) {
					log.log(Level.WARNING, "Error while converting data", e);
				} finally {
					dataRepoPool.release(preparedStatement, resultSet);
				}
			}
			log.log(Level.INFO, "Conversion for {0} finished, {1} of {2} failed",
					new String[]{convertible.getClass().getSimpleName(), String.valueOf(failCount.get()),
								 String.valueOf(totalCount.get())});
		});
	}

	@SuppressWarnings("unchecked")
	private void init() throws Exception {

		ConfigBuilder builder = ConverterUtil.getConfig(destinationURI, converterProperties.getVHost(), 1,
														ConfigTypeEnum.DefaultMode, components);
		final HashMap config = builder.build();

		log.log(Level.CONFIG, "Using DSL configuration bootstrap: " + config);
		kernel = ConverterUtil.prepareKernel(config);
		final DSLBeanConfigurator instance = kernel.getInstance(DSLBeanConfigurator.class);
		StringWriter writer = new StringWriter();
		instance.dumpConfiguration(writer);
		log.log(Level.FINE, "Effective DSL config: " + writer.toString());

		final Class<?> repoClazz = Class.forName(respositoryClassStr);

		try {
			dataRepoPool = new DataRepoPool();
			dataRepoPool.initialize(sourceURI);
			final int repoPoolSize = 10;
			for (int i = 0; i < repoPoolSize; i++) {

				DataRepository sourceDataRepository = (DataRepository) repoClazz.newInstance();
				sourceDataRepository.initialize(sourceURI);
				dataRepoPool.addRepo(sourceDataRepository);
			}
			log.log(Level.INFO, "Source database type: " + dataRepoPool.getDatabaseType());

			converterProperties.setDatabaseType(dataRepoPool.getDatabaseType());
		} catch (KernelException e) {
			throw new ClassCastException("Class must implement DataRepository interface");
		}

		kernel.registerBean("QueryExecutor").asClass(QueryExecutor.class).exec();
		final QueryExecutor queryExecutor = kernel.getInstance(QueryExecutor.class);
		queryExecutor.initialise(dataRepoPool);

		convertibles = tigase.util.ClassUtil.getClassesImplementing(Convertible.class);

		log.log(Level.INFO, "Found converters: " + convertibles);
		convertibles.forEach(beanClass -> kernel.registerBean(beanClass.getSimpleName()).asClass(beanClass).exec());
		final Set<Convertible> allConvertibleInstances = convertibles.stream()
				.map(cls -> kernel.getInstance(cls.getSimpleName()))
				.filter(obj -> Convertible.class.isAssignableFrom(obj.getClass()))
				.map(convertible -> {
					final Convertible convertibleInstance = (Convertible<RowEntity>) convertible;
					convertibleInstance.initialise(converterProperties);
					return convertibleInstance;
				})
				.collect(Collectors.toSet());

		final Set<Convertible> supportedConvertibles = allConvertibleInstances.stream()
				.filter(convertible -> convertible.getMainQuery().isPresent())
				.collect(Collectors.toSet());

		for (Convertible supportedConvertible : supportedConvertibles) {
			final Map<String, String> queriesToInit = supportedConvertible.getAdditionalQueriesToInitialise();
			for (Map.Entry<String, String> entry : queriesToInit.entrySet()) {
				dataRepoPool.initPreparedStatement(entry.getKey(), entry.getValue());
			}
		}

		allConvertibleInstances.removeAll(supportedConvertibles);
		allConvertibleInstances.forEach(convertible -> {
			log.log(Level.FINE, "Unregistering: " + convertible.getClass().getSimpleName());
			convertibles.remove(convertible.getClass());
			kernel.unregister(convertible.getClass().getSimpleName());
		});

		log.log(Level.INFO, "Compatible converters: " + convertibles);

		this.initialised = true;
	}

	public static class ConverterProperties {

		private String VHost;
		private DataRepository.dbTypes databaseType;
		private SERVER serverType;

		public ConverterProperties() {
		}

		public String getVHost() {
			return VHost;
		}

		private void setVHost(String VHost) {
			this.VHost = VHost;
		}

		public SERVER getServerType() {
			return serverType;
		}

		private void setServerType(SERVER serverType) {
			this.serverType = serverType;
		}

		public DataRepository.dbTypes getDatabaseType() {
			return databaseType;
		}

		private void setDatabaseType(DataRepository.dbTypes databaseType) {
			this.databaseType = databaseType;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("ConverterProperties{");
			sb.append("VHost='").append(VHost).append('\'');
			sb.append('}');
			return sb.toString();
		}
	}
}
