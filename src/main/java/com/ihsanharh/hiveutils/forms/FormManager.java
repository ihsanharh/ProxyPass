package com.ihsanharh.hiveutils.forms;

import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FormManager {
    private static final FormManager INSTANCE = new FormManager();
    private final AtomicInteger formIdCounter = new AtomicInteger(100000);
    private final Map<Integer, Consumer<String>> formCallbacks = new ConcurrentHashMap<>();

    private FormManager() {}

    public static FormManager getInstance() {
        return INSTANCE;
    }

    public void sendForm(ProxyPlayerSession session, Form form, Consumer<String> onResponse) {
        int formId = formIdCounter.getAndIncrement();
        formCallbacks.put(formId, onResponse);

        ModalFormRequestPacket packet = new ModalFormRequestPacket();
        packet.setFormId(formId);
        packet.setFormData(form.toJson());
        session.getUpstream().sendPacketImmediately(packet);
    }

    public boolean handleResponse(int formId, String formData) {
        Consumer<String> callback = formCallbacks.remove(formId);
        if (callback != null) {
            if (formData != null && !formData.equals("null") && !formData.trim().isEmpty()) {
                callback.accept(formData);
            }
            return true;
        }
        return false;
    }
}
