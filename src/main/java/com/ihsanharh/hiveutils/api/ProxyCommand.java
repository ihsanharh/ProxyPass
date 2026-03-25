package com.ihsanharh.hiveutils.api;

import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

public interface ProxyCommand {
    String getName();

    String getDescription();

    CommandData buildCommandData();

    void execute(ProxyPlayerSession session, String[] args);
}
