package com.ihsanharh.hiveutils.core;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.List;
import java.util.function.Function;

public abstract class ModPacketHandler {
    protected final List<ProxyMod> activeMods;

    protected ModPacketHandler(List<ProxyMod> activeMods) {
        this.activeMods = activeMods;
    }

    protected abstract String getCurrentServerName();

    public final ModResult processMods(BedrockPacket packet, Function<ProxyMod, ModResult> handler) {
        ModResult finalResult = ModResult.PASS;
        String currentServer = getCurrentServerName();

        for (ProxyMod mod : activeMods) {
            if (mod instanceof BaseMod baseMod) {
                if (!baseMod.isEnabled()) continue;
                if (!baseMod.matchesServer(currentServer)) continue;
            }

            ModResult result = handler.apply(mod);

            if (result == ModResult.DENY) {
                return ModResult.DENY;
            }
            if (result == ModResult.MODIFIED) {
                finalResult = ModResult.MODIFIED;
            }
        }

        return finalResult;
    }
}
