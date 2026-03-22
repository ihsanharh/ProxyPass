package com.ihsanharh.hiveutils.core;

public class ServerStore {
    private static final ServerStore INSTANCE = new ServerStore();
    private String currentServerName = "UNKNOWN";
    private String previousServerName = "UNKNOWN";

    public static ServerStore getInstance() {
        return INSTANCE;
    }

    public String getCurrentServerName() {
        return currentServerName;
    }

    public String getPreviousServerName() {
        return previousServerName;
    }

    public Boolean setCurrentServerName(String serverName) {
        boolean moved = this.currentServerName != "UNKNOWN" && !this.currentServerName.contains(serverName);

        if (moved) {
            this.previousServerName = this.currentServerName;
            this.currentServerName = serverName;
        }

        return moved;
    }
}
