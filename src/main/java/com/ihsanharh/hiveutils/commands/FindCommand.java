package com.ihsanharh.hiveutils.commands;

import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseProxyCommand;
import com.ihsanharh.hiveutils.core.ModContext;
import com.ihsanharh.hiveutils.core.PlayerData;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class FindCommand extends BaseProxyCommand {
    public FindCommand() {
        super("find", "Finds a player's coordinates in the world");
    }

    @Override
    public void execute(ProxyPlayerSession session, String[] args) {
        if (args.length == 0) {
            this.sendUserText(session, "§bUsage: /find [player]");
            return;
        }

        String targetName = String.join(" ", args);
        ModContext ctx = ModContext.forSession(session);
        if (ctx == null) {
            this.sendUserText(session, "§cMod context not available!");
            return;
        }

        PlayerData target = ctx.getPlayerStore().getPlayerByName(targetName);

        if (target != null) {
            String coords = String.format("X: %.1f, Y: %.1f, Z: %.1f",
                    target.getPosition().getX(),
                    target.getPosition().getY(),
                    target.getPosition().getZ());
            this.sendUserText(session, "§aFound " + target.getPlayerName() + " at " + coords);
        } else {
            this.sendUserText(session, "§cThat is not a valid player!");
        }
    }
}
