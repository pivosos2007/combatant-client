/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

import java.util.WeakHashMap;

/**
 * Frame-to-frame producer transform cache. The cache is keyed by the actual scene object rather
 * than render-state instances because vanilla may allocate/reuse render states independently of
 * Combatant's temporal lifetime. Same-frame repeated extraction never advances previous state.
 */
final class DeferredObjectMotionTracker {
    private final WeakHashMap<Entity, Entry> entities = new WeakHashMap<>();
    private final WeakHashMap<BlockEntity, Entry> blockEntities = new WeakHashMap<>();
    private long frameId = Long.MIN_VALUE;
    private long epoch = Long.MIN_VALUE;

    void beginFrame(long nextFrameId, long nextEpoch) {
        if (epoch != Long.MIN_VALUE && epoch != nextEpoch) {
            entities.clear();
            blockEntities.clear();
        }
        frameId = nextFrameId;
        epoch = nextEpoch;
    }

    DeferredMotionState captureEntity(Entity entity, EntityRenderState state) {
        Matrix4f current = translation(state.x, state.y, state.z);
        return capture(entities, entity, current, DeferredMotionState.Source.ENTITY,
                DeferredMotionState.TransformCompleteness.TRANSLATION_ONLY,
                DeferredMotionState.DeformationHistory.UNAVAILABLE);
    }

    DeferredMotionState captureBlockEntity(BlockEntity blockEntity, BlockEntityRenderState state) {
        var pos = state.blockPos != null ? state.blockPos : blockEntity.getBlockPos();
        Matrix4f current = translation(pos.getX(), pos.getY(), pos.getZ());
        return capture(blockEntities, blockEntity, current, DeferredMotionState.Source.BLOCK_ENTITY,
                DeferredMotionState.TransformCompleteness.TRANSLATION_ONLY,
                DeferredMotionState.DeformationHistory.UNAVAILABLE);
    }

    void reset() {
        entities.clear();
        blockEntities.clear();
        frameId = Long.MIN_VALUE;
        epoch = Long.MIN_VALUE;
    }

    private <T> DeferredMotionState capture(WeakHashMap<T, Entry> map,
                                            T key,
                                            Matrix4f current,
                                            DeferredMotionState.Source source,
                                            DeferredMotionState.TransformCompleteness completeness,
                                            DeferredMotionState.DeformationHistory deformationHistory) {
        Entry entry = map.computeIfAbsent(key, ignored -> new Entry());
        if (entry.frameId == frameId && entry.frameState != null) {
            return entry.frameState;
        }

        boolean previousAvailable = entry.epoch == epoch
                && entry.frameId != Long.MIN_VALUE
                && entry.frameId == frameId - 1
                && entry.current != null;
        Matrix4f previous = previousAvailable ? new Matrix4f(entry.current) : null;
        DeferredMotionState result = DeferredMotionState.of(
                current,
                previous,
                DeferredMotionState.CoordinateSpace.WORLD,
                completeness,
                deformationHistory,
                source
        );

        entry.current = new Matrix4f(current);
        entry.frameId = frameId;
        entry.epoch = epoch;
        entry.frameState = result;
        return result;
    }

    private static Matrix4f translation(double x, double y, double z) {
        return new Matrix4f().translation((float) x, (float) y, (float) z);
    }

    private static final class Entry {
        Matrix4f current;
        long frameId = Long.MIN_VALUE;
        long epoch = Long.MIN_VALUE;
        DeferredMotionState frameState;
    }
}
