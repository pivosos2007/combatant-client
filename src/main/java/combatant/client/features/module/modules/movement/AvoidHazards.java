/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 *
 * Hazard categories and the collision-blocking approach adapted from LiquidBounce
 * AvoidHazards, copyright (c) 2015-2025 CCBlueX (GPL-3.0-or-later).
 * https://github.com/CCBlueX/LiquidBounce
 */
package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlockCollisionShapeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.navigation.HazardAvoidance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional world-collision guard; TargetStrafe also uses HazardAvoidance for route planning. */
@ModuleInfo(id = "avoidhazards", displayName = "AvoidHazards",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.BASIC,
        description = "module.avoidhazards.description")
public final class AvoidHazards extends Module {
    private static final VoxelShape UNSAFE_CAP = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.25, 1.0);
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanMapValue avoid = group("avoidHazardsBlocks", "avoid", defaults());

    private static Map<String, Boolean> defaults() {
        Map<String, Boolean> entries = new LinkedHashMap<>();
        for (HazardAvoidance.Kind kind : HazardAvoidance.Kind.values()) {
            entries.put(kind.name().toLowerCase(), true);
        }
        return entries;
    }

    @EventHandler
    private void onCollisionShape(BlockCollisionShapeEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null || event.getWorld() != mc.level) return;
        if (!(event.getContext() instanceof EntityCollisionContext entityContext)
                || entityContext.getEntity() != mc.player) return;
        BlockPos pos = event.getPos();
        HazardAvoidance.Kind kind = HazardAvoidance.classify(mc.level, pos, event.getState());
        // LiquidBounce adds a low collision cap in the *air block above* magma.
        // Keep magma's existing full-block collision at its own position.
        if (kind == HazardAvoidance.Kind.MAGMA) return;
        if (kind == null && mc.level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
            kind = HazardAvoidance.Kind.MAGMA;
        }
        if (kind == null || !avoid.get(kind.name().toLowerCase())) return;
        // While already trapped inside a web/fire, disable that hazard's
        // virtual collisions entirely, including neighbouring blocks. Otherwise
        // two adjacent cobwebs make it impossible to leave the first one.
        if (HazardAvoidance.contains(mc.level, mc.player.getBoundingBox(), kind)) return;
        // Do not imprison a player already standing in a dangerous block.
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        if (px >= pos.getX() && px < pos.getX() + 1
                && py >= pos.getY() && py < pos.getY() + 1
                && pz >= pos.getZ() && pz < pos.getZ() + 1) return;
        event.setShape(kind == HazardAvoidance.Kind.MAGMA
                || kind == HazardAvoidance.Kind.PRESSURE_PLATE ? UNSAFE_CAP : Shapes.block());
    }
}
