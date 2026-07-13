package com.ihsanharh.hiveutils.mods.internal;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.ServerStore;
import com.ihsanharh.hiveutils.core.SilentCommandManager;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.List;

@Log4j2
public class ServerTrackerMod extends BaseMod {
    private static final List<String> expectedResponses = List.of(
        "You are connected to public IP",
        "You are connected to internal IP",
        "You are connected to server name",
        "You are connected to server"
    );

    private static final long QUERY_DEBOUNCE_MS = 2000;
    private volatile long lastQueryTime = 0L;
    private volatile boolean armed = false;

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof StartGamePacket) {
            armed = true;
        } else if (packet instanceof ChangeDimensionPacket) {
            armed = true;
            onServerChange(session);
        } else if (packet instanceof PlayStatusPacket playStatusPacket && playStatusPacket.getStatus() == PlayStatusPacket.Status.PLAYER_SPAWN) {
            if (armed) {
                onServerChange(session);
            }

            armed = false;
        }

        return ModResult.PASS;
    }

    private void onServerChange(ProxyPlayerSession session) {
        long now = System.currentTimeMillis();
        if (now - lastQueryTime < QUERY_DEBOUNCE_MS) {
            return;
        }

        lastQueryTime = now;
        SilentCommandManager commandManager = context.getSilentCommandManager();

        commandManager.executeCommand(session, "/connection", expectedResponses).thenAccept(response -> {
            lastQueryTime = System.currentTimeMillis();
            String serverName = "HUB";

            for (String line : response.textLines) {
                if (line.contains("server name")) {
                    serverName = line.substring(line.lastIndexOf(" ") + 1);
                    break;
                }
            }

            ServerStore serverStore = context.getServerStore();
            serverStore.setCurrentServerName(serverName);
        }).exceptionally(ex -> {
            lastQueryTime = System.currentTimeMillis();
            log.error("[ServerTrackerMod] Command execution failed", ex);

            return null;
        });
    }
}
