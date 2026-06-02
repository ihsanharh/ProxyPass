package com.ihsanharh.hiveutils.commands;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.BaseProxyCommand;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.CustomForm;
import com.ihsanharh.hiveutils.core.SimpleForm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.List;

public class ModsCommand extends BaseProxyCommand {
    public ModsCommand() {
        super("mods", "Manage Proxy Mods");
    }

    @Override
    public void execute(ProxyPlayerSession session, String[] args) {
        ModContext ctx = ModContext.forSession(session);
        if (ctx == null) {
            this.sendUserText(session, "§cMod context not available!");
            return;
        }

        List<ProxyMod> mods = ctx.getModRegistry().getMods();

        SimpleForm mainForm = new SimpleForm("Mod Manager", "Select a mod to configure:");
        for (ProxyMod m : mods) {
            if (m instanceof BaseMod baseMod) {
                String color = baseMod.isEnabled() ? "§a" : "§c";
                mainForm.addButton(color + baseMod.getName());
            }
        }

        ctx.getFormManager().sendForm(session, mainForm, responseStr -> {
            try {
                int index = Integer.parseInt(responseStr);
                int count = 0;
                for (ProxyMod m : mods) {
                    if (m instanceof BaseMod baseMod) {
                        if (count == index) {
                            openModSettings(session, baseMod);
                            return;
                        }
                        count++;
                    }
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    private void openModSettings(ProxyPlayerSession session, BaseMod mod) {
        ModContext ctx = ModContext.forSession(session);
        if (ctx == null) return;

        CustomForm modForm = new CustomForm(mod.getName() + " Settings");
        modForm.addToggle("Enabled", mod.isEnabled());

        if (mod.hasSettingsForm()) {
            mod.buildSettingsForm(session, modForm);
        }

        ctx.getFormManager().sendForm(session, modForm, response -> {
            try {
                ObjectMapper MAPPER = new ObjectMapper();
                JsonNode node = MAPPER.readTree(response);

                if (node.isArray() && node.size() > 0) {
                    boolean enabled = node.get(0).asBoolean();
                    boolean wasEnabled = mod.isEnabled();
                    mod.setEnabled(enabled);

                    if (enabled != wasEnabled || mod.hasSettingsForm() && mod.handleSettingsSubmit(session, response)) {
                        if (enabled != wasEnabled) {
                            ctx.getConfigStore().saveEnabledState(mod, enabled);
                        } else if (mod.hasSettingsForm()) {
                            ctx.getConfigStore().saveModSettings(mod, mod.getSettings());
                        }
                        this.sendUserText(session, "§aUpdated settings for " + mod.getName());
                    }
                }
            } catch (Exception e) {
                this.sendUserText(session, "§cFailed to save settings for " + mod.getName());
            }
        });
    }
}
