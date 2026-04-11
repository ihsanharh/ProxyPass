package com.ihsanharh.hiveutils.commands;

import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseProxyCommand;
import com.ihsanharh.deepl.DeepLLang;
import com.ihsanharh.deepl.DeepLScraper;
import com.ihsanharh.hiveutils.api.ModResult;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class TranslateCommand extends BaseProxyCommand {
    private static DeepLLang autoTranslateLang = null;

    public TranslateCommand() {
        super("translate", "Translate your messages to another language");
    }

    public static DeepLLang getAutoTranslateLang() {
        return autoTranslateLang;
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket && (textPacket.getType() == TextPacket.Type.CHAT || textPacket.getType() == TextPacket.Type.RAW)) {
            if (autoTranslateLang != null) {
                String message = textPacket.getMessage();
                log.info("Auto-translating upstream message via DeepL: {}", message);

                CompletableFuture.supplyAsync(() -> DeepLScraper.getInstance().translate(message, DeepLLang.DETECT_LANGUAGE, autoTranslateLang))
                    .thenAccept(translated -> {
                        log.info("Translation result: {}", translated.translatedText());
                        if (translated.translatedText() != null) {
                            this.sendTextChat(session, translated.translatedText());
                        }
                    })
                    .exceptionally(e -> {
                        log.error("DeepL translation failed", e);
                        return null;
                    });
                return ModResult.DENY;
            }
        }
        return ModResult.PASS;
    }

    @Override
    public void execute(ProxyPlayerSession session, String[] args) {
        if (args.length == 0) {
            if (autoTranslateLang != null) {
                autoTranslateLang = null;
                this.sendUserText(session, "§cAuto-translation disabled.");
            } else {
                this.sendUserText(session, "§bUsage: /translate <lang> [message]");
                this.sendUserText(session, "§7Example: /translate jp Hello!");
                this.sendUserText(session, "§7Or just /translate jp to enable auto-translate for all your messages.");
            }
            return;
        }

        String targetLang = args[0];

        // Case 1: /translate <lang> (Toggle Auto-Translate)
        if (args.length == 1) {
            if (autoTranslateLang != null && (targetLang.equalsIgnoreCase(autoTranslateLang.getCode()) || targetLang.equalsIgnoreCase(autoTranslateLang.getUiLabel()))) {
                autoTranslateLang = null;
                this.sendUserText(session, "§cAuto-translation disabled.");
            } else {
                DeepLLang target = DeepLLang.fromCodeOrLabel(targetLang);

                if (target == null) {
                    this.sendUserText(session, "§cInvalid language! Please provide a valid language code or name.");
                    return;
                }

                autoTranslateLang = target;
                this.sendUserText(session, "§aAuto-translation enabled! Target: §f" + targetLang);
                this.sendUserText(session, "§7Run /translate again to disable.");
            }
            return;
        }

        // Case 2: /translate <lang> <message> (One-time translate
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        CompletableFuture.supplyAsync(() -> DeepLScraper.getInstance().translate(message, DeepLLang.DETECT_LANGUAGE, DeepLLang.fromCodeOrLabel(targetLang)))
            .thenAccept(translated -> {
                if (translated != null) {
                    this.sendTextChat(session, translated.translatedText());
                }
            }).exceptionally(e -> {
                log.error("Manual translation failed", e);
                this.sendUserText(session, "§cTranslation failed.");
                return null;
            });
    }

    private void sendTextChat(ProxyPlayerSession session, String message) {
        String username = session.getAuthData().getDisplayName();
        String xuid = session.getAuthData().getXuid();

        TextPacket textPacket = new TextPacket();
        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setMessage(message);
        textPacket.setXuid(xuid);
        textPacket.setSourceName(username);

        session.getDownstream().sendPacketImmediately(textPacket);
    }
}
