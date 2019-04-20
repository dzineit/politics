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
package pw.ollie.politics.world.plot;

import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import pw.ollie.politics.Politics;
import pw.ollie.politics.data.Storable;
import pw.ollie.politics.event.PoliticsEventFactory;
import pw.ollie.politics.event.plot.subplot.SubplotPrivilegeChangeEvent;
import pw.ollie.politics.group.privilege.Privilege;
import pw.ollie.politics.group.privilege.PrivilegeType;
import pw.ollie.politics.group.privilege.Privileges;
import pw.ollie.politics.util.Position;
import pw.ollie.politics.util.math.Cuboid;
import pw.ollie.politics.util.math.Vector3i;
import pw.ollie.politics.world.PoliticsWorld;

import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A subplot must be wholly contained within a single plot. Subplots do not have owner groups as normal plots, as they
 * are always within a plot owned by a group already. Rather, subplots have a single player assigned as their owner.
 * This player is afforded all possible plot privileges within the subplot, and may also assign plot privileges to other
 * players. This player must be a member of a group owning the parent plot, as must the other players who are given
 * privileges within the subplot.
 * <p>
 * A subplot may have different privilege settings to its parent plot. Subplot privileges override plot privileges.
 */
public final class Subplot implements Storable {
    private final PoliticsWorld world;
    private final int id;
    private final int parentX;
    private final int parentZ;

    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int xSize;
    private final int ySize;
    private final int zSize;

    private final Map<UUID, Set<Privilege>> individualPrivileges;

    private UUID owner;

    public Subplot(PoliticsWorld world, int id, int parentX, int parentZ, int baseX, int baseY, int baseZ, int xSize, int ySize, int zSize, UUID owner) {
        this.world = world;
        this.id = id;
        this.parentX = parentX;
        this.parentZ = parentZ;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;
        this.owner = owner;
        this.individualPrivileges = new THashMap<>();
    }

    public Subplot(PoliticsWorld world, int id, int parentX, int parentZ, Cuboid cuboid, UUID owner) {
        this(world, id, parentX, parentZ, cuboid.getMinX(), cuboid.getMinY(), cuboid.getMinZ(), cuboid.getXSize(), cuboid.getYSize(), cuboid.getZSize(), owner);
    }

    public Subplot(BasicBSONObject bObj) {
        world = Politics.getWorld(bObj.getString("world"));
        id = bObj.getInt("id");
        parentX = bObj.getInt("parent-x");
        parentZ = bObj.getInt("parent-z");
        baseX = bObj.getInt("base-x");
        baseY = bObj.getInt("base-y");
        baseZ = bObj.getInt("base-Z");
        xSize = bObj.getInt("x-size");
        ySize = bObj.getInt("y-size");
        zSize = bObj.getInt("z-size");
        owner = UUID.fromString(bObj.getString("owner"));

        individualPrivileges = new THashMap<>();
        if (bObj.containsField("privileges")) {
            BasicBSONObject privilegesObj = (BasicBSONObject) bObj.get("privileges");

            for (String privilegesKey : privilegesObj.keySet()) {
                Set<Privilege> privileges = new THashSet<>();
                individualPrivileges.put(UUID.fromString(privilegesKey), privileges);

                for (Object object : (BasicBSONList) privilegesObj.get(privilegesKey)) {
                    privileges.add(Politics.getPrivilegeManager().getPrivilege(object.toString()));
                }
            }
        }
    }

    public PoliticsWorld getWorld() {
        return world;
    }

    public int getId() {
        return id;
    }

    public int getParentX() {
        return parentX;
    }

    public int getParentZ() {
        return parentZ;
    }

    public Plot getParent() {
        return world.getPlotAtChunkPosition(parentX, parentZ);
    }

    public Chunk getParentChunk() {
        return getParent().getChunk();
    }

    public int getBaseX() {
        return baseX;
    }

    public int getBaseY() {
        return baseY;
    }

    public int getBaseZ() {
        return baseZ;
    }

    public Position getBasePosition() {
        return new Position(world.getName(), (parentX * 16) + baseX, baseY, (parentZ * 16) + baseZ);
    }

    public int getXSize() {
        return xSize;
    }

    public int getYSize() {
        return ySize;
    }

    public int getZSize() {
        return zSize;
    }

    public Vector3i getSize() {
        return new Vector3i(xSize, ySize, zSize);
    }

    public UUID getOwnerId() {
        return owner;
    }

    public void setOwner(UUID ownerId) {
        this.owner = ownerId;
    }

    public Cuboid getCuboid() {
        return new Cuboid(getBasePosition().toLocation(), getSize());
    }

    public boolean contains(Location location) {
        return getCuboid().contains(location);
    }

    public boolean can(UUID playerId, Privilege privilege) {
        if (!privilege.getTypes().contains(PrivilegeType.PLOT)) {
            return false;
        }

        if (playerId.equals(owner)) {
            return true;
        }

        Set<Privilege> privileges = individualPrivileges.get(playerId);
        return privileges != null && privileges.contains(privilege);
    }

    public boolean can(Player player, Privilege privilege) {
        return can(player.getUniqueId(), privilege);
    }

    public boolean givePrivilege(UUID playerId, Privilege privilege) {
        if (!privilege.getTypes().contains(PrivilegeType.PLOT)) {
            return false;
        }

        SubplotPrivilegeChangeEvent event = PoliticsEventFactory.callSubplotPrivilegeChangeEvent(getParent(), this, playerId, privilege, true);
        if (event.isCancelled()) {
            return false;
        }

        individualPrivileges.putIfAbsent(playerId, new THashSet<>());
        individualPrivileges.get(playerId).add(privilege);
        return true;
    }

    public boolean givePrivilege(Player player, Privilege privilege) {
        return givePrivilege(player.getUniqueId(), privilege);
    }

    public boolean revokePrivilege(UUID playerId, Privilege privilege) {
        if (!privilege.getTypes().contains(PrivilegeType.PLOT)) {
            return false;
        }

        SubplotPrivilegeChangeEvent event = PoliticsEventFactory.callSubplotPrivilegeChangeEvent(getParent(), this, playerId, privilege, false);
        if (event.isCancelled()) {
            return false;
        }

        Set<Privilege> currentPrivileges = individualPrivileges.get(playerId);
        if (currentPrivileges == null) {
            return false;
        }

        return currentPrivileges.remove(privilege);
    }

    public boolean revokePrivilege(Player player, Privilege privilege) {
        return revokePrivilege(player.getUniqueId(), privilege);
    }

    @Override
    public BSONObject toBSONObject() {
        BSONObject result = new BasicBSONObject();
        result.put("world", world.getName());
        result.put("id", id);
        result.put("parent-x", parentX);
        result.put("parent-z", parentZ);
        result.put("base-x", baseX);
        result.put("base-y", baseY);
        result.put("base-z", baseZ);
        result.put("x-size", xSize);
        result.put("y-size", ySize);
        result.put("z-size", zSize);
        result.put("owner", owner.toString());

        BasicBSONObject privilegesObj = new BasicBSONObject();
        for (UUID individualId : individualPrivileges.keySet()) {
            BasicBSONList privilegeList = new BasicBSONList();
            Set<Privilege> privileges = individualPrivileges.get(individualId);
            privilegeList.addAll(privileges.stream().map(Privilege::getName).collect(Collectors.toSet()));
            privilegesObj.put(individualId.toString(), privilegeList);
        }
        result.put("privileges", privilegesObj);

        return result;
    }

    @Override
    public boolean canStore() {
        return true;
    }
}
