package com.ihsanharh.hiveutils.api;

import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.CustomForm;

import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.List;
import java.util.Map;

public abstract class BaseMod implements ProxyMod {
    protected ModContext context;
    private boolean enabled = true;
    private List<String> serverFilters;

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

    public List<String> getServerFilters() {
        return serverFilters;
    }

    public void setServerFilters(List<String> serverFilters) {
        this.serverFilters = serverFilters;
    }

    public List<String> getDefaultServerFilters() {
        return null;
    }

    public boolean matchesServer(String serverName) {
        if (serverFilters == null || serverFilters.isEmpty()) return true;
        if (serverName == null) return false;

        String lower = serverName.toLowerCase();

        for (String filter : serverFilters) {
            if (lower.contains(filter.toLowerCase())) return true;
        }

        return false;
    }
}
