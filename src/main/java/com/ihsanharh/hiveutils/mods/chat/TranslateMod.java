package com.ihsanharh.hiveutils.mods.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.CustomForm;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.SimpleForm;
import com.ihsanharh.hiveutils.utils.ChatParser;
import com.ihsanharh.hiveutils.utils.TextPacketUtils;

import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class TranslateMod extends BaseMod {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TRANSLATE_TIMEOUT = Duration.ofSeconds(15);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TRANSLATE_TIMEOUT).build();
    private volatile String deeplxUrl = "https://dplx.xi-xu.me/deepl";
    private final Object translationLock = new Object();
    private volatile boolean translationAvailable = true;
    private volatile long unreachableUntil = 0;
    private static final long UNREACHABLE_COOLDOWN_MS = 30_000;
    private static final int TRANSLATE_MAX_RETRIES = 2;
    private static final long RATE_LIMIT_BACKOFF_MS = 1000;
    private String target = "";
    private DeepLLang targetLanguage = DeepLLang.ENGLISH_AMERICAN;
    private DeepLLang autoTranslateLang = null;
    private final Map<String, DeepLLang> targetPlayerMap = new HashMap<>();
    private final Map<String, DeepLLang> globalLangMap = new HashMap<>();

    @Override
    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("target", this.target);
        settings.put("targetLanguage", this.targetLanguage.getCode());
        settings.put("deeplxUrl", this.deeplxUrl);
        
        return settings;
    }

    @Override
    public void loadSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) return;

        Object targetObj = settings.get("target");

        if (targetObj instanceof String) {
            this.target = (String) targetObj;
        }

        Object langObj = settings.get("targetLanguage");

        if (langObj instanceof String) {
            DeepLLang lang = DeepLLang.fromCodeOrLabel((String) langObj);
            
            if (lang != null) {
                this.targetLanguage = lang;
            }
        }

        Object urlObj = settings.get("deeplxUrl");

        if (urlObj instanceof String) {
            String url = (String) urlObj;
        
            if (isValidDeeplxUrl(url)) {
                this.deeplxUrl = url;
            } else {
                log.warn("Ignoring invalid DeeplX URL in settings: {}", url);
            }
        }

        if (this.target != null && !this.target.isEmpty()) {
            this.parseTargetPlayers(null);
        }
    }

    private enum TranslationStatus {
        OK, BAD_REQUEST, RATE_LIMITED, SERVICE_DOWN, NETWORK_ERROR, PARSE_ERROR
    }

    private record TranslationResult(String translatedText, DeepLLang detectedLanguage, TranslationStatus status, int statusCode) {
        TranslationResult(String translatedText, DeepLLang detectedLanguage) {
            this(translatedText, detectedLanguage, TranslationStatus.OK, -1);
        }

        TranslationResult(String translatedText, DeepLLang detectedLanguage, TranslationStatus status) {
            this(translatedText, detectedLanguage, status, -1);
        }

        boolean success() {
            return status == TranslationStatus.OK;
        }

        boolean serviceDown() {
            return status == TranslationStatus.SERVICE_DOWN;
        }
    }

    private CompletableFuture<TranslationResult> translate(String text, DeepLLang sourceLang, DeepLLang targetLang) {
        if (!isTranslationEnabled()) {
            return CompletableFuture.completedFuture(new TranslationResult(text, sourceLang, TranslationStatus.SERVICE_DOWN));
        }

        return attemptTranslate(text, sourceLang, targetLang, 0);
    }

    private CompletableFuture<TranslationResult> attemptTranslate(String text, DeepLLang sourceLang, DeepLLang targetLang, int attempt) {
        String sourceCode = sourceLang.getDeeplxCode();
        String targetCode = targetLang.asTarget().getDeeplxCode();
        
        String body;
        try {
            body = MAPPER.writeValueAsString(java.util.Map.of(
                    "text", text,
                    "source_lang", sourceCode,
                    "target_lang", targetCode
            ));
        } catch (Exception e) {
            log.error("Failed to serialize translation request", e);
            return CompletableFuture.completedFuture(new TranslationResult(text, sourceLang, TranslationStatus.PARSE_ERROR));
        }

        URI endpoint;
        try {
            endpoint = URI.create(this.deeplxUrl);
        } catch (IllegalArgumentException e) {
            log.error("Invalid DeeplX URL '{}', cannot translate", this.deeplxUrl);
            return CompletableFuture.completedFuture(new TranslationResult(text, sourceLang, TranslationStatus.SERVICE_DOWN));
        }

        HttpRequest request = HttpRequest.newBuilder().uri(endpoint).timeout(TRANSLATE_TIMEOUT)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .handle((response, throwable) -> classify(response, throwable, text, sourceLang))
        .thenCompose(result -> {
            if (result.success()) {
                onTranslationSuccess();
                return CompletableFuture.completedFuture(result);
            }
            if (attempt < TRANSLATE_MAX_RETRIES && isRetryable(result.status())) {
                long delayMs = result.status() == TranslationStatus.RATE_LIMITED ? RATE_LIMIT_BACKOFF_MS : 0;
                return delay(delayMs).thenCompose(v -> attemptTranslate(text, sourceLang, targetLang, attempt + 1));
            }
            
            return CompletableFuture.completedFuture(result);
        });
    }

    private TranslationResult classify(HttpResponse<String> response, Throwable throwable, String text, DeepLLang sourceLang) {
        if (throwable != null) {
            log.warn("[TranslateMod] DeeplX request failed: {}", throwable.getMessage());
            return new TranslationResult(text, sourceLang, TranslationStatus.NETWORK_ERROR);
        }

        int code = response.statusCode();

        try {
            JsonNode root = MAPPER.readTree(response.body());

            if (root.has("code")) {
                code = root.get("code").asInt(code);
            }
        } catch (Exception ignored) {}

        return switch (code) {
            case 200 -> parseResponse(response.body(), sourceLang);
            case 400 -> new TranslationResult(text, sourceLang, TranslationStatus.BAD_REQUEST);
            case 429 -> new TranslationResult(text, sourceLang, TranslationStatus.RATE_LIMITED);
            case 500, 503 -> new TranslationResult(text, sourceLang, TranslationStatus.SERVICE_DOWN, code);
            default -> new TranslationResult(text, sourceLang, TranslationStatus.SERVICE_DOWN, code);
        };
    }

    private static boolean isRetryable(TranslationStatus status) {
        return status == TranslationStatus.BAD_REQUEST || status == TranslationStatus.RATE_LIMITED || status == TranslationStatus.NETWORK_ERROR;
    }

    private static CompletableFuture<Void> delay(long ms) {
        if (ms <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS).execute(() -> future.complete(null));
        
        return future;
    }

    private TranslationResult parseResponse(String body, DeepLLang fallbackSource) {
        try {
            JsonNode root = MAPPER.readTree(body);
            String data = root.path("data").asText(null);
            
            if (data == null || data.isEmpty()) {
                log.error("[TranslateMod] DeeplX returned empty translation: {}", body);
                return new TranslationResult(body, fallbackSource, TranslationStatus.PARSE_ERROR);
            }

            String detectedCode = root.path("source_lang").asText(null);
            DeepLLang detectedLang = detectedCode != null ? DeepLLang.fromCodeOrLabel(detectedCode) : fallbackSource;
            
            if (detectedLang == null) {
                detectedLang = fallbackSource;
            }

            return new TranslationResult(data, detectedLang);
        } catch (Exception e) {
            log.error("[TranslateMod] Failed to parse DeeplX response: {}", body, e);
            return new TranslationResult(body, fallbackSource, TranslationStatus.PARSE_ERROR);
        }
    }

    private boolean isTranslationEnabled() {
        synchronized (translationLock) {
            if (translationAvailable) {
                return true;
            }
            if (System.currentTimeMillis() >= unreachableUntil) {
                translationAvailable = true;
                return true;
            }

            return false;
        }
    }

    private void onTranslationSuccess() {
        synchronized (translationLock) {
            translationAvailable = true;
        }
    }

    private void onTranslationFailure(ProxyPlayerSession session, int statusCode) {
        boolean shouldWarn;
        
        synchronized (translationLock) {
            if (translationAvailable) {
                translationAvailable = false;
                unreachableUntil = System.currentTimeMillis() + UNREACHABLE_COOLDOWN_MS;
                shouldWarn = true;
            } else {
                shouldWarn = false;
            }
        }
        if (shouldWarn && session != null) {
            String codeStr = statusCode > 0 ? String.valueOf(statusCode) : "SERVICE_DOWN";
            sendUserText(session, "§cTranslation service is unreachable (status " + codeStr + "). Translation is paused until it recovers.");
        }
    }

    private boolean isValidDeeplxUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof AvailableCommandsPacket availableCommandsPacket) {
            availableCommandsPacket.getCommands().add(buildTranslateCommandData());
            log.debug("[TranslateMod] Injected /translate command.");
            return ModResult.MODIFIED;
        }

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

        DeepLLang playerSourceLang = this.targetPlayerMap.get(cleanUsername.toLowerCase());
        if (playerSourceLang != null) {
            this.performTargetedTranslation(session, textPacket, parsedChat, cleanMessage, playerSourceLang, this.targetLanguage);
            return ModResult.DENY;
        }

        boolean hasGlobalFilters = !this.globalLangMap.isEmpty();
        boolean translateAll = this.target.isEmpty();

        if (hasGlobalFilters || translateAll) {
            this.performGlobalTranslation(session, textPacket, parsedChat, cleanMessage, translateAll);
            return ModResult.DENY;
        }

        return ModResult.PASS;
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket && (textPacket.getType() == TextPacket.Type.CHAT || textPacket.getType() == TextPacket.Type.RAW)) {
            if (autoTranslateLang != null) {
                String message = textPacket.getMessage();

                this.translate(message, DeepLLang.DETECT_LANGUAGE, autoTranslateLang)
                .thenAccept(translated -> {
                    if (!translated.success()) {
                        if (translated.serviceDown()) onTranslationFailure(session, translated.statusCode());
                        session.getDownstream().sendPacket(textPacket);
                        return;
                    }
                    if (translated.translatedText() != null) {
                        sendTranslatedChat(session, translated.translatedText());
                    } else {
                        session.getDownstream().sendPacket(textPacket);
                    }
                })
                .exceptionally(e -> {
                    log.error("[TranslateMod] Auto-translation failed", e);
                    session.getDownstream().sendPacket(textPacket);
                    return null;
                });

                return ModResult.DENY;
            }
        }

        if (packet instanceof CommandRequestPacket commandRequest) {
            String rawCommand = commandRequest.getCommand();
            if (rawCommand.startsWith("/translate")) {
                String[] parts = rawCommand.substring(1).split(" ");
                String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                executeTranslateCommand(session, args);
                
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }

    private void executeTranslateCommand(ProxyPlayerSession session, String[] args) {
        if (args.length == 0) {
            if (autoTranslateLang != null) {
                autoTranslateLang = null;
                sendUserText(session, "§cAuto-translation disabled.");
            } else {
                sendUserText(session, "§bUsage: §3/translate <lang> [message] §7- Translates a message to the specified language");
                sendUserText(session, "§bUsage: §3/translate <lang> §7- Toggles auto-translation for all of your messages to the specified language");
                sendUserText(session, "§bUsage: §3/translate langs §7- Shows a list of available languages");
                sendUserText(session, "§bUsage: §3/translate languages §7- Shows a list of available languages");
                sendUserText(session, "§bExample: §7/translate id hello");
            }

            return;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("langs") || arg.equals("languages")) {
            showLanguagesForm(session);
            return;
        }

        String targetArg = args[0];
        String targetLang = targetArg.contains(" | ") ? targetArg.split(" - ")[0].trim() : targetArg;

        if (args.length == 1) {
            if (autoTranslateLang != null && targetLang.equalsIgnoreCase(autoTranslateLang.getCode())) {
                autoTranslateLang = null;
                sendUserText(session, "§cAuto-translation disabled.");
            } else {
                DeepLLang target = DeepLLang.fromCodeOrLabel(targetLang);

                if (target == null) {
                    sendUserText(session, "§cThat is not a valid language!");
                    return;
                }

                autoTranslateLang = target;
                sendUserText(session, "§aAuto-translation enabled! Target: §3" + target.getUiLabel() + "§a. Run /translate again to disable.");
            }

            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        DeepLLang target = DeepLLang.fromCodeOrLabel(targetLang);

        if (target == null) {
            sendUserText(session, "§cThat is not a valid language!");
            return;
        }

        this.translate(message, DeepLLang.DETECT_LANGUAGE, target)
        .thenAccept(translated -> {
            if (translated != null && translated.success() && translated.translatedText() != null) {
                sendTranslatedChat(session, translated.translatedText());
            } else {
                if (translated != null && translated.serviceDown()) onTranslationFailure(session, translated.statusCode());
                sendUserText(session, "§cTranslation failed.");
            }
        }).exceptionally(e -> {
            log.error("[TranslateMod] Manual translation failed", e);
            sendUserText(session, "§cTranslation failed.");
            return null;
        });
    }

    private void showLanguagesForm(ProxyPlayerSession session) {
        ModContext ctx = ModContext.forSession(session);
        if (ctx == null) return;

        List<DeepLLang> sortedLangs = getSortedTargetLanguages();

        SimpleForm form = new SimpleForm("Available Languages", "Select a language to auto-translate for all messages you sent:");
        for (DeepLLang lang : sortedLangs) {
            form.addButton("§d" + lang.getUiLabel() + "\n§8" + lang.getCode());
        }

        final List<DeepLLang> finalLangs = sortedLangs;
        ctx.getFormManager().sendForm(session, form, responseStr -> {
            if (responseStr == null || responseStr.isEmpty()) return;
            try {
                int index = Integer.parseInt(responseStr);
                if (index >= 0 && index < finalLangs.size()) {
                    DeepLLang selected = finalLangs.get(index);
                    autoTranslateLang = selected;
                    sendUserText(session, "§aAuto-translation enabled! Target: §f" + selected.getUiLabel());
                    sendUserText(session, "§7Run §f/translate §7again to disable.");
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    private static List<DeepLLang> getSortedTargetLanguages() {
        Set<String> addedCodes = new HashSet<>();
        List<DeepLLang> sorted = new ArrayList<>();
        for (DeepLLang lang : DeepLLang.values()) {
            if (lang == DeepLLang.DETECT_LANGUAGE) continue;
            DeepLLang target = lang.asTarget();
            if (addedCodes.add(target.getCode())) {
                sorted.add(target);
            }
        }
        sorted.sort(Comparator.comparing(DeepLLang::getUiLabel, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private CommandData buildTranslateCommandData() {
        Map<String, Set<CommandEnumConstraint>> enumValues = new HashMap<>();
        enumValues.put("langs", Set.of());
        enumValues.put("languages", Set.of());

        List<DeepLLang> sortedTargets = getSortedTargetLanguages();

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

        Set<CommandData.Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();

        return new CommandData("translate", "Translate your messages to another language", commandFlags, null, null, subCommands, overloads);
    }

    private void performTargetedTranslation(ProxyPlayerSession session, TextPacket textPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, DeepLLang fromLang, DeepLLang toLang) {
        this.translate(cleanMessage, fromLang, toLang)
        .thenAccept(translated -> {
            if (!translated.success()) {
                if (translated.serviceDown()) onTranslationFailure(session, translated.statusCode());
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }
            this.dispatchTranslatedPacket(session, textPacket, parsedChat, cleanMessage, translated);
        })
        .exceptionally(e -> {
            log.error("[TranslateMod] Targeted translation failed", e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });
    }

    private void performGlobalTranslation(ProxyPlayerSession session, TextPacket textPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, boolean translateAll) {
        this.translate(cleanMessage, DeepLLang.DETECT_LANGUAGE, this.targetLanguage)
        .thenAccept(translated -> {
            if (!translated.success()) {
                if (translated.serviceDown()) onTranslationFailure(session, translated.statusCode());
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            DeepLLang detectedEnum = translated.detectedLanguage();

            if (detectedEnum != null && detectedEnum == this.targetLanguage) {
                session.getUpstream().sendPacketImmediately(textPacket);
                return;
            }

            boolean passesFilter = translateAll;

            if (!passesFilter && detectedEnum != null) {
                if (this.globalLangMap.containsKey(detectedEnum.getCode().toLowerCase())) {
                    passesFilter = true;
                }
            }

            if (passesFilter) {
                this.dispatchTranslatedPacket(session, textPacket, parsedChat, cleanMessage, translated);
            } else {
                session.getUpstream().sendPacketImmediately(textPacket);
            }
        })
        .exceptionally(e -> {
            log.error("[TranslateMod] Global translation failed", e);
            session.getUpstream().sendPacketImmediately(textPacket);
            return null;
        });
    }

    private void dispatchTranslatedPacket(ProxyPlayerSession session, TextPacket originalPacket, ChatParser.ParsedChat parsedChat, String cleanMessage, TranslationResult translated) {
        String rawUsername = parsedChat.rawUsername();
        String rawSplitter = parsedChat.rawSplitter();
        String rawMessage = parsedChat.rawMessage();

        String translatedString = translated.translatedText();

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

    private void sendTranslatedChat(ProxyPlayerSession session, String message) {
        String username = session.getAuthData().getDisplayName();
        String xuid = session.getAuthData().getXuid();

        TextPacket textPacket = new TextPacket();
        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setMessage(message);
        textPacket.setXuid(xuid);
        textPacket.setSourceName(username);

        session.getDownstream().sendPacket(textPacket);
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

        List<DeepLLang> langOptions = getSortedTargetLanguages();
        List<String> langLabels = new ArrayList<>();
        int currentLangIndex = 0;
        String currentTargetCode = this.targetLanguage.asTarget().getCode();
        for (int i = 0; i < langOptions.size(); i++) {
            DeepLLang lang = langOptions.get(i);
            langLabels.add(lang.getUiLabel());
            if (lang.getCode().equalsIgnoreCase(currentTargetCode)) {
                currentLangIndex = i;
            }
        }
        form.addDropdown("Target Language", langLabels, currentLangIndex);
        form.addInput("DeeplX URL", "https://your-deeplx.example.com/deepl", this.deeplxUrl);
    }

    @Override
    public boolean handleSettingsSubmit(ProxyPlayerSession session, String response) {
        try {
            JsonNode node = MAPPER.readTree(response);
            if (!node.isArray()) return false;

            String oldTargetPlayer = this.target;
            DeepLLang oldTargetLanguage = this.targetLanguage;
            String oldUrl = this.deeplxUrl;

            if (node.has(2)) {
                this.target = node.get(2).asText();
            }

            if (node.has(3)) {
                int langIndex = node.get(3).asInt(-1);
                List<DeepLLang> langOptions = getSortedTargetLanguages();
                if (langIndex >= 0 && langIndex < langOptions.size()) {
                    this.targetLanguage = langOptions.get(langIndex);
                } else {
                    sendUserText(session, "§cInvalid target language selection, keeping previous value.");
                }
            }

            if (node.has(4)) {
                String url = node.get(4).asText();
                if (url != null && !url.trim().isEmpty()) {
                    if (isValidDeeplxUrl(url.trim())) {
                        if (!url.trim().equals(oldUrl)) {
                            this.deeplxUrl = url.trim();
                            onTranslationSuccess();
                            sendUserText(session, "§aDeeplX endpoint updated, translation re-enabled.");
                        }
                    } else {
                        sendUserText(session, "§cInvalid DeeplX URL, keeping previous value.");
                    }
                }
            }

            boolean changed = !oldTargetPlayer.equals(this.target) || !oldTargetLanguage.equals(this.targetLanguage) || !oldUrl.equals(this.deeplxUrl);
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
        java.util.List<String> validEntries = new java.util.ArrayList<>();

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
                    if (lang != null) {
                        this.globalLangMap.put(lang.getCode().toLowerCase(), lang);
                        validEntries.add("(" + lang.getCode() + ")");
                    } else {
                        sendUserText(session, "§cRemoved invalid global language filter: (" + langStr + "). Please provide a valid language code or name.");
                    }
                } else {
                    if (langStr != null && !langStr.trim().isEmpty()) {
                        if (lang != null) {
                            this.targetPlayerMap.put(mapName, lang);
                            validEntries.add(originalName + " (" + lang.getCode() + ")");
                        } else {
                            this.targetPlayerMap.put(mapName, DeepLLang.DETECT_LANGUAGE);
                            validEntries.add(originalName);
                            sendUserText(session, "§cRemoved invalid language hint '" + langStr + "' for player '" + originalName + "'. Fallback to auto-detect.");
                        }
                    } else {
                        this.targetPlayerMap.put(mapName, DeepLLang.DETECT_LANGUAGE);
                        validEntries.add(originalName);
                    }
                }
            }
        }

        this.target = String.join(", ", validEntries);
    }

    private void sendUserText(ProxyPlayerSession session, String message) {
        TextPacketUtils.sendRawToClient(session, message);
    }

    private enum DeepLLang {
        DETECT_LANGUAGE("Detect language", "auto"),
        ACEHNESE("Acehnese", "ace"),
        AFRIKAANS("Afrikaans", "af"),
        ALBANIAN("Albanian", "sq"),
        ARABIC("Arabic", "ar"),
        ARAGONESE("Aragonese", "an"),
        ARMENIAN("Armenian", "hy"),
        ASSAMESE("Assamese", "as"),
        AYMARA("Aymara", "ay"),
        AZERBAIJANI("Azerbaijani", "az"),
        BASHKIR("Bashkir", "ba"),
        BASQUE("Basque", "eu"),
        BELARUSIAN("Belarusian", "be"),
        BENGALI("Bengali", "bn"),
        BHOJPURI("Bhojpuri", "bho"),
        BOSNIAN("Bosnian", "bs"),
        BRETON("Breton", "br"),
        BULGARIAN("Bulgarian", "bg"),
        BURMESE("Burmese", "my"),
        CANTONESE("Cantonese", "yue"),
        CATALAN("Catalan", "ca"),
        CEBUANO("Cebuano", "ceb"),
        CHINESE("Chinese", "zh"),
        CROATIAN("Croatian", "hr"),
        CZECH("Czech", "cs"),
        DANISH("Danish", "da"),
        DARI("Dari", "prs"),
        DUTCH("Dutch", "nl"),
        ENGLISH("English", "en"),
        ESPERANTO("Esperanto", "eo"),
        ESTONIAN("Estonian", "et"),
        FINNISH("Finnish", "fi"),
        FRENCH("French", "fr"),
        GALICIAN("Galician", "gl"),
        GEORGIAN("Georgian", "ka"),
        GERMAN("German", "de"),
        GREEK("Greek", "el"),
        GUARANI("Guarani", "gn"),
        GUJARATI("Gujarati", "gu"),
        HAITIAN_CREOLE("Haitian Creole", "ht"),
        HAUSA("Hausa", "ha"),
        HEBREW("Hebrew", "he"),
        HINDI("Hindi", "hi"),
        HUNGARIAN("Hungarian", "hu"),
        ICELANDIC("Icelandic", "is"),
        IGBO("Igbo", "ig"),
        INDONESIAN("Indonesian", "id"),
        IRISH("Irish", "ga"),
        ITALIAN("Italian", "it"),
        JAPANESE("Japanese", "ja"),
        JAVANESE("Javanese", "jv"),
        KAPAMPANGAN("Kapampangan", "pam"),
        KAZAKH("Kazakh", "kk"),
        KONKANI("Konkani", "gom"),
        KOREAN("Korean", "ko"),
        KURDISH_KURMANJI("Kurdish (Kurmanji)", "kmr"),
        KURDISH_SORANI("Kurdish (Sorani)", "ckb"),
        KYRGYZ("Kyrgyz", "ky"),
        LATIN("Latin", "la"),
        LATVIAN("Latvian", "lv"),
        LINGALA("Lingala", "ln"),
        LITHUANIAN("Lithuanian", "lt"),
        LOMBARD("Lombard", "lmo"),
        LUXEMBOURGISH("Luxembourgish", "lb"),
        MACEDONIAN("Macedonian", "mk"),
        MAITHILI("Maithili", "mai"),
        MALAGASY("Malagasy", "mg"),
        MALAY("Malay", "ms"),
        MALAYALAM("Malayalam", "ml"),
        MALTESE("Maltese", "mt"),
        MAORI("Maori", "mi"),
        MARATHI("Marathi", "mr"),
        MONGOLIAN("Mongolian", "mn"),
        NEPALI("Nepali", "ne"),
        NORWEGIAN_BOKM_L("Norwegian (bokmål)", "nb"),
        OCCITAN("Occitan", "oc"),
        OROMO("Oromo", "om"),
        PANGASINAN("Pangasinan", "pag"),
        PASHTO("Pashto", "ps"),
        PERSIAN("Persian", "fa"),
        POLISH("Polish", "pl"),
        PORTUGUESE("Portuguese", "pt-PT"),
        PUNJABI("Punjabi", "pa"),
        QUECHUA("Quechua", "qu"),
        ROMANIAN("Romanian", "ro"),
        RUSSIAN("Russian", "ru"),
        SANSKRIT("Sanskrit", "sa"),
        SERBIAN("Serbian", "sr"),
        SESOTHO("Sesotho", "st"),
        SICILIAN("Sicilian", "scn"),
        SLOVAK("Slovak", "sk"),
        SLOVENIAN("Slovenian", "sl"),
        SPANISH("Spanish", "es-ES"),
        SUNDANESE("Sundanese", "su"),
        SWAHILI("Swahili", "sw"),
        SWEDISH("Swedish", "sv"),
        TAGALOG("Tagalog", "tl"),
        TAJIK("Tajik", "tg"),
        TAMIL("Tamil", "ta"),
        TATAR("Tatar", "tt"),
        TELUGU("Telugu", "te"),
        TSONGA("Tsonga", "ts"),
        TSWANA("Tswana", "tn"),
        TURKISH("Turkish", "tr"),
        TURKMEN("Turkmen", "tk"),
        UKRAINIAN("Ukrainian", "uk"),
        URDU("Urdu", "ur"),
        UZBEK("Uzbek", "uz"),
        VIETNAMESE("Vietnamese", "vi"),
        WELSH("Welsh", "cy"),
        WOLOF("Wolof", "wo"),
        XHOSA("Xhosa", "xh"),
        YIDDISH("Yiddish", "yi"),
        ZULU("Zulu", "zu"),
        CHINESE_SIMPLIFIED("Chinese (simplified)", "zh-Hans"),
        CHINESE_TRADITIONAL("Chinese (traditional)", "zh-Hant"),
        ENGLISH_AMERICAN("English (American)", "en-US"),
        ENGLISH_BRITISH("English (British)", "en-GB"),
        PORTUGUESE_BRAZILIAN("Portuguese (Brazilian)", "pt-BR"),
        SPANISH_LATIN_AMERICAN("Spanish (Latin American)", "es-419");

        private final String uiLabel;
        private final String code;

        DeepLLang(String uiLabel, String code) {
            this.uiLabel = uiLabel;
            this.code = code;
        }

        public String getUiLabel() {
            return uiLabel;
        }

        public String getCode() {
            return code;
        }

        public String getDeeplxCode() {
            if (this == DETECT_LANGUAGE) {
                return "AUTO";
            }
            String base = code.contains("-") ? code.substring(0, code.indexOf('-')) : code;
            return base.toUpperCase();
        }

        public DeepLLang asTarget() {
            switch (this) {
                case DETECT_LANGUAGE:
                    throw new IllegalArgumentException("Cannot use DETECT_LANGUAGE as a target language!");
                case ENGLISH:
                    return ENGLISH_AMERICAN;
                case CHINESE:
                    return CHINESE_SIMPLIFIED;
                default:
                    return this;
            }
        }

        public static DeepLLang fromCodeOrLabel(String input) {
            if (input == null || input.trim().isEmpty()) return null;

            for (DeepLLang lang : values()) {
                if (lang.code.equalsIgnoreCase(input) || lang.uiLabel.equalsIgnoreCase(input)) {
                    return lang;
                }
            }

            if (input.equalsIgnoreCase("es")) return SPANISH;
            if (input.equalsIgnoreCase("pt")) return PORTUGUESE;
            if (input.equalsIgnoreCase("zh")) return CHINESE;

            return null;
        }
    }
}
