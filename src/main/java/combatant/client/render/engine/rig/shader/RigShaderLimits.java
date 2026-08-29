/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.shader;

/** Fixed UBO capacities mirrored by the rig GLSL sources. */
public final class RigShaderLimits {
    public static final int MAX_BONES = 64;
    public static final int MAX_DEFORMS = 16;
    public static final int MAX_RIBBON_SAMPLES = 16;
    public static final int MAX_RIBBON_FRAMES = MAX_DEFORMS * MAX_RIBBON_SAMPLES;

    private RigShaderLimits() {
    }
}
