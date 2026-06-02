package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.PlayerStore;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class PlayerTrackerMod extends BaseMod {
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        PlayerStore playerStore = context.getPlayerStore();

        if (packet instanceof PlayerListPacket playerListPacket) {
            PlayerListPacket.Action action = playerListPacket.getAction();

            if (action == PlayerListPacket.Action.ADD) {
                for (PlayerListPacket.Entry player : playerListPacket.getEntries()) {
                    playerStore.addPlayer(player.getUuid().toString(), player.getName(), player.getEntityId());
                }
            } else if (action == PlayerListPacket.Action.REMOVE) {
                for (PlayerListPacket.Entry player : playerListPacket.getEntries()) {
                    playerStore.removePlayer(player.getUuid().toString());
                }
            }
        }

        if (packet instanceof AddPlayerPacket addPlayerPacket) {
            playerStore.addPlayer(addPlayerPacket.getUuid().toString(), addPlayerPacket.getUsername(), 0,
                    addPlayerPacket.getRuntimeEntityId(), addPlayerPacket.getPosition());
        }

        if (packet instanceof MoveEntityAbsolutePacket moveEntityPacket) {
            playerStore.updatePlayerPosition(moveEntityPacket.getRuntimeEntityId(), moveEntityPacket.getPosition());
        }

        return ModResult.PASS;
    }
}
