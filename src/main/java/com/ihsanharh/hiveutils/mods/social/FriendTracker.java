package com.ihsanharh.hiveutils.mods.social;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FriendTracker extends BaseMod {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FRIEND_NAME_PATTERN = Pattern.compile("^((?:§[0-9a-z])*)(.+?)(?:\\s*(?:§[0-9a-z])*\\[|\\s*\\n)");

    private Map<String, String> friends = new ConcurrentHashMap<>();
    private Map<UUID, String> friendUuids = new ConcurrentHashMap<>();
    private ProxyPlayerSession currentSession;
    private final ConcurrentLinkedQueue<PlayerListPacket> pendingPackets = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean friendsReady = new AtomicBoolean(false);
    private volatile boolean initialSyncDone = false;

    @Override
    public void onInitialize() {
        context.getServerStore().addListener((oldServer, newServer) -> {
            if (currentSession != null) fetchFriends();
        });
    }

    private void fetchFriends() {
        friendsReady.set(false);
        pendingPackets.clear();
        friendUuids.clear();
        initialSyncDone = false;

        ArrayList<String> expected = new ArrayList<>();
        expected.add("form:friends");

        context.getSilentCommandManager().executeCommand(currentSession, "/friends", expected)
        .thenAccept(result -> {
            if (result.form != null) parseFriendsForm(result.form.getFormData());
        })
        .exceptionally(ex -> {
            log.error("Failed to fetch friends", ex);
            friendsReady.set(true);
            initialSyncDone = true;
            flushPendingPackets();

            return null;
        });
    }

    private void parseFriendsForm(String formData) {
        try {
            JsonNode rootNode = MAPPER.readTree(formData);
            JsonNode buttons = rootNode.get("buttons");

            if (buttons == null || !buttons.isArray()) return;

            int totalButtons = buttons.size();
            int friendButtons = Math.max(0, totalButtons - 3);

            Map<String, String> newFriends = new ConcurrentHashMap<>();

            for (int i = 0; i < friendButtons; i++) {
                JsonNode button = buttons.get(i);
                JsonNode textNode = button.get("text");

                if (textNode == null || !textNode.isTextual()) continue;

                String buttonText = textNode.asText();
                Matcher matcher = FRIEND_NAME_PATTERN.matcher(buttonText);

                if (!matcher.find()) continue;

                String formattedName = matcher.group(1) + matcher.group(2);
                String cleanName = matcher.group(2).trim();

                newFriends.put(cleanName, formattedName);
            }

            this.friends.clear();
            this.friends.putAll(newFriends);
            friendsReady.set(true);
            flushPendingPackets();
        } catch (Exception e) {
            log.error("Failed to parse friends form", e);
        }
    }

    private void notify(String message) {
        if (currentSession != null) {
            TextPacket textPacket = new TextPacket();
            textPacket.setType(TextPacket.Type.RAW);
            textPacket.setNeedsTranslation(false);
            textPacket.setMessage(message);
            textPacket.setXuid("");

            currentSession.getUpstream().sendPacket(textPacket);
        }
    }

    private void processPlayerListPacket(PlayerListPacket packet) {
        if (packet.getAction() == PlayerListPacket.Action.ADD) {
            boolean isInitialSync = !initialSyncDone && packet.getEntries().size() > 1;

            for (PlayerListPacket.Entry entry : packet.getEntries()) {
                String playerName = entry.getName();

                if (playerName == null) continue;

                if (this.friends.containsKey(playerName)) {
                    friendUuids.put(entry.getUuid(), playerName);

                    String formattedName = friends.get(playerName);

                    if (isInitialSync) {
                        notify(String.format("%s§e is in this server", formattedName));
                    } else {
                        notify(String.format("%s§a Joined your server", formattedName));
                    }
                }
            }

            if (isInitialSync) initialSyncDone = true;
        } else if (packet.getAction() == PlayerListPacket.Action.REMOVE) {
            if (packet.getEntries().size() > 1) return;

            for (PlayerListPacket.Entry entry : packet.getEntries()) {
                String playerName = friendUuids.remove(entry.getUuid());

                if (playerName == null) continue;
                if (this.friends.containsKey(playerName)) notify(String.format("%s§c Left your server", friends.get(playerName)));
            }
        }
    }

    private void flushPendingPackets() {
        PlayerListPacket packet;

        while ((packet = pendingPackets.poll()) != null) {
            processPlayerListPacket(packet);
        }
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        this.currentSession = session;

        if (packet instanceof PlayerListPacket playerListPacket) {
            if (!friendsReady.get()) {
                pendingPackets.offer(playerListPacket);
                
                return ModResult.PASS;
            }

            processPlayerListPacket(playerListPacket);
        }

        return ModResult.PASS;
    }
}
