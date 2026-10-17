/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import com.mojang.renderpearl.api.pipeline.IndexType;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.nio.ByteBuffer;

/**
 * Backend-neutral immutable/static mesh residency API.
 *
 * <p>Implementations must not expose OpenGL/Vulkan objects to callers. The same API is used by
 * both production RHI backends so imported assets, compute-generated templates and other reusable
 * geometry do not acquire backend-specific ownership.</p>
 */
public interface PersistentMeshBackend extends AutoCloseable {
    PersistentGpuMesh upload(String label, ByteBuffer vertices, int vertexStride,
                             ByteBuffer indices, int indexCount, IndexType indexType);

    default PersistentGpuMesh upload(String label, MeshBuilder mesh) {
        if (mesh == null) throw new IllegalArgumentException("mesh");
        if (mesh.isBuilding()) mesh.end();
        mesh.validateComplete(label == null ? "persistent mesh upload" : label + " upload");
        return upload(label, mesh.vertexBufferView(), mesh.getVertexStride(), mesh.indexBufferView(),
                mesh.getIndicesCount(), mesh.getIndexType());
    }

    @Override
    void close();
}
