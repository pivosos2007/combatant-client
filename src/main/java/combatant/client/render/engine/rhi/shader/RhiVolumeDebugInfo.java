/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;

/** Lightweight immutable debug snapshot for a native 3D resource. */
public record RhiVolumeDebugInfo(String backend,
                                 String label,
                                 int width,
                                 int height,
                                 int depth,
                                 int mipLevels,
                                 GpuFormat format,
                                 int usage,
                                 long approximateBytes,
                                 boolean closed) {
}
