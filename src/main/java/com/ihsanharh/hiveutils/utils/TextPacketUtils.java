package com.ihsanharh.hiveutils.utils;

import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

public final class TextPacketUtils {
    private TextPacketUtils() {}

    public static void sendRawToClient(ProxyPlayerSession session, String message) {
        if (session == null || session.getUpstream() == null) return;
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.RAW);
        packet.setMessage(message);
        packet.setXuid("");
        session.getUpstream().sendPacket(packet);
    }

    public static void sendRawToClientImmediately(ProxyPlayerSession session, String message) {
        if (session == null || session.getUpstream() == null) return;
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.RAW);
        packet.setMessage(message);
        packet.setXuid("");
        session.getUpstream().sendPacketImmediately(packet);
    }

    public static void sendChatToServer(ProxyPlayerSession session, String message) {
        if (session == null || session.getDownstream() == null) return;
        String username = session.getAuthData().getDisplayName();
        String xuid = session.getAuthData().getXuid();
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setMessage(message);
        packet.setXuid(xuid);
        packet.setSourceName(username);
        session.getDownstream().sendPacketImmediately(packet);
    }
}
