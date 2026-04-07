package com.ihsanharh.hiveutils.mods.chat;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.Translator;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LiveTranslator extends BaseMod {
    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (!(packet instanceof TextPacket textPacket)) {
            return ModResult.PASS;
        }

        String fullMessage = textPacket.getMessage();
        String sourceName = textPacket.getSourceName();

        if (fullMessage == null || fullMessage.isEmpty()) {
            return ModResult.PASS;
        }

        if (fullMessage.endsWith(")") && fullMessage.contains(" -> ")) {
            return ModResult.PASS;
        }

        String prefix;
        String actualMessage;

        int splitIndex = -1;
        if (fullMessage.contains("» ")) {
            splitIndex = fullMessage.indexOf("» ") + 1;
        } else if (fullMessage.contains(": ")) {
            splitIndex = fullMessage.indexOf(": ") + 1;
        } else if (fullMessage.contains("> ")) {
            splitIndex = fullMessage.indexOf("> ") + 1;
        }

        if (splitIndex != -1) {
            prefix = fullMessage.substring(0, splitIndex + 1);
            actualMessage = fullMessage.substring(splitIndex + 1);
        } else {
            prefix = "";
            actualMessage = fullMessage;
        }

        final String cleanMessage = actualMessage.replaceAll("(?i)\u00A7[0-9A-FK-OR]", "");
        final String finalPrefix = prefix;
        final String finalActualMessage = actualMessage;

        Translator.detectLanguage(cleanMessage).thenAccept(detectedLang -> {
            if (detectedLang.equalsIgnoreCase("en") || detectedLang.equalsIgnoreCase("unknown")) {
                log.info("[Original Chat] {}: {}", sourceName, fullMessage);
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            Translator.translateText(cleanMessage, "auto", "en").thenAccept(result -> {
                TextPacket outPacket = new TextPacket();
                outPacket.setType(textPacket.getType());
                outPacket.setNeedsTranslation(textPacket.isNeedsTranslation());
                outPacket.setSourceName(textPacket.getSourceName());
                outPacket.setXuid(textPacket.getXuid());
                outPacket.setPlatformChatId(textPacket.getPlatformChatId());
                outPacket.setFilteredMessage(textPacket.getFilteredMessage());
                outPacket.setParameters(textPacket.getParameters());

                if (result.translatedText() != null && !cleanMessage.equals(result.translatedText())) {
                    String formatted = finalActualMessage +
                            "\u00A7r (\u00A7e" + detectedLang +
                            " \u00A77-> \u00A7f" + result.translatedText() + "\u00A7r)";
                    outPacket.setMessage(finalPrefix + formatted);
                    log.info("[Translated Chat] {}: {}", sourceName, outPacket.getMessage());
                } else {
                    outPacket.setMessage(fullMessage);
                    log.info("[Original Chat] {}: {}", sourceName, fullMessage);
                }

                session.getUpstream().sendPacketImmediately(outPacket);
            }).exceptionally(e -> {
                log.error("Translation request failed for message from {}", sourceName, e);
                session.getUpstream().sendPacketImmediately(textPacket);
                return null;
            });
        }).exceptionally(e -> {
            log.error("Language detection failed for message from {}", sourceName, e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });

        return ModResult.DENY;
    }
}
