package com.ihsanharh.hiveutils.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatParser {
    public record ParsedChat(String cleanName, String cleanMessage, String rawUsername, String rawSplitter, String rawMessage) {}
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?:§|\\\\u00A7)[a-zA-Z0-9]");
    private static final Pattern RANK_PATTERN = Pattern.compile("\\s*\\[.*?\\]$");
    private static final Pattern CHAT_SPLITTER = Pattern.compile("^(.*?)\\s*((?:(?:§|\\\\u00A7)[a-zA-Z0-9])*»)\\s*(.*)$");
    
    /**
     * Parses a raw Hive chat line into a clean username and the actual message.
     * @param rawChatLine The raw text from the server.
     * @return A ParsedChat record containing the name and message, or null if it's not a player chat.
     */
    public static ParsedChat parse(String rawChatLine) {
        if (rawChatLine == null || rawChatLine.isEmpty()) return null;

        Matcher matcher = CHAT_SPLITTER.matcher(rawChatLine);
        if (!matcher.matches()) {
            return null;
        }

        String rawUsername = matcher.group(1);
        String rawSplitter = matcher.group(2);
        String rawMessage = matcher.group(3);  
        String cleanName = COLOR_PATTERN.matcher(rawUsername).replaceAll("");
        cleanName = RANK_PATTERN.matcher(cleanName).replaceAll("").trim();

        String cleanMessage = COLOR_PATTERN.matcher(rawMessage).replaceAll("").trim();

        return new ParsedChat(cleanName, cleanMessage, rawUsername, rawSplitter, rawMessage);
    }
}