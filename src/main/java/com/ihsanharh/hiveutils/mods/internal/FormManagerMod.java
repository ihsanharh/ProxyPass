package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;

public class FormManagerMod extends BaseMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof ModalFormResponsePacket responsePacket) {
            String data = responsePacket.getFormData();
            if (data != null && data.endsWith("\n")) {
                data = data.trim();
            }
            if (context.getFormManager().handleResponse(responsePacket.getFormId(), data)) {
                return ModResult.DENY; 
            }
        }
        return ModResult.PASS;
    }
}
