package com.ihsanharh.hiveutils;

import java.util.List;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.Account;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.UpstreamPacketHandler;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ModContext;

public class UpstreamModManager extends UpstreamPacketHandler {
    private final ProxyServerSession session;
    private final ModContext context = new ModContext();
    private final List<ProxyMod> activeMods = context.getMods();
 
    public UpstreamModManager(ProxyServerSession session, ProxyPass proxy, Account account) {
        super(session, proxy, account);
        this.session = session;
        context.register(session);
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        ProxyPlayerSession playerSession = this.session.getPlayer();

        if (!(packet instanceof LoginPacket) && (playerSession == null || playerSession.getDownstream() == null)) {
            return super.handlePacket(packet);
        }

        if (playerSession != null) {
            context.bind(playerSession);
        }

        ModResult finalResult = ModResult.PASS;

        for (ProxyMod mod : activeMods) {
            if (mod instanceof BaseMod baseMod && !baseMod.isEnabled()) {
                continue;
            }

            ModResult result = mod.handleUpstream(packet, this.session.getPlayer());

            if (result == ModResult.DENY) {
                return PacketSignal.HANDLED;
            }
            if (result == ModResult.MODIFIED) {
                finalResult = ModResult.MODIFIED;
            }
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (finalResult == ModResult.MODIFIED) {
            playerSession.getDownstream().sendPacket(packet);

            return PacketSignal.HANDLED;
        }

        return originalSignal;
    }
}
