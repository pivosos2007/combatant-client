/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rig.shader;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import java.nio.ByteOrder;

/** Per-entity vanilla lightmap coordinates. The 16-byte std140 vec4 is independent of rig bones. */
public final class PlayerRigLightingUniforms {
    public static final String BLOCK_NAME = "RigPlayerLighting";
    private PlayerRigLightingUniforms() {}

    public static GpuBufferSlice upload(int packedLight) {
        float block = ((packedLight & 0xffff) / 256.0f) + (0.5f / 16.0f);
        float sky = (((packedLight >>> 16) & 0xffff) / 256.0f) + (0.5f / 16.0f);
        return CombatantRenderSystem.uniforms().write("Combatant - PlayerRigLighting UBO", 16, 32,
                buffer -> buffer.order(ByteOrder.nativeOrder())
                        .putFloat(block).putFloat(sky).putFloat(0.0f).putFloat(0.0f));
    }
}
