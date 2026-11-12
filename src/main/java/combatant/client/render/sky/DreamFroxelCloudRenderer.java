/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.pipeline.EffectRenderPipelines;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * A view-frustum froxel renderer, deliberately independent of the deferred rendering pipeline.
 *
 * Pass 1 computes coarse volumetric lighting into a small RGBA16F 3D cache.
 * Passes 2-3 raymarch the actual density for near and far/cirrus at reduced resolution.
 * Pass 4 reprojects and reconstructs subpixel-jittered clouds into two full-resolution
 * ping-pong history images, with representative cloud distance for disocclusion.
 * A fullscreen draw composites the resolved image before vanilla translucency.
 * Only active with compute, SSBO, image-load/store and descriptor-supported sampled 3D volumes.
 */
public final class DreamFroxelCloudRenderer implements AutoCloseable {
    private static final Identifier DENSITY_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_cloud_density");
    private static final Identifier INTEGRATE_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_cloud_integrate");
    private static final Identifier MERGE_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_cloud_merge");
    private static final Identifier TEMPORAL_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_cloud_temporal");
    private static final Identifier NOISE_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_cloud_noise");
    private static final Identifier FAR_SHADER = Identifier.fromNamespaceAndPath("combatant", "dream_far_clouds");
    private static final GpuFormat NOISE_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int NOISE_SIZE = 96;
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int FROXEL_LOCAL_X = 4;
    private static final int FROXEL_LOCAL_Y = 4;
    private static final int FROXEL_LOCAL_Z = 4;
    private static final int INTEGRATE_LOCAL_SIZE = 8;
    private static final float MAX_RANGE = 640.0f;
    private static final float START_RANGE = 4.0f;

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("camera", Std430Type.VEC4)
            .member("grid", Std430Type.VEC4)
            .member("output", Std430Type.VEC4)
            .member("shape", Std430Type.VEC4)
            .member("appearance", Std430Type.VEC4)
            .member("light", Std430Type.VEC4)
            .member("wind", Std430Type.VEC4)
            .member("sampling", Std430Type.VEC4)
            .member("cloudLightColor", Std430Type.VEC4)
            .member("cloudShadowColor", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout TEMPORAL_PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("dimensions", Std430Type.VEC4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("windDelta", Std430Type.VEC4)
            .member("layers", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout MERGE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, FORMAT),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.R32_FLOAT)
    ));
    private static final ShaderResourceLayout TEMPORAL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, FORMAT),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.R32_FLOAT),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout NOISE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY, NOISE_FORMAT)
    ));
    private static final ShaderResourceLayout FAR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, FORMAT),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY, NOISE_FORMAT),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.R32_FLOAT),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout DENSITY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY, FORMAT),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY, NOISE_FORMAT),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout INTEGRATION_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY, FORMAT),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, FORMAT),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY, NOISE_FORMAT),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY, GpuFormat.R32_FLOAT),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private final DreamCloudWind wind = new DreamCloudWind();
    private CombatantRhi owner;
    private RhiComputePipeline densityPipeline;
    private RhiComputePipeline integrationPipeline;
    private RhiComputePipeline noisePipeline;
    private RhiComputePipeline farPipeline;
    private RhiComputePipeline temporalPipeline;
    private RhiComputePipeline mergePipeline;
    private RhiStorageVolume noiseVolume;
    private RhiStorageImage farClouds;
    private RhiStorageImage nearDistance;
    private RhiStorageImage mergedClouds;
    private RhiStorageImage mergedDistance;
    private RhiStorageImage farDistance;
    private RhiStorageImage historyColorA;
    private RhiStorageImage historyColorB;
    private RhiStorageImage historyDistanceA;
    private RhiStorageImage historyDistanceB;
    private RhiStorageBuffer temporalParameters;
    private boolean historyAIsRead = true;
    private boolean historyReady;
    private int historyW;
    private int historyH;
    private long frameCount;
    private Matrix4f previousView;
    private Matrix4f previousProjection;
    private Vec3 previousCamera;
    private Object previousLevel;
    private double previousTick = Double.NaN; // monotonic client seconds, not world ticks
    private float previousLowWindX, previousLowWindZ, previousHighWindX, previousHighWindZ;
    private int previousSettingsSignature;
    private ReimaginedVisual.DreamCloudQuality previousQuality;

    private boolean noiseReady;
    private int farW;
    private int farH;
    private RhiStorageVolume froxels;
    private RhiStorageImage integratedClouds;
    private RhiStorageBuffer parameters;
    private int froxelW;
    private int froxelH;
    private int froxelD;
    private int imageW;
    private int imageH;
    private boolean failed;

    public void render(RenderTarget framebuffer, ReimaginedVisual visual, SkyRenderState sky) {
        if (framebuffer == null || framebuffer.getColorTextureView() == null || visual == null
                || sky == null || !visual.areDreamCloudsEnabled() || IrisRuntime.isModLoaded()) {
            historyReady = false;
            return;
        }
        GpuTextureView depth = PreTranslucentDepth.getCapturedDepthView();
        // Never draw clouds over terrain without valid opaque depth (including MSAA resolve).
        if (depth == null || !CombatantWorldMatrices.isValid()) {
            historyReady = false;
            return;
        }
        Vec3 pos = CombatantWorldMatrices.cameraPosition();
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (pos == null || view == null || projection == null) {
            historyReady = false;
            return;
        }
        float height = visual.dreamCloudHeight();
        float thickness = visual.dreamCloudThickness();
        // Conservative altitude cull must include all active layers, not just
        // the 640-block froxel field. Otherwise high cirrus vanish above it.
        float highestLayer = height + thickness * 4.0f + 650.0f
                + 85.0f * Math.max(.4f, visual.dreamCloudScale());
        float reach = visual.areDreamCirrusEnabled() ? 15000.0f
                : visual.areDreamFarCloudsEnabled() ? 9500.0f : MAX_RANGE;
        if (pos.y > highestLayer + reach || pos.y < height - thickness * .55f - reach) {
            historyReady = false;
            return;
        }

        CombatantRhi rhi = CombatantRenderSystem.rhi();
        if (owner != rhi) {
            closeOwned();
            owner = rhi;
        }
        if (failed || !supported(rhi)) return;

        ReimaginedVisual.DreamCloudQuality quality = visual.dreamCloudQuality();
        // Froxel lighting is intentionally coarse. Independent half/quarter-res
        // raymarches carry the silhouette; temporal reconstruction restores
        // full-resolution detail while limiting 3D sampling and VRAM.
        int stride = quality.pixelStride();
        int slices = quality.slices();
        int gridW = ceilDiv(Math.max(1, framebuffer.width), stride);
        int gridH = ceilDiv(Math.max(1, framebuffer.height), stride);
        long maxCells = switch (quality) {
            case LOW -> 300_000L;
            case MEDIUM -> 650_000L;
            case HIGH -> 1_250_000L;
            case ULTRA -> 2_200_000L;
        };
        while ((long) gridW * gridH * slices > maxCells) {
            stride++;
            gridW = ceilDiv(Math.max(1, framebuffer.width), stride);
            gridH = ceilDiv(Math.max(1, framebuffer.height), stride);
        }
        int outW = ceilDiv(Math.max(1, framebuffer.width), quality.integrationStride());
        int outH = ceilDiv(Math.max(1, framebuffer.height), quality.integrationStride());
        int farStride = switch (quality) {
            case LOW -> 5;
            case MEDIUM -> 4;
            case HIGH -> 3;
            case ULTRA -> 2;
        };
        int fw = ceilDiv(Math.max(1, framebuffer.width), farStride);
        int fh = ceilDiv(Math.max(1, framebuffer.height), farStride);
        int farSteps = switch (quality) {
            case LOW -> 24;
            case MEDIUM -> 30;
            case HIGH -> 40;
            case ULTRA -> 52;
        };

        try {
            ensureResources(gridW, gridH, slices, outW, outH, fw, fh, framebuffer.width, framebuffer.height);
            // Camera origin is reduced modulo the repeating cloud domain to preserve precision
            // for worlds millions of blocks away from the origin.
            // All noise wavelengths are divisors of 8192 * scale; rebase at an
            // exact common period so the world-space density does not pop at wrap.
            double repeatPeriod = 8192.0 * Math.max(.4f, visual.dreamCloudScale());
            float camX = (float) (pos.x % repeatPeriod);
            float camZ = (float) (pos.z % repeatPeriod);
            float night = DreamSkyLighting.nightFactor(sky);
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float rain = Minecraft.getInstance().level.getRainLevel(partial);
            float thunder = Minecraft.getInstance().level.getThunderLevel(partial);
            DreamSkyPalette palette = visual.dreamSkyPalette();
            double tick = DreamSkyClock.seconds();
            wind.update(Minecraft.getInstance().level, tick);
            float currentLowWindX = wind.lowX(repeatPeriod);
            float currentLowWindZ = wind.lowZ(repeatPeriod);
            float currentHighWindX = wind.highX(repeatPeriod);
            float currentHighWindZ = wind.highZ(repeatPeriod);
            int settingsSignature = java.util.Objects.hash(quality, height, thickness,
                    visual.dreamCloudCoverage(), visual.dreamCloudScale(), visual.dreamCloudDensity(),
                    visual.areDreamFarCloudsEnabled(), visual.areDreamCirrusEnabled());
            boolean canReproject = historyReady && previousLevel == Minecraft.getInstance().level
                    && previousCamera != null && previousView != null && previousProjection != null
                    && previousQuality == quality && previousSettingsSignature == settingsSignature
                    && Double.isFinite(previousTick) && tick >= previousTick && tick - previousTick < 0.50
                    && pos.distanceToSqr(previousCamera) < 4096.0;
            Std430Writer uniforms = new Std430Writer(PARAMS_LAYOUT, 1)
                    .putMat4(0, "inverseProjection", new Matrix4f(projection).invert())
                    .putMat4(0, "inverseView", new Matrix4f(view).invert())
                    .putVec4(0, "camera", camX, (float) pos.y, camZ, (float) (tick % 20000.0))
                    .putVec4(0, "grid", gridW, gridH, slices, START_RANGE)
                    .putVec4(0, "output", outW, outH, MAX_RANGE, farSteps)
                    .putVec4(0, "shape", height, thickness, visual.dreamCloudCoverage(), visual.dreamCloudScale())
                    .putVec4(0, "appearance", visual.dreamCloudDensity(), night, rain,
                            visual.areDreamFarCloudsEnabled() ? 1f : 0f)
                    .putVec4(0, "light", sky.sunAngle, DreamSkyLayout.cloudX(), DreamSkyLayout.cloudZ(),
                            visual.areDreamCirrusEnabled() ? 1f : 0f)
                    .putVec4(0, "wind", currentLowWindX, currentLowWindZ,
                            currentHighWindX, currentHighWindZ)
                    .putVec4(0, "sampling", switch (quality) {
                        case LOW -> 36f;
                        case MEDIUM -> 44f;
                        case HIGH -> 56f;
                        case ULTRA -> 72f;
                    }, (float) (frameCount % 16), thunder, 0f)
                    .putVec4(0, "cloudLightColor", DreamSkyPalette.linearRed(palette.cloudLight()),
                            DreamSkyPalette.linearGreen(palette.cloudLight()), DreamSkyPalette.linearBlue(palette.cloudLight()), 1f)
                    .putVec4(0, "cloudShadowColor", DreamSkyPalette.linearRed(palette.cloudShadow()),
                            DreamSkyPalette.linearGreen(palette.cloudShadow()), DreamSkyPalette.linearBlue(palette.cloudShadow()), 1f);
            parameters.upload(uniforms.buffer(), 0L);
            var advanced = rhi.advancedShaders();
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            GpuSampler repeat = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            if (!noiseReady) {
                advanced.dispatch(new ComputeDispatchCommand(
                        "Dreamcore tileable Perlin-Worley atlas", noisePipeline,
                        ceilDiv(NOISE_SIZE, 4), ceilDiv(NOISE_SIZE, 4), ceilDiv(NOISE_SIZE, 4),
                        List.of(), List.of(), List.of(),
                        List.of(new StorageVolumeBinding(0, noiseVolume, StorageAccess.WRITE_ONLY)), List.of()));
                advanced.barrier(new RhiResourceBarrier(
                        RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                        RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                        List.of(), List.of(), List.of(noiseVolume)));
                noiseReady = true;
            }

            // The depth snapshot may have been produced by a copy/resolve command.
            advanced.barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of()));
            advanced.dispatch(new ComputeDispatchCommand(
                    "Dreamcore cloud froxel occupancy and lighting", densityPipeline,
                    ceilDiv(gridW, FROXEL_LOCAL_X), ceilDiv(gridH, FROXEL_LOCAL_Y), ceilDiv(slices, FROXEL_LOCAL_Z),
                    List.of(new StorageBinding(7, parameters, 0L, uniforms.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(), List.of(),
                    List.of(new StorageVolumeBinding(0, froxels, StorageAccess.WRITE_ONLY)),
                    List.of(new SampledVolumeBinding(1, noiseVolume, repeat))
            ));
            advanced.barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(), List.of(froxels)));
            advanced.dispatch(new ComputeDispatchCommand(
                    "Dreamcore cloud optical integration", integrationPipeline,
                    ceilDiv(outW, INTEGRATE_LOCAL_SIZE), ceilDiv(outH, INTEGRATE_LOCAL_SIZE), 1,
                    List.of(new StorageBinding(7, parameters, 0L, uniforms.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(1, depth, nearest)),
                    List.of(new StorageImageBinding(2, integratedClouds, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(4, nearDistance, StorageAccess.WRITE_ONLY)),
                    List.of(), List.of(new SampledVolumeBinding(0, froxels, linear),
                            new SampledVolumeBinding(3, noiseVolume, repeat))
            ));
            advanced.dispatch(new ComputeDispatchCommand(
                    "Dreamcore distant 3D clouds and high cirrus", farPipeline,
                    ceilDiv(fw, INTEGRATE_LOCAL_SIZE), ceilDiv(fh, INTEGRATE_LOCAL_SIZE), 1,
                    List.of(new StorageBinding(7, parameters, 0L, uniforms.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(2, depth, nearest)),
                    List.of(new StorageImageBinding(0, farClouds, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(3, farDistance, StorageAccess.WRITE_ONLY)),
                    List.of(), List.of(new SampledVolumeBinding(1, noiseVolume, repeat))));
            advanced.barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(integratedClouds, farClouds, nearDistance, farDistance)));
            advanced.dispatch(new ComputeDispatchCommand(
                    "Dreamcore merge near and far optical volumes", mergePipeline,
                    ceilDiv(outW, INTEGRATE_LOCAL_SIZE), ceilDiv(outH, INTEGRATE_LOCAL_SIZE), 1,
                    List.of(),
                    List.of(new SampledTextureBinding(0, integratedClouds.view(), linear),
                            new SampledTextureBinding(1, farClouds.view(), linear),
                            new SampledTextureBinding(2, nearDistance.view(), linear),
                            new SampledTextureBinding(3, farDistance.view(), linear),
                            new SampledTextureBinding(4, depth, nearest)),
                    List.of(new StorageImageBinding(5, mergedClouds, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(6, mergedDistance, StorageAccess.WRITE_ONLY))));
            advanced.barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(mergedClouds, mergedDistance)));
            RhiStorageImage readColor = historyAIsRead ? historyColorA : historyColorB;
            RhiStorageImage writeColor = historyAIsRead ? historyColorB : historyColorA;
            RhiStorageImage readDistance = historyAIsRead ? historyDistanceA : historyDistanceB;
            RhiStorageImage writeDistance = historyAIsRead ? historyDistanceB : historyDistanceA;
            Vec3 delta = canReproject ? pos.subtract(previousCamera) : Vec3.ZERO;
            Std430Writer temporal = new Std430Writer(TEMPORAL_PARAMS_LAYOUT, 1)
                    .putMat4(0, "inverseProjection", new Matrix4f(projection).invert())
                    .putMat4(0, "inverseView", new Matrix4f(view).invert())
                    .putMat4(0, "previousView", canReproject ? previousView : view)
                    .putMat4(0, "previousProjection", canReproject ? previousProjection : projection)
                    .putVec4(0, "dimensions", framebuffer.width, framebuffer.height, canReproject ? 1f : 0f,
                            (float) (frameCount % 16))
                    .putVec4(0, "cameraDelta", (float) delta.x, (float) delta.y, (float) delta.z,
                            (float) pos.y)
                    .putVec4(0, "windDelta", canReproject ? currentLowWindX - previousLowWindX : 0f,
                            canReproject ? currentLowWindZ - previousLowWindZ : 0f,
                            canReproject ? currentHighWindX - previousHighWindX : 0f,
                            canReproject ? currentHighWindZ - previousHighWindZ : 0f)
                    .putVec4(0, "layers", height, thickness, visual.dreamCloudScale(),
                            visual.areDreamCirrusEnabled() ? 1f : 0f);
            temporalParameters.upload(temporal.buffer(), 0L);
            advanced.dispatch(new ComputeDispatchCommand(
                    "Dreamcore temporal volumetric reconstruction", temporalPipeline,
                    ceilDiv(framebuffer.width, INTEGRATE_LOCAL_SIZE),
                    ceilDiv(framebuffer.height, INTEGRATE_LOCAL_SIZE), 1,
                    List.of(new StorageBinding(7, temporalParameters, 0L, temporal.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(0, mergedClouds.view(), linear),
                            new SampledTextureBinding(1, mergedDistance.view(), linear),
                            new SampledTextureBinding(2, readColor.view(), linear),
                            new SampledTextureBinding(3, readDistance.view(), linear),
                            new SampledTextureBinding(4, depth, nearest)),
                    List.of(new StorageImageBinding(5, writeColor, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(6, writeDistance, StorageAccess.WRITE_ONLY))));
            advanced.barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.GRAPHICS, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(writeColor, writeDistance)));
            FullScreenRenderer.begin("Dreamcore Clouds Before Translucency")
                    .attachment(framebuffer)
                    .pipeline(framebuffer.getColorTextureView().texture().getFormat() == GpuFormat.RGBA16_FLOAT
                            ? EffectRenderPipelines.DREAM_CLOUD_COMPOSITE_HDR
                            : EffectRenderPipelines.DREAM_CLOUD_COMPOSITE)
                    .sampler("u_Clouds", writeColor.view(), linear)
                    .end();
            historyAIsRead = !historyAIsRead;
            historyReady = true;
            previousView = new Matrix4f(view);
            previousProjection = new Matrix4f(projection);
            previousCamera = pos;
            previousLevel = Minecraft.getInstance().level;
            previousTick = tick;
            previousLowWindX = currentLowWindX;
            previousLowWindZ = currentLowWindZ;
            previousHighWindX = currentHighWindX;
            previousHighWindZ = currentHighWindZ;
            previousQuality = quality;
            previousSettingsSignature = settingsSignature;
            frameCount++;

        } catch (Throwable error) {
            // Disable only this backend session. The sky and vanilla world remain usable.
            failed = true;
            try { closeOwned(); } catch (RuntimeException cleanupFailure) { error.addSuppressed(cleanupFailure); }
            DebugLog.warnOnce("dream-clouds-gpu-fallback", "Dreamcore clouds disabled after GPU failure", error);
        }
    }

    private static int ceilDiv(int n, int divisor) {
        return (n + divisor - 1) / divisor;
    }

    private static boolean supported(CombatantRhi rhi) {
        return rhi != null && rhi.capabilities().nativeComputeSubmission()
                && rhi.capabilities().shaderStorageBuffers()
                && rhi.capabilities().imageLoadStore()
                && rhi.advancedShaders().supports(RhiShaderStage.COMPUTE);
    }

    private void ensureResources(int gw, int gh, int gd, int ow, int oh, int fw, int fh, int width, int height) {
        if (froxels != null && (froxelW != gw || froxelH != gh || froxelD != gd)) {
            froxels.close();
            froxels = null;
        }
        if (integratedClouds != null && (imageW != ow || imageH != oh)) {
            integratedClouds.close();
            integratedClouds = null;
            if (nearDistance != null) { nearDistance.close(); nearDistance = null; }
            if (mergedClouds != null) { mergedClouds.close(); mergedClouds = null; }
            if (mergedDistance != null) { mergedDistance.close(); mergedDistance = null; }
        }
        if (farClouds != null && (farW != fw || farH != fh)) {
            farClouds.close();
            farClouds = null;
            if (farDistance != null) { farDistance.close(); farDistance = null; }
        }
        if (noiseVolume == null) {
            StorageVolumeDescriptor descriptor = new StorageVolumeDescriptor(
                    "Dreamcore tileable cloud noise atlas", NOISE_SIZE, NOISE_SIZE, NOISE_SIZE,
                    NOISE_FORMAT, StorageAccess.READ_WRITE,
                    RhiTextureUsage.STORAGE_IMAGE | GpuTexture.USAGE_TEXTURE_BINDING, 1);
            if (!owner.advancedShaders().queryStorageVolumeCapabilities(descriptor).supportsDescriptor(descriptor)) {
                throw new UnsupportedOperationException("RGBA8 sampled 3D cloud noise is unsupported");
            }
            noiseVolume = owner.advancedShaders().createStorageVolume(descriptor);
            noiseReady = false;
        }
        if (froxels == null) {
            StorageVolumeDescriptor descriptor = new StorageVolumeDescriptor(
                    "Dreamcore cloud froxels", gw, gh, gd, FORMAT, StorageAccess.READ_WRITE,
                    RhiTextureUsage.STORAGE_IMAGE | GpuTexture.USAGE_TEXTURE_BINDING, 1);
            if (!owner.advancedShaders().queryStorageVolumeCapabilities(descriptor).supportsDescriptor(descriptor)) {
                throw new UnsupportedOperationException("RGBA16F sampled 3D froxel storage is unsupported");
            }
            froxels = owner.advancedShaders().createStorageVolume(descriptor);
            froxelW = gw; froxelH = gh; froxelD = gd;
        }
        if (integratedClouds == null) {
            integratedClouds = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore integrated clouds", ow, oh, FORMAT, StorageAccess.READ_WRITE, true, false));
            nearDistance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore near-cloud travel distance", ow, oh, GpuFormat.R32_FLOAT,
                    StorageAccess.READ_WRITE, true, false));
            mergedClouds = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore combined volumetric clouds", ow, oh, FORMAT, StorageAccess.READ_WRITE, true, false));
            mergedDistance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore combined cloud travel distance", ow, oh, GpuFormat.R32_FLOAT,
                    StorageAccess.READ_WRITE, true, false));
            imageW = ow; imageH = oh;
        }
        if (farClouds == null) {
            farClouds = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore far clouds", fw, fh, FORMAT, StorageAccess.READ_WRITE, true, false));
            farDistance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore distant-cloud travel distance", fw, fh, GpuFormat.R32_FLOAT,
                    StorageAccess.READ_WRITE, true, false));
            farW = fw; farH = fh;
        }
        if (historyColorA != null && (historyW != width || historyH != height)) {
            closeHistory();
        }
        if (historyColorA == null) {
            historyColorA = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore reprojection color A", width, height, FORMAT, StorageAccess.READ_WRITE, true, false));
            historyColorB = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore reprojection color B", width, height, FORMAT, StorageAccess.READ_WRITE, true, false));
            historyDistanceA = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore reprojection depth A", width, height, GpuFormat.R32_FLOAT, StorageAccess.READ_WRITE, true, false));
            historyDistanceB = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "Dreamcore reprojection depth B", width, height, GpuFormat.R32_FLOAT, StorageAccess.READ_WRITE, true, false));
            historyW = width; historyH = height;
            historyReady = false;
        }
        if (temporalParameters == null) {
            temporalParameters = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "Dreamcore temporal uniforms", TEMPORAL_PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        if (mergePipeline == null) {
            mergePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "dream-cloud-merge", MERGE_SHADER, MERGE_LAYOUT));
        }
        if (temporalPipeline == null) {
            temporalPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "dream-cloud-temporal", TEMPORAL_SHADER, TEMPORAL_LAYOUT));
        }
        if (parameters == null) {
            parameters = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "Dreamcore cloud uniforms", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        if (noisePipeline == null) {
            noisePipeline = owner.advancedShaders().createComputePipeline(
                    new ComputePipelineDescriptor("dream-cloud-noise", NOISE_SHADER, NOISE_LAYOUT));
        }
        if (farPipeline == null) {
            farPipeline = owner.advancedShaders().createComputePipeline(
                    new ComputePipelineDescriptor("dream-far-clouds", FAR_SHADER, FAR_LAYOUT));
        }
        if (densityPipeline == null) {
            densityPipeline = owner.advancedShaders().createComputePipeline(
                    new ComputePipelineDescriptor("dream-cloud-density", DENSITY_SHADER, DENSITY_LAYOUT));
        }
        if (integrationPipeline == null) {
            integrationPipeline = owner.advancedShaders().createComputePipeline(
                    new ComputePipelineDescriptor("dream-cloud-integrate", INTEGRATE_SHADER, INTEGRATION_LAYOUT));
        }
    }

    public void reset() {
        failed = false;
        historyReady = false;
    }

    /** Used during a backend switch; do not accidentally close a new backend's resources. */
    public void release(CombatantRhi backend) {
        if (owner == backend) {
            closeOwned();
            owner = null;
        }
        failed = false;
        historyReady = false;
    }

    @Override
    public void close() {
        wind.reset();
        closeOwned();
        owner = null;
        failed = false;
    }

    private void closeOwned() {
        closeResources();
        if (densityPipeline != null) { densityPipeline.close(); densityPipeline = null; }
        if (integrationPipeline != null) { integrationPipeline.close(); integrationPipeline = null; }
        if (farPipeline != null) { farPipeline.close(); farPipeline = null; }
        if (temporalPipeline != null) { temporalPipeline.close(); temporalPipeline = null; }
        if (mergePipeline != null) { mergePipeline.close(); mergePipeline = null; }
        if (temporalParameters != null) { temporalParameters.close(); temporalParameters = null; }
        if (noisePipeline != null) { noisePipeline.close(); noisePipeline = null; }
        if (parameters != null) { parameters.close(); parameters = null; }
    }

    private void closeHistory() {
        if (historyColorA != null) { historyColorA.close(); historyColorA = null; }
        if (historyColorB != null) { historyColorB.close(); historyColorB = null; }
        if (historyDistanceA != null) { historyDistanceA.close(); historyDistanceA = null; }
        if (historyDistanceB != null) { historyDistanceB.close(); historyDistanceB = null; }
        historyReady = false;
        historyW = historyH = 0;
        previousCamera = null;
        previousLevel = null;
        previousView = previousProjection = null;
        previousTick = Double.NaN;
        frameCount = 0L;
    }

    private void closeResources() {
        closeHistory();
        if (froxels != null) { froxels.close(); froxels = null; }
        if (integratedClouds != null) { integratedClouds.close(); integratedClouds = null; }
        if (farClouds != null) { farClouds.close(); farClouds = null; }
        if (nearDistance != null) { nearDistance.close(); nearDistance = null; }
        if (mergedClouds != null) { mergedClouds.close(); mergedClouds = null; }
        if (mergedDistance != null) { mergedDistance.close(); mergedDistance = null; }
        if (farDistance != null) { farDistance.close(); farDistance = null; }
        if (noiseVolume != null) { noiseVolume.close(); noiseVolume = null; }
        noiseReady = false;
        froxelW = froxelH = froxelD = imageW = imageH = farW = farH = 0;
    }
}
