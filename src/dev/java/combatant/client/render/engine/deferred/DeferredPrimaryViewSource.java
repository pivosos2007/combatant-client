/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;

/**
 * Authoritative primary-world camera temporal state captured before terrain submission.
 * Jittered/unjittered matrices, resolution and frame identity live here so every temporal consumer
 * uses one convention instead of reconstructing its own previous-camera state.
 */
public final class DeferredPrimaryViewSource {
    private static final double CAMERA_CUT_DISTANCE_SQUARED = 64.0 * 64.0;

    private @Nullable FrameView current;
    private @Nullable FrameView previous;
    private Object worldOwner;
    private long historyEpoch;
    private DeferredHistoryResetReason pendingReset = DeferredHistoryResetReason.FIRST_FRAME;

    public void reset() {
        current = null;
        previous = null;
        worldOwner = null;
        queueHistoryReset(DeferredHistoryResetReason.RENDERER_RESET);
    }

    public void beginWorld(Object world) {
        if (worldOwner == world) return;
        boolean hadWorld = worldOwner != null;
        current = null;
        previous = null;
        worldOwner = world;
        queueHistoryReset(hadWorld
                ? DeferredHistoryResetReason.DIMENSION_CHANGE
                : DeferredHistoryResetReason.FIRST_FRAME);
    }

    /** Compatibility capture for the current non-jittered renderer path. */
    public void capture(long frameId,
                        Matrix4fc view,
                        Matrix4fc projection,
                        @Nullable Vec3 cameraPosition,
                        float farPlane) {
        capture(frameId, view, projection, projection, new Vector2f(), cameraPosition, farPlane);
    }

    /** Exact temporal-camera capture used once a TAA jitter producer is present. */
    public void capture(long frameId,
                        Matrix4fc view,
                        Matrix4fc jitteredProjection,
                        Matrix4fc unjitteredProjection,
                        Vector2fc jitter,
                        @Nullable Vec3 cameraPosition,
                        float farPlane) {
        if (view == null || jitteredProjection == null || unjitteredProjection == null || cameraPosition == null) return;
        Vector2f resolvedJitter = jitter == null ? new Vector2f() : new Vector2f(jitter);
        double nowSeconds = System.nanoTime() * 1.0e-9;

        if (current != null && current.frameId == frameId) {
            current = current.withCamera(view, jitteredProjection, unjitteredProjection, resolvedJitter,
                    cameraPosition, farPlane);
            return;
        }

        FrameView prior = current;
        DeferredHistoryResetReason resetReason = pendingReset;
        pendingReset = DeferredHistoryResetReason.NONE;
        if (resetReason == DeferredHistoryResetReason.NONE) {
            if (prior == null) {
                resetReason = DeferredHistoryResetReason.FIRST_FRAME;
            } else if (prior.frameId + 1L != frameId) {
                resetReason = DeferredHistoryResetReason.FRAME_GAP;
            } else if (cameraPosition.distanceToSqr(prior.cameraPosition) > CAMERA_CUT_DISTANCE_SQUARED) {
                resetReason = DeferredHistoryResetReason.CAMERA_CUT;
            }
        }
        if (resetReason != DeferredHistoryResetReason.NONE) historyEpoch++;

        int historyAge = resetReason == DeferredHistoryResetReason.NONE && prior != null
                && prior.historyEpoch == historyEpoch ? prior.historyAge + 1 : 0;
        previous = prior;
        current = new FrameView(
                frameId,
                nowSeconds,
                new Matrix4f(view),
                new Matrix4f(jitteredProjection),
                new Matrix4f(unjitteredProjection),
                resolvedJitter,
                cameraPosition,
                Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f,
                Float.NaN,
                0, 0, 0, 0,
                historyEpoch,
                historyAge,
                resetReason
        );
    }

    public void updateSunAngle(long frameId, float sunAngle) {
        if (current == null || current.frameId != frameId || !Float.isFinite(sunAngle)) return;
        current = current.withSunAngle(sunAngle);
    }

    public void updateResolutions(long frameId, int renderWidth, int renderHeight,
                                  int outputWidth, int outputHeight) {
        if (current == null || current.frameId != frameId) return;
        current = current.withResolutions(renderWidth, renderHeight, outputWidth, outputHeight);
    }

    /** Queues an exact invalidation for the next primary-view capture. */
    public void queueHistoryReset(DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        pendingReset = DeferredHistoryResetReason.merge(pendingReset, reason);
    }

    /** Invalidates the named frame if it has already been captured, otherwise queues the reset. */
    public void invalidateHistory(long frameId, DeferredHistoryResetReason reason) {
        if (reason == null || reason == DeferredHistoryResetReason.NONE) return;
        if (current == null || current.frameId != frameId) {
            queueHistoryReset(reason);
            return;
        }
        DeferredHistoryResetReason merged = DeferredHistoryResetReason.merge(current.historyResetReason, reason);
        if (merged == current.historyResetReason && current.historyResetReason != DeferredHistoryResetReason.NONE) return;
        if (current.historyResetReason == DeferredHistoryResetReason.NONE) historyEpoch++;
        current = current.withHistory(historyEpoch, 0, merged);
    }

    public @Nullable FrameView current() {
        return current;
    }

    public @Nullable FrameView previous() {
        return previous;
    }

