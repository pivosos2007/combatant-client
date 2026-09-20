/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Object/deformation motion producer contract. Transform availability and transform completeness
 * are deliberately separate: a producer may know exact frame-to-frame object translation while
 * still lacking previous skeletal/vertex deformation. Consumers must not reinterpret the latter
 * as stationary geometry.
 */
public record DeferredMotionState(
        @Nullable Matrix4f currentTransform,
        @Nullable Matrix4f previousTransform,
        boolean previousTransformAvailable,
        CoordinateSpace coordinateSpace,
        TransformCompleteness transformCompleteness,
        DeformationHistory deformationHistory,
        Source source
) {
    public enum Source {
        STATIC_TERRAIN,
        ENTITY,
        BLOCK_ENTITY,
        WATER,
        CLOUD,
        VERTEX_ANIMATION,
        EXTENSION
    }

    public enum CoordinateSpace {
        WORLD,
        CAMERA_RELATIVE,
        OBJECT_LOCAL
    }

    public enum TransformCompleteness {
        TRANSLATION_ONLY,
        RIGID,
        FULL
    }

    public enum DeformationHistory {
        NOT_REQUIRED,
        AVAILABLE,
        UNAVAILABLE
    }

    public DeferredMotionState {
        currentTransform = currentTransform == null ? null : new Matrix4f(currentTransform);
        previousTransform = previousTransform == null ? null : new Matrix4f(previousTransform);
        previousTransformAvailable &= previousTransform != null;
        coordinateSpace = coordinateSpace == null ? CoordinateSpace.WORLD : coordinateSpace;
        transformCompleteness = transformCompleteness == null ? TransformCompleteness.FULL : transformCompleteness;
        deformationHistory = deformationHistory == null ? DeformationHistory.NOT_REQUIRED : deformationHistory;
        source = source == null ? Source.EXTENSION : source;
    }

    /** Compatibility constructor for the original Task-D contract. */
    public DeferredMotionState(@Nullable Matrix4f currentTransform,
                               @Nullable Matrix4f previousTransform,
                               boolean previousTransformAvailable,
                               DeformationHistory deformationHistory,
                               Source source) {
        this(currentTransform, previousTransform, previousTransformAvailable,
                CoordinateSpace.WORLD, TransformCompleteness.FULL, deformationHistory, source);
    }

    public static DeferredMotionState rigid(Matrix4fc current, @Nullable Matrix4fc previous, Source source) {
        return of(current, previous, CoordinateSpace.WORLD, TransformCompleteness.RIGID,
                DeformationHistory.NOT_REQUIRED, source);
    }

    public static DeferredMotionState of(Matrix4fc current, @Nullable Matrix4fc previous,
                                         DeformationHistory deformationHistory, Source source) {
        return of(current, previous, CoordinateSpace.WORLD, TransformCompleteness.FULL,
                deformationHistory, source);
    }

    public static DeferredMotionState of(Matrix4fc current,
                                         @Nullable Matrix4fc previous,
                                         CoordinateSpace coordinateSpace,
                                         TransformCompleteness transformCompleteness,
                                         DeformationHistory deformationHistory,
                                         Source source) {
        return new DeferredMotionState(
                current == null ? null : new Matrix4f(current),
                previous == null ? null : new Matrix4f(previous),
                previous != null,
                coordinateSpace,
                transformCompleteness,
                deformationHistory,
                source
        );
    }

    /** Exact producer-side object transform exists for both temporal endpoints. */
    public boolean objectMotionAvailable() {
        return currentTransform != null && previousTransformAvailable && previousTransform != null;
    }

    /** Previous deformation is either not required or explicitly supplied by the producer. */
    public boolean deformationMotionAvailable() {
        return deformationHistory != DeformationHistory.UNAVAILABLE;
    }

    /** Full per-vertex motion may only be trusted when both object and deformation history exist. */
    public boolean motionAvailable() {
        return objectMotionAvailable() && deformationMotionAvailable();
    }
}
