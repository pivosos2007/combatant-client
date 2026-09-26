/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

import java.util.WeakHashMap;

/** Frame-to-frame transform cache keyed by the actual scene object. */
final class TemporalMotionTracker {
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

    TemporalMotionState captureEntity(Entity entity, EntityRenderState state) {
        Matrix4f current = translation(state.x, state.y, state.z);
        return capture(entities, entity, current, TemporalMotionState.Source.ENTITY,
                TemporalMotionState.TransformCompleteness.TRANSLATION_ONLY,
                TemporalMotionState.DeformationHistory.UNAVAILABLE);
    }

    TemporalMotionState captureBlockEntity(BlockEntity blockEntity, BlockEntityRenderState state) {
        var pos = state.blockPos != null ? state.blockPos : blockEntity.getBlockPos();
        Matrix4f current = translation(pos.getX(), pos.getY(), pos.getZ());
        return capture(blockEntities, blockEntity, current, TemporalMotionState.Source.BLOCK_ENTITY,
                TemporalMotionState.TransformCompleteness.TRANSLATION_ONLY,
                TemporalMotionState.DeformationHistory.UNAVAILABLE);
    }

    void reset() {
        entities.clear();
        blockEntities.clear();
        frameId = Long.MIN_VALUE;
        epoch = Long.MIN_VALUE;
    }

    private <T> TemporalMotionState capture(WeakHashMap<T, Entry> map,
                                            T key,
                                            Matrix4f current,
                                            TemporalMotionState.Source source,
                                            TemporalMotionState.TransformCompleteness completeness,
                                            TemporalMotionState.DeformationHistory deformationHistory) {
        Entry entry = map.computeIfAbsent(key, ignored -> new Entry());
        if (entry.frameId == frameId && entry.frameState != null) return entry.frameState;

        boolean previousAvailable = entry.epoch == epoch
                && entry.frameId != Long.MIN_VALUE
                && entry.frameId == frameId - 1
                && entry.current != null;
        Matrix4f previous = previousAvailable ? new Matrix4f(entry.current) : null;
        TemporalMotionState result = TemporalMotionState.of(
                current, previous, TemporalMotionState.CoordinateSpace.WORLD, completeness, deformationHistory, source);

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
        TemporalMotionState frameState;
    }
}
