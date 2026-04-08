package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.forms.FormManager;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FormManagerMod implements ProxyMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof ModalFormResponsePacket responsePacket) {
            String data = responsePacket.getFormData();
            if (data != null && data.endsWith("\n")) {
                data = data.trim();
            }
            if (FormManager.getInstance().handleResponse(responsePacket.getFormId(), data)) {
                return ModResult.DENY; 
            }
        }
        return ModResult.PASS;
    }
}
