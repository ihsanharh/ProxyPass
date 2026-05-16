package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import lombok.extern.log4j.Log4j2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Map;

@Log4j2
public class SilentCommandManager {
    private static final SilentCommandManager INSTANCE = new SilentCommandManager();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String FORM_PREFIX = "form:";

    public static class SilentCommandResult {
        public final ArrayList<String> textLines;
        public final ModalFormRequestPacket form;

        public SilentCommandResult(ArrayList<String> textLines, ModalFormRequestPacket form) {
            this.textLines = textLines;
            this.form = form;
        }
    }

    private static class CommandTask {
        final String expectedFormTitle;
        final ArrayList<String> expectedTriggers;
        final ArrayList<String> capturedLines = new ArrayList<>();
        ModalFormRequestPacket capturedForm;
        boolean formCaptured;
        final CompletableFuture<SilentCommandResult> future;

        CommandTask(String expectedFormTitle, ArrayList<String> expectedTriggers, CompletableFuture<SilentCommandResult> future) {
            this.expectedFormTitle = expectedFormTitle;
            this.expectedTriggers = expectedTriggers;
            this.future = future;
        }

        boolean isDone() {
            boolean textDone = expectedTriggers.isEmpty() || capturedLines.size() >= expectedTriggers.size();
            boolean formDone = expectedFormTitle == null || formCaptured;
            return textDone && formDone;
        }
    }

    private final Map<String, CommandTask> activeTasks = new ConcurrentHashMap<>();

    public static SilentCommandManager getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<SilentCommandResult> executeCommand(ProxyPlayerSession player, String commandString, ArrayList<String> expectedResponses) {
        String playerUuid = player.getAuthData().getIdentity().toString();

        String formTitle = null;
        ArrayList<String> textTriggers = new ArrayList<>(expectedResponses);

        if (!textTriggers.isEmpty() && textTriggers.get(0).startsWith(FORM_PREFIX)) {
            formTitle = textTriggers.remove(0).substring(FORM_PREFIX.length());
        }

        CompletableFuture<SilentCommandResult> future = new CompletableFuture<>();
        CommandTask task = new CommandTask(formTitle, textTriggers, future);
        activeTasks.put(playerUuid, task);

        CommandOriginData originData = new CommandOriginData(CommandOriginType.PLAYER, player.getAuthData().getIdentity(), "", 0);
        CommandRequestPacket command = new CommandRequestPacket();
        command.setCommand(commandString);
        command.setCommandOriginData(originData);
        command.setInternal(false);

        player.getDownstream().sendPacketImmediately(command);

        return future;
    }

    public boolean checkAndComplete(ProxyPlayerSession player, String serverMessage) {
        String playerUuid = player.getAuthData().getIdentity().toString();

        CommandTask task = activeTasks.get(playerUuid);

        if (task == null || task.expectedTriggers.isEmpty()) {
            return false;
        }

        boolean matchedThisLine = false;

        for (String expected : task.expectedTriggers) {
            if (serverMessage.contains(expected)) {
                task.capturedLines.add(serverMessage);
                matchedThisLine = true;
                break;
            }
        }

        if (matchedThisLine) {
            if (task.isDone()) {
                completeTask(playerUuid, task);
            }
            return true;
        }

        return false;
    }

    public boolean checkAndCaptureForm(ProxyPlayerSession player, ModalFormRequestPacket formPacket) {
        String playerUuid = player.getAuthData().getIdentity().toString();

        CommandTask task = activeTasks.get(playerUuid);

        if (task == null || task.expectedFormTitle == null) {
            return false;
        }

        try {
            JsonNode rootNode = JSON_MAPPER.readTree(formPacket.getFormData());
            JsonNode titleNode = rootNode.get("title");

            log.info(rootNode.toPrettyString());

            if (titleNode != null && titleNode.isTextual()) {
                String formTitle = titleNode.asText();

                if (formTitle.toLowerCase().contains(task.expectedFormTitle.toLowerCase())) {
                    task.capturedForm = formPacket;
                    task.formCaptured = true;

                    if (task.isDone()) {
                        completeTask(playerUuid, task);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse form JSON for title matching", e);
        }

        return false;
    }

    private void completeTask(String playerUuid, CommandTask task) {
        activeTasks.remove(playerUuid);
        task.future.complete(new SilentCommandResult(task.capturedLines, task.capturedForm));
    }
}
