package com.ihsanharh.hiveutils.mods.internal;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

public class SilentCommandMod extends BaseMod {
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket) {
            String message = textPacket.getMessage();
            if (message != null && context.getSilentCommandManager().checkAndComplete(session, message)) {
                return ModResult.DENY;
            }
        }

        if (packet instanceof ModalFormRequestPacket formPacket) {
            if (context.getSilentCommandManager().checkAndCaptureForm(session, formPacket)) {
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }
}
