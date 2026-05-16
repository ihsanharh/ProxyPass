package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.SilentCommandManager;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SilentCommandMod implements ProxyMod {
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket) {
            String message = textPacket.getMessage();
            if (message != null && SilentCommandManager.getInstance().checkAndComplete(session, message)) {
                return ModResult.DENY;
            }
        }

        if (packet instanceof ModalFormRequestPacket formPacket) {
            if (SilentCommandManager.getInstance().checkAndCaptureForm(session, formPacket)) {
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }
}
