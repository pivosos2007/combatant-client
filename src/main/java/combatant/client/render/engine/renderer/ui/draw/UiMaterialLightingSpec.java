/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Optional scene-derived ambient lighting of an opaque UI material.
 * No GPU resources or UI ownership leak into this immutable contract.
 */
public record UiMaterialLightingSpec(Source source, float strength, float samplingRadiusPx) {
    public enum Source { NONE, CAPTURED_SCENE, CURRENT_TARGET }
    public static final UiMaterialLightingSpec NONE = new UiMaterialLightingSpec(Source.NONE, 0f, 0f);
    public static final UiMaterialLightingSpec CURRENT = new UiMaterialLightingSpec(Source.CURRENT_TARGET, 0.65f, 12f);
    public static final UiMaterialLightingSpec CAPTURED = new UiMaterialLightingSpec(Source.CAPTURED_SCENE, 0.65f, 12f);

    public UiMaterialLightingSpec {
        source = source == null ? Source.NONE : source;
        strength = Float.isFinite(strength) ? Math.clamp(strength, 0f, 1f) : 0f;
        samplingRadiusPx = Float.isFinite(samplingRadiusPx)
                ? Math.clamp(samplingRadiusPx, 0f, 32f) : 0f;
        if (strength <= 0f) source = Source.NONE;
    }
    public boolean enabled() { return source != Source.NONE; }
    public UiMaterialLightingSpec withStrength(float v) {
        return new UiMaterialLightingSpec(source, v, samplingRadiusPx);
    }
    public UiMaterialLightingSpec withSamplingRadius(float v) {
        return new UiMaterialLightingSpec(source, strength, v);
    }
}
