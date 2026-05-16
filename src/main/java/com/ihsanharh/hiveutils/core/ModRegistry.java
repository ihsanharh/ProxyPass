package com.ihsanharh.hiveutils.core;

import java.util.ArrayList;
import java.util.List;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.mods.chat.LiveTranslator;
import com.ihsanharh.hiveutils.mods.hideandseek.HideAndSeekESP;
import com.ihsanharh.hiveutils.mods.internal.ClientInfo;
import com.ihsanharh.hiveutils.mods.internal.CommandManagerMod;
import com.ihsanharh.hiveutils.mods.internal.FormManagerMod;
import com.ihsanharh.hiveutils.mods.internal.PlayerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SelfPlayerRemapMod;
import com.ihsanharh.hiveutils.mods.internal.ServerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.SilentCommandMod;
import com.ihsanharh.hiveutils.mods.utils.DebugMod;

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

        /* mandatory mods */
        this.activeMods.add(new DebugMod());
        this.activeMods.add(new ClientInfo());
        this.activeMods.add(new ServerTrackerMod());
        this.activeMods.add(new SelfPlayerRemapMod());
        this.activeMods.add(new PlayerTrackerMod());
        this.activeMods.add(new CommandManagerMod());
        this.activeMods.add(new FormManagerMod());
        this.activeMods.add(new SilentCommandMod());

        /* extra mods - load saved enabled states */
        BaseMod hideAndSeek = new HideAndSeekESP();
        hideAndSeek.setEnabled(configStore.getEnabledState(hideAndSeek));
        configStore.loadSettings(hideAndSeek);
        this.activeMods.add(hideAndSeek);

        BaseMod liveTranslator = new LiveTranslator();
        liveTranslator.setEnabled(configStore.getEnabledState(liveTranslator));
        configStore.loadSettings(liveTranslator);
        this.activeMods.add(liveTranslator);

        log.info("Loaded {} mods", this.activeMods.size());
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