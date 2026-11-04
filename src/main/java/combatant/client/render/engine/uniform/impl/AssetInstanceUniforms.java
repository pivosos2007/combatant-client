/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import org.joml.Matrix4f;

import java.util.List;

/** Per-draw transform palette consumed by the static PBR hardware-instancing vertex shader. */
public final class AssetInstanceUniforms {
    public static final int MAX_INSTANCES = 64;
    private static final int MATRIX_BYTES = 64;
    private static final int SIZE = MAX_INSTANCES * MATRIX_BYTES;

    private AssetInstanceUniforms() {}

    /** Caller owns the matrix snapshots; they must not be mutated while writing the UBO. */
    public static GpuBufferSlice write(List<Matrix4f> models, int offset, int count) {
        if (models == null || count < 2 || count > MAX_INSTANCES
                || offset < 0 || offset > models.size() - count) {
            throw new IllegalArgumentException("Invalid GPU instance palette");
        }
        return CombatantRenderSystem.uniforms().write(
                "Combatant - AssetInstances UBO", SIZE, 16, buffer -> {
                    Std140Builder writer = Std140Builder.intoBuffer(buffer);
                    for (int i = 0; i < count; i++) {
                        writer.putMat4f(models.get(offset + i));
                    }
                    // The GPU only reads [0, instanceCount), so no padding upload is necessary.
                });
    }
}
