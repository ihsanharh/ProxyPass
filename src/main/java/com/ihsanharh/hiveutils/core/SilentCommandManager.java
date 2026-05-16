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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.Map;

@Log4j2
public class SilentCommandManager {
    private static final SilentCommandManager INSTANCE = new SilentCommandManager();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String FORM_PREFIX = "form:";
    private static final long COMMAND_DELAY_MS = 2000;

    public static class SilentCommandResult {
        public final ArrayList<String> textLines;
        public final ModalFormRequestPacket form;

        public SilentCommandResult(ArrayList<String> textLines, ModalFormRequestPacket form) {
            this.textLines = textLines;
            this.form = form;
        }
    }

    private static class QueuedCommand {
        final ProxyPlayerSession player;
        final String commandString;
        final String formTitle;
        final ArrayList<String> textTriggers;
        final CompletableFuture<SilentCommandResult> future;

        QueuedCommand(ProxyPlayerSession player, String commandString, String formTitle, ArrayList<String> textTriggers) {
            this.player = player;
            this.commandString = commandString;
            this.formTitle = formTitle;
            this.textTriggers = textTriggers;
            this.future = new CompletableFuture<>();
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
    private final ConcurrentLinkedQueue<QueuedCommand> globalQueue = new ConcurrentLinkedQueue<>();
    private volatile long lastCommandTime = 0L;
    private volatile boolean processing = false;

    public static SilentCommandManager getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<SilentCommandResult> executeCommand(ProxyPlayerSession player, String commandString, ArrayList<String> expectedResponses) {
        String formTitle = null;
        ArrayList<String> textTriggers = new ArrayList<>(expectedResponses);

        if (!textTriggers.isEmpty() && textTriggers.get(0).startsWith(FORM_PREFIX)) {
            formTitle = textTriggers.remove(0).substring(FORM_PREFIX.length());
        }

        QueuedCommand queued = new QueuedCommand(player, commandString, formTitle, textTriggers);

        globalQueue.add(queued);
        processGlobalQueue();

        return queued.future;
    }

    private synchronized void processGlobalQueue() {
        if (processing) {
            return;
        }
        processing = true;

        long now = System.currentTimeMillis();
        long delayNeeded = Math.max(0, COMMAND_DELAY_MS - (now - lastCommandTime));

        if (delayNeeded > 0) {
            CompletableFuture.delayedExecutor(delayNeeded, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
                sendNextGlobalCommand();
            });
        } else {
            sendNextGlobalCommand();
        }
    }

    private void sendNextGlobalCommand() {
        QueuedCommand queued = globalQueue.poll();
        if (queued == null) {
            processing = false;
            return;
        }

        String playerUuid = queued.player.getAuthData().getIdentity().toString();
        CommandTask task = new CommandTask(queued.formTitle, queued.textTriggers, queued.future);
        activeTasks.put(playerUuid, task);

        CommandOriginData originData = new CommandOriginData(CommandOriginType.PLAYER, queued.player.getAuthData().getIdentity(), "", 0);
        CommandRequestPacket command = new CommandRequestPacket();
        command.setCommand(queued.commandString);
        command.setCommandOriginData(originData);
        command.setInternal(false);

        queued.player.getDownstream().sendPacketImmediately(command);
        lastCommandTime = System.currentTimeMillis();

        log.debug("Sent queued command: {}", queued.commandString);

        if (!globalQueue.isEmpty()) {
            CompletableFuture.delayedExecutor(COMMAND_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
                sendNextGlobalCommand();
            });
        } else {
            processing = false;
        }
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
