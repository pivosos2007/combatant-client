/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Resolved placement data consumed by text backends.
 */
public record TextPlacementTransform(TextPlacementMode mode,
                                     Matrix4f matrix,
                                     Vec3 worldAnchor,
                                     float screenX,
                                     float screenY,
                                     float screenZ,
                                     float scale,
                                     boolean billboard) {
    public static TextPlacementTransform ui(float x, float y, float z, float scale) {
        return new TextPlacementTransform(TextPlacementMode.UI, null, null, x, y, z, scale, false);
    }

    public static TextPlacementTransform screen(float x, float y, float z, float scale) {
        return new TextPlacementTransform(TextPlacementMode.SCREEN_SPACE, null, null, x, y, z, scale, false);
    }

    public static TextPlacementTransform worldBillboard(Vec3 anchor, float scale) {
        return new TextPlacementTransform(TextPlacementMode.WORLD_BILLBOARD, null, anchor, 0f, 0f, 0f, scale, true);
    }

    public static TextPlacementTransform worldAligned(Vec3 anchor, Matrix4f matrix, float scale) {
        return new TextPlacementTransform(TextPlacementMode.WORLD_ALIGNED, matrix != null ? new Matrix4f(matrix) : null, anchor, 0f, 0f, 0f, scale, false);
    }

    public boolean world() {
        return mode != null && mode.world();
    }

    public boolean screenLike() {
        return mode == null || mode.screenLike();
    }
}
