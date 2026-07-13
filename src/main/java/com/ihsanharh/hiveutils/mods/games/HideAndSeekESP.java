package com.ihsanharh.hiveutils.mods.games;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.PlayerData;
import com.ihsanharh.hiveutils.core.PlayerStore;
import com.ihsanharh.hiveutils.utils.TextPacketUtils;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
public class HideAndSeekESP extends BaseMod {
    private Map<String, Long> players = new ConcurrentHashMap<>();
    private Map<Long, Long> nametags = new ConcurrentHashMap<>();
    private volatile boolean ongoing = false;

    @Override
    public List<String> getDefaultServerFilters() {
        return List.of("HIDE");
    }

    @Override
    public void onInitialize() {
        context.getServerStore().addListener(getServerFilters(), (oldServer, newServer) -> {
            ongoing = false;
            ProxyPlayerSession session = context.getPlayerSession();
            
            cleanup(session);
            if (session != null) {
                TextPacketUtils.sendRawToClientImmediately(session, "[HideAndSeekESP] enabled in this server.");
            }
        });
    }

    private void spawnNametag(ProxyPlayerSession session, Long runtimeId, String text, Vector3f position) {
        long entityId = runtimeId + ThreadLocalRandom.current().nextLong(66043, 76043 + 1);
        Vector3f entityPos = Vector3f.from(position.getX(), position.getY() - 2, position.getZ());

        AddEntityPacket addEntityPacket = new AddEntityPacket();
        addEntityPacket.setUniqueEntityId(entityId);
        addEntityPacket.setRuntimeEntityId(entityId);
        addEntityPacket.setRotation(Vector2f.ZERO);
        addEntityPacket.setPosition(entityPos);
        addEntityPacket.setMotion(Vector3f.ZERO);

        EntityDataMap entityDataMap = new EntityDataMap();
        entityDataMap.put(EntityDataTypes.NAME, "§a" + text);
        entityDataMap.put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
        entityDataMap.put(EntityDataTypes.SCALE, 0.0f);

        addEntityPacket.setMetadata(entityDataMap);
        addEntityPacket.setIdentifier("minecraft:sheep");

        session.getUpstream().sendPacketImmediately(addEntityPacket);
        nametags.put(runtimeId, entityId);
    }

    private void despawnNametag(ProxyPlayerSession session, Long runtimeId) {
        Long entityId = nametags.get(runtimeId);

        if (entityId != null) {
            RemoveEntityPacket removeEntityPacket = new RemoveEntityPacket();

            removeEntityPacket.setUniqueEntityId(entityId);
            session.getUpstream().sendPacketImmediately(removeEntityPacket);
            nametags.remove(runtimeId);
        }
    }

    private void cleanup(ProxyPlayerSession session) {
        int playerCount = this.players.size();
        int nametagCount = this.nametags.size();

        if (session != null) {
            for (Long runtimeId : new ArrayList<>(this.nametags.keySet())) {
                try {
                    this.despawnNametag(session, runtimeId);
                } catch (Exception e) {
                    log.debug("[HideAndSeekESP] Failed to despawn nametag during cleanup", e);
                }
            }
        }

        this.players.clear();

        log.debug("[HideAndSeekESP] cleaned up: {} players & {} nametags", playerCount, nametagCount);
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        PlayerStore playerStore = context.getPlayerStore();

        if (packet instanceof AddPlayerPacket addPlayerPacket) {
            PlayerData player = playerStore.getPlayer(addPlayerPacket.getRuntimeEntityId());

            if (player == null) {
                return ModResult.PASS;
            }

            Long playerRuntimeId = player.getRuntimeId();

            players.put(player.getUuid().toString(), playerRuntimeId);
            this.despawnNametag(session, playerRuntimeId);

            EntityDataMap entityDataMap = addPlayerPacket.getMetadata();
            Object nameObj = entityDataMap.get(EntityDataTypes.NAME);
            String nametag = nameObj != null ? nameObj.toString() : "";

            if (nametag.isEmpty()) {
                entityDataMap.put(EntityDataTypes.NAME, "§e" + player.getPlayerName());

                return ModResult.MODIFIED;
            }
        }

        if (packet instanceof RemoveEntityPacket removeEntityPacket) {
            PlayerData player = playerStore.getPlayer(removeEntityPacket.getUniqueEntityId());

            if (player == null || !players.containsKey(player.getUuid().toString())) {
                return ModResult.PASS;
            }
            if (this.ongoing) {
                this.spawnNametag(session, removeEntityPacket.getUniqueEntityId(), player.getPlayerName(), player.getPosition());
            }
        }

        if (packet instanceof TextPacket textPacket) {
            if (textPacket.getType() != TextPacket.Type.RAW) {
                return ModResult.PASS;
            }

            String lowercasedMsg = textPacket.getMessage().toLowerCase();

            if (!this.ongoing && lowercasedMsg.contains("seconds until seekers released")) {
                log.debug("[HideAndSeekESP] Game started.");
                this.ongoing = true;
            } else if (this.ongoing && lowercasedMsg.contains("game over")) {
                log.debug("[HideAndSeekESP] Game ended.");
                this.ongoing = false;
                this.cleanup(session);
            }
        }

        if (packet instanceof PlayerListPacket playerListPacket) {
            if (playerListPacket.getAction() == PlayerListPacket.Action.REMOVE) {
                for (PlayerListPacket.Entry player : playerListPacket.getEntries()) {
                    String uuid = player.getUuid().toString();
                    Long runtimeId = players.remove(uuid);

                    if (runtimeId != null) {
                        this.despawnNametag(session, runtimeId);
                    }
                }
            }
        }

        return ModResult.PASS;
    }
}
