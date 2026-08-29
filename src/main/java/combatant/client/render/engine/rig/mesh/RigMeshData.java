/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/**
 * CPU-side rig mesh description/compiler input. It deliberately owns no GPU resource or backend state.
 */
public final class RigMeshData {
    private final RigMeshPart[] parts;
    private final List<RigMeshPart> partView;
    private final int vertexCount;
    private final int indexCount;

    public RigMeshData(List<RigMeshPart> parts) {
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("Rig mesh data must contain at least one part");
        this.parts = parts.toArray(new RigMeshPart[0]);
        int vertices = 0;
        int indices = 0;
        for (RigMeshPart part : this.parts) {
            if (part == null) throw new IllegalArgumentException("Rig mesh data contains a null part");
            vertices = Math.addExact(vertices, part.vertexCount());
            indices = Math.addExact(indices, part.indexCount());
        }
        this.partView = List.of(this.parts);
        this.vertexCount = vertices;
        this.indexCount = indices;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int partCount() {
        return parts.length;
    }

    public RigMeshPart part(int index) {
        return parts[index];
    }

    public List<RigMeshPart> parts() {
        return partView;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public static final class Builder {
        private final ObjectArrayList<RigMeshPart> parts = new ObjectArrayList<>();

        public Builder part(RigMeshPart part) {
            if (part == null) throw new IllegalArgumentException("Rig mesh part must not be null");
            parts.add(part);
            return this;
        }

        public RigMeshData build() {
            return new RigMeshData(parts);
        }
    }
}
