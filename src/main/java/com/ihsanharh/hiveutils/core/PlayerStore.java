package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.math.vector.Vector3f;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class PlayerStore {
    private static final PlayerStore INSTANCE = new PlayerStore();
    private final Map<String, PlayerData> byUuid = new ConcurrentHashMap<>();
    private final Map<String, PlayerData> byName = new ConcurrentHashMap<>();
    private final Map<Long, PlayerData> byEntityId = new ConcurrentHashMap<>();
    private final Map<Long, PlayerData> byRuntimeId = new ConcurrentHashMap<>();

    public static PlayerStore getInstance() {
        return INSTANCE;
    }

    public void addPlayer(String uuid, String playerName, long entityId) {
        addPlayer(uuid, playerName, entityId, 0, Vector3f.ZERO);
    }

    public void addPlayer(String uuid, String playerName, long entityId, long runtimeId, Vector3f position) {
        PlayerData data = byUuid.computeIfAbsent(uuid,
                k -> new PlayerData(uuid, playerName, entityId, runtimeId, position));

        byName.put(playerName.toLowerCase(), data);

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
        PlayerData removed = byEntityId.remove(entityId);

        if (removed != null)
            removePlayer(removed.getUuid());
    }

    public void removePlayerByRuntimeId(long runtimeId) {
        PlayerData removed = byRuntimeId.remove(runtimeId);

        if (removed != null)
            removePlayer(removed.getUuid());
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

    public void clear() {
        byUuid.clear();
        byName.clear();
        byEntityId.clear();
        byRuntimeId.clear();
    }

    public void printLiveMap() {
        log.info("\n========== PLAYER STORE DIAGNOSTICS ==========");

        // 1. Map Sync Check: If these numbers don't match, you have a memory leak!
        log.info("Map Sync Status -> UUIDs: {}, EntityIDs: {}, Names: {}",
                byUuid.size(), byEntityId.size(), byName.size());

        log.info(
                "----------------------------------------------------------------------------------------------------");
        // 2. The Table Header (Using standard Java String formatting for perfect
        // alignment)
        log.info(String.format("| %-36s | %-16s | %-5s | %-5s | %-22s |", "UUID", "Username", "Entity ID",
                "Runtime ID", "Position (X, Y, Z)"));
        log.info(
                "----------------------------------------------------------------------------------------------------");

        // 3. The Data Rows
        if (byUuid.isEmpty()) {
            log.info(String.format("| %-93s |", "No players currently tracked in this world."));
        } else {
            for (PlayerData data : byUuid.values()) {

                // Format the Vector3f so it doesn't look like a messy Java object string
                String posStr = "UNKNOWN";
                if (data.getPosition() != null) {
                    posStr = String.format("%.1f, %.1f, %.1f",
                            data.getPosition().getX(),
                            data.getPosition().getY(),
                            data.getPosition().getZ());
                }

                log.info(String.format("| %-36s | %-16s | %-5d | %-5d | %-22s |",
                        data.getUuid(),
                        data.getPlayerName(),
                        data.getEntityId(),
                        data.getRuntimeId(),
                        posStr));
            }
        }
        log.info(
                "====================================================================================================\n");
    }
}