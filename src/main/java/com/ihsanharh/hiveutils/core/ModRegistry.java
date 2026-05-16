package com.ihsanharh.hiveutils.core;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.reflections.Reflections;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.mods.internal.ClientInfo;
import com.ihsanharh.hiveutils.mods.internal.CommandManagerMod;
import com.ihsanharh.hiveutils.mods.internal.FormManagerMod;
import com.ihsanharh.hiveutils.mods.internal.PlayerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SelfPlayerRemapMod;
import com.ihsanharh.hiveutils.mods.internal.ServerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SilentCommandMod;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModRegistry {
    private static final ModRegistry INSTANCE = new ModRegistry();
    private final List<ProxyMod> activeMods;
    private ModConfigStore configStore;
    private boolean initialized = false;

    static {
        INSTANCE.ensureInitialized();
    }

    private ModRegistry() {
        this.activeMods = new ArrayList<>();
    }

    private void ensureInitialized() {
        if (this.initialized) return;
        this.initialized = true;

        this.configStore = ModConfigStore.getInstance();
        this.configStore.ensureInitialized();
        this.configStore.load();

        /* mandatory internal mods - registered in specific order */
        registerInternalMod(new ClientInfo());
        registerInternalMod(new ServerTrackerMod());
        registerInternalMod(new SelfPlayerRemapMod());
        registerInternalMod(new PlayerTrackerMod());
        registerInternalMod(new CommandManagerMod());
        registerInternalMod(new FormManagerMod());
        registerInternalMod(new SilentCommandMod());

        /* auto-discover external mods */
        autoDiscoverMods();

        log.info("Loaded {} mods", this.activeMods.size());
    }

    private void registerInternalMod(ProxyMod mod) {
        this.activeMods.add(mod);
        log.debug("Registered internal mod: {}", mod.getClass().getSimpleName());
    }

    private void autoDiscoverMods() {
        Reflections reflections = new Reflections("com.ihsanharh.hiveutils.mods");
        Set<Class<? extends ProxyMod>> modClasses = reflections.getSubTypesOf(ProxyMod.class);

        for (Class<? extends ProxyMod> modClass : modClasses) {
            String packageName = modClass.getPackageName();

            if (packageName.contains(".mods.internal")) {
                continue;
            }

            if (Modifier.isAbstract(modClass.getModifiers())) {
                continue;
            }

            try {
                ProxyMod mod = modClass.getDeclaredConstructor().newInstance();

                if (mod instanceof BaseMod baseMod) {
                    baseMod.setEnabled(configStore.getEnabledState(baseMod));
                    configStore.loadSettings(baseMod);
                }

                this.activeMods.add(mod);
                log.debug("Auto-discovered external mod: {}", modClass.getSimpleName());
            } catch (Exception e) {
                log.error("Failed to load mod: {}", modClass.getSimpleName(), e);
            }
        }
    }

    public static ModRegistry getInstance() {
        if (!INSTANCE.initialized) {
            INSTANCE.ensureInitialized();
        }
        return INSTANCE;
    }

    public List<ProxyMod> getMods() {
        return this.activeMods;
    }

    public BaseMod getModByName(String name) {
        for (ProxyMod mod : activeMods) {
            if (mod instanceof BaseMod baseMod && baseMod.getName().equalsIgnoreCase(name)) {
                return baseMod;
            }
        }

        return null;
    }
}
