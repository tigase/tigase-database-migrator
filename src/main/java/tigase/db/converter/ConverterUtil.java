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
import tigase.component.DSLBeanConfiguratorWithBackwardCompatibility;
import tigase.conf.ConfigBuilder;
import tigase.conf.ConfiguratorAbstract;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.beans.selector.ServerBeanSelector;
import tigase.kernel.core.Kernel;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.XMPPServer;
import tigase.util.log.LogFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.*;

public class ConverterUtil {

	private static final Logger log = Logger.getLogger(ConverterUtil.class.getCanonicalName());

	static void initLogger() {
		final String logsDirectory = "logs";
		if (!Files.exists(Paths.get(logsDirectory))) {
			try {
				Files.createDirectory(Paths.get(logsDirectory));
			} catch (IOException e) {
				// can be ignored
			}
		}

		//@formatter:off
		String initial_config =
				"tigase.level=FINE\n" +
				"tigase.db.jdbc.level=INFO\n" +
				"tigase.db.converter.level=FINEST\n" +
				"tigase.xml.level=FINE\n" +
				"tigase.auth.level=FINE\n" +
				"handlers=java.util.logging.ConsoleHandler java.util.logging.FileHandler\n" +
				"java.util.logging.ConsoleHandler.level=INFO\n" +
				"java.util.logging.ConsoleHandler.formatter=tigase.util.log.LogFormatter\n" +
				"java.util.logging.FileHandler.level=ALL\n" +
				"java.util.logging.FileHandler.formatter=tigase.util.log.LogFormatter\n" +
				"java.util.logging.FileHandler.pattern=" + logsDirectory + "/tigase-database-converter.log\n" +
				"tigase.useParentHandlers=true\n";
		//@formatter:on

		ConfiguratorAbstract.loadLogManagerConfig(initial_config);

		Logger logger = Logger.getLogger("convertible");
		logger.setLevel(Level.ALL);
		logger.setUseParentHandlers(false);
		final LogFormatter logFormatter = new PlainLogFormatter();
		try {
			FileHandler fileHandler = null;
			fileHandler = new FileHandler(logsDirectory + "/tigase-database-converter_status.log");
			fileHandler.setLevel(Level.ALL);
			fileHandler.setFormatter(logFormatter);
			logger.addHandler(fileHandler);
		} catch (IOException e) {
			System.out.println("problem with adding log file handler");
			// can be ignored
		}
		final ConsoleHandler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.INFO);
		consoleHandler.setFormatter(logFormatter);
		logger.addHandler(consoleHandler);

	}

	static Kernel prepareKernel(Map config) {
		Kernel kernel = new Kernel("root");
		try {
			if (XMPPServer.isOSGi()) {
				kernel.registerBean("classUtilBean")
						.asInstance(Class.forName("tigase.osgi.util.ClassUtilBean").newInstance())
						.exportable()
						.exec();
			} else {
				kernel.registerBean("classUtilBean")
						.asInstance(Class.forName("tigase.util.reflection.ClassUtilBean").newInstance())
						.exportable()
						.exec();
			}
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		// register default types converter and properties bean configurator
		kernel.registerBean(DefaultTypesConverter.class).exportable().exec();
		kernel.registerBean(DSLBeanConfiguratorWithBackwardCompatibility.class).exportable().exec();
		kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();

		DSLBeanConfigurator configurator = kernel.getInstance(DSLBeanConfigurator.class);
		configurator.setProperties(config);
		ModulesManagerImpl.getInstance().setBeanConfigurator(configurator);
		kernel.registerBean("beanSelector").asInstance(new ServerBeanSelector()).exportable().exec();

		configurator.registerBeans(null, null, config);

		return kernel;
	}

	private static void setupConvertibleLogger() {

	}

	private static class PlainLogFormatter
			extends LogFormatter {

		@Override
		public synchronized String format(LogRecord record) {
			StringBuilder sb = new StringBuilder(200);

			cal.setTimeInMillis(record.getMillis());
			sb.append('[');
			sb.append(String.format("%1$tF %1$tT.%1$tL", cal));
			sb.append(']');
			sb.append(' ');

			sb.append(formatMessage(record));
			if (record.getThrown() != null) {
				sb.append('\n').append(record.getThrown().toString());

				StringBuilder st_sb = new StringBuilder(1024);

				getStackTrace(st_sb, record.getThrown());
				sb.append(st_sb.toString());
				addError(record.getThrown(), st_sb.toString(), sb.toString());
			}

			return sb.toString() + "\n";
		}
	}
}
