/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

public record RhiUniformBinding(String name, GpuBufferSlice slice) {
}
