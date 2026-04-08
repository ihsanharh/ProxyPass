package com.ihsanharh.hiveutils.mods.chat;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.Translator;
import com.ihsanharh.hiveutils.utils.ChatParser;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LiveTranslator extends BaseMod {
    private String targetPlayer = "";
    private String targetLanguage = "en";
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (!(packet instanceof TextPacket textPacket)) {
            return ModResult.PASS;
        }

        if (textPacket.getType() != TextPacket.Type.CHAT) {
            return ModResult.PASS;
        }

        String fullMessage = textPacket.getMessage();
        ChatParser.ParsedChat parsedChat = ChatParser.parse(fullMessage);
        String cleanUsername = parsedChat.cleanName();
        String cleanMessage = parsedChat.cleanMessage();
        String rawUsername = parsedChat.rawUsername();
        String rawSplitter = parsedChat.rawSplitter();
        String rawMessage = parsedChat.rawMessage();

        if (cleanMessage == null || cleanMessage.isEmpty()) {
            return ModResult.PASS;
        }

        if (cleanMessage.endsWith(")") && cleanMessage.contains(" -> ")) {
            return ModResult.PASS;
        }

        if (!targetPlayer.isEmpty() && !targetPlayer.toLowerCase().contains(cleanUsername.toLowerCase())) {
            return ModResult.PASS;
        }

        Translator.detectLanguage(cleanMessage).thenAccept(detectedLang -> {
            if (detectedLang.equalsIgnoreCase(targetLanguage) || detectedLang.equalsIgnoreCase("unknown")) {
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            Translator.translateText(cleanMessage, "auto", targetLanguage).thenAccept(result -> {
                TextPacket outPacket = new TextPacket();
                outPacket.setType(textPacket.getType());
                outPacket.setNeedsTranslation(textPacket.isNeedsTranslation());
                outPacket.setSourceName(textPacket.getSourceName());
                outPacket.setXuid(textPacket.getXuid());
                outPacket.setPlatformChatId(textPacket.getPlatformChatId());
                outPacket.setFilteredMessage(textPacket.getFilteredMessage());
                outPacket.setParameters(textPacket.getParameters());

                if (result.translatedText() != null && !cleanMessage.equals(result.translatedText())) {
                    String formatted = rawMessage + "§r (§e" + detectedLang + " §7-> §f" + result.translatedText() + "§r)";
                    outPacket.setMessage(rawUsername + " " + rawSplitter + " " + formatted);
                } else {
                    outPacket.setMessage(fullMessage);
                }

                session.getUpstream().sendPacketImmediately(outPacket);
            }).exceptionally(e -> {
                log.error("Translation request failed for message from {}", cleanUsername, e);
                session.getUpstream().sendPacketImmediately(textPacket);
                return null;
            });
        }).exceptionally(e -> {
            log.error("Language detection failed for message from {}", cleanUsername, e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });

        return ModResult.DENY;
    }

    @Override
    public boolean hasSettingsForm() {
        return true;
    }

    @Override
    public void buildSettingsForm(com.ihsanharh.hiveutils.forms.CustomForm form) {
        form.addInput("Target Player (Leave empty for all, separate with comma for multiple player)", "PlayerName", targetPlayer);
        form.addInput("Target Language", "en, id, es...", targetLanguage);
    }

    @Override
    public void handleSettingsSubmit(ProxyPlayerSession session, String response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(response);
            
            if (node.isArray() && node.size() >= 3) {
                targetPlayer = node.get(1).asText();
                String lang = node.get(2).asText();
                if (!lang.isEmpty()) {
                    targetLanguage = lang;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse settings for LiveTranslator", e);
        }
    }
}
