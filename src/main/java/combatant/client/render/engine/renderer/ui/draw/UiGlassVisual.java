/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Immutable per-draw shader inputs; safe to retain until deferred UI submission. */
public record UiGlassVisual(Identifier shader, List<Float> parameters, float sampleReachPx) {
    public static final int PARAMETER_COUNT = 16;
    public static final UiGlassVisual DEFAULT = new UiGlassVisual(null, List.of(), 0f);
    public static final UiGlassVisual FLOW = shader("ui_glass_flow.frag", 64f)
            .withVector(0, 1f, 1f, 1f, 1f);
    public static final UiGlassVisual IMPACT = shader("ui_glass_impact.frag", 256f)
            .withVector(0, 1f, 1f, 1f, 1f);
    public static final UiGlassVisual RIPPLE = shader("ui_glass_ripple.frag", 24f)
            .withVector(0, 1f, 28f, 6f, 0.45f);
    public static UiGlassVisual ice(UiIceMaterialSpec material) {
        UiIceMaterialSpec ice = Objects.requireNonNull(material, "material");
        return shader("ui_glass_ice_blend.frag", ice.displacementPx())
                .withVector(0, ice.seed(), ice.density(), ice.frost(), ice.cracks())
                .withVector(1, ice.blueAbsorption(), ice.displacementPx(), ice.facetContrast(), ice.clarity());
    }

    public UiGlassVisual {
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.size() > PARAMETER_COUNT) throw new IllegalArgumentException("At most 16 visual parameters");
        Float[] values = new Float[PARAMETER_COUNT];
        for (int i = 0; i < values.length; i++) {
            float value = i < parameters.size() ? Objects.requireNonNull(parameters.get(i)) : 0f;
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Visual parameters must be finite");
            values[i] = value;
        }
        parameters = List.of(values);
        if (!Float.isFinite(sampleReachPx) || sampleReachPx < 0f || sampleReachPx > 256f) {
            throw new IllegalArgumentException("Sample reach must be in [0, 256] framebuffer pixels");
        }
        if (shader != null && (!"combatant".equals(shader.getNamespace())
                || !shader.getPath().startsWith("shaders/") || !shader.getPath().endsWith(".frag")
                || shader.getPath().contains("__analytic_clip") || shader.getPath().contains("__ui_underlay"))) {
            throw new IllegalArgumentException("Expected a base combatant:shaders/*.frag resource");
        }
        if (shader == null && (sampleReachPx != 0f || parameters.stream().anyMatch(value -> value != 0f))) {
            throw new IllegalArgumentException("Visual parameters require a shader");
        }
    }

    public static UiGlassVisual shader(String path, float sampleReachPx) {
        return new UiGlassVisual(Identifier.fromNamespaceAndPath("combatant", "shaders/" + path),
                List.of(), sampleReachPx);
    }

    public boolean custom() {
        return shader != null;
    }

    public UiGlassVisual withVector(int index, float x, float y, float z, float w) {
        Objects.checkIndex(index, PARAMETER_COUNT / 4);
        Float[] values = parameters.toArray(Float[]::new);
        values[index * 4] = x;
        values[index * 4 + 1] = y;
        values[index * 4 + 2] = z;
        values[index * 4 + 3] = w;
        return new UiGlassVisual(shader, List.of(values), sampleReachPx);
    }
}
