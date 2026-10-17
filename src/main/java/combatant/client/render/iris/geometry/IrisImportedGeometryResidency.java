/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.renderpearl.api.pipeline.IndexType;
import combatant.client.render.engine.asset.gltf.GltfRuntimeAsset;
import combatant.client.render.engine.asset.gltf.GltfAssetRepository;
import combatant.client.render.engine.asset.gltf.geometry.GltfCompiledPrimitive;
import combatant.client.render.engine.asset.gltf.model.GltfMesh;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import net.irisshaders.iris.vertices.IrisVertexFormats;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Backend-scoped persistent Iris copies of immutable imported mesh/material resources. */
public final class IrisImportedGeometryResidency implements AutoCloseable {
    private static final IdentityHashMap<GltfRuntimeAsset, IrisImportedGeometryResidency> CACHE = new IdentityHashMap<>();
    private static CombatantRhi owner;
    private static long sourceEpoch = Long.MIN_VALUE;

    private final List<List<Primitive>> meshes;
    private final List<IrisCanonicalMaterialAtlas> materials;

    private IrisImportedGeometryResidency(List<List<Primitive>> meshes, List<IrisCanonicalMaterialAtlas> materials) {
        this.meshes = List.copyOf(meshes);
        this.materials = List.copyOf(materials);
    }

    static synchronized IrisImportedGeometryResidency acquire(CombatantRhi rhi, GltfRuntimeAsset asset) {
        long currentEpoch = GltfAssetRepository.global().epoch();
        if (asset.sourceEpoch() > 0L && asset.sourceEpoch() != currentEpoch) {
            throw new IllegalStateException("Stale imported asset cannot acquire Iris residency after reload: "
                    + asset.asset().id());
        }
        if (owner != rhi) {
            closeCached();
            owner = rhi;
            sourceEpoch = currentEpoch;
        } else if (sourceEpoch != currentEpoch) {
            retireCached();
            sourceEpoch = currentEpoch;
        }
        return CACHE.computeIfAbsent(asset, key -> upload(rhi, key));
    }

    static synchronized void releaseBackend(CombatantRhi rhi) {
        if (owner != rhi) return;
        closeCached();
        owner = null;
        sourceEpoch = Long.MIN_VALUE;
    }

    public static synchronized void invalidateImportedIrisResidency() {
        retireCached();
        sourceEpoch = GltfAssetRepository.global().epoch();
    }

    Primitive primitive(int meshIndex, int primitiveIndex) { return meshes.get(meshIndex).get(primitiveIndex); }
    IrisCanonicalMaterialAtlas material(int materialIndex) { return materials.get(materialIndex); }

