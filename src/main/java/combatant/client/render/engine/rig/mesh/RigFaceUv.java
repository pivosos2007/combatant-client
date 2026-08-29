/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Four UV corners matching a {@link RigFace}'s A/B/C/D winding.
 * Keeping all four corners lets vanilla conversion preserve mirrored/rotated face UVs exactly.
 */
public record RigFaceUv(float uA, float vA,
                        float uB, float vB,
                        float uC, float vC,
                        float uD, float vD) {

    public static RigFaceUv rectangle(float minU, float minV, float maxU, float maxV) {
        return new RigFaceUv(
                minU, minV,
                maxU, minV,
                maxU, maxV,
                minU, maxV
        );
    }

    float u(float s, float t) {
        float top = lerp(uA, uB, s);
        float bottom = lerp(uD, uC, s);
        return lerp(top, bottom, t);
    }

    float v(float s, float t) {
        float top = lerp(vA, vB, s);
        float bottom = lerp(vD, vC, s);
        return lerp(top, bottom, t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
