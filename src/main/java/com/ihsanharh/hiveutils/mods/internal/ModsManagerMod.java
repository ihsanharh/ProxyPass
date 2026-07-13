package com.ihsanharh.hiveutils.mods.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.core.CustomForm;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.SimpleForm;
import com.ihsanharh.hiveutils.utils.TextPacketUtils;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
public class ModsManagerMod extends BaseMod {
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof AvailableCommandsPacket availableCommandsPacket) {
            availableCommandsPacket.getCommands().add(buildModsCommandData());
            log.debug("[ModsManagerMod] Injected /mods command.");
            
            return ModResult.MODIFIED;
        }

        return ModResult.PASS;
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof CommandRequestPacket commandRequest) {
            String rawCommand = commandRequest.getCommand();

            if (rawCommand.startsWith("/mods")) {
                executeModsCommand(session);
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }

    private void executeModsCommand(ProxyPlayerSession session) {
        ModContext ctx = ModContext.forSession(session);
        
        if (ctx == null) {
            log.warn("[ModsManagerMod] Mod context not available for session: {}", session);
            TextPacketUtils.sendRawToClient(session, "§cMod context not available!");
            
            return;
        }

        List<BaseMod> visibleMods = new ArrayList<>();
        for (ProxyMod m : ctx.getModRegistry().getMods()) {
            if (m instanceof BaseMod baseMod && !m.getClass().getPackageName().contains(".mods.internal")) {
                visibleMods.add(baseMod);
            }
        }

        SimpleForm mainForm = new SimpleForm("Mod Manager", "Select a mod to configure:");
        for (BaseMod baseMod : visibleMods) {
            String color = baseMod.isEnabled() ? "§a" : "§c";
            mainForm.addButton(color + baseMod.getName());
        }

        ctx.getFormManager().sendForm(session, mainForm, responseStr -> {
            try {
                int index = Integer.parseInt(responseStr);
                if (index >= 0 && index < visibleMods.size()) {
                    openModSettings(session, visibleMods.get(index));
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
                        TextPacketUtils.sendRawToClient(session, "§aUpdated settings for " + mod.getName());
                    }
                }
            } catch (Exception e) {
                TextPacketUtils.sendRawToClient(session, "§cFailed to save settings for " + mod.getName());
            }
        });
    }

    private CommandData buildModsCommandData() {
        CommandOverloadData[] overloads = new CommandOverloadData[0];

        Set<CommandData.Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();

        return new CommandData("mods", "Manage Proxy Mods", commandFlags, null, null, subCommands, overloads);
    }
}
