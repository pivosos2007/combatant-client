/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

import combatant.client.render.engine.core.CombatantRenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Production owner of producer-side temporal motion metadata. */
public enum TemporalMotionRuntime {
    ;
    private static final TemporalMotionTracker TRACKER = new TemporalMotionTracker();
    private static Object worldOwner;
    private static long worldEpoch;

    public static @Nullable TemporalMotionState captureEntity(Entity entity, EntityRenderState state) {
        if (entity == null || state == null) return null;
        if (!beginFrame()) return null;
        return TRACKER.captureEntity(entity, state);
    }

    public static @Nullable TemporalMotionState captureBlockEntity(BlockEntity blockEntity, BlockEntityRenderState state) {
        if (blockEntity == null || state == null) return null;
        if (!beginFrame()) return null;
        return TRACKER.captureBlockEntity(blockEntity, state);
    }

    public static void reset() {
        TRACKER.reset();
        worldOwner = null;
        worldEpoch++;
    }

    private static boolean beginFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        Object currentWorld = minecraft != null ? minecraft.level : null;
        if (worldOwner != currentWorld) {
            TRACKER.reset();
            worldOwner = currentWorld;
            worldEpoch++;
        }
        var frame = CombatantRenderSystem.currentContext();
        if (frame == null) return false;
        TRACKER.beginFrame(frame.frameId(), worldEpoch);
        return true;
    }
}
