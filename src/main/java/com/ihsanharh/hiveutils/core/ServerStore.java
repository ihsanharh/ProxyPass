package com.ihsanharh.hiveutils.core;

public class ServerStore {
    private static final ServerStore INSTANCE = new ServerStore();
    private String currentServerName = "UNKNOWN";
    private String previousServerName = "UNKNOWN";
    private String serverType = "UNKNOWN";
    private int serverNumber = -1;

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

        this.serverType = serverName.replaceAll("\\d", "");
        String numPart = serverName.replaceAll("\\D", "");

        if (!numPart.isEmpty()) {
            this.serverNumber = Integer.parseInt(numPart);
        } else {
            this.serverNumber = -1;
        }

        if (!this.previousServerName.equals("UNKNOWN")) {
            PlayerStore.getInstance().clear();
        }

        return true;
    }
}
