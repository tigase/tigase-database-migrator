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

import tigase.db.AuthRepository;
import tigase.db.UserRepository;
import tigase.db.converter.Converter;
import tigase.db.converter.Convertible;
import tigase.db.converter.QueryExecutor;
import tigase.kernel.beans.Inject;
import tigase.vhosts.VHostItem;
import tigase.vhosts.VHostManager;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.jid.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Class responsible for converting user-data
 *
 * Based on:
 * https://docs.ejabberd.im/developer/sql-schema/
 * https://github.com/processone/ejabberd/tree/master/sql
 */
public class UserCredentialsConverter
		implements Convertible<UserEntity> {

	@Inject
	AuthRepository authRepository;
	Converter.ConverterProperties properties;
	@Inject
	QueryExecutor queryExecutor;
	@Inject
	UserRepository userRepository;
	@Inject
	VHostManager vHostManager;
	private UserDataQueries queries;

	public UserCredentialsConverter() {
	}

	@Override
	public void initialise(Converter.ConverterProperties properties) {
		this.properties = properties;
		queries = new UserDataQueries(properties.getServerType(), properties.getDatabaseType());
	}

	@Override
	public Optional<String> getMainQuery() {
		return queries.getQuery(QUERY.users.name());
	}

	@Override
	public Optional<UserEntity> processResultSet(ResultSet rs) throws Exception {
		String username = null;
		String server_host = null;
		String password = null;
		switch (properties.getServerType()) {
			case ejabberd:
				username = rs.getString("username");
				server_host = properties.getVHost();
				password = rs.getString("password");
				break;
			case ejabberd_new:
				username = rs.getString("username");
				server_host = rs.getString("server_host");
				password = rs.getString("password");
				break;
		}
		if (username != null && server_host != null && password != null) {
			final BareJID jid = BareJID.bareJIDInstance(username, server_host);
			final UserEntity userEntity = new UserEntity(jid, password);
			addRosterItems(userEntity, jid);
			return Optional.of(userEntity);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public boolean storeEntity(UserEntity entity) throws Exception {
		authRepository.addUser(entity.getJid(), entity.getPassword());

		if (!vHostManager.isLocalDomain(entity.getJid().getDomain())) {
			final VHostItem vHostItem = new VHostItem(entity.getJid().getDomain());
			vHostManager.getComponentRepository().addItem(vHostItem);
		}

		final String roster = entity.getRosterItems()
				.stream()
				.map(userRosterItem -> userRosterItem.getRosterElement().getRosterElement().toString())
				.collect(Collectors.joining());
		if (roster != null && !roster.isEmpty()) {
			userRepository.setData(entity.getJid(), null, RosterAbstract.ROSTER, roster);
		}

		return true;
	}

	@Override
	public Map<String, String> getAdditionalQueriesToInitialise() {
		return queries.getAllQueriesForServerAndDatabase().orElse(Collections.emptyMap());
	}

	@SuppressWarnings("unchecked")
	private void addRosterItems(UserEntity userEntity, BareJID jid) throws Exception {
		final List<UserRosterItem> rosterItems = (List<UserRosterItem>) queryExecutor.executeQuery(
				QUERY.rosteritems.name(), getRosterItems(jid));
		userEntity.addRosterItems(rosterItems);
	}

	private QueryExecutor.QueryFunction<PreparedStatement, List<String>> getRosterItemGroups(BareJID ownerJid,
																							 BareJID contactJid) {
		return preparedStatement -> {
			List<String> items = new CopyOnWriteArrayList<>();

			ResultSet resultSet = null;
			// private final static String ROSTER_GROUPS = "SELECT username, jid, grp FROM rostergroups WHERE username = ? AND jid = ?";
			preparedStatement.setString(1, String.valueOf(ownerJid.getLocalpart()));
			preparedStatement.setString(2, String.valueOf(contactJid));
			if (Converter.SERVER.ejabberd_new.equals(properties.getServerType())) {
				preparedStatement.setString(3, String.valueOf(ownerJid.getDomain()));
			}
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				final String group = resultSet.getString("grp");
				items.add(group);
			}
			return items;
		};
	}

	@SuppressWarnings("unchecked")
	private QueryExecutor.QueryFunction<PreparedStatement, List<UserRosterItem>> getRosterItems(BareJID jid) {
		return preparedStatement -> {
			List<UserRosterItem> items = new CopyOnWriteArrayList<>();

			ResultSet resultSet = null;

			// private final static String ROSTER_ITEMS = "SELECT username, jid, nick, subscription, ask, askmessage, server, subscribe, type, created_at FROM rosterusers WHERE username = ?";
			preparedStatement.setString(1, String.valueOf(jid.getLocalpart()));
			if (Converter.SERVER.ejabberd_new.equals(properties.getServerType())) {
				preparedStatement.setString(2, String.valueOf(jid.getDomain()));
			}
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				final String conJidStr = resultSet.getString("jid");
				final BareJID conJid = BareJID.bareJIDInstance(conJidStr);
				final String nick = resultSet.getString("nick");
				final String subscription = resultSet.getString("subscription");

				final List<String> groups = (List<String>) queryExecutor.executeQuery(QUERY.rostergroups.name(),
																					  getRosterItemGroups(jid, conJid));
				final UserRosterItem userRosterItem = new UserRosterItem(jid, conJid, nick, subscription, groups);
				items.add(userRosterItem);
			}
			return items;
		};
	}

	enum QUERY {
		users,
		rosteritems,
		rostergroups,
		vcard
	}
}
