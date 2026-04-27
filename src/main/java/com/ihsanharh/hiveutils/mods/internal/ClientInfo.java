package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ConnectedClient;

public class ClientInfo implements ProxyMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof LoginPacket loginPacket) {
            try {
                String clientJwt = loginPacket.getClientJwt();
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(clientJwt);
                JSONObject clientData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));

                String selfSignedId = clientData.get("SelfSignedId").toString();
                String thirdPartyName = clientData.get("ThirdPartyName").toString();

                ConnectedClient.getInstance().setClient(thirdPartyName, selfSignedId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (packet instanceof DisconnectPacket) {
            ConnectedClient.getInstance().clear();
        }

        return ModResult.PASS;
    }
}
