/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Immutable, per-surface weather response: no world access during GPU submission. */
public record UiMaterialClimateSpec(float rain, float waterExit, float frost,
                                    float snow, float condensation) {
    public static final UiMaterialClimateSpec DRY = new UiMaterialClimateSpec(0, 0, 0, 0, 0);

    public UiMaterialClimateSpec {
        rain = bounded(rain);
        waterExit = bounded(waterExit);
        frost = bounded(frost);
        snow = bounded(snow);
        condensation = bounded(condensation);
    }

    public boolean active() {
        return rain + waterExit + frost + snow + condensation > 0.015f;
    }

    private static float bounded(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 0f;
    }
}
