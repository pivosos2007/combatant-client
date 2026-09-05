/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/** Small material block used only by the liquid-glass UI-underlay shader variants. */
public enum UIBackdropUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator().putVec4().get();
    private static final String UNIFORM_NAME = "Combatant - UIBackdrop UBO";
    private static final Data DATA = new Data();

    public static GpuBufferSlice write(float uiMix) {
        DATA.uiMix = Float.isFinite(uiMix) ? Math.max(0.0f, Math.min(1.0f, uiMix)) : 0.0f;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, 32, DATA);
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float uiMix;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(uiMix).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
