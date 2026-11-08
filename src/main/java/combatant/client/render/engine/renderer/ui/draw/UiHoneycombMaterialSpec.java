/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

public record UiHoneycombMaterialSpec(float cellRadiusPx,
                                      float lineWidthPx,
                                      float lineOpacity,
                                      float emission,
                                      float glow,
                                      float motionSpeed) implements UiMaterialSpec {
    @Override public Family family() { return Family.HONEYCOMB; }
    @Override public boolean samplesBackdrop() { return false; }

    public static final UiHoneycombMaterialSpec HEX_PATH = new UiHoneycombMaterialSpec(19.0f, 0.90f, 0.17f, 0.85f, 0.62f, 1.0f);
    public static final UiHoneycombMaterialSpec SUBTLE = new UiHoneycombMaterialSpec(23.0f, 0.75f, 0.08f, 0.46f, 0.34f, 0.75f);
    public static final UiHoneycombMaterialSpec EMISSIVE = new UiHoneycombMaterialSpec(18.0f, 1.15f, 0.05f, 1.0f, 0.90f, 1.15f);

    public UiHoneycombMaterialSpec {
        cellRadiusPx = clamp(cellRadiusPx, 2.0f, 1024.0f);
        lineWidthPx = clamp(lineWidthPx, 0.05f, 12.0f);
        lineOpacity = clamp(lineOpacity, 0.0f, 1.0f);
        emission = clamp(emission, 0.0f, 2.0f);
        glow = clamp(glow, 0.0f, 2.0f);
        motionSpeed = clamp(motionSpeed, 0.0f, 6.0f);
    }

    public UiHoneycombMaterialSpec withEmission(float value) {
        return new UiHoneycombMaterialSpec(cellRadiusPx, lineWidthPx, lineOpacity, value, glow, motionSpeed);
    }

    public UiHoneycombMaterialSpec withMotionSpeed(float value) {
        return new UiHoneycombMaterialSpec(cellRadiusPx, lineWidthPx, lineOpacity, emission, glow, value);
    }

    public UiHoneycombMaterialSpec withCellSize(float radius, float stroke) {
        return new UiHoneycombMaterialSpec(radius, stroke, lineOpacity, emission, glow, motionSpeed);
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
