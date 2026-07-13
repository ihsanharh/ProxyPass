package com.ihsanharh.hiveutils.core;

import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
public class ConnectedClient {
    private String xuid;
    private UUID uuid;
    private String username;
    private boolean connected = false;

    public void setClient(String xuid, String username) {
        this.xuid = xuid;
        this.uuid = UUID.nameUUIDFromBytes(xuid.getBytes(StandardCharsets.UTF_8));
        this.username = username;
        this.connected = true;
    }

    public void clear() {
        this.xuid = null;
        this.username = null;
        this.uuid = null;
        this.connected = false;
    }
}
