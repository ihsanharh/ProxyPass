package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FormManager {
    private final AtomicInteger formIdCounter = new AtomicInteger(100000);
    private final Map<Integer, Consumer<String>> formCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Deque<FormEntry>> playerFormHistory = new ConcurrentHashMap<>();

    private record FormEntry(Form form, Consumer<String> callback) {}

    public void sendForm(ProxyPlayerSession session, Form form, Consumer<String> onResponse) {
        this.sendForm(session, form, onResponse, true);
    }

    public void sendForm(ProxyPlayerSession session, Form form, Consumer<String> onResponse, boolean addToHistory) {
        String xuid = session.getAuthData().getXuid();
        if (addToHistory) {
            this.playerFormHistory.computeIfAbsent(xuid, k -> new ArrayDeque<>()).push(new FormEntry(form, onResponse));
        }

        int formId = formIdCounter.getAndIncrement();
        formCallbacks.put(formId, response -> {
            if (response == null || response.equals("null") || response.trim().isEmpty()) {
                this.handleBack(session);
            } else {
                onResponse.accept(response);
            }
        });

        ModalFormRequestPacket packet = new ModalFormRequestPacket();
        packet.setFormId(formId);
        packet.setFormData(form.toJson());
        session.getUpstream().sendPacket(packet);
    }

    public void handleBack(ProxyPlayerSession session) {
        String xuid = session.getAuthData().getXuid();
        Deque<FormEntry> history = this.playerFormHistory.get(xuid);
        
        if (history != null && !history.isEmpty()) {
            history.pop();
            
            if (!history.isEmpty()) {
                FormEntry previous = history.peek();
                this.sendForm(session, previous.form(), previous.callback(), false);
            }
        }
    }

    public boolean handleResponse(int formId, String formData) {
        Consumer<String> callback = formCallbacks.remove(formId);
        if (callback != null) {
            callback.accept(formData);
            return true;
        }
        return false;
    }

    public void removePlayer(ProxyPlayerSession session) {
        playerFormHistory.remove(session.getAuthData().getXuid());
    }
}
