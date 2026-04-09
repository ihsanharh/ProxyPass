package com.ihsanharh.hiveutils.mods.chat;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihsanharh.hiveutils.api.ModResult;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ihsanharh.hiveutils.core.Translator;
import com.ihsanharh.hiveutils.forms.CustomForm;
import com.ihsanharh.hiveutils.utils.ChatParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LiveTranslator extends BaseMod {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private String targetPlayer = "";
    private String targetLanguage = "en";
    private final Map<String, String> targetPlayerMap = new HashMap<>();
    private final Map<String, String> globalLangMap = new HashMap<>();

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (!(packet instanceof TextPacket textPacket)) {
            return ModResult.PASS;
        }

        if (textPacket.getType() != TextPacket.Type.CHAT) {
            return ModResult.PASS;
        }

        ChatParser.ParsedChat parsedChat = ChatParser.parse(textPacket.getMessage());
        if (parsedChat == null) {
            return ModResult.PASS;
        }

        String cleanUsername = parsedChat.cleanName();
        String cleanMessage = parsedChat.cleanMessage();

        if (cleanMessage == null || cleanMessage.isEmpty()) {
            return ModResult.PASS;
        }

        if (cleanMessage.endsWith(")") && cleanMessage.contains(" -> ")) {
            return ModResult.PASS;
        }

        String playerSourceLangHint = this.targetPlayerMap.get(cleanUsername.toLowerCase());

        // Case 1: Player is specifically targeted
        if (this.targetPlayerMap.containsKey(cleanUsername.toLowerCase())) {
            this.handleTranslationLogic(session, textPacket, parsedChat, cleanMessage, playerSourceLangHint);
            return ModResult.DENY;
        }

        // Case 2: Global language filters (Japan) (Indonesian) etc.
        if (!this.globalLangMap.isEmpty()) {
            Translator.detectLanguage(cleanMessage).thenAccept(detectedLang -> {
                if (detectedLang.equalsIgnoreCase(this.targetLanguage) || detectedLang.equalsIgnoreCase("unknown")) {
                    session.getUpstream().sendPacketImmediately(textPacket);
                    return;
                }

                boolean shouldTranslate = this.targetPlayer.isEmpty();
                if (!shouldTranslate) {
                    for (String hint : this.globalLangMap.values()) {
                        if (detectedLang.toLowerCase().contains(hint.toLowerCase()) || hint.toLowerCase().contains(detectedLang.toLowerCase())) {
                            shouldTranslate = true;
                            break;
                        }
                    }
                }

                if (shouldTranslate) {
                    this.performTranslation(session, textPacket, parsedChat, cleanMessage, detectedLang, this.targetLanguage);
                } else {
                    session.getUpstream().sendPacketImmediately(textPacket);
                }
            }).exceptionally(e -> {
                log.error("Language detection failed for message from {}", cleanUsername, e);
                session.getUpstream().sendPacketImmediately(textPacket);
                return null;
            });
            return ModResult.DENY;
        }

        // Case 3: No specific players and no global filters, but Target Player is empty -> translate everything
        if (this.targetPlayer.isEmpty()) {
            this.handleTranslationLogic(session, textPacket, parsedChat, cleanMessage, null);
            return ModResult.DENY;
        }

        return ModResult.PASS;
    }

    private void handleTranslationLogic(ProxyPlayerSession session, TextPacket textPacket,
            ChatParser.ParsedChat parsedChat, String cleanMessage, String sourceHint) {
        String cleanUsername = parsedChat.cleanName();

        if (sourceHint != null) {
            this.performTranslation(session, textPacket, parsedChat, cleanMessage, sourceHint, this.targetLanguage);
        } else {
            Translator.detectLanguage(cleanMessage).thenAccept(detectedLang -> {
                if (detectedLang.equalsIgnoreCase(this.targetLanguage) || detectedLang.equalsIgnoreCase("unknown")) {
                    session.getUpstream().sendPacketImmediately(textPacket);
                    return;
                }
                this.performTranslation(session, textPacket, parsedChat, cleanMessage, detectedLang, this.targetLanguage);
            }).exceptionally(e -> {
                log.error("Language detection failed for message from {}", cleanUsername, e);
                session.getUpstream().sendPacketImmediately(textPacket);
                return null;
            });
        }
    }

    private void performTranslation(ProxyPlayerSession session, TextPacket textPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, String fromLang, String toLang) {
        String cleanUsername = parsedChat.cleanName();
        String rawUsername = parsedChat.rawUsername();
        String rawSplitter = parsedChat.rawSplitter();
        String rawMessage = parsedChat.rawMessage();

        Translator.translateText(cleanMessage, fromLang, toLang).thenAccept(result -> {
            TextPacket outPacket = new TextPacket();
            outPacket.setType(textPacket.getType());
            outPacket.setNeedsTranslation(textPacket.isNeedsTranslation());
            outPacket.setSourceName(textPacket.getSourceName());
            outPacket.setXuid(textPacket.getXuid());
            outPacket.setPlatformChatId(textPacket.getPlatformChatId());
            outPacket.setFilteredMessage(textPacket.getFilteredMessage());
            outPacket.setParameters(textPacket.getParameters());

            boolean isAutoDetection = fromLang.equalsIgnoreCase("auto");
            boolean langIsDifferent = !result.detectedLang().equalsIgnoreCase(this.targetLanguage);
            boolean textIsDifferent = !cleanMessage.equalsIgnoreCase(result.translatedText());
            boolean shouldShow = textIsDifferent && (isAutoDetection ? langIsDifferent : true);

            if (result.translatedText() != null && shouldShow) {
                String formatted = rawMessage + "§r (§e" + result.detectedLang() + " §7-> §f" + result.translatedText() + "§r)";
                outPacket.setMessage(rawUsername + " " + rawSplitter + " " + formatted);
            } else {
                outPacket.setMessage(textPacket.getMessage());
            }

            session.getUpstream().sendPacketImmediately(outPacket);
        }).exceptionally(e -> {
            log.error("Translation request failed for message from {}", cleanUsername, e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });
    }

    @Override
    public boolean hasSettingsForm() {
        return true;
    }

    @Override
    public void buildSettingsForm(ProxyPlayerSession session, CustomForm form) {
        String myName = session.getAuthData().getDisplayName();
        form.addLabel("§b§lHow to use Filters:§r\n" +
                      "§eFormat: §fUser §7(Hint)§f, §7(GlobalFilter)§r\n\n" +
                      "§6Example: §7" + myName + " (id), (ja), the slayer§r\n" +
                      "§8- §bUser (Hint): §7Translates §fUser §7as §fHint §7lang§r\n" +
                      "§8- §b(Lang): §7Translates §fanyone §7speaking §fLang§r\n" +
                      "§8- §bUser: §7Translates specific player (Auto)§r\n" +
                      "§8- §bEmpty: §7Translates §fEVERYONE§r");
        form.addInput("Target Player / Filters", "PlayerName (Hint), (Lang)...", this.targetPlayer);
        form.addInput("Target Language", "en, id, es...", this.targetLanguage);
    }

    @Override
    public boolean handleSettingsSubmit(ProxyPlayerSession session, String response) {
        try {
            JsonNode node = MAPPER.readTree(response);
            if (!node.isArray())
                return false;

            String oldTargetPlayer = this.targetPlayer;
            String oldTargetLanguage = this.targetLanguage;

            if (node.has(2)) {
                this.targetPlayer = node.get(2).asText();
            }

            if (node.has(3)) {
                String langInput = node.get(3).asText();
                if (langInput != null && !langInput.trim().isEmpty()) {
                    this.targetLanguage = langInput.trim();
                }
            }

            boolean changed = !oldTargetPlayer.equals(this.targetPlayer) || !oldTargetLanguage.equals(this.targetLanguage);
            if (changed) {
                this.parseTargetPlayers();
            }
            return changed;
        } catch (Exception e) {
            log.error("Failed to parse settings for LiveTranslator", e);
            return false;
        }
    }

    private void parseTargetPlayers() {
        this.targetPlayerMap.clear();
        this.globalLangMap.clear();

        if (this.targetPlayer.isEmpty()) {
            return;
        }

        String[] parts = this.targetPlayer.split(",");
        Pattern pattern = Pattern.compile("^(.*?)(?:\\s*\\((.*?)\\))?$");

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;

            Matcher matcher = pattern.matcher(trimmedPart);
            if (matcher.matches()) {
                String name = matcher.group(1).trim().toLowerCase();
                String lang = matcher.group(2);
                
                if (name.isEmpty() && lang != null) {
                    this.globalLangMap.put(lang.toLowerCase(), lang);
                } else if (!name.isEmpty()) {
                    this.targetPlayerMap.put(name, lang != null ? lang.trim() : null);
                }
            }
        }
    }
}
