package com.ihsanharh.deepl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route.ResumeOptions;
import com.microsoft.playwright.options.AriaRole;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Log4j2
public class DeepLScraper {
    private static final int MAX_INIT_RETRIES = 3;
    private static DeepLScraper INSTANCE;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Playwright playwright;
    private Browser browser;
    private Page page;
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private final Semaphore lock = new Semaphore(1);
    private volatile String intendedTargetCode = null;
    private volatile String intendedSourceCode = null;
    private final List<PendingTranslation> pendingTranslations = new ArrayList<>();

    private DeepLScraper() {}

    private record PendingTranslation(String text, DeepLLang sourceLang, DeepLLang targetLang, CompletableFuture<TranslationResult> completableFuture) {}

    public record TranslationResult(String translatedText, DeepLLang detectedLanguage) {}

    public static synchronized DeepLScraper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DeepLScraper();
        }
        return INSTANCE;
    }

    /**
     * Initializes the DeepL Scraper with retry logic.
     * @return A CompletableFuture representing the initialization process.
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            if (isInitialized || isInitializing) return;

            isInitializing = true;
            int attempt = 0;

            while (attempt < MAX_INIT_RETRIES && !isInitialized) {
                attempt++;
                log.debug("Initializing DeepL Scraper (attempt {}/{})...", attempt, MAX_INIT_RETRIES);

                try {
                    this.playwright = Playwright.create();
                    this.browser = playwright.chromium().launch();
                    this.page = browser.newPage();

                    this.page.route("**/v1/storefront/translate", route -> {
                        if (route.request().method().equals("POST")) {
                            String postData = route.request().postData();

                            if (postData != null && this.intendedTargetCode != null) {
                                postData = postData.replaceAll("\"target_lang\":\"[^\"]+\"", "\"target_lang\":\"" + this.intendedTargetCode + "\"");

                                if (this.intendedSourceCode != null) {
                                    postData = postData.replaceAll("\"source_lang\":\"[^\"]+\"", "\"source_lang\":\"" + this.intendedSourceCode + "\"");
                                }

                                route.resume(new ResumeOptions().setPostData(postData));
                                return;
                            }
                        }

                        route.resume();
                    });

                    log.debug("Navigating to DeepL...");
                    this.page.navigate("https://www.deepl.com/en?tab=translate-text");

                    Thread.sleep(3000);

                    this.clickSourceLanguageSelector();
                    this.page.waitForSelector("[data-testid='translator-source-lang-list-all-languages-grid']");
                    List<String> scrapedSourceLanguages = this.page.locator("[data-testid^='translator-lang-option-'] span.truncate").allTextContents();
                    this.checkLanguages(scrapedSourceLanguages, "source");

                    this.clickTargetLanguageSelector();
                    this.page.waitForSelector("[data-testid='translator-target-lang-list-all-languages-grid']");
                    List<String> scrapedTargetLanguages = this.page.locator("[data-testid^='translator-lang-option-'] span.truncate").allTextContents();
                    this.checkLanguages(scrapedTargetLanguages, "target");

                    this.isInitialized = true;
                    log.info("DeepL Scraper initialized successfully (attempt {})", attempt);
                    processPendingTranslations();
                    break;
                } catch (Exception e) {
                    log.error("Failed to initialize DeepL Scraper (attempt {}/{}): {}", attempt, MAX_INIT_RETRIES, e.getMessage());
                    this.shutdown();

                    if (attempt < MAX_INIT_RETRIES) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (!isInitialized) {
                log.error("DeepL Scraper failed to initialize after {} attempts", MAX_INIT_RETRIES);
            }

            isInitializing = false;
        });
    }

    /**
     * Shuts down the DeepL Scraper.
     */
    public void shutdown() {
        log.debug("Shutting down DeepL Scraper...");
        this.isInitialized = false;
        this.isInitializing = false;

        try {
            if (this.page != null) this.page.close();
            if (this.browser != null) this.browser.close();
            if (this.playwright != null) this.playwright.close();
        } catch (Exception e) {
            log.error("Error during DeepL Scraper shutdown", e);
        }
    }
    
    /**
     * Checks if the DeepL Scraper is initialized.
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return this.isInitialized;
    }

    /**
     * Translates the given text from the source language to the target language.
     * Queues the request if DeepL Scraper is still initializing.
     * @param text The text to translate.
     * @param sourceLang The source language.
     * @param targetLang The target language.
     * @return new {@link TranslationResult} or null if an error occurs.
     */
    public CompletableFuture<TranslationResult> translate(String text, DeepLLang sourceLang, DeepLLang targetLang) {
        if (!this.isInitialized) {
            CompletableFuture<TranslationResult> future = new CompletableFuture<>();

            synchronized (pendingTranslations) {
                pendingTranslations.add(new PendingTranslation(text, sourceLang, targetLang, future));
            }

            if (!this.isInitializing) {
                log.debug("DeepL Scraper is not initialized. Starting initialization...");
                this.initialize();
            }

            return future;
        }

        return CompletableFuture.supplyAsync(() -> doTranslate(text, sourceLang, targetLang));
    }

    /**
    * Clears the pending translation queue and cancels waiting futures.
    * @return The number of pending translations that were cleared.
        */
    public int clearQueue() {
        synchronized (pendingTranslations) {
            int clearedCount = pendingTranslations.size();
        
            if (clearedCount == 0) {
                return 0;
            }
        
            log.debug("Clearing {} pending translations from the queue...", clearedCount);
            for (PendingTranslation pending : pendingTranslations) {
                pending.completableFuture().complete(new TranslationResult(pending.text(), pending.sourceLang())); 
            }
        
            pendingTranslations.clear();
            return clearedCount;
        }
    }

    private void processPendingTranslations() {
        synchronized (pendingTranslations) {
            log.debug("Processing {} pending translation requests...", pendingTranslations.size());

            for (PendingTranslation pending : pendingTranslations) {
                try {
                    TranslationResult result = doTranslate(pending.text(), pending.sourceLang(), pending.targetLang());
                    pending.completableFuture().complete(result);
                } catch (Exception e) {
                    log.error("Failed to process pending translation: {}", pending.text(), e);
                    pending.completableFuture().complete(new TranslationResult(pending.text(), pending.sourceLang()));
                }
            }

            pendingTranslations.clear();
        }
    }

    private TranslationResult doTranslate(String text, DeepLLang sourceLang, DeepLLang targetLang) {
        long startTime = System.currentTimeMillis();
        final TranslationResult[] finalResult = {new TranslationResult(text, sourceLang)};

        try {
            log.debug("Queueing translation request for: '{}'", text);
            lock.acquire();

            log.debug("Processing translation: '{}' from '{}' to '{}'", text, sourceLang, targetLang);

            DeepLLang targetLangToSet = targetLang.asTarget();

            this.intendedTargetCode = targetLangToSet.getCode();
            this.intendedSourceCode = sourceLang.getCode();

            if (this.getCurrentTargetLanguage() != targetLangToSet) {
                this.setUITargetLanguage(targetLangToSet.getUiLabel());
            }

            this.setUISourceLanguage(sourceLang.getUiLabel());

            this.page.waitForResponse(response -> {
                boolean isApiResult = response.url().contains("/v1/storefront/translate")
                        && response.request().method().equals("POST")
                        && response.status() == 200;

                if (isApiResult) {
                    try {
                        String requestData = response.request().postData();
                        
                        if (requestData != null) {
                            JsonNode reqNode = MAPPER.readTree(requestData);
                            JsonNode textArray = reqNode.findPath("text");

                            if (textArray.isArray() && textArray.size() > 0 && textArray.get(0).asText().equals(text)) {
                                String body = response.text();
                                JsonNode root = MAPPER.readTree(body);
                                JsonNode textNode = root.findPath("text");
                                JsonNode langNode = root.findPath("detected_source_language");

                                if (!textNode.isMissingNode() && !textNode.asText().trim().isEmpty()) {
                                    String translatedStr = textNode.asText();
                                    DeepLLang detectedLang = langNode.isMissingNode() ? sourceLang : DeepLLang.fromCodeOrLabel(langNode.asText());
                                    
                                    finalResult[0] = new TranslationResult(translatedStr, detectedLang);
                                    return true;
                                }
                            }
                        }
                    } catch(Exception e) {
                        log.error("Failed to parse DeepL JSON response", e);
                    }
                }

                return false;
            }, () -> {
                this.setSourceText(text);
            });

            this.clearInput();

            long endTime = System.currentTimeMillis();
            log.debug("Translation completed in " + (endTime - startTime) + "ms.");

            return finalResult[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Translation thread was interrupted", e);
            return new TranslationResult(text, sourceLang);
        } catch (Exception e) {
            log.error("Error during translation process", e);
            return new TranslationResult(text, sourceLang);
        } finally {
            lock.release();
            log.debug("Translation complete, lock released.");
        }
    }

    private void setUISourceLanguage(String lang) {
        this.clickSourceLanguageSelector();
        this.selectUILanguage(lang);
        log.debug("Setting source language to: " + lang);
    }

    private void setSourceText(String text) {
        this.page.fill("textarea#source-text-area", text);
    }

    private void setUITargetLanguage(String lang) {
        this.clickTargetLanguageSelector();
        this.selectUILanguage(lang);
        log.debug("Setting target language to: " + lang);
    }

    private void clearInput() {
        this.page.locator("button[aria-label='Clear text']").click();
    }

    private DeepLLang getCurrentTargetLanguage() {
        return DeepLLang.fromCodeOrLabel(this.page.locator("[data-testid='translator-target-lang']").textContent());
    }

    private void selectUILanguage(String lang) {
        this.page.getByRole(AriaRole.LISTBOX).waitFor();
        this.page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions()
                .setName(lang)
                .setExact(true))
                .click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true));
    }

    private void clickSourceLanguageSelector() {
        this.page.locator("button:has([data-testid='translator-source-lang'])").click();
    }

    private void clickTargetLanguageSelector() {
        this.page.locator("button:has([data-testid='translator-target-lang'])").click();
    }

    private void checkLanguages(List<String> scrapedWebLanguages, String listType) {
        log.debug("Performing two-way health check of Enum against UI...");
        
        this.page.locator("button[aria-expanded='true']").click();

        List<String> expectedWebLabels = new ArrayList<>();
        
        for (DeepLLang lang : DeepLLang.values()) { 
            if (listType == "source") {
                if (lang.asSource() == lang) {
                    expectedWebLabels.add(lang.getUiLabel());
                }
            } else if (listType == "target") {
                if (lang != DeepLLang.DETECT_LANGUAGE && lang.asTarget() == lang) {
                    expectedWebLabels.add(lang.getUiLabel());
                }
            }
        }
        
        List<String> missingFromWeb = new ArrayList<>(expectedWebLabels);
        missingFromWeb.removeAll(scrapedWebLanguages);
        
        List<String> missingFromEnum = new ArrayList<>(scrapedWebLanguages);
        missingFromEnum.removeAll(expectedWebLabels);
        missingFromEnum.remove("Detect language");

        if (!missingFromWeb.isEmpty()) {
            log.warn("⚠️ ALARM: Enum " + listType + " languages missing from DeepL website (Removed or Renamed?): {}", missingFromWeb);
        } else {
            log.debug("✅ All Enum " + listType + " languages exist on the website.");
        }

        if (!missingFromEnum.isEmpty()) {
            log.debug("💡 NOTICE: New " + listType + " languages found on DeepL website (Consider adding these to your Enum!): {}", missingFromEnum);
        }
    }
}
