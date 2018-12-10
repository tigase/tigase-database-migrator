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
import tigase.conf.ConfigReader;
import tigase.db.DataRepository;
import tigase.db.DataSource;
import tigase.db.TigaseDBException;
import tigase.db.jdbc.DataRepositoryImpl;
import tigase.kernel.KernelException;
import tigase.kernel.beans.RegistrarBean;
import tigase.kernel.core.BeanConfig;
import tigase.kernel.core.Kernel;
import tigase.util.ui.console.CommandlineParameter;
import tigase.util.ui.console.ParameterParser;

import java.io.File;
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

	private final ConverterProperties converterProperties;
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
		this.respositoryClassStr = properties.getProperty(repositoryClassParameter);
		
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
		registeredConvertibleBeans.stream().map(bean -> (Convertible) bean.getKernel().getInstance(bean.getClazz())).forEach(convertible -> {
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

		final Map config = new ConfigReader().read(new File("etc/config.tdsl"));
		config.put("schema-management", false);
		config.put("pool-size", 1);

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

		kernel.registerBean("QueryExecutor").asClass(QueryExecutor.class).exportable().exec();
		final QueryExecutor queryExecutor = kernel.getInstance(QueryExecutor.class);
		queryExecutor.initialise(dataRepoPool);

		convertibles = tigase.util.ClassUtil.getClassesImplementing(Convertible.class);

		log.log(Level.INFO, "Found converters: " + convertibles);

		registerConvertibleBeans();

		final Set<Convertible> allConvertibleInstances = registeredConvertibleBeans.stream()
				.map(bean -> {
					log.log(Level.FINE, "Retrieving bean " + bean.getBeanName() + " from " + bean.getKernel().getName());
					return bean.getKernel().getInstance(bean.getBeanName());
				})
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
			//kernel.unregister(convertible.getClass().getSimpleName());
			List<BeanConfig> toUnregister = registeredConvertibleBeans.stream()
					.filter(bean -> bean.getClazz().equals(convertible.getClass()))
					.collect(Collectors.toList());

			toUnregister.forEach(bean -> {
				bean.getKernel().unregister(convertible.getClass().getSimpleName());
			});
			registeredConvertibleBeans.removeAll(toUnregister);
		});

		log.log(Level.INFO, "Compatible converters: " + convertibles);

		this.initialised = true;
	}

	private List<BeanConfig> registeredConvertibleBeans = new ArrayList<>();

	private void registerConvertibleBeans() {
		convertibles.forEach(this::registerConvertibleBean);
	}

	private void registerConvertibleBean(Class<Convertible> convertible) {
		try {
			Optional<Class> parent = convertible.newInstance().getParentBean();
			if (parent.isPresent()) {
				List<BeanConfig> found = kernel.getDependencyManager().getBeanConfigs((Class<?>) parent.get(), null, null, true);
				log.log(Level.FINEST, "Found parent beans for convertible " + convertible.getCanonicalName() + ": " + found);
				if (found.size() > 1) {
					log.log(Level.WARNING, "Too many parent beans for convertible " + convertible.getCanonicalName() + ": " + found + ", skipping conversion...");
					return;
				} else if (found.isEmpty()) {
					log.log(Level.WARNING, "No parent beans for convertible " + convertible.getCanonicalName() + ": " + found + ", skipping conversion...");
					return;
				}

				BeanConfig parentBean = found.get(0);
				if (RegistrarBean.class.isAssignableFrom(parentBean.getClazz())) {
					Object o = kernel.getInstance(parentBean.getClazz());
					Kernel localKernel = kernel.getInstance(parentBean.getBeanName() + "#KERNEL");
					localKernel.registerBean(convertible.getSimpleName()).asClass(convertible).exec();
					this.registeredConvertibleBeans.add(localKernel.getDependencyManager().getBeanConfig(convertible.getSimpleName()));
				} else {
					parentBean.getKernel().registerBean(convertible.getSimpleName()).asClass(convertible).exec();
					this.registeredConvertibleBeans.add(parentBean.getKernel().getDependencyManager().getBeanConfig(convertible.getSimpleName()));
				}
			} else {
				kernel.registerBean(convertible.getSimpleName()).asClass(convertible).exec();
				this.registeredConvertibleBeans.add(kernel.getDependencyManager().getBeanConfig(convertible.getSimpleName()));
			}
		} catch (Throwable ex) {
			throw new RuntimeException(ex);
		}
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
