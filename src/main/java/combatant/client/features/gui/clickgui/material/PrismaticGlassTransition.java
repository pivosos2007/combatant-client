/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.material;

import combatant.client.render.engine.animation.AnimationUtility;

/** A short, one-shot material response used while a large glass surface settles. */
public record PrismaticGlassTransition(float strength, float phase) {
    public static final PrismaticGlassTransition CALM = new PrismaticGlassTransition(0f, 0f);

    public static PrismaticGlassTransition fromProgress(float progress) {
        float phaseT = AnimationUtility.clamp((progress - 0.04f) / 0.92f, 0f, 1f);
        float phase = phaseT * phaseT * (3f - 2f * phaseT);
        float strengthT = AnimationUtility.clamp((progress - 0.06f) / 0.90f, 0f, 1f);
        if (strengthT <= 0f || strengthT >= 1f) return new PrismaticGlassTransition(0f, phase);
        float wave = (float) Math.sin(Math.PI * strengthT);
        return new PrismaticGlassTransition(wave * wave, phase);
    }
}
