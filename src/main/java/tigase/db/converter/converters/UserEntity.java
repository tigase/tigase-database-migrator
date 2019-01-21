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
package tigase.db.converter.converters;

import tigase.db.converter.RowEntity;
import tigase.xmpp.jid.BareJID;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserEntity
		implements RowEntity {

	BareJID jid;
	String password;
	List<UserRosterItem> rosterItems = new CopyOnWriteArrayList<>();

	public UserEntity(BareJID jid, String password) {
		this.jid = jid;
		this.password = password;
	}

	public BareJID getJid() {
		return jid;
	}

	public String getPassword() {
		return password;
	}

	@Override
	public String getID() {
		return String.valueOf(jid);
	}

	public boolean addRosterItem(UserRosterItem item) {
		return rosterItems.add(item);
	}

	public List<UserRosterItem> getRosterItems() {
		return Collections.unmodifiableList(rosterItems);
	}

	public boolean addRosterItems(List<UserRosterItem> items) {
		return rosterItems.addAll(items);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("UserEntity{");
		sb.append("jid=").append(jid);
		sb.append(", password='").append(password).append('\'');
		sb.append(", rosterItems=").append(rosterItems);
		sb.append('}');
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		UserEntity that = (UserEntity) o;

		if (jid != null ? !jid.equals(that.jid) : that.jid != null) {
			return false;
		}
		if (password != null ? !password.equals(that.password) : that.password != null) {
			return false;
		}
		return rosterItems != null ? rosterItems.equals(that.rosterItems) : that.rosterItems == null;
	}

	@Override
	public int hashCode() {
		int result = jid != null ? jid.hashCode() : 0;
		result = 31 * result + (password != null ? password.hashCode() : 0);
		result = 31 * result + (rosterItems != null ? rosterItems.hashCode() : 0);
		return result;
	}
}
