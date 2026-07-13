package com.ihsanharh.hiveutils.core;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ProxyMod;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Getter
public class ModContext {
    private static final Map<Object, ModContext> CONTEXTS = new ConcurrentHashMap<>();

    private final PlayerStore playerStore;
    private final ServerStore serverStore;
    private final ConnectedClient connectedClient;
    private final ModConfigStore configStore;
    private final SilentCommandManager silentCommandManager;
    private final FormManager formManager;
    private final ModRegistry modRegistry;
    private final List<ProxyMod> mods;

    private volatile ProxyPlayerSession playerSession;
    private volatile ProxyServerSession clientSession;

    public ModContext() {
        log.info("Initializing ModContext...");

        this.playerStore = new PlayerStore();
        this.serverStore = new ServerStore(playerStore);
        this.connectedClient = new ConnectedClient();
        this.configStore = new ModConfigStore();
        this.configStore.initialize();
        this.silentCommandManager = new SilentCommandManager();
        this.formManager = new FormManager();

        this.modRegistry = new ModRegistry(this);
        this.mods = modRegistry.getMods();

        for (ProxyMod mod : mods) {
            if (mod instanceof BaseMod baseMod) {
                baseMod.setContext(this);
                baseMod.onInitialize();
            }
        }

        log.info("ModContext initialized with {} mods", mods.size());
    }

    public void register(ProxyServerSession upstream) {
        this.clientSession = upstream;
        CONTEXTS.put(upstream, this);
    }

    public void bind(ProxyPlayerSession session) {
        this.playerSession = session;
        if (CONTEXTS.putIfAbsent(session, this) == null) {
            CONTEXTS.remove(session.getUpstream());
        }
    }

    public static ModContext forSession(ProxyPlayerSession session) {
        if (session == null) return null;
        ModContext ctx = CONTEXTS.get(session);
        if (ctx == null) {
            ctx = CONTEXTS.get(session.getUpstream());
        }
        return ctx;
    }

    public static void removeContext(ProxyPlayerSession session) {
        if (session != null) {
            CONTEXTS.remove(session);
            CONTEXTS.remove(session.getUpstream());
        }
    }
}
