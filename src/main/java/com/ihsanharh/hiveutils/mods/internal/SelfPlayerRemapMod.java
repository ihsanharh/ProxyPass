package com.ihsanharh.hiveutils.mods.internal;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ConnectedClient;

import java.util.*;

@Log4j2
public class SelfPlayerRemapMod implements ProxyMod {
    private static final Map<ProxyPlayerSession, Map<UUID, UUID>> SESSION_REMAPS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<UUID, UUID> getRemapTable(ProxyPlayerSession session) {
        return SESSION_REMAPS.computeIfAbsent(session, k -> new HashMap<>());
    }

    private static boolean needsRemap(ProxyPlayerSession session) {
        ConnectedClient client = ConnectedClient.getInstance();
        if (!client.isConnected()) return false;

        String machineUser = client.getUsername();
        String proxyUser   = session.getAuthData().getDisplayName();
        return !machineUser.equalsIgnoreCase(proxyUser);
    }

    private static UUID toFakeUuid(Map<UUID, UUID> table, UUID real) {
        return table.computeIfAbsent(real, k -> UUID.randomUUID());
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (!needsRemap(session)) return ModResult.PASS;

        String machineUser = ConnectedClient.getInstance().getUsername();
        Map<UUID, UUID> remapTable = getRemapTable(session);

        if (packet instanceof PlayerListPacket playerListPacket) {
            boolean modified = false;
            List<PlayerListPacket.Entry> entries = playerListPacket.getEntries();

            if (playerListPacket.getAction() == PlayerListPacket.Action.ADD) {
                for (int i = 0; i < entries.size(); i++) {
                    PlayerListPacket.Entry old = entries.get(i);

                    if (machineUser.equalsIgnoreCase(old.getName())) {
                        UUID real = old.getUuid();
                        UUID fake = toFakeUuid(remapTable, real);

                        PlayerListPacket.Entry rebuilt = new PlayerListPacket.Entry(fake);
                        rebuilt.setEntityId(old.getEntityId());
                        rebuilt.setName(old.getName());
                        rebuilt.setXuid(old.getXuid());
                        rebuilt.setPlatformChatId(old.getPlatformChatId());
                        rebuilt.setBuildPlatform(old.getBuildPlatform());
                        rebuilt.setSkin(old.getSkin());
                        rebuilt.setTeacher(old.isTeacher());
                        rebuilt.setHost(old.isHost());
                        rebuilt.setTrustedSkin(old.isTrustedSkin());
                        rebuilt.setSubClient(old.isSubClient());
                        rebuilt.setColor(old.getColor());
                        entries.set(i, rebuilt);

                        modified = true;

                        log.debug("[SelfRemap] PlayerList ADD  {} : {} -> {}", machineUser, real, fake);
                    }
                }

            } else if (playerListPacket.getAction() == PlayerListPacket.Action.REMOVE) {
                for (int i = 0; i < entries.size(); i++) {
                    PlayerListPacket.Entry old = entries.get(i);
                    UUID fake = remapTable.get(old.getUuid());

                    if (fake != null) {
                        PlayerListPacket.Entry rebuilt = new PlayerListPacket.Entry(fake);
                        entries.set(i, rebuilt);

                        modified = true;
                        
                        log.debug("[SelfRemap] PlayerList REMOVE remapped -> {}", fake);
                    }
                }
            }

            return modified ? ModResult.MODIFIED : ModResult.PASS;
        }

        if (packet instanceof AddPlayerPacket addPlayerPacket) {
            UUID real = addPlayerPacket.getUuid();

            if (remapTable.containsKey(real)) {
                UUID fake = remapTable.get(real);
                addPlayerPacket.setUuid(fake);
                
                log.debug("[SelfRemap] AddPlayer UUID matched: {} -> {}", real, fake);

                return ModResult.MODIFIED;
            }
        }

        if (packet instanceof PlayerSkinPacket playerSkinPacket) {
            UUID real = playerSkinPacket.getUuid();

            if (remapTable.containsKey(real)) {
                UUID fake = remapTable.get(real);
                playerSkinPacket.setUuid(fake);

                log.debug("[SelfRemap] PlayerSkin UUID matched: {} -> {}", real, fake);

                return ModResult.MODIFIED;
            }
        }

        return ModResult.PASS;
    }
}
