package com.ihsanharh.hiveutils.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class Translator {
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String groqApiKey = null;

    public static void configure(String apiKey) {
        groqApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey.trim() : null;
        if (groqApiKey != null) {
            log.info("Translator: Groq Cloud API enabled.");
        } else {
            log.info("Translator: No Groq API key found, falling back to Google Translate.");
        }
    }

    public record TranslationResult(String translatedText, String detectedLang) {}

    public static CompletableFuture<String> detectLanguage(String text) {
        return translateWithGoogle(text, "auto", "en")
                .thenApply(TranslationResult::detectedLang);
    }
    
    public static CompletableFuture<TranslationResult> translateText(String text, String fromLang, String toLang) {
        if (groqApiKey != null) {
            return translateWithGroq(text, fromLang, toLang, "llama-3.3-70b-versatile");
        } else {
            return translateWithGoogle(text, fromLang, toLang);
        }
    }

    public static CompletableFuture<TranslationResult> translateWithGroq(String text, String fromLang, String toLang, String modelId) {
        try {
            String url = "https://api.groq.com/openai/v1/chat/completions";

            ObjectNode requestBodyNode = mapper.createObjectNode();
            requestBodyNode.put("model", modelId);
            requestBodyNode.put("temperature", 0.1);
            requestBodyNode.put("max_completion_tokens", 256);
            requestBodyNode.put("stream", false);

            ArrayNode messages = requestBodyNode.putArray("messages");

            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", String.format(
                    "You are an expert translation engine. " +
                    "Translate the user's message into the target language: '%s'. " +
                    "The user might have provided a source language hint: '%s'. " +
                    "If this hint is a valid language name, country, or code, prioritize it to understand the source text. " +
                    "If the hint is 'auto', nonsense, or contradicts the text, ignore it and detect the source language yourself. " +
                    "Respond ONLY with a valid JSON object with keys: " +
                    "'translatedText' (the translated string) and 'detectedLang' (the standard ISO 639 or BCP-47 code of the true source language, e.g., 'en', 'id', 'zh-CN'). " +
                    "No markdown, no explanation, no extra fields.", toLang, fromLang));

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", text);

            ObjectNode responseFormat = requestBodyNode.putObject("response_format");
            responseFormat.put("type", "json_object");

            String requestBody = mapper.writeValueAsString(requestBodyNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() == 429) {
                            if (modelId.equals("llama-3.3-70b-versatile")) {
                                log.warn("Groq model 70b returned 429 (rate limited), cascading to 8b-instant.");
                                return translateWithGroq(text, fromLang, toLang, "llama-3.1-8b-instant");
                            } else {
                                log.warn("Groq models returned 429 (rate limited), falling back to Google Translate.");
                                return translateWithGoogle(text, "auto", toLang);
                            }
                        }
                        if (response.statusCode() == 200) {
                            try {
                                JsonNode root = mapper.readTree(response.body());
                                JsonNode choices = root.path("choices");
                                if (choices.isArray() && !choices.isEmpty()) {
                                    JsonNode message = choices.get(0).path("message");
                                    if (message.hasNonNull("content")) {
                                        JsonNode contentJson = mapper.readTree(message.get("content").asText());
                                        String translatedText = contentJson.path("translatedText").asText(text);
                                        String detectedLang = contentJson.path("detectedLang").asText("unknown");
                                        return CompletableFuture.completedFuture(new TranslationResult(translatedText, detectedLang));
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse Groq response", e);
                            }
                        } else {
                            log.warn("Groq API returned status {} body: {}", response.statusCode(), response.body());
                        }
                        return CompletableFuture.completedFuture(new TranslationResult(text, "en"));
                    })
                    .exceptionally(e -> {
                        log.error("Groq API request failed", e);
                        return new TranslationResult(text, "en");
                    });

        } catch (Exception e) {
            log.error("Failed to build Groq request", e);
            return CompletableFuture.completedFuture(new TranslationResult(text, "en"));
        }
    }

    private static CompletableFuture<TranslationResult> translateWithGoogle(String text, String fromLang, String toLang) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = String.format("https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s", fromLang, toLang, encodedText);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonNode root = mapper.readTree(response.body());
                                JsonNode array = root.get(0);
                                StringBuilder sb = new StringBuilder();
                                if (array != null && array.isArray()) {
                                    for (JsonNode n : array) {
                                        sb.append(n.get(0).asText());
                                    }
                                }
                                String detectedLang = "unknown";
                                if (root.size() > 2 && root.get(2) != null) {
                                    detectedLang = root.get(2).asText();
                                }
                                return new TranslationResult(sb.toString(), detectedLang);
                            } catch (Exception e) {
                                log.error("Failed to parse Google Translate response", e);
                            }
                        } else {
                            log.warn("Google Translate API returned status: {}", response.statusCode());
                        }
                        return new TranslationResult(text, "en");
                    })
                    .exceptionally(e -> {
                        log.error("Google Translate request failed", e);
                        return new TranslationResult(text, "en");
                    });

        } catch (Exception e) {
            log.error("Failed to build Google Translate request", e);
            return CompletableFuture.completedFuture(new TranslationResult(text, "en"));
        }
    }
}
