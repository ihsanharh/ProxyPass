package com.ihsanharh.hiveutils.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModConfigStore {
    private static final String CONFIG_FILE = "hiveutils_mods.json";
    private static final ModConfigStore INSTANCE = new ModConfigStore();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Boolean> enabledStates = new HashMap<>();
    private final Map<String, Map<String, Object>> modSettings = new HashMap<>();
    private Path configPath;
    private boolean initialized = false;

    public static ModConfigStore getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (this.initialized) return;

        log.info("Initializing mod config store...");
        try {
            Path baseDir = Paths.get(".").toAbsolutePath();
            this.configPath = baseDir.resolve(CONFIG_FILE);
            log.info("Config path: {}", this.configPath);
            this.initialized = true;
            load();
        } catch (Exception e) {
            log.error("Could not initialize config, using defaults: {}", e.getMessage());
        }
    }

    public void ensureInitialized() {
        if (!this.initialized) {
            initialize();
        }
    }

    public void load() {
        if (configPath == null) {
            log.warn("Config not initialized yet, using defaults");
            return;
        }

        try {
            if (!Files.exists(configPath)) {
                log.info("No existing config file, will create on first save");
                return;
            }

            Map<String, Object> config = MAPPER.readValue(configPath.toFile(), new TypeReference<Map<String, Object>>() {});
            if (config == null) {
                return;
            }

            Object enabledObj = config.get("enabled");
            if (enabledObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> enabledMap = (Map<String, Object>) enabledObj;
                for (Map.Entry<String, Object> entry : enabledMap.entrySet()) {
                    if (entry.getValue() instanceof Boolean) {
                        enabledStates.put(entry.getKey(), (Boolean) entry.getValue());
                    }
                }
            }

            Object settingsObj = config.get("settings");
            if (settingsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> settingsMap = (Map<String, Object>) settingsObj;
                for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> settingMap = (Map<String, Object>) entry.getValue();
                        modSettings.put(entry.getKey(), settingMap);
                    }
                }
            }

            log.info("Loaded mod config: {}", enabledStates.keySet());
        } catch (Exception e) {
            log.debug("Could not load mod config: {}", e.getMessage());
        }
    }

    public void save() {
        if (configPath == null) {
            log.warn("Config not initialized yet, cannot save");
            return;
        }

        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", enabledStates);
            config.put("settings", modSettings);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
            log.info("Saved mod config to: {}", configPath);
        } catch (IOException e) {
            log.error("Failed to save mod config", e);
        }
    }

    public boolean getEnabledState(com.ihsanharh.hiveutils.api.BaseMod mod) {
        if (!initialized) {
            log.warn("Config not initialized, returning default: true");
            return true;
        }
        return enabledStates.getOrDefault(mod.getName(), true);
    }

    public void setEnabledState(com.ihsanharh.hiveutils.api.BaseMod mod, boolean enabled) {
        if (!initialized) {
            log.warn("Config not initialized, cannot save state");
            return;
        }
        enabledStates.put(mod.getName(), enabled);
        mod.setEnabled(enabled);
        log.info("Saved {} = {}", mod.getName(), enabled);

        Map<String, Object> settings = mod.getSettings();
        if (settings != null && !settings.isEmpty()) {
            modSettings.put(mod.getName(), settings);
            log.info("Saved settings for {}", mod.getName());
        }

        save();
    }

    public void loadSettings(com.ihsanharh.hiveutils.api.BaseMod mod) {
        if (!initialized) return;
        Map<String, Object> settings = modSettings.get(mod.getName());
        if (settings != null && !settings.isEmpty()) {
            mod.loadSettings(settings);
            log.info("Loaded settings for {}", mod.getName());
        }
    }

    public void saveModSettings(com.ihsanharh.hiveutils.api.BaseMod mod, Map<String, Object> settings) {
        if (!initialized) return;
        modSettings.put(mod.getName(), settings);
        log.info("Saved settings for {}", mod.getName());
        save();
    }

    public Map<String, Object> getModSettings(com.ihsanharh.hiveutils.api.BaseMod mod) {
        return modSettings.getOrDefault(mod.getName(), new HashMap<>());
    }
}