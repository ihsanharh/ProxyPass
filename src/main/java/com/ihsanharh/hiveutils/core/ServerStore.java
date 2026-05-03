package com.ihsanharh.hiveutils.core;

import java.util.ArrayList;
import java.util.List;

import com.ihsanharh.hiveutils.api.ServerChangeListener;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ServerStore {
    private static final ServerStore INSTANCE = new ServerStore();
    private String currentServerName = "UNKNOWN";
    private String previousServerName = "UNKNOWN";
    private final List<ServerChangeListener> listeners = new ArrayList<>();

    public static ServerStore getInstance() {
        return INSTANCE;
    }

    public String getCurrentServerName() {
        return currentServerName;
    }

    public String getPreviousServerName() {
        return previousServerName;
    }

    public void addListener(ServerChangeListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ServerChangeListener listener) {
        this.listeners.remove(listener);
    }

    public boolean setCurrentServerName(String serverName) {
        if (this.currentServerName.equals(serverName)) {
            return false;
        }
        String oldServer = this.previousServerName;
        this.previousServerName = this.currentServerName;
        this.currentServerName = serverName;

        for (ServerChangeListener listener : listeners) {
            try {
                listener.onServerChange(oldServer, serverName);
            } catch (Exception e) {
                ServerStore.log.error("Listener error", e);
            }
        }

        if (!this.previousServerName.equals("UNKNOWN")) {
            PlayerStore.getInstance().clear();
        }

        return true;
    }
}
