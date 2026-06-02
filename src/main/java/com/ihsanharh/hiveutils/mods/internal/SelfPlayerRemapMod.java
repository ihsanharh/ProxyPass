package com.ihsanharh.hiveutils.mods.internal;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.ConnectedClient;

import java.util.*;

@Log4j2
public class SelfPlayerRemapMod extends BaseMod {
    private final Map<UUID, UUID> remapTable = new HashMap<>();

    private UUID toFakeUuid(UUID real) {
        return remapTable.computeIfAbsent(real, k -> UUID.randomUUID());
    }

    private boolean needsRemap(ProxyPlayerSession session) {
        ConnectedClient client = context.getConnectedClient();
        if (!client.isConnected()) return false;

        String machineUser = client.getUsername();
        String proxyUser = session.getAuthData().getDisplayName();
        return !machineUser.equalsIgnoreCase(proxyUser);
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (!needsRemap(session)) return ModResult.PASS;

        ConnectedClient client = context.getConnectedClient();
        String clientUsername = client.getUsername();

        if (packet instanceof PlayerListPacket playerListPacket) {
            boolean modified = false;
            List<PlayerListPacket.Entry> entries = playerListPacket.getEntries();

            if (playerListPacket.getAction() == PlayerListPacket.Action.ADD) {
                for (int i = 0; i < entries.size(); i++) {
                    PlayerListPacket.Entry player = entries.get(i);
                    UUID playerUuid = player.getUuid();

                    if (clientUsername.equalsIgnoreCase(player.getName())) {
                        UUID fakeUuid = toFakeUuid(playerUuid);

                        PlayerListPacket.Entry rebuilt = new PlayerListPacket.Entry(fakeUuid);
                        rebuilt.setEntityId(player.getEntityId());
                        rebuilt.setName(player.getName());
                        rebuilt.setXuid(player.getXuid());
                        rebuilt.setPlatformChatId(player.getPlatformChatId());
                        rebuilt.setBuildPlatform(player.getBuildPlatform());
                        rebuilt.setSkin(player.getSkin());
                        rebuilt.setTeacher(player.isTeacher());
                        rebuilt.setHost(player.isHost());
                        rebuilt.setTrustedSkin(player.isTrustedSkin());
                        rebuilt.setSubClient(player.isSubClient());
                        rebuilt.setColor(player.getColor());

                        entries.set(i, rebuilt);
                        modified = true;

                        log.debug("[SelfRemap : PlayerList.ADD] Local Client is here, remapped its uuid: {} -> {}", playerUuid, fakeUuid);
                    }
                }

            } else if (playerListPacket.getAction() == PlayerListPacket.Action.REMOVE) {
                for (int i = 0; i < entries.size(); i++) {
                    PlayerListPacket.Entry player = entries.get(i);
                    UUID fakeUuid = remapTable.get(player.getUuid());

                    if (fakeUuid != null) {
                        PlayerListPacket.Entry rebuilt = new PlayerListPacket.Entry(fakeUuid);
                        entries.set(i, rebuilt);

                        modified = true;
                        
                        log.debug("[SelfRemap : PlayerList.REMOVE] Local client left, remapped its uuid: {}", fakeUuid);
                    }
                }
            }

            return modified ? ModResult.MODIFIED : ModResult.PASS;
        }

        if (packet instanceof AddPlayerPacket addPlayerPacket) {
            UUID playerUuid = addPlayerPacket.getUuid();

            if (remapTable.containsKey(playerUuid)) {
                UUID fakeUuid = remapTable.get(playerUuid);
                addPlayerPacket.setUuid(fakeUuid);

                log.debug("[SelfRemap : AddPlayer] Local Client is here, remapped its uuid: {} -> {}", playerUuid, fakeUuid);

                return ModResult.MODIFIED;
            }
        }

        return ModResult.PASS;
    }
}
