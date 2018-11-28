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

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for all converters of data from other servers. In principle
 * it makes a query to database, process each row creating an object
 * of {@link RowEntity} and then insets it to Tigase repository
 */
public interface Convertible<T extends RowEntity> {

	/**
	 * Principal query used to retrieve data from source repository. It's used to
	 * determine if implementation offers support for particular combination of source
	 * XMPP Server and database type (empty Optional indicates lack of support
	 * resulting in skipping implementation).
	 */
	Optional<String> getMainQuery();

	/**
	 * Method is responsible for initialising converter based on ConverterProperties.
	 *
	 * @param properties various properties allowing determine appropriate set
	 * of queries and applied processing.
	 */
	void initialise(Converter.ConverterProperties properties);

	/**
	 * Process {@link ResultSet} to produce object extending {@link RowEntity}
	 *
	 * @param rs result set to be processed
	 *
	 * @return {@link RowEntity} with all data from single Row
	 *
	 * @throws Exception indicates any problem with processing of the data
	 */
	Optional<T> processResultSet(ResultSet rs) throws Exception;

	/**
	 * Method stores {@link RowEntity} in the destination repositories.
	 *
	 * @param entity {@link RowEntity} to be stored
	 *
	 * @return value indicating if storing was successful
	 *
	 * @throws Exception indicates any problem with storing of the {@link RowEntity}
	 */
	boolean storeEntity(T entity) throws Exception;

	/**
	 * Method allows providing additional queries that needs to be initialised
	 * in {@link tigase.db.DataRepository} for future use
	 *
	 * @return {@link Map} with key-value pair of query ID and actual query to be initialised
	 */
	default Map<String, String> getAdditionalQueriesToInitialise() {
		return Collections.emptyMap();
	}
}
