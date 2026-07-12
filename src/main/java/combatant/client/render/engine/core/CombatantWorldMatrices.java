/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.minecraft.world.phys.Vec3;

public enum CombatantWorldMatrices {
    ;
    private static final Matrix4f POSITION = new Matrix4f();
    private static final Matrix4f RENDER_PROJECTION = new Matrix4f();
    private static final Matrix4f FRUSTUM_PROJECTION = new Matrix4f();
    private static Vec3 CAMERA_POS;
    private static boolean valid;

    public static void reset() {
        valid = false;
    }

    public static void capture(Matrix4fc positionMatrix, Matrix4fc renderProjectionMatrix, Matrix4fc frustumProjectionMatrix) {
        capture(positionMatrix, renderProjectionMatrix, frustumProjectionMatrix, null);
    }

    public static void capture(Matrix4fc positionMatrix, Matrix4fc renderProjectionMatrix, Matrix4fc frustumProjectionMatrix, Vec3 cameraPos) {
        if (positionMatrix == null || renderProjectionMatrix == null || frustumProjectionMatrix == null) {
            valid = false;
            CAMERA_POS = null;
            return;
        }

        POSITION.set(positionMatrix);
        RENDER_PROJECTION.set(renderProjectionMatrix);
        FRUSTUM_PROJECTION.set(frustumProjectionMatrix);
        CAMERA_POS = cameraPos;
        valid = true;
    }

    public static void capturePosition(Matrix4fc positionMatrix, Vec3 cameraPos) {
        if (!valid || positionMatrix == null) {
            return;
        }
        POSITION.set(positionMatrix);
        if (cameraPos != null) {
            CAMERA_POS = cameraPos;
        }
    }

    public static boolean isValid() {
        return valid;
    }

    public static Matrix4f positionMatrix() {
        return valid ? new Matrix4f(POSITION) : null;
    }

    public static Matrix4f renderProjectionMatrix() {
        return valid ? new Matrix4f(RENDER_PROJECTION) : null;
    }

    public static Matrix4f frustumProjectionMatrix() {
        return valid ? new Matrix4f(FRUSTUM_PROJECTION) : null;
    }

    public static Vec3 cameraPosition() {
        return valid ? CAMERA_POS : null;
    }
}
