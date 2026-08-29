/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform.curve;

import org.joml.Vector3f;

/**
 * Allocation-free parametric centerline consumed by the ribbon sampler.
 * Implementations write into caller-owned vectors so animated curves can be sampled in hot paths.
 */
public interface RigRibbonCurve {
    Vector3f sample(float t, Vector3f destination);

    Vector3f tangent(float t, Vector3f destination);
}
