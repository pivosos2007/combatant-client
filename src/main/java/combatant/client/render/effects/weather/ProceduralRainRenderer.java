/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.effects.weather;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.postprocess.PostProcessBackendResourceOwner;
import combatant.client.render.engine.postprocess.PostProcessContext;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import net.minecraft.resources.Identifier;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.ProceduralRainUniforms;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;

/**
 * Compute-driven refracting 3D precipitation. A compute pass compacts three spatial
 * LODs into a visible-drop SSBO and writes a native indexed-indirect command. The CPU
 * submits no per-drop geometry, draw counts, or readbacks.
 *
 * Compute culls by weather/roof, range, density and projected frustum.
 * Sampled scene depth performs per-fragment visibility; the source color is frozen
 * before drawing, so refraction never forms a framebuffer feedback loop.
 *
 * A rolling 2-metre roof/biome map uses *loaded world heightmaps*, not vanilla
 * rainColumns. Its CPU-side height/exposure data can later feed splashes or wetness.
 */
public final class ProceduralRainRenderer implements PostProcessPass, PostProcessBackendResourceOwner {
    private static final int INSTANCE_COUNT = 40960 + 64000 + 51200;
    private static final int COMPUTE_GROUP_SIZE = 256;
    private static final Identifier RESET = Identifier.fromNamespaceAndPath("combatant", "rain_reset");
    private static final Identifier COMPACT = Identifier.fromNamespaceAndPath("combatant", "rain_compact");
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("combatant", "rain_instanced.vert");
    private static final Identifier FRAGMENT = Identifier.fromNamespaceAndPath("combatant", "rain_instanced.frag");
    private static final Std430StructLayout VISIBLE_LAYOUT = Std430StructLayout.builder()
            .member("center", Std430Type.VEC4).member("motion", Std430Type.VEC4).build();
    private static final Std430StructLayout DRAW_LAYOUT = Std430StructLayout.builder()
            .member("indexCount", Std430Type.UINT).member("instanceCount", Std430Type.UINT)
            .member("firstIndex", Std430Type.UINT).member("vertexOffset", Std430Type.UINT)
            .member("firstInstance", Std430Type.UINT).build();
    private static final ShaderResourceLayout RESET_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE)));
    private static final ShaderResourceLayout COMPACT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE)));
    private static final ShaderResourceLayout RASTER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)));

    private static final int ATLAS_SIZE = 128;
    private static final int ATLAS_MASK = ATLAS_SIZE - 1;
    private static final int ATLAS_CELL = 2;
    private static final int ATLAS_PIXELS = ATLAS_SIZE * ATLAS_SIZE;
    private static final float RESPONSE = 0.14f;
    private static final int ATLAS_ROWS_PER_REFRESH = 6;
    private static final int INITIAL_ROWS_PER_FRAME = 12;
    private static final int INITIAL_NEAR_RADIUS_CELLS = 14;
    private static final int REFRESH_TICKS = 10;

    private final byte[] exposurePixels = new byte[ATLAS_PIXELS * 4];
    private final short[] impactHeights = new short[ATLAS_PIXELS];
    private CombatantRhi owner;
    private PersistentGpuMesh quad;
    private RhiComputePipeline resetPipeline;
    private RhiComputePipeline compactPipeline;
    private RhiMeshPipeline rasterPipeline;
    private RhiStorageBuffer frameBuffer;
    private RhiStorageBuffer visibleBuffer;
    private RhiStorageBuffer indirectBuffer;
    private GpuFormat rasterTargetFormat;
    private Texture exposureTexture;
    private ClientLevel currentWorld;
    private int centerCellX = Integer.MIN_VALUE;
    private int centerCellZ = Integer.MIN_VALUE;
    private int refreshedAt = Integer.MIN_VALUE;
    private int rowCursor;
    private int initialFillRow = ATLAS_SIZE;
    private boolean healthy = true;
    private boolean initializedRain;
    private float smoothedRain;
    private long lastDrawFrame = Long.MIN_VALUE;

    public boolean healthy() {
        return healthy;
    }

    /** Vanilla weather is intercepted only after this renderer actually submitted a recent frame. */
    public boolean recentlySubmittedForWorld(ClientLevel world) {
        if (!healthy || world == null || world != currentWorld || lastDrawFrame == Long.MIN_VALUE) return false;
        long frame = CombatantRenderSystem.resources().frameId();
        return frame >= lastDrawFrame && frame - lastDrawFrame <= 1L;
    }

    public void resetSmoothing() {
        smoothedRain = 0.0f;
        initializedRain = false;
    }

    public void reset() {
        resetSmoothing();
        currentWorld = null;
        centerCellX = Integer.MIN_VALUE;
        centerCellZ = Integer.MIN_VALUE;
        refreshedAt = Integer.MIN_VALUE;
        healthy = true;
        lastDrawFrame = Long.MIN_VALUE;
        release(owner);
    }

    @Override
    public int getPriority() {
        // Submit before TAA (-100) so small drops can accumulate temporally, and before DoF.
        return -110;
    }

    @Override
    public Phase getPhase() { return Phase.PRE_HAND; }

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (!ReimaginedVisual.isProceduralRainEnabledStatic()) {
            if (DebugLog.isRenderThreadDebugEnabled()) {
                DebugLog.renderThreadOnChange("reimaginedvisual.gpu_rain.status", "not selected or disabled",
                        "[ProceduralRain] inactive (effect unselected, module disabled, or renderer unhealthy)");
            }
            resetSmoothing();
            return false;
        }
        String reason;
        if (!healthy) reason = "renderer unhealthy";
        else if (mc == null || mc.level == null || mc.player == null) reason = "no active world/player";
        else if (!CombatantWorldMatrices.isValid()) reason = "world matrices not captured";
        else if (mc.level.getRainLevel(0.0f) <= 0.001f) reason = "rain intensity zero";
        else reason = "ready";
        // No per-frame messages: status changes only, and only with render-thread debug.
        if (DebugLog.isRenderThreadDebugEnabled()) {
            DebugLog.renderThreadOnChange("reimaginedvisual.gpu_rain.status", reason,
                    "[ProceduralRain] status={} selected={} backendHealthy={}",
                    reason, ReimaginedVisual.isProceduralRainEnabledStatic(), healthy);
        }
        if (!"ready".equals(reason)) {
            resetSmoothing();
            return false;
        }
        return true;
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (execution == null || execution.context() == null || execution.rhi() == null
                || execution.source() == null || execution.destination() == null) {
            DebugLog.renderThreadOnce("reimaginedvisual.gpu_rain.invalid_context",
                    "[ProceduralRain] pass invoked without a complete postprocess execution context");
            return false;
        }
        if (!isActive()) return false;
        PostProcessContext ctx = execution.context();
        if (ctx.mainDepth() == null) {
            // A missing depth capture makes scene refraction/occlusion unsafe.
            // Stop intercepting vanilla rain on subsequent extraction instead of drawing through walls.
            healthy = false;
            DebugLog.errorOnce("reimaginedvisual.gpu_rain.depth_unavailable",
                    "[ProceduralRain] Missing mainDepth at PRE_HAND: refractive rain disabled; vanilla weather restored");
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = CombatantWorldMatrices.cameraPosition();
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        ReimaginedVisual settings = Modules.get(ReimaginedVisual.class);
        if (camera == null || view == null || projection == null || settings == null) {
            DebugLog.renderThreadOnce("reimaginedvisual.gpu_rain.missing_camera",
                    "[ProceduralRain] no draw: camera={} view={} projection={} settings={}",
                    camera != null, view != null, projection != null, settings != null);
            return false;
        }

        try {
            CombatantRhi rhi = execution.rhi();
            ensureResources(rhi, execution.destination().texture().getFormat());
            if (currentWorld != mc.level) {
                currentWorld = mc.level;
                centerCellX = Integer.MIN_VALUE;
                centerCellZ = Integer.MIN_VALUE;
                refreshedAt = Integer.MIN_VALUE;
                resetSmoothing();
                lastDrawFrame = Long.MIN_VALUE;
            }
            float target = Mth.clamp(mc.level.getRainLevel(ctx.tickDelta()), 0.0f, 1.0f);
            if (!initializedRain) {
                smoothedRain = target;
                initializedRain = true;
            }
            float dt = Mth.clamp(mc.getDeltaTracker().getRealtimeDeltaTicks(), 0.0f, 2.0f);
            smoothedRain += (target - smoothedRain) * (1.0f - (float) Math.exp(-RESPONSE * dt));
            if (smoothedRain <= 0.003f) return false;
            updateExposure(mc.level, camera);

            float thunder = Mth.clamp(mc.level.getThunderLevel(ctx.tickDelta()), 0.0f, 1.0f);
            double now = (mc.level.getGameTime() + (double) ctx.tickDelta()) * 0.05;
            float windX = (float) ((Math.sin(now * 0.1065) + Math.sin(now * 0.0345 + 2.0) * 0.4)
                    * (0.6 + thunder * 1.3));
            float windZ = (float) ((Math.cos(now * 0.0795 + 1.7) + Math.sin(now * 0.0465) * 0.35)
                    * (0.6 + thunder * 1.3));
            Matrix4f cameraViewProjection = projection.mul(view, new Matrix4f());
            ProceduralRainUniforms.update(cameraViewProjection, camera, (float) now,
                    smoothedRain, settings.getProceduralRainDensity(), settings.getProceduralRainDistance(),
                    windX, windZ, thunder, ctx.width(), ctx.height(), rhi.capabilities().zeroToOneDepth(),
                    (int) mc.level.getGameTime());
            AdvancedShaderBackend advanced = rhi.advancedShaders();
            frameBuffer.upload(ProceduralRainUniforms.computeParameters(), 0L);
            StorageBinding frame = new StorageBinding(3, frameBuffer, 0L, 0L, StorageAccess.READ_ONLY);
            StorageBinding visibleWrite = new StorageBinding(4, visibleBuffer, 0L, 0L, StorageAccess.WRITE_ONLY);
            StorageBinding visibleRead = new StorageBinding(4, visibleBuffer, 0L, 0L, StorageAccess.READ_ONLY);
            StorageBinding counter = new StorageBinding(5, indirectBuffer, 0L, 0L, StorageAccess.READ_WRITE);

            // Reset and compaction are separate GPU dispatches: workgroup scheduling cannot
            // guarantee a single-workgroup reset happens before atomics in the same dispatch.
            advanced.dispatch(new ComputeDispatchCommand("Rain indirect reset", resetPipeline, 1, 1, 1,
                    List.of(counter), List.of(), List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.READ_WRITE, List.of(indirectBuffer), List.of()));
            int distance = settings.getProceduralRainDistance();
            int candidates = distance <= 25 ? 40960 : distance <= 71 ? 104960 : INSTANCE_COUNT;
            advanced.dispatch(new ComputeDispatchCommand("Rain GPU visibility and compaction", compactPipeline,
                    (candidates + COMPUTE_GROUP_SIZE - 1) / COMPUTE_GROUP_SIZE, 1, 1,
                    List.of(frame, visibleWrite, counter),
                    List.of(new SampledTextureBinding(0, exposureTexture.getTextureView(), exposureTexture.getSampler())),
                    List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.GRAPHICS,
                    RhiResourceBarrier.Access.READ, List.of(visibleBuffer), List.of()));
            advanced.barrier(new RhiResourceBarrier(RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE, RhiResourceBarrier.Stage.INDIRECT,
                    RhiResourceBarrier.Access.READ, List.of(indirectBuffer), List.of()));

            // Sample the immutable source color while drawing into the separate destination.
            PostProcessManager.copy(execution.source(), execution.destination());
            advanced.drawMeshIndirect(new MeshIndirectDrawCommand("Compute-driven refractive rain",
                    rasterPipeline, execution.destination(), quad.handle(), indirectBuffer, 0L,
                    List.of(frame, visibleRead), List.of(
                        new SampledTextureBinding(1, execution.source(), PostProcessManager.getSampler()),
                        new SampledTextureBinding(2, ctx.mainDepth(), PostProcessManager.getSampler()))));
            lastDrawFrame = CombatantRenderSystem.resources().frameId();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            healthy = false;
            release(owner);
            DebugLog.errorOnce("reimaginedvisual.gpu_rain.failed",
                    "[ProceduralRain] GPU pipeline failed; vanilla rain restored next weather extraction", failure);
            return false;
        }
    }

    private void ensureResources(CombatantRhi rhi, GpuFormat destinationFormat) {
        if (owner != rhi) {
            release(owner);
            owner = rhi;
        }
        if (quad == null || quad.isClosed()) {
            try (MeshBuilder mesh = new MeshBuilder(DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLES, 4, 6)) {
                mesh.beginLocal();
                int a = mesh.vec3(-1.0, -1.0, 0.0).next();
                int b = mesh.vec3(1.0, -1.0, 0.0).next();
                int c = mesh.vec3(1.0, 1.0, 0.0).next();
                int d = mesh.vec3(-1.0, 1.0, 0.0).next();
                mesh.quad(a, b, c, d);
                mesh.end();
                quad = rhi.persistentMeshes().upload("Combatant Rain Instanced Quad", mesh);
            }
        }
        if (exposureTexture == null) {
            exposureTexture = new Texture(ATLAS_SIZE, ATLAS_SIZE, GpuFormat.RGBA8_UNORM,
                    FilterMode.NEAREST, FilterMode.NEAREST, AddressMode.CLAMP_TO_EDGE);
            centerCellX = Integer.MIN_VALUE;
            centerCellZ = Integer.MIN_VALUE;
        }
        AdvancedShaderBackend advanced = rhi.advancedShaders();
        if (!advanced.supports(RhiShaderStage.COMPUTE))
            throw new UnsupportedOperationException("Rain needs native compute and GPU storage buffers");
        if (frameBuffer == null) frameBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Rain frame parameters", ProceduralRainUniforms.COMPUTE_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        if (visibleBuffer == null) visibleBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Rain compacted visible drops", VISIBLE_LAYOUT, INSTANCE_COUNT, StorageAccess.READ_WRITE, false, true));
        if (indirectBuffer == null) indirectBuffer = advanced.createStorageBuffer(new StorageBufferDescriptor(
                "Rain DrawIndexedIndirect", DRAW_LAYOUT, 1, StorageAccess.READ_WRITE, true, true));
        if (resetPipeline == null) resetPipeline = advanced.createComputePipeline(
                new ComputePipelineDescriptor("Rain reset indirect args", RESET, RESET_LAYOUT));
        if (compactPipeline == null) compactPipeline = advanced.createComputePipeline(
                new ComputePipelineDescriptor("Rain compact visible", COMPACT, COMPACT_LAYOUT));
        if (rasterPipeline != null && rasterTargetFormat != destinationFormat) {
            rasterPipeline.close();
            rasterPipeline = null;
        }
        if (rasterPipeline == null) {
            rasterPipeline = advanced.createMeshPipeline(new MeshPipelineDescriptor(
                "Rain native refractive triangle renderer", VERTEX, FRAGMENT,
                VertexLayoutSpec.of("rain-quad", DefaultVertexFormat.POSITION, PrimitiveTopology.TRIANGLES),
                RASTER_LAYOUT, AdvancedBlendMode.ALPHA, AdvancedDepthMode.DISABLED,
                AdvancedCullMode.NONE, List.of(destinationFormat), null, 1));
            rasterTargetFormat = destinationFormat;
        }
    }

    private void updateExposure(ClientLevel level, Vec3 camera) {
        int centerX = Mth.floor(camera.x / ATLAS_CELL);
        int centerZ = Mth.floor(camera.z / ATLAS_CELL);
        int tick = (int) level.getGameTime();
        boolean changed = false;
        if (centerCellX == Integer.MIN_VALUE || Math.abs(centerX - centerCellX) >= ATLAS_SIZE
                || Math.abs(centerZ - centerCellZ) >= ATLAS_SIZE) {
            centerCellX = centerX;
            centerCellZ = centerZ;
            // Do not query 16k heightmap entries in one render frame when the world
            // changes. Seed the visible near field now; stream the distant envelope
            // in small strips over subsequent frames (unseeded cells reject rain).
            Arrays.fill(exposurePixels, (byte) 0);
            Arrays.fill(impactHeights, Short.MAX_VALUE);
            for (int dz = -INITIAL_NEAR_RADIUS_CELLS; dz <= INITIAL_NEAR_RADIUS_CELLS; dz++) {
                for (int dx = -INITIAL_NEAR_RADIUS_CELLS; dx <= INITIAL_NEAR_RADIUS_CELLS; dx++) {
                    sampleColumn(level, centerX + dx, centerZ + dz);
                }
            }
            initialFillRow = 0;
            rowCursor = 0;
            changed = true;
        } else {
            // Retain 2D toroidal cells. Fetch only strips revealed by camera movement.
            if (centerX != centerCellX) {
                int direction = Integer.signum(centerX - centerCellX);
                for (int x = centerCellX + direction; direction > 0 ? x <= centerX : x >= centerX; x += direction) {
                    int uncoveredX = direction > 0 ? x + ATLAS_SIZE / 2 - 1 : x - ATLAS_SIZE / 2;
                    for (int dz = -ATLAS_SIZE / 2; dz < ATLAS_SIZE / 2; dz++) {
                        sampleColumn(level, uncoveredX, centerZ + dz);
                    }
                }
                changed = true;
            }
            if (centerZ != centerCellZ) {
                int direction = Integer.signum(centerZ - centerCellZ);
                for (int z = centerCellZ + direction; direction > 0 ? z <= centerZ : z >= centerZ; z += direction) {
                    int uncoveredZ = direction > 0 ? z + ATLAS_SIZE / 2 - 1 : z - ATLAS_SIZE / 2;
                    for (int dx = -ATLAS_SIZE / 2; dx < ATLAS_SIZE / 2; dx++) {
                        sampleColumn(level, centerX + dx, uncoveredZ);
                    }
                }
                changed = true;
            }
        }
        centerCellX = centerX;
        centerCellZ = centerZ;
        // Amortized initial fill. This also avoids a one-frame spike when changing dimensions.
        if (initialFillRow < ATLAS_SIZE) {
            int end = Math.min(ATLAS_SIZE, initialFillRow + INITIAL_ROWS_PER_FRAME);
            for (int row = initialFillRow; row < end; row++) {
                int worldRow = centerZ - ATLAS_SIZE / 2 + row;
                for (int dx = -ATLAS_SIZE / 2; dx < ATLAS_SIZE / 2; dx++) {
                    sampleColumn(level, centerX + dx, worldRow);
                }
            }
            initialFillRow = end;
            changed = true;
        }
        // Loaded chunks, changed blocks/biomes and snow lines need eventual refresh even when stationary.
        if (refreshedAt == Integer.MIN_VALUE || tick - refreshedAt < 0
                || tick - refreshedAt >= REFRESH_TICKS) {
            for (int row = 0; row < ATLAS_ROWS_PER_REFRESH; row++) {
                int worldRow = centerZ - ATLAS_SIZE / 2 + ((rowCursor + row) & ATLAS_MASK);
                for (int dx = -ATLAS_SIZE / 2; dx < ATLAS_SIZE / 2; dx++) {
                    sampleColumn(level, centerX + dx, worldRow);
                }
            }
            rowCursor = (rowCursor + ATLAS_ROWS_PER_REFRESH) & ATLAS_MASK;
            refreshedAt = tick;
            changed = true;
        }
        if (changed) exposureTexture.upload(exposurePixels);
    }

    private void sampleColumn(ClientLevel level, int tileX, int tileZ) {
        int index = (tileX & ATLAS_MASK) + ((tileZ & ATLAS_MASK) << 7);
        int pixel = index * 4;
        int worldX = tileX * ATLAS_CELL;
        int worldZ = tileZ * ATLAS_CELL;
        if (!level.getChunkSource().hasChunk(worldX >> 4, worldZ >> 4)) {
            impactHeights[index] = Short.MAX_VALUE;
            Arrays.fill(exposurePixels, pixel, pixel + 4, (byte) 0);
            return;
        }
        // Max of four loaded block columns prevents leakage underneath narrow roof edges.
        int maxY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
        if (Math.abs(tileX - centerCellX) <= 18 && Math.abs(tileZ - centerCellZ) <= 18) {
            maxY = Math.max(maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX + 1, worldZ));
            maxY = Math.max(maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ + 1));
            maxY = Math.max(maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX + 1, worldZ + 1));
        }
        boolean raining = level.getPrecipitationAt(new BlockPos(worldX, maxY, worldZ))
                == Biome.Precipitation.RAIN;
        impactHeights[index] = (short) maxY;
        int encoded = Mth.clamp(maxY + 64, 0, 65535);
        exposurePixels[pixel] = (byte) (encoded & 255);
        exposurePixels[pixel + 1] = (byte) ((encoded >>> 8) & 255);
        exposurePixels[pixel + 2] = raining ? (byte) 255 : 0;
        exposurePixels[pixel + 3] = (byte) 255;
    }

    /** Exposes the same surface envelope for future impact and accumulation passes. */
    public int sampledSurfaceHeight(int worldX, int worldZ) {
        if (centerCellX == Integer.MIN_VALUE) return Integer.MAX_VALUE;
        int tileX = Math.floorDiv(worldX, ATLAS_CELL);
        int tileZ = Math.floorDiv(worldZ, ATLAS_CELL);
        if (Math.abs(tileX - centerCellX) >= ATLAS_SIZE / 2
                || Math.abs(tileZ - centerCellZ) >= ATLAS_SIZE / 2) return Integer.MAX_VALUE;
        return impactHeights[(tileX & ATLAS_MASK) + ((tileZ & ATLAS_MASK) << 7)];
    }

    @Override
    public void releaseBackendResources(CombatantRhi rhi) {
        if (owner == rhi) release(rhi);
    }

    private void release(CombatantRhi unused) {
        lastDrawFrame = Long.MIN_VALUE;
        if (rasterPipeline != null) { rasterPipeline.close(); rasterPipeline = null; }
        rasterTargetFormat = null;
        if (compactPipeline != null) { compactPipeline.close(); compactPipeline = null; }
        if (resetPipeline != null) { resetPipeline.close(); resetPipeline = null; }
        if (indirectBuffer != null) { indirectBuffer.close(); indirectBuffer = null; }
        if (visibleBuffer != null) { visibleBuffer.close(); visibleBuffer = null; }
        if (frameBuffer != null) { frameBuffer.close(); frameBuffer = null; }
        if (quad != null) {
            quad.close();
            quad = null;
        }
        if (exposureTexture != null) {
            exposureTexture.close();
            exposureTexture = null;
        }
        owner = null;
        centerCellX = Integer.MIN_VALUE;
        centerCellZ = Integer.MIN_VALUE;
        initialFillRow = ATLAS_SIZE;
    }
}
