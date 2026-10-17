/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.gpu;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import combatant.client.render.engine.asset.gltf.geometry.GltfCompiledPrimitive;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import combatant.client.render.engine.vertex.CombatantVertexFormats;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Packs normalized glTF CPU geometry into Combatant's canonical shaderpack-neutral vertex ABI and
 * uploads it through the shared persistent RHI path. No GL/Vulkan API appears here.
 */
public final class GltfGpuPrimitiveCompiler {
    private GltfGpuPrimitiveCompiler() {}

    public static GltfGpuPrimitive upload(CombatantRhi rhi, String label, GltfCompiledPrimitive primitive) {
        Objects.requireNonNull(rhi, "rhi");
        Objects.requireNonNull(primitive, "primitive");

        boolean skinned = primitive.skinned();
        VertexFormat format = skinned ? CombatantVertexFormats.ASSET_PBR_SKINNED : CombatantVertexFormats.ASSET_PBR_STATIC;
        int stride = format.getVertexSize();
        ByteBuffer vertices = ByteBuffer.allocateDirect(Math.multiplyExact(primitive.vertexCount(), stride))
                .order(ByteOrder.nativeOrder());

        float[] positions = primitive.positions();
        float[] normals = primitive.normals();
        float[] tangents = primitive.tangents();
        float[] uv0 = primitive.texCoords0();
        float[] uv1 = primitive.texCoords1();
        float[] colors = primitive.colors();
        int[] joints = primitive.joints();
        float[] weights = primitive.weights();

        for (int vertex = 0; vertex < primitive.vertexCount(); vertex++) {
            put3(vertices, positions, vertex * 3, 0.0f, 0.0f, 0.0f);
            put2(vertices, uv0, vertex * 2, 0.0f, 0.0f);
            put2(vertices, uv1, vertex * 2, 0.0f, 0.0f);
            put3(vertices, normals, vertex * 3, 0.0f, 1.0f, 0.0f);
            put4(vertices, tangents, vertex * 4, 1.0f, 0.0f, 0.0f, 1.0f);
            putColor(vertices, colors, vertex, primitive.vertexCount());
            if (skinned) {
                int jointOffset = vertex * 4;
                vertices.putInt(joints[jointOffset]);
                vertices.putInt(joints[jointOffset + 1]);
                vertices.putInt(joints[jointOffset + 2]);
                vertices.putInt(joints[jointOffset + 3]);
                int weightOffset = vertex * 4;
                vertices.putFloat(weights[weightOffset]);
                vertices.putFloat(weights[weightOffset + 1]);
                vertices.putFloat(weights[weightOffset + 2]);
                vertices.putFloat(weights[weightOffset + 3]);
            }
        }
        vertices.flip();

        int[] sourceIndices = primitive.indices();
        ByteBuffer indices = ByteBuffer.allocateDirect(Math.multiplyExact(sourceIndices.length, Integer.BYTES))
                .order(ByteOrder.nativeOrder());
        for (int index : sourceIndices) indices.putInt(index);
        indices.flip();

        PersistentGpuMesh allocation = rhi.persistentMeshes().upload(
                label, vertices, stride, indices, sourceIndices.length, IndexType.INT
        );
        return new GltfGpuPrimitive(allocation, format, primitive.material(), primitive.bounds(), skinned);
    }

    private static void put2(ByteBuffer out, float[] values, int offset, float x, float y) {
        out.putFloat(values != null ? values[offset] : x);
        out.putFloat(values != null ? values[offset + 1] : y);
    }

    private static void put3(ByteBuffer out, float[] values, int offset, float x, float y, float z) {
        out.putFloat(values != null ? values[offset] : x);
        out.putFloat(values != null ? values[offset + 1] : y);
        out.putFloat(values != null ? values[offset + 2] : z);
    }

    private static void put4(ByteBuffer out, float[] values, int offset, float x, float y, float z, float w) {
        out.putFloat(values != null ? values[offset] : x);
        out.putFloat(values != null ? values[offset + 1] : y);
        out.putFloat(values != null ? values[offset + 2] : z);
        out.putFloat(values != null ? values[offset + 3] : w);
    }

    private static void putColor(ByteBuffer out, float[] colors, int vertex, int vertexCount) {
        if (colors == null) {
            out.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            return;
        }
        int components = colors.length == vertexCount * 3 ? 3 : 4;
        int offset = vertex * components;
        out.put(unorm8(colors[offset]));
        out.put(unorm8(colors[offset + 1]));
        out.put(unorm8(colors[offset + 2]));
        out.put(components == 4 ? unorm8(colors[offset + 3]) : (byte) 0xFF);
    }

    private static byte unorm8(float value) {
        if (!Float.isFinite(value)) value = 0.0f;
        return (byte) Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
    }
}
