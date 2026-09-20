/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Projection-convention-independent conservative culling for secondary perspective views.
 *
 * <p>Local shadow projections use reversed depth. Extracting clip planes from that matrix through
 * generic frustum helpers is backend/convention sensitive, while the geometric side planes are
 * completely defined by the view basis, FOV and finite near/far range. This helper performs the
 * latter directly and therefore stays identical for zero-to-one and negative-one-to-one depth.</p>
 */
public final class DeferredSecondaryViewCulling {
    private DeferredSecondaryViewCulling() {
    }

    public static boolean testPerspectiveAabb(DeferredSecondaryView view,
                                              double minX,
                                              double minY,
                                              double minZ,
                                              double maxX,
                                              double maxY,
                                              double maxZ) {
        if (view == null) return false;

        double centerX = ((minX + maxX) * 0.5) - view.origin().x;
        double centerY = ((minY + maxY) * 0.5) - view.origin().y;
        double centerZ = ((minZ + maxZ) * 0.5) - view.origin().z;
        float extentX = (float) ((maxX - minX) * 0.5);
        float extentY = (float) ((maxY - minY) * 0.5);
        float extentZ = (float) ((maxZ - minZ) * 0.5);

        Matrix4f viewMatrix = view.view();
        Vector3f center = new Vector3f((float) centerX, (float) centerY, (float) centerZ);
        viewMatrix.transformPosition(center);

        // Transform the world-axis AABB extents by the absolute rotation part of the view matrix.
        float viewExtentX = Math.abs(viewMatrix.m00()) * extentX
                + Math.abs(viewMatrix.m10()) * extentY
                + Math.abs(viewMatrix.m20()) * extentZ;
        float viewExtentY = Math.abs(viewMatrix.m01()) * extentX
                + Math.abs(viewMatrix.m11()) * extentY
                + Math.abs(viewMatrix.m21()) * extentZ;
        float viewExtentZ = Math.abs(viewMatrix.m02()) * extentX
                + Math.abs(viewMatrix.m12()) * extentY
                + Math.abs(viewMatrix.m22()) * extentZ;

        float forward = -center.z;
        float near = Math.max(0.0f, view.nearPlane());
        float far = Math.max(near, view.farPlane());
        if (forward + viewExtentZ < near || forward - viewExtentZ > far) return false;
        if (forward + viewExtentZ <= 0.0f) return false;

        Matrix4f projection = view.projection();
        float m00 = Math.abs(projection.m00());
        float m11 = Math.abs(projection.m11());
        float tanHalfX = m00 > 1.0e-6f ? 1.0f / m00 : 1.0f;
        float tanHalfY = m11 > 1.0e-6f ? 1.0f / m11 : 1.0f;

        // Use the farthest possible point of the AABB along the view ray for a conservative side
        // plane test. This can over-include sections touching a face boundary, never reject them.
        float conservativeDepth = Math.max(near, forward + viewExtentZ);
        if (Math.abs(center.x) - viewExtentX > conservativeDepth * tanHalfX) return false;
        return Math.abs(center.y) - viewExtentY <= conservativeDepth * tanHalfY;
    }

    public static boolean testPerspectiveAabb(DeferredSecondaryView view, AABB box) {
        return box != null && testPerspectiveAabb(
                view, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ
        );
    }

    public static AABB perspectiveRangeBounds(DeferredSecondaryView view, double padding) {
        Vec3 origin = view == null ? Vec3.ZERO : view.origin();
        double radius = view == null ? 0.0 : Math.max(0.0, view.farPlane()) + Math.max(0.0, padding);
        return new AABB(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
    }
}
