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

    public boolean setCurrentServerName(String serverName) {
        if (this.currentServerName.equals(serverName)) {
            return false;
        }
        this.previousServerName = this.currentServerName;
        this.currentServerName = serverName;

        if (!this.previousServerName.equals("UNKNOWN")) {
            PlayerStore.getInstance().clear();
        }

        return true;
    }
}