    private static IrisImportedGeometryResidency upload(CombatantRhi rhi, GltfRuntimeAsset asset) {
        ArrayList<List<Primitive>> meshes = new ArrayList<>();
        ArrayList<Primitive> primitiveRollback = new ArrayList<>();
        ArrayList<IrisCanonicalMaterialAtlas> materials = new ArrayList<>();
        try {
            for (int meshIndex = 0; meshIndex < asset.asset().meshes().size(); meshIndex++) {
                GltfMesh mesh = asset.asset().meshes().get(meshIndex);
                ArrayList<Primitive> primitives = new ArrayList<>();
                for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                    Primitive primitive = uploadPrimitive(rhi,
                            "iris-gltf:" + asset.asset().id() + "/mesh_" + meshIndex + "/primitive_" + primitiveIndex,
                            asset.compilePrimitive(meshIndex, primitiveIndex));
                    primitives.add(primitive);
                    primitiveRollback.add(primitive);
                }
                meshes.add(List.copyOf(primitives));
            }
            for (int materialIndex = 0; materialIndex < asset.materials().size(); materialIndex++) {
                materials.add(IrisCanonicalMaterialAtlasCompiler.upload(rhi, asset, materialIndex,
                        "iris-gltf:" + asset.asset().id() + "/material_" + materialIndex));
            }
            return new IrisImportedGeometryResidency(meshes, materials);
        } catch (RuntimeException | Error failure) {
            for (int i = materials.size() - 1; i >= 0; i--) try { materials.get(i).close(); } catch (Throwable ignored) { }
            for (int i = primitiveRollback.size() - 1; i >= 0; i--) try { primitiveRollback.get(i).close(); } catch (Throwable ignored) { }
            throw failure;
        }
    }

    private static Primitive uploadPrimitive(CombatantRhi rhi, String label, GltfCompiledPrimitive primitive) {
        int stride = IrisVertexFormats.ENTITY.getVertexSize();
        ByteBuffer vertices = ByteBuffer.allocateDirect(Math.multiplyExact(primitive.vertexCount(), stride))
                .order(ByteOrder.nativeOrder());
        float[] positions = primitive.positions();
        float[] normals = primitive.normals();
        float[] tangents = primitive.tangents();
        float[] uv0 = primitive.texCoords0();
        float[] uv1 = primitive.texCoords1();
        float[] colors = primitive.colors();

        for (int vertex = 0; vertex < primitive.vertexCount(); vertex++) {
            put3(vertices, positions, vertex * 3, 0.0f, 0.0f, 0.0f);
            putColor(vertices, colors, vertex, primitive.vertexCount());
            put2(vertices, uv0, vertex * 2, 0.0f, 0.0f);
            vertices.putShort((short) 0).putShort((short) 0); // UV1 / overlay
            // UV2 is the normal Iris lightmap channel. Keep imported geometry on a valid
            // shaderpack vertex contract instead of smuggling light through matrix elements.
            // Block light is zero, sky light is full for this first immutable residency path;
            // dynamic per-instance light can later move to an explicit instance payload.
            vertices.putShort((short) 0).putShort((short) 240);
            putSnorm4(vertices, normals, vertex * 3, 0.0f, 1.0f, 0.0f, 0.0f);
            // Entity information remains neutral. Imported-material selection is an explicit
            // draw-scoped Iris uniform and must never overload IDs shared by native entities.
            vertices.putShort((short) 0).putShort((short) 0)
                    .putShort((short) 0).putShort((short) 0);
            put2(vertices, uv1, vertex * 2, 0.0f, 0.0f);
            putIrisTangent(vertices, tangents, vertex * 4);
        }
        vertices.flip();
        if (vertices.remaining() != primitive.vertexCount() * stride) {
            throw new IllegalStateException("Iris entity vertex packing does not match runtime stride");
        }
        int[] sourceIndices = primitive.indices();
        ByteBuffer indices = ByteBuffer.allocateDirect(Math.multiplyExact(sourceIndices.length, Integer.BYTES))
                .order(ByteOrder.nativeOrder());
        for (int index : sourceIndices) indices.putInt(index);
        indices.flip();
        PersistentGpuMesh allocation = rhi.persistentMeshes().upload(
                label, vertices, stride, indices, sourceIndices.length, IndexType.INT);
        return new Primitive(allocation, primitive.material(), primitive.skinned());
    }

    @Override
    public void close() {
        for (IrisCanonicalMaterialAtlas material : materials) try { material.close(); } catch (Throwable ignored) { }
        for (List<Primitive> mesh : meshes) for (Primitive primitive : mesh) {
            try { primitive.close(); } catch (Throwable ignored) { }
        }
    }

    private static void retireCached() {
        if (CACHE.isEmpty()) return;
        CombatantRhi currentOwner = owner;
        for (IrisImportedGeometryResidency residency : CACHE.values()) {
            if (currentOwner != null) currentOwner.resources().retire(residency);
            else try { residency.close(); } catch (Throwable ignored) { }
        }
        CACHE.clear();
    }

    private static void closeCached() {
        for (IrisImportedGeometryResidency residency : CACHE.values()) {
            try { residency.close(); } catch (Throwable ignored) { }
        }
        CACHE.clear();
    }

    record Primitive(PersistentGpuMesh allocation, int material, boolean skinned) implements AutoCloseable {
        GpuMeshHandle mesh() { return allocation.handle(); }
        @Override public void close() { allocation.close(); }
    }

    private static void put2(ByteBuffer out, float[] values, int offset, float x, float y) {
        out.putFloat(values != null ? values[offset] : x).putFloat(values != null ? values[offset + 1] : y);
    }
    private static void put3(ByteBuffer out, float[] values, int offset, float x, float y, float z) {
        out.putFloat(values != null ? values[offset] : x)
                .putFloat(values != null ? values[offset + 1] : y)
                .putFloat(values != null ? values[offset + 2] : z);
    }
    private static void putSnorm4(ByteBuffer out, float[] values, int offset, float x, float y, float z, float w) {
        out.put(snorm(values != null ? values[offset] : x));
        out.put(snorm(values != null ? values[offset + 1] : y));
        out.put(snorm(values != null ? values[offset + 2] : z));
        out.put(snorm(values != null && values.length > offset + 3 ? values[offset + 3] : w));
    }
    private static void putIrisTangent(ByteBuffer out, float[] values, int offset) {
        float x = values != null ? values[offset] : 1.0f;
        float y = values != null ? values[offset + 1] : 0.0f;
        float z = values != null ? values[offset + 2] : 0.0f;
        float gltfHandedness = values != null && values.length > offset + 3 ? values[offset + 3] : 1.0f;
        /*
         * glTF defines B = cross(N, T) * tangent.w. Iris entity shaders define the stored sign
         * for B = cross(T, N) * at_tangent.w. The cross products have opposite signs, so the
         * handedness must be inverted when adapting a glTF vertex to Iris' ENTITY format.
         */
        out.put(snorm(x)).put(snorm(y)).put(snorm(z)).put(snorm(-gltfHandedness));
    }
    private static void putColor(ByteBuffer out, float[] colors, int vertex, int vertexCount) {
        if (colors == null) {
            out.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            return;
        }
        int components = colors.length == vertexCount * 3 ? 3 : 4;
        int offset = vertex * components;
        out.put(unorm(colors[offset])).put(unorm(colors[offset + 1])).put(unorm(colors[offset + 2]));
        out.put(components == 4 ? unorm(colors[offset + 3]) : (byte) 0xFF);
    }
    private static byte snorm(float value) {
        if (!Float.isFinite(value)) value = 0.0f;
        return (byte) Math.round(Math.max(-1.0f, Math.min(1.0f, value)) * 127.0f);
    }
    private static byte unorm(float value) {
        if (!Float.isFinite(value)) value = 0.0f;
        return (byte) Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
    }
}
