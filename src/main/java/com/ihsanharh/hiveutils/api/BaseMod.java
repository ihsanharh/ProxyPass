package com.ihsanharh.hiveutils.api;

import java.util.Map;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.core.ModConfigStore;
import com.ihsanharh.hiveutils.forms.CustomForm;

public abstract class BaseMod implements ProxyMod {
    private boolean enabled = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        boolean changed = this.enabled != enabled;
        this.enabled = enabled;
        if (changed) {
            ModConfigStore.getInstance().setEnabledState(this, enabled);
        }
    }

    public void toggle() {
        this.enabled = !this.enabled;
        ModConfigStore.getInstance().setEnabledState(this, this.enabled);
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public Map<String, Object> getSettings() {
        return null;
    }

    public void loadSettings(Map<String, Object> settings) {
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

    public void buildSettingsForm(ProxyPlayerSession session, CustomForm form) {}

    public boolean handleSettingsSubmit(ProxyPlayerSession session, String response) {
        return true;
    }
}
