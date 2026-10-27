/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.compat.xaero.XaeroWaypointStore;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.util.screen.ClientScreen;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;

/**
 * Reports where you died (XYZ and dimension) in chat once per death and optionally drops a Xaero waypoint
 * there through the existing waypoint store.
 */
@ModuleInfo(
        id = "deathcoordinates",
        displayName = "DeathCoordinates",
        aliases = {"DeathPos", "DeathWaypoint"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.deathcoordinates.description")
public final class DeathCoordinates extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue chat = bool("deathCoordinatesChat", "chat", true);
    private final BooleanValue waypoint = bool("deathCoordinatesWaypoint", "waypoint", true);
    private final StringValue waypointName = visibleWhen(
            text("deathCoordinatesWaypointName", "waypoint_name", "Death"), waypoint::get);

    private boolean reported;

    @Override
    public void onEnable() {
        reported = false;
    }

    @Override
    public void onDisable() {
        reported = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reported = false;
            return;
        }
        boolean dead = player.isDeadOrDying() || ClientScreen.current() instanceof DeathScreen;
        if (!dead) {
            reported = false;
            return;
        }
        if (reported) return;
        reported = true;

        BlockPos pos = player.blockPosition();
        String dimension = mc.level.dimension().identifier().toString();
        if (chat.get()) {
            CommandOutput.warning(I18n.get("notification.deathcoordinates.died",
                    pos.getX(), pos.getY(), pos.getZ(), dimension));
        }
        if (waypoint.get()) {
            XaeroWaypointStore.AddResult result = XaeroWaypointStore.addFromCurrentDimension(
                    pos.getX(), pos.getY(), pos.getZ(), waypointName.get(), false);
            if (chat.get()) {
                if (result.success()) CommandOutput.success(result.message());
                else CommandOutput.error(result.message());
            }
        }
    }
}
