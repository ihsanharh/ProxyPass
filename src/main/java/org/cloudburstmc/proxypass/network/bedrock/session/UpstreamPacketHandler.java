package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.ForgeryUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {
    private final ProxyServerSession session;
    private final ProxyPass proxy;
    private final Account account;
    private JSONObject skinData;
    private AuthData authData;
    private ProxyPlayerSession player;

    private static ECPublicKey mojangPublicKey;
    private static AuthPayload authPayload;

    private static boolean verifyJwt(String jwt, PublicKey key) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(key);
        jws.setCompactSerialization(jwt);
        return jws.verifySignature();
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        int protocolVersion = packet.getProtocolVersion();

        if (protocolVersion != ProxyPass.PROTOCOL_VERSION) {
            PlayStatusPacket status = new PlayStatusPacket();
            status.setStatus(protocolVersion > ProxyPass.PROTOCOL_VERSION
                    ? PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD
                    : PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);

            session.sendPacketImmediately(status);
            return PacketSignal.HANDLED;
        }
        session.setCodec(ProxyPass.CODEC);

        NetworkSettingsPacket networkSettingsPacket = new NetworkSettingsPacket();
        networkSettingsPacket.setCompressionThreshold(0);
        networkSettingsPacket.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);

        session.sendPacketImmediately(networkSettingsPacket);
        session.setCompression(PacketCompressionAlgorithm.ZLIB);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        try {
            if (!(packet.getAuthPayload() instanceof DualPayload dualPayload)) {
                throw new IllegalStateException(
                        "Unexpected payload type: " + packet.getAuthPayload().getClass().getName());
            }

            // OIDC FLOW: extract cpk from Token JWT (1.26.10+)
            JsonWebSignature tokenJws = new JsonWebSignature();
            tokenJws.setCompactSerialization(dualPayload.getToken());
            JSONObject tokenClaims = new JSONObject(JsonUtil.parseJson(tokenJws.getUnverifiedPayload()));

            String cpkBase64 = String.valueOf(tokenClaims.get("cpk"));
            ECPublicKey identityPublicKey = EncryptionUtils.parseKey(cpkBase64);

            if (account == null) {
                String xname = String.valueOf(tokenClaims.get("xname"));
                String xid = String.valueOf(tokenClaims.get("xid"));
                this.authData = new AuthData(xname,
                        UUID.nameUUIDFromBytes(xid.getBytes(StandardCharsets.UTF_8)), xid);
            } else {
                MinecraftMultiplayerToken token = account.authManager().getMinecraftMultiplayerToken().getCached();
                this.authData = new AuthData(token.getDisplayName(), token.getUuid(), token.getXuid());
            }

            String clientJwt = packet.getClientJwt();
            verifyJwt(clientJwt, identityPublicKey);
            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(clientJwt);

            skinData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));

            log.info(skinData.toString());

            if (skinData.get("ServerAddress") != null) {
                session.setConnectedViaAddress(skinData.get("ServerAddress").toString());
            }

            initializeProxySession();

        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (!this.proxy.getConfiguration().isDownloadPacks()) {
            return PacketSignal.UNHANDLED;
        }
        if (packet.getStatus() != ResourcePackClientResponsePacket.Status.COMPLETED) {
            return PacketSignal.UNHANDLED;
        }

        player.getPackDownloader().processPacks();
        return PacketSignal.UNHANDLED;
    }

    private void initializeProxySession() {
        log.debug("Initializing proxy session");

        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            BedrockCodec.Builder codecBuilder = ProxyPass.CODEC.toBuilder();
            downstream.setCodec(codecBuilder.build());

            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            KeyPair sessionKeyPair = (account != null)
                    ? account.authManager().getSessionKeyPair()
                    : EncryptionUtils.createKeyPair();

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                    this.session,
                    downstream,
                    this.proxy,
                    this.authData,
                    sessionKeyPair);
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            LoginPacket login = prepareLoginPacket(proxySession);

            downstream
                    .setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);
            this.player.getLogger().logPacket(this.session, packet, true);
        });
    }

    private LoginPacket prepareLoginPacket(ProxyPlayerSession proxySession) {
        String jwtSkinData;
        AuthPayload payload;

        if (account == null) {
            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            String forgedAuth = ForgeryUtils.forgeOfflineAuthData(proxySession.getProxyKeyPair(), this.authData);
            jwtSkinData = ForgeryUtils.forgeOfflineSkinData(proxySession.getProxyKeyPair(), this.skinData);
            payload = new CertificateChainPayload(List.of(forgedAuth), AuthType.SELF_SIGNED);

        } else {
            try {
                // For 944+ OIDC, mojangPublicKey is not needed (dummy chain is sent instead)
                if (ProxyPass.CODEC.getProtocolVersion() < 944 && mojangPublicKey == null) {
                    mojangPublicKey = ForgeryUtils.forgeMojangPublicKey();
                }
                // Always regenerate authPayload per session to ensure skin data is fresh
                authPayload = ForgeryUtils.forgeOnlineAuthData(account.authManager(), mojangPublicKey);
            } catch (Exception e) {
                log.error("Failed to get login chain", e);
            }

            jwtSkinData = ForgeryUtils.forgeOnlineSkinData(account, this.skinData, this.proxy.getTargetAddress());

            // Diagnostic: compare Token cpk vs skin JWT x5u (they must match for The Hive to accept the skin)
            try {
                if (ProxyPass.CODEC.getProtocolVersion() >= 944 && payload instanceof DualPayload dp) {
                    JsonWebSignature tokenJws = new JsonWebSignature();
                    tokenJws.setCompactSerialization(dp.getToken());
                    JSONObject tokenClaims = new JSONObject(JsonUtil.parseJson(tokenJws.getUnverifiedPayload()));
                    String tokenCpk = String.valueOf(tokenClaims.get("cpk"));

                    JsonWebSignature skinJws = new JsonWebSignature();
                    skinJws.setCompactSerialization(jwtSkinData);
                    String skinX5u = skinJws.getHeader("x5u");

                    log.info("[SKIN DEBUG] Token cpk  : {}", tokenCpk);
                    log.info("[SKIN DEBUG] Skin JWT x5u: {}", skinX5u);
                    log.info("[SKIN DEBUG] Keys match  : {}", tokenCpk.equals(skinX5u));
                }
            } catch (Exception e) {
                log.error("[SKIN DEBUG] Failed to compare keys", e);
            }


            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            payload = authPayload;
        }

        LoginPacket login = new LoginPacket();
        login.setClientJwt(jwtSkinData);
        login.setAuthPayload(payload);
        login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
        return login;
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        if (this.session.getSendSession() != null && this.session.getSendSession().isConnected()) {
            this.session.getSendSession().disconnect(reason);
        }
    }
}