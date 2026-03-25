package com.ihsanharh.hiveutils.mods.murdermystery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.PlayerData;
import com.ihsanharh.hiveutils.core.PlayerStore;
import com.ihsanharh.hiveutils.core.ServerStore;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MurderMysteryESP extends BaseMod {
    private Boolean ongoing = false;

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        ServerStore serverStore = ServerStore.getInstance();
        PlayerStore playerStore = PlayerStore.getInstance();

        // if (!serverStore.getCurrentServerName().contains("MURDER")) {
        // if (this.ongoing)
        // this.ongoing = false;
        // return ModResult.PASS;
        // }

        if (packet instanceof AddPlayerPacket addPlayerPacket) {
            PlayerData player = playerStore.getPlayer(addPlayerPacket.getRuntimeEntityId());

            if (player == null)
                return ModResult.PASS;

            EntityDataMap entityDataMap = addPlayerPacket.getMetadata();
            Object nameObj = entityDataMap.get(EntityDataTypes.NAME);
            String nametag = nameObj != null ? nameObj.toString() : "";

            if (nametag.isEmpty()) {
                entityDataMap.put(EntityDataTypes.NAME, "§e" + player.getPlayerName());

                return ModResult.MODIFIED;
            }
        }

        if (packet instanceof AddItemEntityPacket addItemEntityPacket) {
            log.info(addItemEntityPacket.toString());
        }

        // if (packet instanceof MobEquipmentPacket mobEquipmentPacket) {
        // PlayerData player =
        // PlayerStore.getInstance().getPlayer(mobEquipmentPacket.getRuntimeEntityId());

        // if (player == null)
        // return ModResult.PASS;

        // String itemHeld =
        // mobEquipmentPacket.getItem().getDefinition().getIdentifier();

        // if (itemHeld.contains("sword")) {
        // log.info("{} is holding {} {}", player.getPlayerName(), itemHeld,
        // itemHeld.contains("sword"));
        // SetEntityDataPacket setEntityDataPacket = new SetEntityDataPacket();
        // setEntityDataPacket.setRuntimeEntityId(mobEquipmentPacket.getRuntimeEntityId());
        // setEntityDataPacket.getMetadata().put(EntityDataTypes.NAME, "§cMurderer");

        // session.getUpstream().sendPacket(setEntityDataPacket);

        // log.info("Set {} as murderer", player.getPlayerName());
        // }
        // }

        return ModResult.PASS;
    }
}
