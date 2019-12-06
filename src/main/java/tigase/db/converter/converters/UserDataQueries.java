/*
 * Tigase Database Migrator - Component responsible for migrating data from other XMPP servers
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
package tigase.db.converter.converters;

import tigase.db.DataRepository;
import tigase.db.converter.Converter;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class UserDataQueries {

	private final static String SELECT_USERS = "SELECT username, password FROM users";
	private final static String SELECT_NEW_USERS = "SELECT username, server_host, password FROM users";
	private final static String ROSTER_ITEMS = "SELECT username, jid, nick, subscription FROM rosterusers WHERE username = ?";
	private final static String ROSTER_NEW_ITEMS = "SELECT username, server_host, jid, nick, subscription FROM rosterusers WHERE username = ? AND server_host = ?";
	private final static String ROSTER_GROUPS = "SELECT username, jid, grp FROM rostergroups WHERE username = ? AND jid = ?";
	private final static String ROSTER_NEW_GROUPS = "SELECT username, server_host, jid, grp FROM rostergroups WHERE username = ? AND jid = ? AND server_host = ?";
	final Map<String, String> selectedQueries;
	DataRepository.dbTypes dbType;
	// Converter.SERVER [type] / DataRepository.dbTypes / query
	Map<String, Map<String, Map<String, String>>> queries = new ConcurrentHashMap<>();
	Converter.SERVER serverType;

	UserDataQueries(Converter.SERVER serverType, DataRepository.dbTypes dbType) {
		this.serverType = serverType;
		this.dbType = dbType;
		final Map<String, Map<String, String>> ejabberdQueries = queries.computeIfAbsent(
				Converter.SERVER.ejabberd.name(), k -> new ConcurrentHashMap<>());
		final Map<String, String> ejabberdSqlGeneric = new ConcurrentHashMap<>();

		ejabberdSqlGeneric.put(UserCredentialsConverter.QUERY.users.name(), SELECT_USERS);
		ejabberdSqlGeneric.put(UserCredentialsConverter.QUERY.rosteritems.name(), ROSTER_ITEMS);
		ejabberdSqlGeneric.put(UserCredentialsConverter.QUERY.rostergroups.name(), ROSTER_GROUPS);

		ejabberdQueries.put(DataRepository.dbTypes.sqlserver.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.jtds.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.mysql.name(), ejabberdSqlGeneric);
		ejabberdQueries.put(DataRepository.dbTypes.postgresql.name(), ejabberdSqlGeneric);

		final Map<String, Map<String, String>> ejabberdNewQueries = queries.computeIfAbsent(
				Converter.SERVER.ejabberd_new.name(), k -> new ConcurrentHashMap<>());
		final Map<String, String> ejabberdSqlNewGeneric = new ConcurrentHashMap<>();

		ejabberdSqlNewGeneric.put(UserCredentialsConverter.QUERY.users.name(), SELECT_NEW_USERS);
		ejabberdSqlNewGeneric.put(UserCredentialsConverter.QUERY.rosteritems.name(), ROSTER_NEW_ITEMS);
		ejabberdSqlNewGeneric.put(UserCredentialsConverter.QUERY.rostergroups.name(), ROSTER_NEW_GROUPS);

		ejabberdNewQueries.put(DataRepository.dbTypes.mysql.name(), ejabberdSqlNewGeneric);
		ejabberdNewQueries.put(DataRepository.dbTypes.postgresql.name(), ejabberdSqlNewGeneric);

		selectedQueries = getAllQueriesForServerAndDatabase().orElse(Collections.emptyMap());
	}

	Optional<String> getQuery(String query) {
		return Optional.ofNullable(selectedQueries.get(query));
	}

	Optional<Map<String, String>> getAllQueriesForServerAndDatabase() {
		final Map<String, Map<String, String>> orDefault = queries.getOrDefault(serverType.name(),
																				Collections.emptyMap());
		final Map<String, String> value = orDefault.get(dbType.name());
		return Optional.ofNullable(value);
	}

}
