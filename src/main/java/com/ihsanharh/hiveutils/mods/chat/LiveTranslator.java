package com.ihsanharh.hiveutils.mods.chat;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ihsanharh.deepl.DeepLLang;
import com.ihsanharh.deepl.DeepLScraper;
import com.ihsanharh.deepl.DeepLScraper.TranslationResult;
import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ServerChangeListener;
import com.ihsanharh.hiveutils.core.ServerStore;
import com.ihsanharh.hiveutils.forms.CustomForm;
import com.ihsanharh.hiveutils.utils.ChatParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LiveTranslator extends BaseMod implements ServerChangeListener {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private String target = "";
    private DeepLLang targetLanguage = DeepLLang.ENGLISH_AMERICAN;
    private final Map<String, DeepLLang> targetPlayerMap = new HashMap<>();
    private final Map<String, DeepLLang> globalLangMap = new HashMap<>();

    public LiveTranslator() {
        ServerStore.getInstance().addListener(this);
    }

    @Override
    public void onServerChange(String oldServer, String newServer) {
        DeepLScraper scraper = DeepLScraper.getInstance();
        scraper.cancel();
        scraper.clearQueue();
        log.debug("cleared translation queue on server change");
    }

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

        // Ignore messages that are already translated
        if (cleanMessage.endsWith(")") && cleanMessage.contains(" -> ")) {
            return ModResult.PASS;
        }

        DeepLLang playerSourceLang = this.targetPlayerMap.get(cleanUsername.toLowerCase());

        // Case 1: Player is specifically targeted
        if (this.targetPlayerMap.containsKey(cleanUsername.toLowerCase())) {
            this.performTargetedTranslation(session, textPacket, parsedChat, cleanMessage, playerSourceLang, this.targetLanguage);
            return ModResult.DENY;
        }

        // Case 2 & 3: Global language filters OR Translate Everything (target is empty)
        boolean hasGlobalFilters = !this.globalLangMap.isEmpty();
        boolean translateAll = this.target.isEmpty();

        if (hasGlobalFilters || translateAll) {
            this.performGlobalTranslation(session, textPacket, parsedChat, cleanMessage, translateAll);
            return ModResult.DENY;
        }

        return ModResult.PASS;
    }

    private void performTargetedTranslation(ProxyPlayerSession session, TextPacket textPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, DeepLLang fromLang, DeepLLang toLang) {
        CompletableFuture.supplyAsync(() -> DeepLScraper.getInstance().translate(cleanMessage, fromLang, toLang))
        .thenAccept(translated -> {
            if (translated == null || translated.translatedText() == null) {
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }
            this.dispatchTranslatedPacket(session, textPacket, parsedChat, cleanMessage, translated);
        })
        .exceptionally(e -> {
            log.error("DeepL targeted translation failed", e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });
    }

    private void performGlobalTranslation(ProxyPlayerSession session, TextPacket textPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, boolean translateAll) {
        CompletableFuture.supplyAsync(() -> DeepLScraper.getInstance().translate(cleanMessage, DeepLLang.DETECT_LANGUAGE, this.targetLanguage))
        .thenAccept(translated -> {
            if (translated == null || translated.translatedText() == null) {
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            // Get the Enum directly from the result!
            DeepLLang detectedEnum = translated.detectedLanguage();

            // Failsafe 1: If the detected language is already our target language, skip
            if (detectedEnum != null && detectedEnum == this.targetLanguage) {
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            // Failsafe 2: Evaluate the Global Filter
            boolean passesFilter = translateAll; 

            if (!passesFilter && detectedEnum != null) {
                // Check if the detected language matches any of the (lang) hints we set
                if (this.globalLangMap.containsKey(detectedEnum.getCode().toLowerCase())) {
                    passesFilter = true;
                }
            }

            if (passesFilter) {
                this.dispatchTranslatedPacket(session, textPacket, parsedChat, cleanMessage, translated);
            } else {
                // It was translated, but didn't pass the filter
                session.getUpstream().sendPacketImmediately(textPacket);
            }
        })
        .exceptionally(e -> {
            log.error("DeepL global translation failed", e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });
    }

    private void dispatchTranslatedPacket(ProxyPlayerSession session, TextPacket originalPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, TranslationResult translated) {
        String rawUsername = parsedChat.rawUsername();
        String rawSplitter = parsedChat.rawSplitter();
        String rawMessage = parsedChat.rawMessage();

        String translatedString = translated.translatedText();
        
        // Safely get the code directly from the Enum
        String displayLang = (translated.detectedLanguage() != null) 
                ? translated.detectedLanguage().getCode() 
                : "??";

        TextPacket outPacket = new TextPacket();
        outPacket.setType(originalPacket.getType());
        outPacket.setNeedsTranslation(originalPacket.isNeedsTranslation());
        outPacket.setSourceName(originalPacket.getSourceName());
        outPacket.setXuid(originalPacket.getXuid());
        outPacket.setPlatformChatId(originalPacket.getPlatformChatId());
        outPacket.setFilteredMessage(originalPacket.getFilteredMessage());
        outPacket.setParameters(originalPacket.getParameters());

        if (!cleanMessage.equalsIgnoreCase(translatedString)) {
            String formatted = rawMessage + "§r (§e" + displayLang + " §7-> §f" + translatedString + "§r)";
            outPacket.setMessage(rawUsername + " " + rawSplitter + " " + formatted);
        } else {
            outPacket.setMessage(originalPacket.getMessage());
        }

        session.getUpstream().sendPacketImmediately(outPacket);
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
        form.addInput("Target Player / Filters", "PlayerName (Hint), (Lang)...", this.target);
        form.addInput("Target Language", "en, id, es...", this.targetLanguage.getUiLabel());
    }

    @Override
    public boolean handleSettingsSubmit(ProxyPlayerSession session, String response) {
        try {
            JsonNode node = MAPPER.readTree(response);
            if (!node.isArray()) return false;

            String oldTargetPlayer = this.target;
            DeepLLang oldTargetLanguage = this.targetLanguage;

            if (node.has(2)) {
                this.target = node.get(2).asText();
            }

            if (node.has(3)) {
                String targetLangInput = node.get(3).asText();

                if (targetLangInput != null && !targetLangInput.trim().isEmpty()) {
                    DeepLLang lang = null;
                    try {
                        lang = DeepLLang.fromCodeOrLabel(targetLangInput);
                    } catch (Exception ignored) {}

                    if (lang != null) {
                        this.targetLanguage = lang;
                    } else {
                        this.sendUserText(session, "§cInvalid target language! Please provide a valid language code or name. Keeping previous value.");
                    }
                }
            }

            boolean changed = !oldTargetPlayer.equals(this.target) || !oldTargetLanguage.equals(this.targetLanguage);
            if (changed) {
                this.parseTargetPlayers(session);
            }
            return changed;
        } catch (Exception e) {
            log.error("Failed to parse settings for LiveTranslator", e);
            return false;
        }
    }

    private void parseTargetPlayers(ProxyPlayerSession session) {
        this.targetPlayerMap.clear();
        this.globalLangMap.clear();

        if (this.target == null || this.target.trim().isEmpty()) {
            this.target = "";
            return;
        }

        String[] parts = this.target.split(",");
        Pattern pattern = Pattern.compile("^(.*?)(?:\\s*\\((.*?)\\))?$");
        List<String> validEntries = new ArrayList<>();

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;

            Matcher matcher = pattern.matcher(trimmedPart);
            if (matcher.matches()) {
                String originalName = matcher.group(1).trim(); 
                String mapName = originalName.toLowerCase();
                String langStr = matcher.group(2);

                DeepLLang lang = null;

                if (langStr != null && !langStr.trim().isEmpty()) {
                    try {
                        lang = DeepLLang.fromCodeOrLabel(langStr.trim());
                    } catch (Exception ignored) {}
                }

                if (originalName.isEmpty()) {
                    // Global Language Filter
                    if (lang != null) {
                        this.globalLangMap.put(lang.getCode().toLowerCase(), lang);
                        validEntries.add("(" + lang.getCode() + ")");
                    } else {
                        this.sendUserText(session, "§cRemoved invalid global language filter: (" + langStr + "). Please provide a valid language code or name.");
                    }
                } else {
                    // PlayerName OR PlayerName (Language)
                    if (langStr != null && !langStr.trim().isEmpty()) {
                        if (lang != null) {
                            this.targetPlayerMap.put(mapName, lang);
                            validEntries.add(originalName + " (" + lang.getCode() + ")");
                        } else {
                            this.targetPlayerMap.put(mapName, DeepLLang.DETECT_LANGUAGE);
                            validEntries.add(originalName);
                            this.sendUserText(session, "§cRemoved invalid language hint '" + langStr + "' for player '" + originalName + "'. Fallback to auto-detect.");
                        }
                    } else {
                        // Valid PlayerName only
                        this.targetPlayerMap.put(mapName, DeepLLang.DETECT_LANGUAGE);
                        validEntries.add(originalName);
                    }
                }
            }
        }

        this.target = String.join(", ", validEntries);
    }

    private void sendUserText(ProxyPlayerSession session, String message) {
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.RAW);
        packet.setMessage(message);
        packet.setXuid("");
        
        session.getUpstream().sendPacket(packet);
    }
}