/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Producer-side object/deformation motion metadata shared by TAA and motion-aware post effects. */
public record TemporalMotionState(
        @Nullable Matrix4f currentTransform,
        @Nullable Matrix4f previousTransform,
        boolean previousTransformAvailable,
        CoordinateSpace coordinateSpace,
        TransformCompleteness transformCompleteness,
        DeformationHistory deformationHistory,
        Source source
) {
    public enum Source { STATIC_TERRAIN, ENTITY, BLOCK_ENTITY, WATER, CLOUD, VERTEX_ANIMATION, EXTENSION }
    public enum CoordinateSpace { WORLD, CAMERA_RELATIVE, OBJECT_LOCAL }
    public enum TransformCompleteness { TRANSLATION_ONLY, RIGID, FULL }
    public enum DeformationHistory { NOT_REQUIRED, AVAILABLE, UNAVAILABLE }

    public TemporalMotionState {
        currentTransform = currentTransform == null ? null : new Matrix4f(currentTransform);
        previousTransform = previousTransform == null ? null : new Matrix4f(previousTransform);
        previousTransformAvailable &= previousTransform != null;
        coordinateSpace = coordinateSpace == null ? CoordinateSpace.WORLD : coordinateSpace;
        transformCompleteness = transformCompleteness == null ? TransformCompleteness.FULL : transformCompleteness;
        deformationHistory = deformationHistory == null ? DeformationHistory.NOT_REQUIRED : deformationHistory;
        source = source == null ? Source.EXTENSION : source;
    }

    public static TemporalMotionState of(Matrix4fc current,
                                         @Nullable Matrix4fc previous,
                                         CoordinateSpace coordinateSpace,
                                         TransformCompleteness transformCompleteness,
                                         DeformationHistory deformationHistory,
                                         Source source) {
        return new TemporalMotionState(
                current == null ? null : new Matrix4f(current),
                previous == null ? null : new Matrix4f(previous),
                previous != null,
                coordinateSpace,
                transformCompleteness,
                deformationHistory,
                source
        );
    }

    public boolean objectMotionAvailable() {
        return currentTransform != null && previousTransformAvailable && previousTransform != null;
    }

    public boolean deformationMotionAvailable() {
        return deformationHistory != DeformationHistory.UNAVAILABLE;
    }

    public boolean motionAvailable() {
        return objectMotionAvailable() && deformationMotionAvailable();
    }
}
