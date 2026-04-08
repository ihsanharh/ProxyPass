package com.ihsanharh.hiveutils.core;

import java.util.ArrayList;
import java.util.List;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ProxyMod;
import com.ihsanharh.hiveutils.mods.chat.LiveTranslator;
import com.ihsanharh.hiveutils.mods.hideandseek.HideAndSeekESP;
import com.ihsanharh.hiveutils.mods.internal.CommandManagerMod;
import com.ihsanharh.hiveutils.mods.internal.FormManagerMod;
import com.ihsanharh.hiveutils.mods.internal.PlayerTrackerMod;
import com.ihsanharh.hiveutils.mods.internal.ServerTrackerMod;
import com.ihsanharh.hiveutils.mods.utils.DebugMod;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModRegistry {
    private static final ModRegistry INSTANCE = new ModRegistry();
    private final List<ProxyMod> activeMods;

    private ModRegistry() {
        this.activeMods = new ArrayList<>();

        this.activeMods.add(new DebugMod());
        this.activeMods.add(new ServerTrackerMod());
        this.activeMods.add(new PlayerTrackerMod());
        this.activeMods.add(new CommandManagerMod());
        this.activeMods.add(new FormManagerMod());
        this.activeMods.add(new HideAndSeekESP());
        this.activeMods.add(new LiveTranslator());

        log.info("Loaded {} mods", this.activeMods.size());
    }

    public static ModRegistry getInstance() {
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