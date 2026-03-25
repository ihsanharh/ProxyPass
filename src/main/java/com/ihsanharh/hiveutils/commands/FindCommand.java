package com.ihsanharh.hiveutils.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData.Flag;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.ProxyCommand;
import com.ihsanharh.hiveutils.core.PlayerData;
import com.ihsanharh.hiveutils.core.PlayerStore;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FindCommand implements ProxyCommand {
    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getDescription() {
        return "Finds a player's coordinates in the world";
    }

    @Override
    public CommandData buildCommandData() {
        Set<Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();
        CommandOverloadData[] overloads = new CommandOverloadData[0];

        return new CommandData(this.getName(), this.getDescription(), commandFlags, null, null, subCommands, overloads);
    }

    @Override
    public void execute(ProxyPlayerSession session, String[] args) {
        if (args.length == 0) {
            sendMessage(session, "§bUsage: /find [player]");
            return;
        }

        String targetName = String.join(" ", args);
        PlayerData target = PlayerStore.getInstance().getPlayerByName(targetName);

        if (target != null) {
            String coords = String.format("X: %.1f, Y: %.1f, Z: %.1f",
                    target.getPosition().getX(),
                    target.getPosition().getY(),
                    target.getPosition().getZ());
            sendMessage(session, "§aFound " + target.getPlayerName() + " at " + coords);
        } else {
            sendMessage(session, "§cThat is not a valid player!");
        }
    }

    private void sendMessage(ProxyPlayerSession session, String text) {
        TextPacket response = new TextPacket();
        response.setType(TextPacket.Type.RAW);
        response.setMessage(text);
        response.setXuid("");
        session.getUpstream().sendPacket(response);
    }
}