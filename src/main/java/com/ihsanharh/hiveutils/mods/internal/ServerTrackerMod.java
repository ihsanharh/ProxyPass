package com.ihsanharh.hiveutils.mods.internal;

import java.util.ArrayList;
import java.util.Arrays;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.ServerStore;
import com.ihsanharh.hiveutils.core.SilentCommandManager;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ServerTrackerMod extends BaseMod {
    private static final ArrayList<String> expectedResponses = new ArrayList<String>(Arrays.asList(
            "You are connected to public IP",
            "You are connected to internal IP",
            "You are connected to server name",
            "You are connected to server"));

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof PlayerListPacket playerListPacket) {
            if (playerListPacket.getAction() == PlayerListPacket.Action.ADD && playerListPacket.getEntries().size() > 1) {
                SilentCommandManager commandManager = context.getSilentCommandManager();

                commandManager.executeCommand(session, "/connection", expectedResponses)
                .thenAccept(response -> {
                    String serverName = "HUB";

                    for (String line : response.textLines) {
                        if (line.contains("server name")) {
                            serverName = line.substring(line.lastIndexOf(" ") + 1);

                            break;
                        }
                    }

                    ServerStore serverStore = context.getServerStore();
                    serverStore.setCurrentServerName(serverName);
                    log.info("moved to server: {}", serverName);
                })
                .exceptionally(ex -> {
                    log.error("Command execution failed", ex);
                    return null;
                });
            }
        }

        return ModResult.PASS;
    }

}
