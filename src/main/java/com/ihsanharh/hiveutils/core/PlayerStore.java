package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.math.vector.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStore {
    private final Map<String, PlayerData> byUuid = new ConcurrentHashMap<>();
    private final Map<String, PlayerData> byName = new ConcurrentHashMap<>();
    private final Map<Long, PlayerData> byEntityId = new ConcurrentHashMap<>();
    private final Map<Long, PlayerData> byRuntimeId = new ConcurrentHashMap<>();
    private Boolean clearable = false;

    public void addPlayer(String uuid, String playerName, long entityId) {
        this.addPlayer(uuid, playerName, entityId, 0, Vector3f.ZERO);
    }

    public void addPlayer(String uuid, String playerName, long entityId, long runtimeId, Vector3f position) {
        PlayerData data = this.byUuid.computeIfAbsent(uuid,
                k -> new PlayerData(uuid, playerName, entityId, runtimeId, position));

        this.byName.put(playerName.toLowerCase(), data);

        if (entityId != 0) {
            data.setEntityId(entityId);
            byEntityId.put(entityId, data);
        }

        if (runtimeId != 0) {
            data.setRuntimeId(runtimeId);
            byRuntimeId.put(runtimeId, data);
        }
    }

    public void updatePlayerPosition(long runtimeId, Vector3f position) {
        PlayerData data = byRuntimeId.get(runtimeId);
        if (data != null) {
            data.updatePosition(position);
        }
    }

    public void removePlayer(String uuid) {
        PlayerData removed = byUuid.remove(uuid);

        if (removed != null) {
            byName.remove(removed.getPlayerName().toLowerCase());
            byEntityId.remove(removed.getEntityId());
            byRuntimeId.remove(removed.getRuntimeId());
        }
    }

    public void removePlayerByEntityId(long entityId) {
        PlayerData removed = this.byEntityId.remove(entityId);

        if (removed != null)
            this.removePlayer(removed.getUuid());
    }

    public void removePlayerByRuntimeId(long runtimeId) {
        PlayerData removed = this.byRuntimeId.remove(runtimeId);

        if (removed != null)
            this.removePlayer(removed.getUuid());
    }

    public PlayerData getPlayer(String uuid) {
        return byUuid.get(uuid);
    }

    public PlayerData getPlayer(long runtimeId) {
        return byRuntimeId.get(runtimeId);
    }

    public PlayerData getPlayerByEntityId(long entityId) {
        return byEntityId.get(entityId);
    }

    public PlayerData getPlayerByName(String playerName) {
        return byName.get(playerName.toLowerCase());
    }

    public Boolean toggleClearable() {
        this.clearable = !this.clearable;
        return this.clearable;
    }

    public void clear() {
        if (!clearable)
            return;

        byUuid.clear();
        byName.clear();
        byEntityId.clear();
        byRuntimeId.clear();
    }
}