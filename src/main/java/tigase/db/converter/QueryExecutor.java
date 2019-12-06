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
package tigase.db.converter;

import tigase.db.DataRepository;
import tigase.db.DataRepositoryPool;
import tigase.db.TigaseDBException;

import java.sql.PreparedStatement;

public class QueryExecutor {

	private DataRepoPool dataRepoPool;

	public QueryExecutor() {
	}

	public <X> X executeQuery(String preparedStatementId, QueryFunction<PreparedStatement, X> fun)
			throws Exception {

		final DataRepository dataRepositoryFromPool = dataRepoPool.takeRepoHandle(null);
		if (DataRepositoryPool.class.isAssignableFrom(dataRepositoryFromPool.getClass())) {
			throw new TigaseDBException("Wrong DataRepositoryImplementation");
		}
		final PreparedStatement preparedStatement = dataRepositoryFromPool.getPreparedStatement(0, preparedStatementId);
		final X apply = fun.apply(preparedStatement);
		dataRepoPool.addRepo(dataRepositoryFromPool);
		return apply;
	}

	void initialise(DataRepoPool dataRepoPool) {
		this.dataRepoPool = dataRepoPool;
	}

	@FunctionalInterface
	public interface QueryFunction<T, R> {

		R apply(T t) throws Exception;
	}

}
