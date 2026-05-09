package com.ihsanharh.hiveutils.mods.internal;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult.IdentityData;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ConnectedClient;

public class ClientInfo implements ProxyMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof LoginPacket loginPacket) {
            try {
                ChainValidationResult chain = EncryptionUtils.validatePayload(loginPacket.getAuthPayload());
                IdentityData identityData = chain.identityClaims().extraData;

                ConnectedClient.getInstance().setClient(identityData.xuid, identityData.displayName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (packet instanceof DisconnectPacket) {
            ConnectedClient.getInstance().clear();
        }

        return ModResult.PASS;
    }
}