    public DeferredHistoryDescriptor historyDescriptor() {
        if (current == null) {
            DeferredHistoryResetReason reason = pendingReset == DeferredHistoryResetReason.NONE
                    ? DeferredHistoryResetReason.FIRST_FRAME : pendingReset;
            return new DeferredHistoryDescriptor(historyEpoch, Long.MIN_VALUE, Long.MIN_VALUE, 0, false, reason);
        }
        boolean valid = current.historyResetReason == DeferredHistoryResetReason.NONE
                && previous != null
                && previous.frameId + 1L == current.frameId
                && previous.historyEpoch == current.historyEpoch;
        return new DeferredHistoryDescriptor(
                current.historyEpoch,
                current.frameId,
                previous == null ? Long.MIN_VALUE : previous.frameId,
                valid ? current.historyAge : 0,
                valid,
                valid ? DeferredHistoryResetReason.NONE : current.historyResetReason
        );
    }

    public boolean hasTemporalHistory() {
        return historyDescriptor().valid();
    }

    public record FrameView(long frameId,
                            double frameTimeSeconds,
                            Matrix4f view,
                            Matrix4f jitteredProjection,
                            Matrix4f unjitteredProjection,
                            Vector2f jitter,
                            Vec3 cameraPosition,
                            float farPlane,
                            float sunAngle,
                            int renderWidth,
                            int renderHeight,
                            int outputWidth,
                            int outputHeight,
                            long historyEpoch,
                            int historyAge,
                            DeferredHistoryResetReason historyResetReason) {
        public FrameView {
            view = new Matrix4f(view);
            jitteredProjection = new Matrix4f(jitteredProjection);
            unjitteredProjection = new Matrix4f(unjitteredProjection);
            jitter = jitter == null ? new Vector2f() : new Vector2f(jitter);
            cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
            farPlane = Float.isFinite(farPlane) && farPlane > 0.0f ? farPlane : 0.0f;
            renderWidth = Math.max(0, renderWidth);
            renderHeight = Math.max(0, renderHeight);
            outputWidth = Math.max(0, outputWidth);
            outputHeight = Math.max(0, outputHeight);
            historyAge = Math.max(0, historyAge);
            if (historyResetReason == null) historyResetReason = DeferredHistoryResetReason.RENDERER_RESET;
        }

        @Override public Matrix4f view() { return new Matrix4f(view); }
        @Override public Matrix4f jitteredProjection() { return new Matrix4f(jitteredProjection); }
        @Override public Matrix4f unjitteredProjection() { return new Matrix4f(unjitteredProjection); }
        @Override public Vector2f jitter() { return new Vector2f(jitter); }

        /** Compatibility alias: world rendering currently consumes the jittered projection. */
        public Matrix4f projection() { return jitteredProjection(); }
        public Matrix4f inverseView() { return view().invert(); }
        public Matrix4f inverseProjection() { return jitteredProjection().invert(); }
        public Matrix4f inverseUnjitteredProjection() { return unjitteredProjection().invert(); }
        public Matrix4f jitteredViewProjection() { return jitteredProjection().mul(view()); }
        public Matrix4f unjitteredViewProjection() { return unjitteredProjection().mul(view()); }
        public Matrix4f inverseJitteredViewProjection() { return jitteredViewProjection().invert(); }
        public Matrix4f inverseUnjitteredViewProjection() { return unjitteredViewProjection().invert(); }
        public boolean hasSunAngle() { return Float.isFinite(sunAngle); }
        public boolean hasRenderResolution() { return renderWidth > 0 && renderHeight > 0; }
        public boolean hasOutputResolution() { return outputWidth > 0 && outputHeight > 0; }

        private FrameView withCamera(Matrix4fc nextView, Matrix4fc nextJitteredProjection,
                                     Matrix4fc nextUnjitteredProjection, Vector2fc nextJitter,
                                     Vec3 nextCameraPosition, float nextFarPlane) {
            return new FrameView(frameId, frameTimeSeconds, new Matrix4f(nextView),
                    new Matrix4f(nextJitteredProjection), new Matrix4f(nextUnjitteredProjection),
                    new Vector2f(nextJitter), nextCameraPosition,
                    Float.isFinite(nextFarPlane) && nextFarPlane > 0.0f ? nextFarPlane : 0.0f,
                    sunAngle, renderWidth, renderHeight, outputWidth, outputHeight,
                    historyEpoch, historyAge, historyResetReason);
        }

        private FrameView withSunAngle(float nextSunAngle) {
            return new FrameView(frameId, frameTimeSeconds, view, jitteredProjection, unjitteredProjection, jitter,
                    cameraPosition, farPlane, nextSunAngle, renderWidth, renderHeight, outputWidth, outputHeight,
                    historyEpoch, historyAge, historyResetReason);
        }

        private FrameView withResolutions(int nextRenderWidth, int nextRenderHeight,
                                          int nextOutputWidth, int nextOutputHeight) {
            return new FrameView(frameId, frameTimeSeconds, view, jitteredProjection, unjitteredProjection, jitter,
                    cameraPosition, farPlane, sunAngle, nextRenderWidth, nextRenderHeight,
                    nextOutputWidth, nextOutputHeight, historyEpoch, historyAge, historyResetReason);
        }

        private FrameView withHistory(long nextEpoch, int nextAge, DeferredHistoryResetReason reason) {
            return new FrameView(frameId, frameTimeSeconds, view, jitteredProjection, unjitteredProjection, jitter,
                    cameraPosition, farPlane, sunAngle, renderWidth, renderHeight, outputWidth, outputHeight,
                    nextEpoch, nextAge, reason);
        }
    }
}
