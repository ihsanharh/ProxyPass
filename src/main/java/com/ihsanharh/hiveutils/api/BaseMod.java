package com.ihsanharh.hiveutils.api;

import java.util.Map;

import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.CustomForm;

public abstract class BaseMod implements ProxyMod {
    protected ModContext context;
    private boolean enabled = true;

    public void setContext(ModContext ctx) {
        this.context = ctx;
    }

    public void onInitialize() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        boolean changed = this.enabled != enabled;
        this.enabled = enabled;
        if (changed && context != null) {
            context.getConfigStore().saveEnabledState(this, enabled);
        }
    }

    public void toggle() {
        this.enabled = !this.enabled;
        if (context != null) {
            context.getConfigStore().saveEnabledState(this, this.enabled);
        }
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public Map<String, Object> getSettings() {
        return null;
    }

    public void loadSettings(Map<String, Object> settings) {
    }

    public boolean hasSettingsForm() {
        return false;
    }

    public void buildSettingsForm(ProxyPlayerSession session, CustomForm form) {}

    public boolean handleSettingsSubmit(ProxyPlayerSession session, String response) {
        return true;
    }
}
