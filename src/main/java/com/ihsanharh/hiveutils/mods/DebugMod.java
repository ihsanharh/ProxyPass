package com.ihsanharh.hiveutils.mods;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.PlayerStore;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DebugMod implements ProxyMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket) {
            String textMessage = textPacket.getMessage();

            if (textMessage.contains("dxzmap")) {
                PlayerStore.getInstance().printLiveMap();

                return ModResult.DENY;
            }

            if (textMessage.contains("find ")) {
                String playerName = textMessage.substring(textMessage.indexOf(" ") + 1);

                log.info(PlayerStore.getInstance().getPlayerByName(playerName));

                return ModResult.DENY;
            }
        }
        return ModResult.PASS;
    }
}
