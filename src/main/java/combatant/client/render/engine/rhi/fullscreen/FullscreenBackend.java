/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.fullscreen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import combatant.client.render.engine.rhi.GpuMeshHandle;

public interface FullscreenBackend extends AutoCloseable {
    void ensureInitialized();

    GpuMeshHandle quad();

    GpuBuffer vertexBuffer();

    GpuBuffer indexBuffer();

    @Override
    void close();
}
