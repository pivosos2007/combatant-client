/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;

@ModuleInfo(
        id = "autorespawn",
        displayName = "AutoRespawn",
        aliases = {"InstantRespawn", "FastRespawn"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autorespawn.description")
public final class AutoRespawn extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delayTicks =
            num("autoRespawnDelayTicks", "delay_ticks", 5, 0, 100);
    private final BooleanValue chatCoordinates =
            bool("autoRespawnChatCoordinates", "chat_coordinates", true);

    private int deathTicks;
    private boolean reported;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) {
            resetState();
            return;
        }

        boolean dead = player.isDeadOrDying() || ClientScreen.current() instanceof DeathScreen;
        if (!dead) {
            resetState();
            return;
        }

        DeathCoordinates deathCoordinates = Modules.get(DeathCoordinates.class);
        boolean handledElsewhere = deathCoordinates != null && deathCoordinates.isEnabled();
        if (!reported && chatCoordinates.get() && !handledElsewhere) {
            BlockPos pos = player.blockPosition();
            CommandOutput.warning(I18n.get(
                    "notification.autorespawn.death_coordinates",
                    pos.getX(), pos.getY(), pos.getZ()
            ));
            reported = true;
        }

        if (deathTicks++ < delayTicks.get()) return;

        player.respawn();
        ClientScreen.show(null);
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        deathTicks = 0;
        reported = false;
    }
}
