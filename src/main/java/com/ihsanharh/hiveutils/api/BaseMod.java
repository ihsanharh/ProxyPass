package com.ihsanharh.hiveutils.api;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.forms.CustomForm;

public abstract class BaseMod implements ProxyMod {
    private boolean enabled = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        return ModResult.PASS;
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        return ModResult.PASS;
    }

    public boolean hasSettingsForm() {
        return false;
    }

    public void buildSettingsForm(CustomForm form) {}

    public void handleSettingsSubmit(ProxyPlayerSession session, String response) {}
}