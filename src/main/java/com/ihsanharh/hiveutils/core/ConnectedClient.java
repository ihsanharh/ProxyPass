package com.ihsanharh.hiveutils.core;

import lombok.Getter;

@Getter
public class ConnectedClient {
    private static final ConnectedClient INSTANCE = new ConnectedClient();

    private String username;
    private String uuid;
    private boolean connected = false;

    public static ConnectedClient getInstance() {
        return INSTANCE;
    }

    public void setClient(String username, String uuid) {
        this.username = username;
        this.uuid = uuid;
        this.connected = true;
    }

    public void clear() {
        this.username = null;
        this.uuid = null;
        this.connected = false;
    }
}