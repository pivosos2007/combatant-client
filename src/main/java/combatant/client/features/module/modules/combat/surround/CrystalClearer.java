/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.surround;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Breaks end crystals that sit where a wall block has to go. */
public final class CrystalClearer {

    private int cooldown;

    public void reset() {
        cooldown = 0;
    }

    /**
     * Attacks every crystal inside the cells that is within {@code range} of the player, then waits
     * {@code delayTicks} before the next volley. Call once per tick.
     */
    public void clear(Minecraft mc, LocalPlayer player, Collection<BlockPos> cells, double range, int delayTicks) {
        if (mc.level == null || mc.gameMode == null || cells.isEmpty()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double maxRangeSqr = range * range;
        // A crystal is two blocks wide, so it can overlap several cells; hit it once.
        Set<EndCrystal> blocking = new LinkedHashSet<>();
        for (BlockPos cell : cells) {
            blocking.addAll(mc.level.getEntitiesOfClass(
                    EndCrystal.class,
                    new AABB(cell),
                    crystal -> crystal.isAlive() && player.distanceToSqr(crystal) <= maxRangeSqr));
        }
        if (blocking.isEmpty()) return;

        for (EndCrystal crystal : blocking) {
            mc.gameMode.attack(player, crystal);
            player.swing(InteractionHand.MAIN_HAND);
        }
        cooldown = delayTicks;
    }
}
