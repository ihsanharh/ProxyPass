package com.ihsanharh.hiveutils.core;

import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Map;

@Log4j2
public class SilentCommandManager {
    private static final SilentCommandManager INSTANCE = new SilentCommandManager();
    
    // 1. THE TASK OBJECT: Groups everything a player is waiting for into one safe bundle.
    private static class CommandTask {
        final ArrayList<String> expectedTriggers;
        final ArrayList<String> capturedLines = new ArrayList<>();
        final CompletableFuture<ArrayList<String>> future = new CompletableFuture<>();

        CommandTask(ArrayList<String> expectedTriggers) {
            this.expectedTriggers = expectedTriggers;
        }
    }

    // 2. ONE MAP TO RULE THEM ALL: Maps the Player UUID to their specific Task.
    private final Map<String, CommandTask> activeTasks = new ConcurrentHashMap<>();

    public static SilentCommandManager getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<ArrayList<String>> executeCommand(ProxyPlayerSession player, String commandString, ArrayList<String> expectedResponses) {
        String playerUuid = player.getAuthData().getIdentity().toString();
        
        // Create a new task and store it safely
        CommandTask task = new CommandTask(expectedResponses);
        activeTasks.put(playerUuid, task);

        // Build and send the command
        CommandOriginData originData = new CommandOriginData(CommandOriginType.PLAYER, player.getAuthData().getIdentity(), "", 0);
        CommandRequestPacket command = new CommandRequestPacket();
        command.setCommand(commandString);
        command.setCommandOriginData(originData);
        command.setInternal(false);
        
        player.getDownstream().sendPacketImmediately(command);

        // Return the ticket
        return task.future;
    }

    public boolean checkAndComplete(ProxyPlayerSession player, String serverMessage) {
        String playerUuid = player.getAuthData().getIdentity().toString();

        // Single efficient lookup
        CommandTask task = activeTasks.get(playerUuid);

        // If task is null, this player isn't waiting for anything. Pass the packet.
        if (task == null) {
            return false; 
        }

        boolean matchedThisLine = false;

        // Check if the incoming message matches any of our expected triggers
        for (String expected : task.expectedTriggers) {
            if (serverMessage.contains(expected)) {
                task.capturedLines.add(serverMessage);
                matchedThisLine = true;
                break; // Found our match, stop checking the other triggers!
            }
        }

        // If we caught a piece of the command response...
        if (matchedThisLine) {
            
            // Did we collect all the parts we were waiting for?
            if (task.capturedLines.size() >= task.expectedTriggers.size()) {
                
                // Remove the task from the map so we don't leak memory
                activeTasks.remove(playerUuid);
                
                // Complete the future. (Notice we DO NOT clear the list here!)
                task.future.complete(task.capturedLines); 
            }
            
            return true; // We intercepted this line, tell the proxy to KILL it.
        }

        return false;
    }
}