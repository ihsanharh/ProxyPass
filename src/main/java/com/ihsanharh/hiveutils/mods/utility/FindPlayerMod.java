package com.ihsanharh.hiveutils.mods.utility;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.PlayerData;
import com.ihsanharh.hiveutils.utils.TextPacketUtils;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
public class FindPlayerMod extends BaseMod {

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof AvailableCommandsPacket availableCommandsPacket) {
            availableCommandsPacket.getCommands().add(buildFindCommandData());
            log.debug("[FindPlayerMod] Injected /find command.");
            return ModResult.MODIFIED;
        }

        return ModResult.PASS;
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof CommandRequestPacket commandRequest) {
            String rawCommand = commandRequest.getCommand();
            if (rawCommand.startsWith("/find")) {
                String[] parts = rawCommand.substring(1).split(" ");
                String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                executeFindCommand(session, args);
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }

    private void executeFindCommand(ProxyPlayerSession session, String[] args) {
        if (args.length == 0) {
            TextPacketUtils.sendRawToClient(session, "§bUsage: /find [player]");
            return;
        }

        String targetName = String.join(" ", args);
        ModContext ctx = ModContext.forSession(session);
        if (ctx == null) {
            TextPacketUtils.sendRawToClient(session, "§cMod context not available!");
            return;
        }

        PlayerData target = ctx.getPlayerStore().getPlayerByName(targetName);

        if (target != null) {
            String coords = String.format("X: %.1f, Y: %.1f, Z: %.1f",
                    target.getPosition().getX(),
                    target.getPosition().getY(),
                    target.getPosition().getZ());
            TextPacketUtils.sendRawToClient(session, "§aFound " + target.getPlayerName() + " at " + coords);
        } else {
            TextPacketUtils.sendRawToClient(session, "§cThat is not a valid player!");
        }
    }

    private CommandData buildFindCommandData() {
        CommandParamData target = new CommandParamData();
        target.setName("player");
        target.setOptional(false);
        target.setType(CommandParam.STRING);

        CommandParamData[] paramData = new CommandParamData[]{target};
        CommandOverloadData[] overloads = new CommandOverloadData[]{new CommandOverloadData(false, paramData)};

        Set<CommandData.Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();

        return new CommandData("find", "Finds a player's coordinates in the world", commandFlags, null, null, subCommands, overloads);
    }
}
