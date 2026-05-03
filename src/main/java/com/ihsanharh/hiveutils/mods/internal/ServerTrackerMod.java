package com.ihsanharh.hiveutils.mods.internal;

import java.util.ArrayList;
import java.util.Arrays;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.deepl.DeepLScraper;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ServerStore;
import com.ihsanharh.hiveutils.core.SilentCommandManager;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ServerTrackerMod implements ProxyMod {
    private static final ArrayList<String> expectedResponses = new ArrayList<String>(Arrays.asList(
            "You are connected to public IP",
            "You are connected to internal IP",
            "You are connected to server name",
            "You are connected to server"));

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        SilentCommandManager commandManager = SilentCommandManager.getInstance();

        if (packet instanceof TextPacket textPacket) {
            if (commandManager.checkAndComplete(session, textPacket.getMessage()))
                return ModResult.DENY;
        }

        if (packet instanceof PlayerListPacket playerListPacket) {
            if (playerListPacket.getAction() == PlayerListPacket.Action.ADD
                    && playerListPacket.getEntries().size() > 1) {
                commandManager.executeCommand(session, "/connection", expectedResponses)
                        .thenAccept(response -> {
                            String serverName = "HUB";
                            for (String line : response) {
                                if (line.contains("server name")) {
                                    serverName = line.substring(line.lastIndexOf(" ") + 1);
                                    break;
                                }
                            }

                            ServerStore serverStore = ServerStore.getInstance();

                            if (serverStore.setCurrentServerName(serverName)) {
                                log.info("moved to server: {}", serverName);
                                DeepLScraper scraper = DeepLScraper.getInstance();
                                scraper.cancel();
                                scraper.clearQueue();
                            }
                            ;
                        })
                        .exceptionally(ex -> {
                            log.error("Command execution failed", ex);
                            return null;
                        });
            }
            ;
        }

        return ModResult.PASS;
    }

}
