/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiSurfaceMaterialSpec(Mode mode,
                                    float detailScale,
                                    float roughness,
                                    float bevelStrength,
                                    float response,
                                    int accentArgb) {
    public static final UiSurfaceMaterialSpec SATIN_TITANIUM = new UiSurfaceMaterialSpec(
            Mode.SATIN_TITANIUM, 0.18f, 0.42f, 0.55f, 0.0f, 0xFFDCE7F2);
    public static final UiSurfaceMaterialSpec SOFT_TOUCH = new UiSurfaceMaterialSpec(
            Mode.SOFT_TOUCH, 0.34f, 0.86f, 0.24f, 0.0f, 0xFFB8C4D0);
    public static final UiSurfaceMaterialSpec PHOSPHOR_LED = new UiSurfaceMaterialSpec(
            Mode.PHOSPHOR_LED, 4.0f, 0.13f, 0.30f, 0.0f, 0xFF7FA2FF);

    public UiSurfaceMaterialSpec {
        mode = mode != null ? mode : Mode.SOFT_TOUCH;
        detailScale = finiteClamp(detailScale, 0.01f, 64.0f);
        roughness = finiteClamp(roughness, 0.0f, 1.0f);
        bevelStrength = finiteClamp(bevelStrength, 0.0f, 1.0f);
        response = finiteClamp(response, 0.0f, 1.0f);
    }

    public UiSurfaceMaterialSpec withAccent(int argb) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength, response, argb);
    }

    public UiSurfaceMaterialSpec withResponse(float value) {
        return new UiSurfaceMaterialSpec(mode, detailScale, roughness, bevelStrength, value, accentArgb);
    }

    public UiSurfaceMaterialSpec withParameters(float scale, float roughnessValue,
                                                float bevel, float responseValue) {
        return new UiSurfaceMaterialSpec(mode, scale, roughnessValue, bevel, responseValue, accentArgb);
    }

    public enum Mode {
        SATIN_TITANIUM(1),
        SOFT_TOUCH(2),
        PHOSPHOR_LED(3);

        private final int shaderId;

        Mode(int shaderId) {
            this.shaderId = shaderId;
        }

        public int shaderId() {
            return shaderId;
        }
    }

    private static float finiteClamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
