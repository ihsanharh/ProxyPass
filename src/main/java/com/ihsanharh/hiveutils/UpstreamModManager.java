package com.ihsanharh.hiveutils;

import java.util.ArrayList;
import java.util.List;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.Account;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.UpstreamPacketHandler;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.mods.DebugMod;
import com.ihsanharh.hiveutils.mods.PlayerTrackerMod;
import com.ihsanharh.hiveutils.mods.ServerTrackerMod;
import com.ihsanharh.hiveutils.mods.hideandseek.HideAndSeekESP;

public class UpstreamModManager extends UpstreamPacketHandler {
    private final List<ProxyMod> activeMods = new ArrayList<>();
    private final ProxyServerSession session;

    public UpstreamModManager(ProxyServerSession session, ProxyPass proxy, Account account) {
        super(session, proxy, account);
        this.session = session;

        this.activeMods.add(new DebugMod());
        this.activeMods.add(new ServerTrackerMod());
        this.activeMods.add(new PlayerTrackerMod());
        this.activeMods.add(new HideAndSeekESP());
    }

    public List<ProxyMod> getActiveMods() {
        return activeMods;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        ProxyPlayerSession playerSession = this.session.getPlayer();

        if (playerSession == null || playerSession.getDownstream() == null) {
            return super.handlePacket(packet);
        }

        ModResult finalResult = ModResult.PASS;

        for (ProxyMod mod : activeMods) {
            ModResult result = mod.handleUpstream(packet, this.session.getPlayer());

            if (result == ModResult.DENY) {
                return PacketSignal.HANDLED; // block the packet immediately
            }
            if (result == ModResult.MODIFIED) {
                finalResult = ModResult.MODIFIED; // send the packet manually
            }
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (finalResult == ModResult.MODIFIED) {
            playerSession.getDownstream().sendPacket(packet);

            return PacketSignal.HANDLED; // we already sent the modified packet, block the original
        }

        return originalSignal;
    }
}
