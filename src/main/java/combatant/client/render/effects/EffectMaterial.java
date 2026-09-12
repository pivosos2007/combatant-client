/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

import net.minecraft.util.Mth;

/** Semantic material data only; no pipeline/resource handles are exposed here. */
public record EffectMaterial(
        int primaryColor,
        int secondaryColor,
        float opacity,
        float emission,
        DepthPolicy depthPolicy,
        BlendPolicy blendPolicy
) {
    public EffectMaterial {
        opacity = Mth.clamp(opacity, 0.0f, 1.0f);
        emission = Math.max(0.0f, emission);
        depthPolicy = depthPolicy == null ? DepthPolicy.MAIN : depthPolicy;
        blendPolicy = blendPolicy == null ? BlendPolicy.ALPHA : blendPolicy;
    }

    public enum DepthPolicy {
        MAIN,
        PRE_DEPTH,
        NONE
    }

    public enum BlendPolicy {
        ALPHA,
        ADDITIVE,
        PREMULTIPLIED
    }
}
