package com.ihsanharh.hiveutils.core;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.mods.internal.ClientInfo;
import com.ihsanharh.hiveutils.mods.internal.FormManagerMod;
import com.ihsanharh.hiveutils.mods.internal.ModsManagerMod;
import com.ihsanharh.hiveutils.mods.internal.PlayerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SelfPlayerRemapMod;
import com.ihsanharh.hiveutils.mods.internal.ServerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SilentCommandMod;

import lombok.extern.log4j.Log4j2;

import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
public class ModRegistry {
    private final List<ProxyMod> activeMods;
    private final ModContext context;

    public ModRegistry(ModContext context) {
        this.context = context;
        this.activeMods = new ArrayList<>();

        registerInternalMod(new ClientInfo());
        registerInternalMod(new ServerTrackerMod());
        registerInternalMod(new SelfPlayerRemapMod());
        registerInternalMod(new PlayerTrackerMod());
        registerInternalMod(new ModsManagerMod());
        registerInternalMod(new FormManagerMod());
        registerInternalMod(new SilentCommandMod());

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
                    baseMod.setEnabled(context.getConfigStore().getEnabledState(baseMod));

                    List<String> defaults = baseMod.getDefaultServerFilters();
                    if (defaults != null) {
                        baseMod.setServerFilters(defaults);
                    }

                    context.getConfigStore().loadSettings(baseMod);

                    Map<String, Object> settings = context.getConfigStore().getModSettings(baseMod);
                    if (settings.containsKey("serverFilters")) {
                        Object filters = settings.get("serverFilters");
                        if (filters instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> list = (List<String>) filters;
                            baseMod.setServerFilters(list);
                        } else if (filters instanceof String) {
                            baseMod.setServerFilters(List.of((String) filters));
                        }
                    }
                }

                this.activeMods.add(mod);
                log.debug("Auto-discovered external mod: {}", modClass.getSimpleName());
            } catch (Exception e) {
                log.error("Failed to load mod: {}", modClass.getSimpleName(), e);
            }
        }
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
