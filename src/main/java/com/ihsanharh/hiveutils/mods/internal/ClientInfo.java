package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.data.auth.DualPayload;
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
                DualPayload authPayload = (DualPayload) loginPacket.getAuthPayload();
                JsonWebSignature tokenJws = new JsonWebSignature();

                tokenJws.setCompactSerialization(authPayload.getToken());

                JSONObject tokenClaims = new JSONObject(JsonUtil.parseJson(tokenJws.getUnverifiedPayload()));
                String xid = String.valueOf(tokenClaims.get("xid"));
                String username = String.valueOf(tokenClaims.get("xname"));

                ConnectedClient.getInstance().setClient(xid, username);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (packet instanceof DisconnectPacket) {
            ConnectedClient.getInstance().clear();
        }

        return ModResult.PASS;
    }
}
