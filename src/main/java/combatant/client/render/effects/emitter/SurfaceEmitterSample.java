/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.emitter;

import net.minecraft.world.phys.Vec3;

public record SurfaceEmitterSample(
        Vec3 position,
        Vec3 normal,
        int partIndex,
        int triangleIndex
) {
    public SurfaceEmitterSample {
        position = position == null ? Vec3.ZERO : position;
        normal = normal == null ? Vec3.ZERO : normal;
    }
}
