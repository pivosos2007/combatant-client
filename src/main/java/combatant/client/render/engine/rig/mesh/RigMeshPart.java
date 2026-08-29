/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Immutable CPU-side mesh part.
 */
public final class RigMeshPart {
    private final String name;
    private final RigVertex[] vertices;
    private final int[] indices;

    public RigMeshPart(String name, RigVertex[] vertices, int[] indices) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Rig mesh part name must not be blank");
        if (vertices == null || vertices.length == 0) throw new IllegalArgumentException("Rig mesh part must contain vertices");
        if (indices == null || indices.length == 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("Rig mesh part indices must contain complete triangles");
        }
        for (int index : indices) {
            if (index < 0 || index >= vertices.length) {
                throw new IllegalArgumentException("Rig mesh index " + index + " outside vertex range [0, " + (vertices.length - 1) + "]");
            }
        }
        this.name = name;
        this.vertices = vertices.clone();
        this.indices = indices.clone();
    }

    public String name() {
        return name;
    }

    public int vertexCount() {
        return vertices.length;
    }

    public RigVertex vertex(int index) {
        return vertices[index];
    }

    public int indexCount() {
        return indices.length;
    }

    public int index(int index) {
        return indices[index];
    }

    RigVertex[] verticesRef() {
        return vertices;
    }

    int[] indicesRef() {
        return indices;
    }

    static final class Builder {
        private final String name;
        private final ObjectArrayList<RigVertex> vertices;
        private final IntArrayList indices;

        Builder(String name, int expectedVertices, int expectedIndices) {
            this.name = name;
            this.vertices = new ObjectArrayList<>(Math.max(0, expectedVertices));
            this.indices = new IntArrayList(Math.max(0, expectedIndices));
        }

        int addVertex(RigVertex vertex) {
            int index = vertices.size();
            vertices.add(vertex);
            return index;
        }

        void triangle(int a, int b, int c) {
            indices.add(a);
            indices.add(b);
            indices.add(c);
        }

        int vertexCount() {
            return vertices.size();
        }

        RigMeshPart build() {
            return new RigMeshPart(name, vertices.toArray(new RigVertex[0]), indices.toIntArray());
        }
    }
}
