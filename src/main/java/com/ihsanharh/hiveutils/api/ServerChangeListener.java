package com.ihsanharh.hiveutils.api;

public interface ServerChangeListener {
    void onServerChange(String oldServer, String newServer);
}