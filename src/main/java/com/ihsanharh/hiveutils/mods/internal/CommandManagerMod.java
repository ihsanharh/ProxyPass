package com.ihsanharh.hiveutils.mods.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyCommand;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.commands.FindCommand;
import com.ihsanharh.hiveutils.commands.ModsCommand;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CommandManagerMod implements ProxyMod {
    private final Map<String, ProxyCommand> commandRegistry = new HashMap<>();

    public CommandManagerMod() {
        registerCommand(new FindCommand());
        registerCommand(new ModsCommand());
    }

    private void registerCommand(ProxyCommand command) {
        commandRegistry.put(command.getName().toLowerCase(), command);
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof AvailableCommandsPacket availableCommandsPacket) {
            for (ProxyCommand cmd : commandRegistry.values()) {
                availableCommandsPacket.getCommands().add(cmd.buildCommandData());
            }

            log.info("Injected {} commands.", commandRegistry.size());

            return ModResult.MODIFIED;
        }

        return ModResult.PASS;
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof CommandRequestPacket commandRequest) {
            String rawCommand = commandRequest.getCommand();

            if (rawCommand.startsWith("/")) {
                String[] parts = rawCommand.substring(1).split(" ");
                String commandName = parts[0].toLowerCase();

                ProxyCommand targetCommand = commandRegistry.get(commandName);

                if (targetCommand != null) {
                    String[] args = Arrays.copyOfRange(parts, 1, parts.length);

                    targetCommand.execute(session, args);

                    return ModResult.DENY;
                }
            }
        }

        return ModResult.PASS;
    }
}