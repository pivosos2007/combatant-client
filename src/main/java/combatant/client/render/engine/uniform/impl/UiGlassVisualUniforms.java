/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.renderer.ui.draw.UiGlassVisual;
import combatant.client.render.engine.uniform.ShaderUniformBindings;

public enum UiGlassVisualUniforms {;
    private static final ShaderUniformBindings.Block BLOCK = ShaderUniformBindings.block("UIGlassVisual");
    public static final String BLOCK_NAME = BLOCK.name();
    private static final ShaderUniformBindings.Writer DATA = BLOCK.writer();

    public static GpuBufferSlice update(UiGlassVisual visual) {
        DATA.vec4("uGlassVisualMeta", visual.sampleReachPx(), 0f, 0f, 0f);
        var p = visual.parameters();
        for (int i = 0; i < 4; i++) {
            DATA.vec4("uGlassVisualParams", i, p.get(i * 4), p.get(i * 4 + 1),
                    p.get(i * 4 + 2), p.get(i * 4 + 3));
        }
        return DATA.upload(64);
    }
}
