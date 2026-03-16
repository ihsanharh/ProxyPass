package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.math.vector.Vector3f;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class PlayerStore {
    private static final PlayerStore INSTANCE = new PlayerStore();

    // THREE TYPE-SAFE MAPS pointing to the exact same PlayerData objects
    private final Map<String, PlayerData> byUuid = new ConcurrentHashMap<>();
    private final Map<Long, PlayerData> byEntityId = new ConcurrentHashMap<>();
    private final Map<String, PlayerData> byName = new ConcurrentHashMap<>();

    public static PlayerStore getInstance() {
        return INSTANCE;
    }

    // Overload 1: From PlayerListPacket (No Position)
    public void addPlayer(String uuid, String playerName, long entityId) {
        addPlayer(uuid, playerName, entityId, Vector3f.ZERO);
    }

    // Overload 2: From AddPlayerPacket (With Position)
    public void addPlayer(String uuid, String playerName, long runtimeId, Vector3f position) {
        // If they already exist, just grab them and update. Otherwise, create new.
        PlayerData data = byUuid.computeIfAbsent(uuid, k -> new PlayerData(uuid, playerName, runtimeId, position));

        data.setRuntimeId(runtimeId);
        data.setPosition(position);

        // Map the shortcuts directly to the object (Instant O(1) lookups later)
        if (runtimeId != 0) {
            byEntityId.put(runtimeId, data);
        }
        if (playerName != null) {
            byName.put(playerName.toLowerCase(), data); // Lowercase for easier command lookups!
        }
    }

    public void updatePlayerPosition(long runtimeId, Vector3f position) {
        PlayerData data = byEntityId.get(runtimeId); // Instant lookup
        if (data != null) {
            data.updatePosition(position);
        }
    }

    // Safely removes from ALL maps in O(1) time. No looping required!
    public void removePlayer(String uuid) {
        PlayerData removed = byUuid.remove(uuid);

        if (removed != null) {
            byEntityId.remove(removed.getRuntimeId());
            byName.remove(removed.getPlayerName().toLowerCase());
        }
    }

    // Additional helper for RemoveEntityPacket (which only gives you an ID)
    public void removePlayerById(long runtimeId) {
        PlayerData removed = byEntityId.remove(runtimeId);

        if (removed != null) {
            byUuid.remove(removed.getUuid());
            byName.remove(removed.getPlayerName().toLowerCase());
        }
    }

    public PlayerData getPlayer(String uuid) {
        return byUuid.get(uuid);
    }

    public PlayerData getPlayer(long runtimeId) {
        return byEntityId.get(runtimeId);
    }

    public PlayerData getPlayerByName(String playerName) {
        if (playerName == null)
            return null;
        return byName.get(playerName.toLowerCase());
    }

    public void clear() {
        byUuid.clear();
        byEntityId.clear();
        byName.clear();
    }

    public void printLiveMap() {
        log.info("\n========== PLAYER STORE DIAGNOSTICS ==========");

        log.info(byName.toString().replace(", ", "\n"));

        // 1. Map Sync Check: If these numbers don't match, you have a memory leak!
        log.info("Map Sync Status -> UUIDs: {}, EntityIDs: {}, Names: {}",
                byUuid.size(), byEntityId.size(), byName.size());

        log.info(
                "----------------------------------------------------------------------------------------------------");
        // 2. The Table Header (Using standard Java String formatting for perfect
        // alignment)
        log.info(String.format("| %-36s | %-16s | %-10s | %-22s |", "UUID", "Username", "Entity ID",
                "Position (X, Y, Z)"));
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

                log.info(String.format("| %-36s | %-16s | %-10d | %-22s |",
                        data.getUuid(),
                        data.getPlayerName(),
                        data.getRuntimeId(),
                        posStr));
            }
        }
        log.info(
                "====================================================================================================\n");
    }
}