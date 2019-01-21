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

import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.List;

class UserRosterItem {

	/*
		CREATE TABLE [dbo].[rosterusers] (
		[username] [varchar] (250) NOT NULL,
		[jid] [varchar] (250) NOT NULL,
		[nick] [text] NOT NULL,
		[subscription] [char] (1) NOT NULL,
		[ask] [char] (1) NOT NULL,
		[askmessage] [text] NOT NULL,
		[server] [char] (1) NOT NULL,
		[subscribe] [text] NOT NULL,
		[type] [text] NULL,
		[created_at] [datetime] NOT NULL DEFAULT GETDATE()
	 */

	final List<String> groups;
	final BareJID jid;
	final String nick;
	final BareJID ownerJid;
	final RosterElement rosterElement;
	final RosterAbstract.SubscriptionType subscription;

	public UserRosterItem(BareJID ownerJid, BareJID iid, String nick, String subscription, List<String> groups) {
		this.ownerJid = ownerJid;
		this.jid = iid;
		this.nick = nick;

		switch (subscription) {
			case "B":
				this.subscription = RosterAbstract.SubscriptionType.both;
				break;
			case "T":
				this.subscription = RosterAbstract.SubscriptionType.to;
				break;
			case "F":
				this.subscription = RosterAbstract.SubscriptionType.from;
				break;
			case "N":
			default:
				this.subscription = RosterAbstract.SubscriptionType.none;
				break;
		}
		this.groups = groups;
		rosterElement = new RosterElement(JID.jidInstance(jid), nick, groups.toArray(new String[0]));
		rosterElement.setSubscription(this.subscription);
	}

	public RosterElement getRosterElement() {
		return rosterElement;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("UserRosterItem{");
		sb.append("ownerJid=").append(ownerJid);
		sb.append(", jid=").append(jid);
		sb.append(", nick='").append(nick).append('\'');
		sb.append(", subscription=").append(subscription);
		sb.append(", groups=").append(groups);
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

		UserRosterItem that = (UserRosterItem) o;

		if (ownerJid != null ? !ownerJid.equals(that.ownerJid) : that.ownerJid != null) {
			return false;
		}
		if (jid != null ? !jid.equals(that.jid) : that.jid != null) {
			return false;
		}
		if (nick != null ? !nick.equals(that.nick) : that.nick != null) {
			return false;
		}
		if (subscription != null ? !subscription.equals(that.subscription) : that.subscription != null) {
			return false;
		}
		return groups != null ? groups.equals(that.groups) : that.groups == null;
	}

	@Override
	public int hashCode() {
		int result = ownerJid != null ? ownerJid.hashCode() : 0;
		result = 31 * result + (jid != null ? jid.hashCode() : 0);
		result = 31 * result + (nick != null ? nick.hashCode() : 0);
		result = 31 * result + (subscription != null ? subscription.hashCode() : 0);
		result = 31 * result + (groups != null ? groups.hashCode() : 0);
		return result;
	}
}
