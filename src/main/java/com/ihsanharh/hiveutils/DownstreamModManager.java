package com.ihsanharh.hiveutils;

import java.util.List;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.DownstreamPacketHandler;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyClientSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;

public class DownstreamModManager extends DownstreamPacketHandler {
    private final List<ProxyMod> activeMods;
    private final ProxyPlayerSession player;

    public DownstreamModManager(ProxyClientSession session, ProxyPlayerSession player, ProxyPass proxy, List<ProxyMod> activeMods) {
        super(session, player, proxy);
        this.player = player;
        this.activeMods = activeMods;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        ModResult finalResult = ModResult.PASS;

        for (ProxyMod mod : activeMods) {
            ModResult result = mod.handleDownstream(packet, player);

            if (result == ModResult.DENY) {
                return PacketSignal.HANDLED; // block the packet immediately
            }
            if (result == ModResult.MODIFIED) {
                finalResult = ModResult.MODIFIED; // send the packet manually
            }
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (finalResult == ModResult.MODIFIED) {
            this.player.getUpstream().sendPacketImmediately(packet);

            return PacketSignal.HANDLED; // we already sent the modified packet, block the original
        }

        return originalSignal;
    }
}
