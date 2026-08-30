/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

/**
 * Provider-facing interaction API. Camera navigation and local subject manipulation are separate
 * on purpose: an orbit inspection, a rotating model and a free-flight scene need different state.
 */
public record VisualPreviewInteractionProfile(
        VisualPreviewCameraMode cameraMode,
        VisualPreviewSubjectMode subjectMode,
        VisualPreviewWheelMode wheelMode,
        boolean primaryCameraDrag,
        boolean secondaryCameraDrag,
        boolean middleSubjectDrag,
        float cameraSensitivity,
        float subjectSensitivity
) {
    public static final VisualPreviewInteractionProfile FIXED = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.FIXED, VisualPreviewSubjectMode.FIXED, VisualPreviewWheelMode.NONE,
            false, false, false, 0.0f, 0.0f
    );
    public static final VisualPreviewInteractionProfile OBJECT = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.FIXED, VisualPreviewSubjectMode.ROTATE, VisualPreviewWheelMode.SUBJECT_SCALE,
            false, false, false, 0.0f, 0.55f
    );
    public static final VisualPreviewInteractionProfile ORBIT = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.ORBIT, VisualPreviewSubjectMode.FIXED, VisualPreviewWheelMode.CAMERA_DOLLY,
            true, true, false, 0.42f, 0.0f
    );
    public static final VisualPreviewInteractionProfile HAND_INSPECTION = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.ORBIT, VisualPreviewSubjectMode.ROTATE, VisualPreviewWheelMode.CAMERA_DOLLY,
            true, true, true, 0.42f, 0.42f
    );
    public static final VisualPreviewInteractionProfile OBJECT_INSPECTION = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.ORBIT, VisualPreviewSubjectMode.ROTATE, VisualPreviewWheelMode.CAMERA_DOLLY,
            true, true, true, 0.42f, 0.42f
    );
    public static final VisualPreviewInteractionProfile FREE_FLIGHT = new VisualPreviewInteractionProfile(
            VisualPreviewCameraMode.FREE_FLY, VisualPreviewSubjectMode.FIXED, VisualPreviewWheelMode.FLY_SPEED,
            true, true, false, 0.34f, 0.0f
    );

    public VisualPreviewInteractionProfile {
        cameraMode = cameraMode == null ? VisualPreviewCameraMode.FIXED : cameraMode;
        subjectMode = subjectMode == null ? VisualPreviewSubjectMode.FIXED : subjectMode;
        wheelMode = wheelMode == null ? VisualPreviewWheelMode.NONE : wheelMode;
        cameraSensitivity = finiteNonNegative(cameraSensitivity);
        subjectSensitivity = finiteNonNegative(subjectSensitivity);
    }

    public static VisualPreviewInteractionProfile fromLegacy(VisualPreviewControlMode mode) {
        if (mode == null) return FIXED;
        return switch (mode) {
            case OBJECT_ROTATE -> OBJECT;
            case CAMERA_ORBIT -> ORBIT;
            case CAMERA_PAN -> new VisualPreviewInteractionProfile(
                    VisualPreviewCameraMode.PAN, VisualPreviewSubjectMode.FIXED, VisualPreviewWheelMode.CAMERA_DOLLY,
                    true, true, false, 1.0f, 0.0f
            );
            case HAND_VIEW -> HAND_INSPECTION;
            case FIXED -> FIXED;
        };
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
