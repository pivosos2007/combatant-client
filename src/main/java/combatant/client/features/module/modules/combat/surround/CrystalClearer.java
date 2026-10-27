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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import combatant.client.features.module.modules.combat.autocrystal.AutoCrystalInteractionUtil;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationWithVector;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.raytrace.RotationRaytrace;
import combatant.client.util.block.interaction.FaceVisibility;
import combatant.client.util.combat.AttackUtil;

/** Breaks end crystals that sit where a wall block has to go. */
public final class CrystalClearer {

    private static final int ROTATION_PRIORITY = 35;
    /** Stays inside the interaction range GrimAC's Reach check allows. */
    private static final double REACH_MARGIN = 0.2;

    private int cooldown;

    public void reset() {
        cooldown = 0;
        RotationManager.INSTANCE.clear(this);
    }

    /**
     * Aims at the nearest crystal inside the cells that is within reach and attacks it once the aim is set, then waits
     * {@code delayTicks} before the next hit. One hit per tick, only while no item is in use, and only along a
     * rotation that hits the crystal, as GrimAC's Reach/hitbox and MultiActions checks require. Call once per tick
     * from the start-of-tick phase.
     */
    public void clear(Minecraft mc, LocalPlayer player, Collection<BlockPos> cells, double range, int delayTicks) {
        if (mc.level == null || mc.gameMode == null || cells.isEmpty()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (player.isUsingItem()) return;

        double reach = Math.min(range, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - REACH_MARGIN);
        double reachSq = reach * reach;
        Vec3 eyes = FaceVisibility.serverEyePosition(player);
        EndCrystal nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (BlockPos cell : cells) {
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(cell), EndCrystal::isAlive)) {
                double distSq = AutoCrystalInteractionUtil.distanceSqToBox(eyes, crystal.getBoundingBox());
                if (distSq <= reachSq && distSq < nearestSq) {
                    nearest = crystal;
                    nearestSq = distSq;
                }
            }
        }
        if (nearest == null) return;

        EndCrystal crystal = nearest;
        // GrimAC measures the hit from the last position the server received; aim slightly inside the box so the
        // rounding of the sent rotation cannot turn the hit into a miss.
        RotationWithVector aim = RotationRaytrace.raytraceBox(eyes, crystal.getBoundingBox().deflate(AutoCrystalInteractionUtil.AIM_INSET),
                reach + AutoCrystalInteractionUtil.AIM_INSET, reach + AutoCrystalInteractionUtil.AIM_INSET);
        if (aim == null) return;

        RotationManager.INSTANCE.setRotationTarget(new RotationTarget(
                aim.rotation().normalize(),
                crystal,
                List.of(),
                1,
                2.0f,
                true,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(() -> attack(mc, player, crystal, reach, delayTicks))
        ), ROTATION_PRIORITY, this);
    }

    private void attack(Minecraft mc, LocalPlayer player, EndCrystal crystal, double reach, int delayTicks) {
        if (mc.gameMode == null || player.isUsingItem() || !crystal.isAlive() || crystal.isRemoved()) return;
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation == null) return;
        Vec3 eyes = FaceVisibility.serverEyePosition(player);
        if (!AutoCrystalInteractionUtil.rayHitsBox(eyes, rotation.directionVector(), crystal.getBoundingBox(), reach)) return;

        mc.gameMode.attack(player, crystal);
        AttackUtil.swingMainHand(player);
        cooldown = delayTicks;
    }
}
