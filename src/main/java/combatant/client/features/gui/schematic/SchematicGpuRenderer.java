/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.schematic;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.features.gui.schematic.view3d.FaceList;
import combatant.client.features.gui.schematic.view3d.FaceMesher;
import combatant.client.features.gui.schematic.view3d.OrbitCamera;
import combatant.client.features.gui.schematic.view3d.VoxelVolume;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * GPU-driven schematic viewport: the CPU uploads one instance per meshed face, never one per block-atlas tile.
 * A compute shader rejects groups against the clip frustum, compacts face indices and updates a single
 * DrawIndexedIndirect command. One native depth-tested raster pass renders to an offscreen UI texture.
 *
 * GPU assets are owned by this viewport and explicitly released on screen close/backend replacement.
 * No per-face geometry or camera-dependent mesh data is uploaded during normal camera movement.
 */
final class SchematicGpuRenderer implements AutoCloseable {
    private static final int CHUNK_SIZE = 256;
    private static final int TILE_SIZE = 128;
    private static final int FACE_BYTES = 144; // 8 vec4 attributes + one uvec4 = 9 * 16
    private static final int CHUNK_BYTES = 64; // bounds, shared tile centre, range + LOD mask
    private static final int CAMERA_BYTES = 128; // view, projection, depth, eye, LOD vec4
    private static final Identifier RESET = Identifier.fromNamespaceAndPath("combatant", "schematic_indirect_reset");
    private static final Identifier COMPACT = Identifier.fromNamespaceAndPath("combatant", "schematic_indirect_compact");
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("combatant", "schematic_instanced.vert");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("combatant", "schematic_instanced.frag");

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("view", Std430Type.MAT4).member("projection", Std430Type.VEC4)
            .member("depth", Std430Type.VEC4).member("eye", Std430Type.VEC4)
            .member("lod", Std430Type.VEC4).build();
    private static final Std430StructLayout FACE_LAYOUT = Std430StructLayout.builder()
            .member("p0", Std430Type.VEC4).member("p1", Std430Type.VEC4)
            .member("p2", Std430Type.VEC4).member("p3", Std430Type.VEC4)
            .member("uv01", Std430Type.VEC4).member("uv23", Std430Type.VEC4)
            .member("atlas0", Std430Type.VEC4).member("atlas1", Std430Type.VEC4)
            .member("style", Std430Type.UVEC4).build();
    private static final Std430StructLayout CHUNK_LAYOUT = Std430StructLayout.builder()
            .member("minimum", Std430Type.VEC4).member("maximum", Std430Type.VEC4)
            .member("tileCentre", Std430Type.VEC4).member("range", Std430Type.UVEC4).build();
    private static final Std430StructLayout INDEX_LAYOUT = Std430StructLayout.builder()
            .member("index", Std430Type.UINT).build();
    private static final Std430StructLayout DRAW_LAYOUT = Std430StructLayout.builder()
            .member("indexCount", Std430Type.UINT).member("instanceCount", Std430Type.UINT)
            .member("firstIndex", Std430Type.UINT).member("vertexOffset", Std430Type.UINT)
            .member("firstInstance", Std430Type.UINT).build();
    private static final ShaderResourceLayout RESET_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE)));
    private static final ShaderResourceLayout CULL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY)));
    private static final ShaderResourceLayout DRAW_RESOURCES = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)));

    private final float[] view = new float[16];
    private final float[] projection = new float[4];
    private final ByteBuffer cameraData = ByteBuffer.allocateDirect(CAMERA_BYTES).order(ByteOrder.nativeOrder());
    private CombatantRhi owner;
    private CombatantRhi unsupported;
    private PersistentGpuMesh unitQuad;
    private RhiComputePipeline resetPipeline, compactPipeline;
    private RhiMeshPipeline meshPipeline;
    private RhiStorageBuffer cameraBuffer, facesBuffer, chunksBuffer, visibleBuffer, commandBuffer;
    private FaceList uploadedFaces, uploadedMedium, uploadedFar;
    private byte[] uploadedStatus;
    private int uploadedStatusRevision = Integer.MIN_VALUE;
    private int chunkCount;

    boolean draw(FaceList faces, FaceList medium, FaceList distant, VoxelVolume volume,
                 SchematicTextures.Entry[] textures, byte[] statuses,
                 combatant.client.util.schematic.SchematicDiff diff, int statusRevision,
                 OrbitCamera camera, SchematicAdaptiveQuality quality, float drawDistanceScale,
                 GpuTextureView atlas, GpuSampler atlasSampler,
                 float x, float y, float w, float h, float opacity, Runnable background) {
        if (faces == null || faces.count() == 0 || atlas == null || atlasSampler == null
                || w <= 2 || h <= 2 || opacity <= 0) return false;
        CombatantRhi rhi = CombatantRenderSystem.rhi();
        if (rhi == null || rhi == unsupported) return false;
        try {
            AdvancedShaderBackend advanced = rhi.advancedShaders();
            if (!advanced.supports(RhiShaderStage.COMPUTE) || !rhi.capabilities().drawIndirect()) return false;
            if (owner != rhi) {
                close();
                owner = rhi;
            }
            ensurePipelines(advanced, rhi);
            // Identical resolutions are not uploaded twice; GPU selection falls back to the available tier.
            if (distant == medium || distant == faces) distant = null;
            if (medium == faces) medium = null;
            if (uploadedFaces != faces || uploadedMedium != medium || uploadedFar != distant
                    || uploadedStatus != statuses || uploadedStatusRevision != statusRevision) {
                uploadFaces(advanced, faces, medium, distant, volume, textures,
                        statuses, diff, statusRevision);
            }
            ViewportContext viewport = ViewportContext.current();
            float resolutionScale = (float) viewport.framebufferWidth() / Math.max(1.0f, viewport.width());
            int width = Math.max(1, Math.round(w * resolutionScale));
            int height = Math.max(1, Math.round(h * resolutionScale));
            TextureTarget target = CombatantRenderSystem.resources().frameTransient(
                    TransientTargetDescriptor.frame("combatant-schematic-preview-gpu", width, height, true,
                            "SchematicViewport"));
            if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null)
                return false;

            writeCamera(camera, x, y, w, h, faces, volume, quality.quality(), drawDistanceScale);
            cameraBuffer.upload(cameraData, 0L);
            StorageBinding cam = binding(2, cameraBuffer, StorageAccess.READ_ONLY);
            StorageBinding chunks = binding(4, chunksBuffer, StorageAccess.READ_ONLY);
            StorageBinding counter = binding(5, commandBuffer, StorageAccess.READ_WRITE);
            StorageBinding visibleWrite = binding(6, visibleBuffer, StorageAccess.WRITE_ONLY);
            StorageBinding visibleRead = binding(6, visibleBuffer, StorageAccess.READ_ONLY);
            StorageBinding faceRead = binding(3, facesBuffer, StorageAccess.READ_ONLY);

            // Avoid reset/compact races: one dispatch initializes the GPU-owned indirect command,
            // then the second culls sections and atomically reserves unique visible instance ranges.
            advanced.dispatch(new ComputeDispatchCommand("Schematic indirect reset", resetPipeline, 1, 1, 1,
                    List.of(counter), List.of(), List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.READ_WRITE, List.of(commandBuffer), List.of()));
            advanced.dispatch(new ComputeDispatchCommand("Schematic frustum compact", compactPipeline,
                    (chunkCount + 63) / 64, 1, 1, List.of(cam, chunks, counter, visibleWrite),
                    List.of(), List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.GRAPHICS,
                    RhiResourceBarrier.Access.READ, List.of(visibleBuffer), List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.INDIRECT,
                    RhiResourceBarrier.Access.READ, List.of(commandBuffer), List.of()));

            // Reversed-Z depth buffer: the rasterizer clips near-plane intersections correctly.
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(target.getColorTexture(), new Vector4f(0.0f));
            encoder.clearDepthTexture(target.getDepthTexture(), 0.0D);
            advanced.drawPatches(new PatchDrawCommand("Schematic GPU-instanced indirect",
                    meshPipeline, List.of(target.getColorTextureView()), target.getDepthTextureView(),
                    unitQuad.handle(), List.of(cam, faceRead, visibleRead),
                    List.of(new SampledTextureBinding(0, atlas, atlasSampler)), List.of(), List.of(),
                    commandBuffer, 0L, 1, 20));

            // The only Renderer2D submission for the entire 3D scene is this composited texture.
            GpuSampler composite = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
            int tint = (Math.round(Math.min(1.0f, opacity) * 255f) << 24) | 0xFFFFFF;
            if (background != null) background.run();
            // This texture is produced by a native GPU render pass, not loaded as a UI image.
            // The rasterized Y axis and the UI's top-down quad UVs have opposite orientation
            // on both OpenGL and Vulkan. Without flipping V here the 3D preview is upside down,
            // while the CPU ray casting / hover projection stays right-side up.
            Renderer2D.COLOR.texturedQuadBatch(target.getColorTextureView(), composite,
                    batch -> batch.quad(x, y, w, h, 0f, 1f, 1f, 0f, tint));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // A broken native shader/backend should not take down the Schematics screen.
            close();
            unsupported = rhi;
            combatant.client.util.logging.DebugLog.warnOnce("schematic.gpu.indirect.disabled",
                    "[Schematic] GPU instanced indirect unavailable, using CPU fallback: %s", failure.toString());
            return false;
        }
    }

    private static StorageBinding binding(int binding, RhiStorageBuffer buffer, StorageAccess access) {
        return new StorageBinding(binding, buffer, 0L, 0L, access);
    }

    private void ensurePipelines(AdvancedShaderBackend advanced, CombatantRhi rhi) {
        if (unitQuad == null) {
            try (MeshBuilder mesh = new MeshBuilder(DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLES, 4, 6)) {
                mesh.beginLocal();
                int a = mesh.vec3(0, 0, 0).next();
                int b = mesh.vec3(1, 0, 0).next();
                int c = mesh.vec3(1, 1, 0).next();
                int d = mesh.vec3(0, 1, 0).next();
                mesh.quad(a, b, c, d);
                mesh.end();
                unitQuad = rhi.persistentMeshes().upload("Schematic instancing quad", mesh);
            }
        }
        if (cameraBuffer == null) cameraBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Schematic camera", CAMERA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        if (resetPipeline == null) resetPipeline = advanced.createComputePipeline(new ComputePipelineDescriptor(
                "Schematic reset indirect", RESET, RESET_LAYOUT));
        if (compactPipeline == null) compactPipeline = advanced.createComputePipeline(new ComputePipelineDescriptor(
                "Schematic compact visible chunks", COMPACT, CULL_LAYOUT));
        if (meshPipeline == null) meshPipeline = advanced.createMeshPipeline(new MeshPipelineDescriptor(
                "Schematic depth-tested instancing", VERTEX, FRAGMENT,
                VertexLayoutSpec.of("schematic-unit-quad", DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLES),
                DRAW_RESOURCES, AdvancedBlendMode.OPAQUE, AdvancedDepthMode.READ_WRITE_GREATER_EQUAL,
                AdvancedCullMode.NONE, List.of(GpuFormat.RGBA8_UNORM), GpuFormat.D32_FLOAT, 1));
    }

    /** Stable spatial buckets prevent LOD decisions from depending on face emission order. */
    private record TileKey(int x, int y, int z) { }

    private static final class FaceIndices {
        private int[] values = new int[32];
        private int size;
        void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }
    }

    private static final class Tile {
        final TileKey key;
        final FaceIndices[] levels = {new FaceIndices(), new FaceIndices(), new FaceIndices()};
        int available;
        Tile(TileKey key) { this.key = key; }
    }

    private static Map<TileKey, Tile> bucketFaces(FaceList[] levels) {
        Map<TileKey, Tile> result = new HashMap<>();
        for (int level = 0; level < levels.length; level++) {
            FaceList faces = levels[level];
            if (faces == null) continue;
            for (int face = 0; face < faces.count(); face++) {
                int x = (int) Math.floor(faces.centre(face, 0) / TILE_SIZE);
                int y = (int) Math.floor(faces.centre(face, 1) / TILE_SIZE);
                int z = (int) Math.floor(faces.centre(face, 2) / TILE_SIZE);
                TileKey key = new TileKey(x, y, z);
                Tile tile = result.computeIfAbsent(key, Tile::new);
                tile.levels[level].add(face);
                tile.available |= 1 << level;
            }
        }
        return result;
    }

    private void uploadFaces(AdvancedShaderBackend advanced, FaceList faces, FaceList medium, FaceList distant,
                             VoxelVolume volume, SchematicTextures.Entry[] textures, byte[] statuses,
                             combatant.client.util.schematic.SchematicDiff diff, int statusRevision) {
        releaseGeometry();
        FaceList[] levels = {faces, medium, distant};
        Map<TileKey, Tile> buckets = bucketFaces(levels);
        int count = 0;
        int sectionsCount = 0;
        for (Tile bucket : buckets.values()) {
            for (FaceIndices list : bucket.levels) {
                count = Math.addExact(count, list.size);
                sectionsCount += (list.size + CHUNK_SIZE - 1) / CHUNK_SIZE;
            }
        }
        if (count == 0 || sectionsCount == 0) return;
        chunkCount = sectionsCount;
        facesBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Schematic static face instances", FACE_LAYOUT, count, StorageAccess.READ_ONLY, false, false, true));
        chunksBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Schematic spatial LOD sections", CHUNK_LAYOUT, chunkCount, StorageAccess.READ_ONLY, false, false, true));
        visibleBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Schematic visible face indices", INDEX_LAYOUT, count, StorageAccess.READ_WRITE, false, true));
        commandBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Schematic GPU DrawIndexedIndirect", DRAW_LAYOUT, 1, StorageAccess.READ_WRITE, true, true));

        ByteBuffer packed = MemoryUtil.memAlloc(Math.multiplyExact(count, FACE_BYTES)).order(ByteOrder.nativeOrder());
        ByteBuffer sections = MemoryUtil.memAlloc(Math.multiplyExact(chunkCount, CHUNK_BYTES)).order(ByteOrder.nativeOrder());
        try {
            int first = 0;
            for (Tile tile : buckets.values()) {
                for (int lod = 0; lod < levels.length; lod++) {
                    FaceList mesh = levels[lod];
                    FaceIndices list = tile.levels[lod];
                    if (mesh == null) continue;
                    for (int offset = 0; offset < list.size; offset += CHUNK_SIZE) {
                        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
                        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
                        int end = Math.min(list.size, offset + CHUNK_SIZE);
                        for (int i = offset; i < end; i++) {
                            int face = list.values[i];
                            byte status = lod == 0 && statuses != null && face < statuses.length ? statuses[face]
                                    : diff != null ? findStatus(diff, mesh.voxel(face)) : 0;
                            packFace(packed, mesh, volume, textures, status, face);
                            for (int c = 0; c < 4; c++) {
                                float px = mesh.corner(face, c, 0), py = mesh.corner(face, c, 1), pz = mesh.corner(face, c, 2);
                                minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
                                maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
                            }
                        }
                        put4(sections, minX - 0.05f, minY - 0.05f, minZ - 0.05f, 0);
                        put4(sections, maxX + 0.05f, maxY + 0.05f, maxZ + 0.05f, 0);
                        put4(sections, (tile.key.x() + .5f) * TILE_SIZE,
                                (tile.key.y() + .5f) * TILE_SIZE, (tile.key.z() + .5f) * TILE_SIZE, 0);
                        sections.putInt(first).putInt(end - offset).putInt(lod).putInt(tile.available);
                        first += end - offset;
                    }
                }
            }
            packed.flip();
            sections.flip();
            facesBuffer.upload(packed, 0L);
            chunksBuffer.upload(sections, 0L);
        } finally {
            MemoryUtil.memFree(sections);
            MemoryUtil.memFree(packed);
        }
        uploadedFaces = faces;
        uploadedMedium = medium;
        uploadedFar = distant;
        uploadedStatus = statuses;
        uploadedStatusRevision = statusRevision;
    }

    private static byte findStatus(combatant.client.util.schematic.SchematicDiff diff, int voxel) {
        int low = 0, high = diff.cellCount() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int index = diff.schematicIndex(mid);
            if (index < voxel) low = mid + 1;
            else if (index > voxel) high = mid - 1;
            else return diff.status(mid);
        }
        return 0;
    }

    private static void packFace(ByteBuffer out, FaceList faces, VoxelVolume volume,
                                 SchematicTextures.Entry[] textures, byte status, int f) {
        boolean quad = faces.isQuad(f);
        int direction = faces.direction(f);
        float normalX = faces.normalOf(f, 0), normalY = faces.normalOf(f, 1), normalZ = faces.normalOf(f, 2);
        float spanU = quad ? 1.0f : faces.textureSpan(f, 0);
        float spanV = quad ? 1.0f : faces.textureSpan(f, 1);
        for (int c = 0; c < 4; c++) put4(out,
                faces.corner(f, c, 0), faces.corner(f, c, 1), faces.corner(f, c, 2),
                c == 0 ? normalX : c == 1 ? normalY : c == 2 ? normalZ : spanV);
        for (int c = 0; c < 4; c++) {
            float u = quad ? faces.quadUv(f, c, 0) : FaceList.textureUv(direction, c, 0) * spanU;
            float v = quad ? faces.quadUv(f, c, 1) : FaceList.textureUv(direction, c, 1) * spanV;
            out.putFloat(u).putFloat(v);
        }
        int palette = volume.cell(faces.voxel(f));
        SchematicTextures.Entry entry = palette >= 0 && palette < textures.length ? textures[palette] : null;
        int base = direction * SchematicTextures.LAYERS;
        boolean layer0 = !quad && entry != null && entry.has[base];
        boolean layer1 = !quad && entry != null && entry.has[base + 1];
        // Model quads already carry their own absolute sprite UVs.
        if (layer0) put4(out, entry.uv[base * 4], entry.uv[base * 4 + 1],
                entry.uv[base * 4 + 2], entry.uv[base * 4 + 3]);
        else put4(out, 0, 0, 0, 0);
        if (layer1) put4(out, entry.uv[(base + 1) * 4], entry.uv[(base + 1) * 4 + 1],
                entry.uv[(base + 1) * 4 + 2], entry.uv[(base + 1) * 4 + 3]);
        else put4(out, 0, 0, 0, 0);
        int color = faces.color(f), light = faces.light(f);
        if (status != 0) {
            color = statusTint(color, status);
            light = statusTint(light, status);
        }
        int light0 = quad ? SchematicTextures.multiply(light, faces.quadTint(f))
                : layer0 ? SchematicTextures.multiply(light, entry.tint[base]) : light;
        int light1 = layer1 ? SchematicTextures.multiply(light, entry.tint[base + 1]) : 0;
        int flags = (quad ? 1 : 0) | (layer0 || quad ? 2 : 0) | (layer1 ? 4 : 0)
                | (faces.cullBack(f) ? 8 : 0) | (!quad && direction >= FaceList.EAST ? 16 : 0);
        out.putInt(color).putInt(light0).putInt(light1).putInt(flags);
    }

    private static int statusTint(int color, byte status) {
        return switch (status) {
            case combatant.client.util.schematic.SchematicDiff.CORRECT -> preserveAlpha(
                    FaceMesher.mix(color, SchematicUi.GOOD, 0.35f), color);
            case combatant.client.util.schematic.SchematicDiff.WRONG -> preserveAlpha(
                    FaceMesher.mix(color, SchematicUi.BAD, 0.55f), color);
            default -> color;
        };
    }

    private static int preserveAlpha(int mixed, int base) {
        return mixed & 0x00FFFFFF | base & 0xFF000000;
    }

    private static void put4(ByteBuffer b, float a, float c, float d, float e) {
        b.putFloat(a).putFloat(c).putFloat(d).putFloat(e);
    }

    private void writeCamera(OrbitCamera camera, float x, float y, float w, float h,
                             FaceList faces, VoxelVolume volume, float quality, float drawDistanceScale) {
        camera.viewMatrix(view);
        camera.gpuProjection(x, y, w, h, projection);
        float focal = Math.min(w, h) * 0.5f / (float) Math.tan(OrbitCamera.FOV * 0.5f);
        // Distance LOD is always active; performance pressure only shifts the transitions.
        float lodNear = Math.max(96f, focal * 0.42f) * quality;
        float lodMedium = Math.max(lodNear * 2.4f, focal * 1.15f * quality);
        float radius = 0.5f * (float) Math.sqrt((double) volume.sizeX() * volume.sizeX()
                + (double) volume.sizeY() * volume.sizeY() + (double) volume.sizeZ() * volume.sizeZ());
        float maxDrawDistance = camera.freeFly()
                ? Math.max(512f, focal * 4.0f) * drawDistanceScale * (0.8f + 0.2f * quality)
                : Math.max(camera.distance() + radius * 1.5f, lodMedium * 2.0f * drawDistanceScale);
        float far = Math.max(8192.0f, Math.max(camera.targetDistance() * 16.0f,
                Math.max(maxDrawDistance + radius, faces.lodStep() * 64.0f)));
        float near = 0.05f;
        boolean zeroToOne = owner.capabilities().zeroToOneDepth();
        float a = zeroToOne ? -near / (far - near) : -(far + near) / (far - near);
        float b = zeroToOne ? near * far / (far - near) : 2.0f * near * far / (far - near);
        cameraData.clear();
        for (float value : view) cameraData.putFloat(value);
        for (float value : projection) cameraData.putFloat(value);
        put4(cameraData, a, b, near, far);
        put4(cameraData, camera.eyeX(), camera.eyeY(), camera.eyeZ(), (float) chunkCount);
        put4(cameraData, lodNear, lodMedium, maxDrawDistance, quality);
        cameraData.flip();
    }

    private void releaseGeometry() {
        if (facesBuffer != null) { facesBuffer.close(); facesBuffer = null; }
        if (chunksBuffer != null) { chunksBuffer.close(); chunksBuffer = null; }
        if (visibleBuffer != null) { visibleBuffer.close(); visibleBuffer = null; }
        if (commandBuffer != null) { commandBuffer.close(); commandBuffer = null; }
        uploadedFaces = uploadedMedium = uploadedFar = null;
        uploadedStatus = null;
        uploadedStatusRevision = Integer.MIN_VALUE;
        chunkCount = 0;
    }

    @Override
    public void close() {
        releaseGeometry();
        if (meshPipeline != null) { meshPipeline.close(); meshPipeline = null; }
        if (compactPipeline != null) { compactPipeline.close(); compactPipeline = null; }
        if (resetPipeline != null) { resetPipeline.close(); resetPipeline = null; }
        if (cameraBuffer != null) { cameraBuffer.close(); cameraBuffer = null; }
        if (unitQuad != null) { unitQuad.close(); unitQuad = null; }
        owner = null;
    }
}
