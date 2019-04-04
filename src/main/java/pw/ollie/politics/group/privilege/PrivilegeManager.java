/*
 * This file is part of Politics.
 *
 * Copyright (c) 2019 Oliver Stanley
 * Politics is licensed under the Affero General Public License Version 3.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pw.ollie.politics.group.privilege;

import pw.ollie.politics.Politics;

import java.util.HashMap;
import java.util.Map;

public final class PrivilegeManager {
    private final Politics plugin;
    private final Map<String, Privilege> privileges = new HashMap<>();

    public PrivilegeManager(Politics plugin) {
        this.plugin = plugin;

        loadDefaultPrivileges();
    }

    private final void loadDefaultPrivileges() {
//        registerPrivileges(GroupPrivileges.ALL);
//        registerPrivileges(GroupPlotPrivileges.ALL);
    }

    public boolean registerPrivilege(Privilege privilege) {
        return privileges.put(privilege.getName(), privilege) == null;
    }

    public boolean registerPrivileges(Privilege... privileges) {
        for (Privilege p : privileges) {
            if (!registerPrivilege(p)) {
                return false;
            }
        }
        return true;
    }

    public Privilege getPrivilege(String name) {
        return privileges.get(name.toUpperCase().replaceAll(" ", "_"));
    }
}
