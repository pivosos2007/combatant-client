/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.renderer.ui.draw.UiGlassStackRegions;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.uniform.ShaderUniformBindings;
import java.util.List;

/** Tight lower-lens regions. Values are per draw; no globally mutable stack state. */
public enum UiGlassStackUniforms {;
    private static final ShaderUniformBindings.Block BLOCK = ShaderUniformBindings.block("UIGlassStack");
    public static final String BLOCK_NAME = BLOCK.name();
    private static final ShaderUniformBindings.Writer DATA = BLOCK.writer();

    public static GpuBufferSlice update(List<UiRect> previous) {
        int count = previous == null ? 0 : Math.min(previous.size(), UiGlassStackRegions.CAPACITY);
        DATA.vec4("uStackMeta", count, 1.5f, count > 0 ? 0.24f : 0f, 0f);
        for (int i = 0; i < UiGlassStackRegions.CAPACITY; i++) {
            UiRect r = i < count ? previous.get(i) : null;
            DATA.vec4("uStackBounds", i,
                    r != null ? r.x() : 0f,
                    r != null ? r.y() : 0f,
                    r != null ? r.width() : 0f,
                    r != null ? r.height() : 0f);
        }
        return DATA.upload(64);
    }
}
