/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * Provides transient mesh uploads and frame/submission lifecycle hooks for the active backend.
 */
public interface DynamicMeshBackend extends AutoCloseable {
    GpuMeshHandle upload(MeshBuilder mesh);

    void beginFrame(long frameId);

    void endSubmission();

    void framePresented();

    @Override
    void close();
}
