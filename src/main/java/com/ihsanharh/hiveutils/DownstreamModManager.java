package com.ihsanharh.hiveutils;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.ModPacketHandler;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.DownstreamPacketHandler;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyClientSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.List;

@Log4j2
public class DownstreamModManager extends DownstreamPacketHandler {
    private final ProxyPlayerSession player;
    private final ModContext context;
    private final ModPacketHandler modHandler;

    public DownstreamModManager(ProxyClientSession session, ProxyPlayerSession player, ProxyPass proxy) {
        super(session, player, proxy);
        this.player = player;
        ModContext ctx = ModContext.forSession(player);
        this.context = ctx;
        List<ProxyMod> activeMods = ctx != null ? ctx.getMods() : List.of();
        this.modHandler = new ModPacketHandler(activeMods) {
            @Override
            protected String getCurrentServerName() {
                return context != null ? context.getServerStore().getCurrentServerName() : null;
            }
        };
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        ModResult modResult = modHandler.processMods(packet, mod -> mod.handleDownstream(packet, player));

        if (modResult == ModResult.DENY) {
            return PacketSignal.HANDLED;
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (modResult == ModResult.MODIFIED) {
            this.player.getUpstream().sendPacket(packet);
            return PacketSignal.HANDLED;
        }

        return originalSignal;
    }
}
