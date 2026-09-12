/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

/** Compact backend-neutral parameter block for transient effect profiles. */
public record EffectParameters(
        float p0,
        float p1,
        float p2,
        float p3,
        float p4,
        float p5,
        float p6,
        float p7
) {
    public static final EffectParameters ZERO = new EffectParameters(0, 0, 0, 0, 0, 0, 0, 0);

    public float get(int index) {
        return switch (index) {
            case 0 -> p0;
            case 1 -> p1;
            case 2 -> p2;
            case 3 -> p3;
            case 4 -> p4;
            case 5 -> p5;
            case 6 -> p6;
            case 7 -> p7;
            default -> throw new IndexOutOfBoundsException("Effect parameter index: " + index);
        };
    }
}
