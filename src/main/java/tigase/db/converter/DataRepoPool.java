/**
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
package tigase.db.converter;

import tigase.db.*;
import tigase.db.jdbc.DataRepositoryImpl;
import tigase.util.Version;
import tigase.xmpp.jid.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataRepoPool
		implements DataRepository, DataSourcePool<DataRepository> {

	private static final Logger log = Logger.getLogger(DataRepoPool.class.getName());
	private dbTypes database = null;
	private LinkedBlockingQueue<DataRepository> repoPool = new LinkedBlockingQueue<>();
	private String resource_uri = null;

	public DataRepoPool() {
	}

	public void addRepo(DataRepository repo) {
		repoPool.offer(repo);
	}

	@Override
	public DataRepository takeRepoHandle(BareJID user_id) {
		try {
			return repoPool.take();
		} catch (InterruptedException ex) {
			log.log(Level.WARNING, "Couldn't obtain DataRepository from the pool", ex);
		}

		return null;
	}

	@Override
	public void releaseRepoHandle(DataRepository repo) {
		repoPool.offer(repo);
	}

	@Override
	public boolean automaticSchemaManagement() {
		if (repoPool.isEmpty()) {
			return true;
		}
		return repoPool.peek().automaticSchemaManagement();
	}

	@Override
	public void checkConnectivity(Duration watchdogTime) {
		repoPool.forEach(repo -> repo.checkConnectivity(watchdogTime));
	}

	@Override
	public boolean checkSchemaVersion(DataSourceAware<? extends DataSource> datasource, boolean shutdownServer) {
		DataRepository repo = takeRepoHandle(null);

		if (repo != null) {
			return repo.checkSchemaVersion(datasource, shutdownServer);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
			return false;
		}
	}

	@Override
	public Optional<Version> getSchemaVersion(String component) {
		DataRepository repo = takeRepoHandle(null);

		if (repo != null) {
			return repo.getSchemaVersion(component);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
			return Optional.empty();
		}
	}

	@Override
	public boolean checkTable(String tableName) throws SQLException {
		DataRepository repo = takeRepoHandle(null);

		if (repo != null) {
			return repo.checkTable(tableName);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
		}

		return false;
	}

	@Override
	public boolean checkTable(String tableName, String createTableQuery) throws SQLException {
		DataRepository repo = takeRepoHandle(null);

		if (repo != null) {
			return repo.checkTable(tableName, createTableQuery);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
		}

		return false;
	}

	@Override
	public Statement createStatement(BareJID user_id) throws SQLException {
		DataRepository repo = takeRepoHandle(user_id);

		if (repo != null) {
			return repo.createStatement(user_id);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
		}

		return null;
	}

	@Override
	public PreparedStatement getPreparedStatement(BareJID user_id, String stIdKey) throws SQLException {
		DataRepository repo = takeRepoHandle(user_id);

		if (repo != null) {
			return repo.getPreparedStatement(user_id, stIdKey);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
		}

		return null;
	}

	@Override
	public PreparedStatement getPreparedStatement(int hashCode, String stIdKey) throws SQLException {
		DataRepository repo = takeRepoHandle(null);

		if (repo != null) {
			return repo.getPreparedStatement(hashCode, stIdKey);
		} else {
			log.log(Level.WARNING, "repo is NULL, pool empty? - {0}", repoPool.size());
		}

		return null;
	}

	@Override
	public String getResourceUri() {
		if (resource_uri == null && !repoPool.isEmpty()) {
			return takeRepoHandle(null).getResourceUri();
		}
		return resource_uri;
	}

	@Override
	public dbTypes getDatabaseType() {
		return database;
	}

	@Override
	public void initPreparedStatement(String stIdKey, String query) throws SQLException {
		for (DataRepository dataRepository : repoPool) {
			dataRepository.initPreparedStatement(stIdKey, query);
		}
	}

	@Override
	public void initPreparedStatement(String stIdKey, String query, int autoGeneratedKeys) throws SQLException {
		for (DataRepository dataRepository : repoPool) {
			dataRepository.initPreparedStatement(stIdKey, query, autoGeneratedKeys);
		}
	}

	@Override
	public void initialize(String resource_uri) throws DBInitException {
		this.resource_uri = resource_uri;

		if (this.database == null) {
			database = DataRepositoryImpl.parseDatabaseType(resource_uri);
		}
	}

	@Override
	public void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) {
			}
		}

		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) {
			}
		}
	}

	@Override
	public void startTransaction() throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	public void commit() throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	public void rollback() throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	public void endTransaction() throws SQLException {
		// TODO Auto-generated method stub

	}

	@Override
	public int getPoolSize() {
		return repoPool.size();
	}

}
