package com.ihsanharh.hiveutils.commands;

import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseProxyCommand;
import com.ihsanharh.hiveutils.forms.FormManager;
import com.ihsanharh.hiveutils.forms.SimpleForm;
import com.ihsanharh.deepl.DeepLLang;
import com.ihsanharh.deepl.DeepLScraper;
import com.ihsanharh.hiveutils.api.ModResult;

import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class TranslateCommand extends BaseProxyCommand {
    private static DeepLLang autoTranslateLang = null;

    public TranslateCommand() {
        super(
            "translate",
            "Translate your messages to another language",
            TranslateCommand.createOverloads()
        );
    }

    private static CommandOverloadData[] createOverloads() {
        Map<String, Set<CommandEnumConstraint>> enumValues = new HashMap<>();
        enumValues.put("langs", Set.of());
        enumValues.put("languages", Set.of());

        List<DeepLLang> sortedTargets = new ArrayList<>();
        Set<String> addedCodes = new HashSet<>();
        for (DeepLLang lang : DeepLLang.values()) {
            if (lang == DeepLLang.DETECT_LANGUAGE) continue;
            DeepLLang target = lang.asTarget();
            if (addedCodes.add(target.getCode())) {
                sortedTargets.add(target);
            }
        }
        sortedTargets.sort(Comparator.comparing(DeepLLang::getUiLabel, String.CASE_INSENSITIVE_ORDER));

        for (DeepLLang target : sortedTargets) {
            String label = target.getCode() + " | " + target.getUiLabel();
            enumValues.put(label, Set.of());
        }

        CommandEnumData optionsEnum = new CommandEnumData("Options", enumValues, true);
        CommandParamData options = new CommandParamData();
        options.setName("code");
        options.setOptional(false);
        options.setEnumData(optionsEnum);

        CommandParamData message = new CommandParamData();
        message.setName("message");
        message.setOptional(true);
        message.setType(CommandParam.STRING);

        CommandParamData[] paramData = new CommandParamData[]{options, message};
        CommandOverloadData[] overloads = new CommandOverloadData[]{new CommandOverloadData(true, paramData)};

        return overloads;
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
                this.sendUserText(session, "§bUsage: §3/translate <lang> [message] §7- Translates a message to the specified language");
                this.sendUserText(session, "§bUsage: §3/translate <lang> §7- Toggles auto-translation for all of your messages to the specified language");
                this.sendUserText(session, "§bUsage: §3/translate langs §7- Shows a list of available languages");
                this.sendUserText(session, "§bUsage: §3/translate languages §7- Shows a list of available languages");
                this.sendUserText(session, "§bExample: §7/translate id hello");
            }
            return;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("langs") || arg.equals("languages")) {
            this.showLanguagesForm(session);
            return;
        }

        String targetArg = args[0];
        String targetLang = targetArg.contains(" | ") ? targetArg.split(" - ")[0].trim() : targetArg;

        // Case 1: /translate <lang> (Toggle Auto-Translate)
        if (args.length == 1) {
            if (autoTranslateLang != null && targetLang.equalsIgnoreCase(autoTranslateLang.getCode())) {
                autoTranslateLang = null;
                this.sendUserText(session, "§cAuto-translation disabled.");
            } else {
                DeepLLang target = DeepLLang.fromCodeOrLabel(targetLang);

                if (target == null) {
                    this.sendUserText(session, "§cThat is not a valid language!");
                    return;
                }

                autoTranslateLang = target;
                this.sendUserText(session, "§aAuto-translation enabled! Target: §3" + target.getUiLabel() + "§a. Run /translate again to disable.");
            }
            return;
        }

        // Case 2: /translate <lang> <message> (One-time translate
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        DeepLLang target = DeepLLang.fromCodeOrLabel(targetLang);
        if (target == null) {
            this.sendUserText(session, "§cThat is not a valid language!");
            return;
        }

        CompletableFuture.supplyAsync(() -> DeepLScraper.getInstance().translate(message, DeepLLang.DETECT_LANGUAGE, target))
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

    private void showLanguagesForm(ProxyPlayerSession session) {
        Set<String> addedCodes = new HashSet<>();
        List<DeepLLang> sortedLangs = new ArrayList<>();
        for (DeepLLang lang : DeepLLang.values()) {
            if (lang == DeepLLang.DETECT_LANGUAGE) continue;
            DeepLLang target = lang.asTarget();
            if (addedCodes.add(target.getCode())) {
                sortedLangs.add(target);
            }
        }

        sortedLangs.sort(Comparator.comparing(DeepLLang::getUiLabel, String.CASE_INSENSITIVE_ORDER));

        SimpleForm form = new SimpleForm("Available Languages", "Select a language to auto-translate for all messages you sent:");
        for (DeepLLang lang : sortedLangs) {
            form.addButton("§d" + lang.getUiLabel() + "\n§8" + lang.getCode());
        }

        final List<DeepLLang> finalLangs = sortedLangs;
        FormManager.getInstance().sendForm(session, form, responseStr -> {
            if (responseStr == null || responseStr.isEmpty()) return;
            try {
                int index = Integer.parseInt(responseStr);
                if (index >= 0 && index < finalLangs.size()) {
                    DeepLLang selected = finalLangs.get(index);
                    autoTranslateLang = selected;
                    this.sendUserText(session, "§aAuto-translation enabled! Target: §f" + selected.getUiLabel());
                    this.sendUserText(session, "§7Run §f/translate §7again to disable.");
                }
            } catch (NumberFormatException ignored) {}
        });
    }
}
