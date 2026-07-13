package com.ihsanharh.hiveutils.core;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Log4j2
public class ServerStore {
    private final PlayerStore playerStore;
    private volatile String currentServerName = "UNKNOWN";
    private String previousServerName = "UNKNOWN";
    private final List<FilteredListener> listeners = new ArrayList<>();

    private static class FilteredListener {
        final List<String> filters;
        final BiConsumer<String, String> callback;

        FilteredListener(List<String> filters, BiConsumer<String, String> callback) {
            this.filters = filters;
            this.callback = callback;
        }

        boolean matches(String serverName) {
            if (filters == null || filters.isEmpty()) {
                return true;
            }
            if (serverName == null) {
                return false;
            }

            String lower = serverName.toLowerCase();

            for (String filter : filters) {
                if (lower.contains(filter.toLowerCase())) {
                    return true;
                }
            }

            return false;
        }
    }

    public ServerStore(PlayerStore playerStore) {
        this.playerStore = playerStore;
    }

    public String getCurrentServerName() {
        return currentServerName;
    }

    public String getPreviousServerName() {
        return previousServerName;
    }

    public void addListener(BiConsumer<String, String> callback) {
        addListener(null, callback);
    }

    public void addListener(List<String> filters, BiConsumer<String, String> callback) {
        this.listeners.add(new FilteredListener(filters, callback));
    }

    public void removeListener(BiConsumer<String, String> callback) {
        this.listeners.removeIf(listener -> listener.callback == callback);
    }

    public boolean setCurrentServerName(String serverName) {
        if (this.currentServerName.equals(serverName)) {
            return false;
        }
        
        String oldServer = this.currentServerName;
        this.previousServerName = this.currentServerName;
        this.currentServerName = serverName;

        for (FilteredListener listener : listeners) {
            try {
                if (listener.matches(serverName) || listener.matches(oldServer)) {
                    listener.callback.accept(oldServer, serverName);
                }
            } catch (Exception e) {
                log.error("[ServerStore] Listener error", e);
            }
        }

        if (!this.previousServerName.equals("UNKNOWN")) {
            playerStore.clear();
        }

        log.debug("[ServerStore] {} -> {}", oldServer, serverName);
        return true;
    }
}
