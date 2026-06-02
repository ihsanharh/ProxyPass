package com.ihsanharh.hiveutils.mods.internal;

import java.util.ArrayList;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.api.ProxyMod;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DebugMod extends BaseMod {
    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof TextPacket textPacket) {
            String textMessage = textPacket.getMessage();

            if (textMessage.contains("find ")) {
                String playerName = textMessage.substring(textMessage.indexOf(" ") + 1);

                log.info(context.getPlayerStore().getPlayerByName(playerName));

                return ModResult.DENY;
            }

            if (textMessage.contains("modlist")) {
                String modName = textMessage.substring(textMessage.indexOf(" ") + 1);

                for (ProxyMod mod : context.getModRegistry().getMods()) {
                    if (mod instanceof BaseMod baseMod) {
                        if (modName.equalsIgnoreCase(baseMod.getName())) {
                            log.info("{} updated", baseMod.getName());
                            baseMod.toggle();
                        }

                        log.info("Mod Name: {}\nEnabled: {}", baseMod.getName(), baseMod.isEnabled());
                    }
                }

                return ModResult.DENY;
            }

            if (textMessage.contains("!testform")) {
                ArrayList<String> expected = new ArrayList<>();
                expected.add("form:friends");
                context.getSilentCommandManager().executeCommand(session, "/friends", expected)
                    .thenAccept(result -> {
                        if (result.form != null) {
                            log.info("Captured form data: {}", result.form.getFormData());
                        } else {
                            log.info("No form captured");
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to capture form", ex);
                        return null;
                    });

                return ModResult.DENY;
            }
        }
        return ModResult.PASS;
    }
}
