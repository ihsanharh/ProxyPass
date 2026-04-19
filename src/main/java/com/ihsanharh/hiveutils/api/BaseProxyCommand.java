package com.ihsanharh.hiveutils.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData.Flag;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import lombok.Getter;

@Getter
public abstract class BaseProxyCommand implements ProxyCommand {
    private final String name;
    private final String description;
    private final CommandOverloadData[] overloads;

    public BaseProxyCommand(String name, String description, CommandOverloadData[] overloads) {
        this.name = name;
        this.description = description;
        this.overloads = overloads;
    }

    public BaseProxyCommand(String name, String description) {
        this(name, description, new CommandOverloadData[0]);
    }

    @Override
    public CommandData buildCommandData() {
        Set<Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();

        return new CommandData(name, description, commandFlags, null, null, subCommands, this.overloads);
    }

    @Override
    public abstract void execute(ProxyPlayerSession session, String[] args);

    protected void sendUserText(ProxyPlayerSession session, String text) {
        TextPacket response = new TextPacket();
        response.setType(TextPacket.Type.RAW);
        response.setMessage(text);
        response.setXuid("");

        session.getUpstream().sendPacketImmediately(response);
    }
}
