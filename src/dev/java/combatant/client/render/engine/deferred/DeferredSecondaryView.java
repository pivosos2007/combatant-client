/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Immutable per-frame metadata for one secondary render view/cascade/probe.
 *
 * <p>The target coordinates describe both array-layer and atlas-backed producers.
 * A zero viewport width/height means "use the complete producer-owned target/layer".</p>
 */
public record DeferredSecondaryView(
        DeferredViewFamily family,
        int index,
        String id,
        Matrix4f view,
        Matrix4f projection,
        Vec3 origin,
        int targetLayer,
        int viewportX,
        int viewportY,
        int viewportWidth,
        int viewportHeight,
        float nearPlane,
        float farPlane
) {
    public DeferredSecondaryView {
        if (family == null) throw new IllegalArgumentException("family");
        if (index < 0) throw new IllegalArgumentException("index");
        id = id == null || id.isBlank() ? family.name().toLowerCase() + "." + index : id.trim();
        view = view == null ? new Matrix4f() : new Matrix4f(view);
        projection = projection == null ? new Matrix4f() : new Matrix4f(projection);
        origin = origin == null ? Vec3.ZERO : origin;
        targetLayer = Math.max(0, targetLayer);
        viewportX = Math.max(0, viewportX);
        viewportY = Math.max(0, viewportY);
        viewportWidth = Math.max(0, viewportWidth);
        viewportHeight = Math.max(0, viewportHeight);
        nearPlane = Math.max(0.0f, nearPlane);
        farPlane = Math.max(nearPlane, farPlane);
    }

    public DeferredSecondaryView(DeferredViewFamily family,
                                 int index,
                                 String id,
                                 Matrix4fc view,
                                 Matrix4fc projection,
                                 Vec3 origin,
                                 int targetLayer,
                                 float nearPlane,
                                 float farPlane) {
        this(family, index, id,
                view == null ? null : new Matrix4f(view),
                projection == null ? null : new Matrix4f(projection),
                origin, targetLayer, 0, 0, 0, 0, nearPlane, farPlane);
    }

    @Override
    public Matrix4f view() {
        return new Matrix4f(view);
    }

    @Override
    public Matrix4f projection() {
        return new Matrix4f(projection);
    }

    public Matrix4f viewProjection() {
        return new Matrix4f(projection).mul(view);
    }

    public boolean hasExplicitViewport() {
        return viewportWidth > 0 && viewportHeight > 0;
    }

    public DeferredSecondaryView withViewport(int x, int y, int width, int height) {
        return new DeferredSecondaryView(
                family, index, id, view, projection, origin, targetLayer,
                x, y, width, height, nearPlane, farPlane
        );
    }
}
