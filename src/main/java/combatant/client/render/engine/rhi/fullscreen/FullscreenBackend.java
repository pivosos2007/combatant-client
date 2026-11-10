/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.fullscreen;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import combatant.client.render.engine.rhi.GpuMeshHandle;

/**
 * Owns the reusable fullscreen geometry exposed to post-processing submissions.
 * Initialization is explicit; consumers borrow the returned GPU objects.
 */
public interface FullscreenBackend extends AutoCloseable {
    void ensureInitialized();

    GpuMeshHandle quad();

    GpuBuffer vertexBuffer();

    GpuBuffer indexBuffer();

    @Override
    void close();
}
