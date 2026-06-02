package com.ihsanharh.hiveutils;

import java.util.List;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.DownstreamPacketHandler;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyClientSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ModContext;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DownstreamModManager extends DownstreamPacketHandler {
    private final ProxyPlayerSession player;
    private final List<ProxyMod> activeMods;

    public DownstreamModManager(ProxyClientSession session, ProxyPlayerSession player, ProxyPass proxy) {
        super(session, player, proxy);
        this.player = player;
        ModContext ctx = ModContext.forSession(player);
        if (ctx != null) {
            this.activeMods = ctx.getMods();
        } else {
            log.warn("ModContext not found for downstream, no mods active");
            this.activeMods = List.of();
        }
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        ModResult finalResult = ModResult.PASS;

        for (ProxyMod mod : activeMods) {
            if (mod instanceof BaseMod baseMod && !baseMod.isEnabled()) {
                continue;
            }

            ModResult result = mod.handleDownstream(packet, player);

            if (result == ModResult.DENY) {
                return PacketSignal.HANDLED;
            }
            if (result == ModResult.MODIFIED) {
                finalResult = ModResult.MODIFIED;
            }
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (finalResult == ModResult.MODIFIED) {
            this.player.getUpstream().sendPacket(packet);

            return PacketSignal.HANDLED;
        }

        return originalSignal;
    }
}
