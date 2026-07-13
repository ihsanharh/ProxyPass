package com.ihsanharh.hiveutils;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.ModPacketHandler;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.session.Account;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.UpstreamPacketHandler;

public class UpstreamModManager extends UpstreamPacketHandler {
    private final ProxyServerSession session;
    private final ModContext context = new ModContext();
    private final ModPacketHandler modHandler;

    public UpstreamModManager(ProxyServerSession session, ProxyPass proxy, Account account) {
        super(session, proxy, account);
        this.session = session;
        context.register(session);
        this.modHandler = new ModPacketHandler(context.getMods()) {
            @Override
            protected String getCurrentServerName() {
                return context.getServerStore().getCurrentServerName();
            }
        };
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

        ModResult modResult = modHandler.processMods(packet, mod -> mod.handleUpstream(packet, this.session.getPlayer()));

        if (modResult == ModResult.DENY) {
            return PacketSignal.HANDLED;
        }

        PacketSignal originalSignal = super.handlePacket(packet);

        if (modResult == ModResult.MODIFIED) {
            playerSession.getDownstream().sendPacket(packet);
            return PacketSignal.HANDLED;
        }

        return originalSignal;
    }
}
