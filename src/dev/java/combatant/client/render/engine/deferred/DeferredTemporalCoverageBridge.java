/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Frame-local attachment contract for raster producers that participate in final temporal resolve.
 *
 * <p>Water consumes it immediately. Particle, portal and custom forward integrations can request
 * the same producer target without learning which physical texture is current. The bridge owns no
 * resources and never carries views across a frame boundary.</p>
 */
public final class DeferredTemporalCoverageBridge {
    private static volatile Snapshot current = Snapshot.unavailable();

    private DeferredTemporalCoverageBridge() {
    }

    static void publish(long frameId,
                        GpuTextureView velocity,
                        GpuTextureView motionValidity,
                        GpuTextureView coverage,
                        GpuTextureView reactiveMask,
                        int samples) {
        if (velocity == null || motionValidity == null || coverage == null || reactiveMask == null) {
            current = Snapshot.unavailable();
            return;
        }
        current = new Snapshot(frameId, velocity, motionValidity, coverage, reactiveMask,
                Math.max(1, samples), true);
    }

    public static Snapshot current() {
        return current;
    }

    /**
     * Explicit producer-side target lookup. A caller must retain the producer identity alongside
     * its draw contract; final consumers never infer particle/water/portal semantics from pixels.
     */
    public static ProducerTarget target(DeferredTemporalCoverageProducer producer, long frameId) {
        Snapshot snapshot = current;
        DeferredTemporalCoverageProducer identity = producer == null
                ? DeferredTemporalCoverageProducer.EXTENSION : producer;
        if (!snapshot.validForFrame(frameId)) return ProducerTarget.unavailable(identity, frameId);
        return new ProducerTarget(identity, frameId, snapshot.velocity(), snapshot.motionValidity(),
                snapshot.coverage(), snapshot.reactiveMask(), snapshot.samples(), true);
    }

    public static void reset() {
        current = Snapshot.unavailable();
    }

    /** Non-owning frame-local views. Producers must not retain them across frames. */
    public record Snapshot(
            long frameId,
            @Nullable GpuTextureView velocity,
            @Nullable GpuTextureView motionValidity,
            @Nullable GpuTextureView coverage,
            @Nullable GpuTextureView reactiveMask,
            int samples,
            boolean available
    ) {
        public Snapshot {
            samples = Math.max(1, samples);
            available &= velocity != null && motionValidity != null && coverage != null && reactiveMask != null;
        }

        private static Snapshot unavailable() {
            return new Snapshot(Long.MIN_VALUE, null, null, null, null, 1, false);
        }

        public boolean validForFrame(long expectedFrameId) {
            return available && frameId == expectedFrameId;
        }
    }

    /** Producer-tagged, non-owning MRT contract for water/particles/portals/custom translucency. */
    public record ProducerTarget(
            DeferredTemporalCoverageProducer producer,
            long frameId,
            @Nullable GpuTextureView velocity,
            @Nullable GpuTextureView motionValidity,
            @Nullable GpuTextureView coverage,
            @Nullable GpuTextureView reactiveMask,
            int samples,
            boolean available
    ) {
        public ProducerTarget {
            producer = producer == null ? DeferredTemporalCoverageProducer.EXTENSION : producer;
            samples = Math.max(1, samples);
            available &= velocity != null && motionValidity != null && coverage != null && reactiveMask != null;
        }

        private static ProducerTarget unavailable(DeferredTemporalCoverageProducer producer, long frameId) {
            return new ProducerTarget(producer, frameId, null, null, null, null, 1, false);
        }
    }
}
