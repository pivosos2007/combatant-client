/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.gpu;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import combatant.client.render.engine.asset.model.data.ModelBounds;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;

import java.util.Objects;

/** Persistent backend-neutral GPU residency for one normalized model surface primitive. */
public final class ModelGpuPrimitive implements AutoCloseable {
    private final PersistentGpuMesh allocation;
    private final VertexFormat vertexFormat;
    private final int material;
    private final ModelBounds bounds;
    private final boolean skinned;

    ModelGpuPrimitive(PersistentGpuMesh allocation, VertexFormat vertexFormat,
                     int material, ModelBounds bounds, boolean skinned) {
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        this.vertexFormat = Objects.requireNonNull(vertexFormat, "vertexFormat");
        this.material = material;
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.skinned = skinned;
    }

    public GpuMeshHandle mesh() { return allocation.handle(); }
    public VertexFormat vertexFormat() { return vertexFormat; }
    public int material() { return material; }
    public ModelBounds bounds() { return bounds; }
    public boolean skinned() { return skinned; }
    public boolean isClosed() { return allocation.isClosed(); }

    @Override
    public void close() {
        allocation.close();
    }
}
