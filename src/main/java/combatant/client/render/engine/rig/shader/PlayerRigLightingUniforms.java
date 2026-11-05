/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rig.shader;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import java.nio.ByteOrder;

/** Per-entity lightmap, animated opacity, hit response and tint (two std140 vec4s). */
public final class PlayerRigLightingUniforms {
    public static final String BLOCK_NAME = "RigPlayerLighting";
    private PlayerRigLightingUniforms() {}

    public static GpuBufferSlice upload(int packedLight) {
        return upload(packedLight, 1.0f, 0.0f, -1);
    }

    /** Lighting and per-actor tint share one std140 UBO; no additional GPU pass. */
    public static GpuBufferSlice upload(int packedLight, float opacity, float hit, int tint) {
        float block = ((packedLight & 0xffff) / 256.0f) + (0.5f / 16.0f);
        float sky = (((packedLight >>> 16) & 0xffff) / 256.0f) + (0.5f / 16.0f);
        return CombatantRenderSystem.uniforms().write("Combatant - PlayerRigLighting UBO", 32, 32,
                buffer -> buffer.order(ByteOrder.nativeOrder())
                        .putFloat(block).putFloat(sky).putFloat(opacity).putFloat(hit)
                        .putFloat(((tint >>> 16) & 255) / 255.0f)
                        .putFloat(((tint >>> 8) & 255) / 255.0f)
                        .putFloat((tint & 255) / 255.0f).putFloat(0.0f));
    }
}
