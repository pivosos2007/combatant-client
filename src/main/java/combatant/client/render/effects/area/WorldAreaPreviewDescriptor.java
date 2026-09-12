/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.area;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Backend-neutral geometry/presentation contract for world-space range and impact previews. */
public record WorldAreaPreviewDescriptor(
        Shape shape,
        Vec3 center,
        float radius,
        float innerRadius,
        float height,
        Vec3 halfExtents,
        float startAngleRadians,
        float endAngleRadians,
        int segments,
        List<Vec3> polygonPoints,
        int fillColor,
        int strokeColor,
        float fillOpacity,
        float strokeOpacity,
        boolean depthTest
) {
    public WorldAreaPreviewDescriptor {
        shape = shape == null ? Shape.CIRCLE : shape;
        center = center == null ? Vec3.ZERO : center;
        radius = Math.max(0.0f, radius);
        innerRadius = Mth.clamp(innerRadius, 0.0f, radius);
        height = Math.max(0.0f, height);
        halfExtents = halfExtents == null ? Vec3.ZERO : new Vec3(
                Math.max(0.0, halfExtents.x),
                Math.max(0.0, halfExtents.y),
                Math.max(0.0, halfExtents.z)
        );
        segments = Mth.clamp(segments, 8, 256);
        polygonPoints = polygonPoints == null ? List.of() : List.copyOf(polygonPoints);
        fillOpacity = Mth.clamp(fillOpacity, 0.0f, 1.0f);
        strokeOpacity = Mth.clamp(strokeOpacity, 0.0f, 1.0f);
    }

    public enum Shape {
        CIRCLE,
        RING,
        DOME,
        SPHERE,
        BOX,
        POLYGON,
        ARC_SECTOR
    }
}
