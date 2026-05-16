package com.ihsanharh.hiveutils.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ServerStore {
    private static final ServerStore INSTANCE = new ServerStore();
    private String currentServerName = "UNKNOWN";
    private String previousServerName = "UNKNOWN";
    private final List<BiConsumer<String, String>> listeners = new ArrayList<>();

    public static ServerStore getInstance() {
        return INSTANCE;
    }

    public String getCurrentServerName() {
        return currentServerName;
    }

    public String getPreviousServerName() {
        return previousServerName;
    }

    public void addListener(BiConsumer<String, String> callback) {
        this.listeners.add(callback);
    }

    public void removeListener(BiConsumer<String, String> callback) {
        this.listeners.remove(callback);
    }

    public boolean setCurrentServerName(String serverName) {
        if (this.currentServerName.equals(serverName)) {
            return false;
        }
        String oldServer = this.previousServerName;
        this.previousServerName = this.currentServerName;
        this.currentServerName = serverName;

        for (BiConsumer<String, String> listener : listeners) {
            try {
                listener.accept(oldServer, serverName);
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
