/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.MeshOwnership;
import combatant.client.render.engine.uniform.MeshBuilder;


record Blaze3dMeshAllocation(Blaze3dMeshArena arena,
                              int vertexOffsetBytes,
                              int indexOffsetBytes,
                              int vertexBytes,
                              int indexBytes,
                              int baseVertex) {
    static int baseVertex(int vertexOffsetBytes, int vertexStride) {
        if (vertexStride <= 0) {
            throw new IllegalArgumentException("Invalid vertex stride: " + vertexStride);
        }
        if (vertexOffsetBytes % vertexStride != 0) {
            throw new IllegalStateException("Dynamic mesh vertex offset is not stride-aligned: offset="
                    + vertexOffsetBytes + ", stride=" + vertexStride);
        }
        return vertexOffsetBytes / vertexStride;
    }

    GpuMeshHandle toHandle(MeshBuilder mesh) {
        return GpuMeshHandle.arena(
                arena.vertexBuffer(),
                arena.indexBuffer(),
                vertexOffsetBytes,
                vertexBytes,
                mesh.getVertexStride(),
                baseVertex,
                indexOffsetBytes,
                indexBytes,
                mesh.getIndicesCount(),
                MeshOwnership.BACKEND_FRAME
        );
    }

    void write(MeshBuilder mesh) {
        mesh.validateComplete("dynamic mesh allocation write");
        arena.writeVertices(vertexOffsetBytes, vertexBytes, mesh.vertexBufferView());
        arena.writeIndices(indexOffsetBytes, indexBytes, mesh.indexBufferView());
    }
}
