/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Backend-owned neutral producers required by later deferred consumers.
 *
 * <p>No visual policy lives here: depth resolve preserves reversed-Z depth and the Hi-Z chain stores
 * the conservative nearest depth (MAX for Minecraft's reversed-Z convention).</p>
 */
final class DeferredBackendPasses implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;

    private static final Identifier DEPTH_RESOLVE_SINGLE = id("deferred/depth_resolve_single");
    private static final Identifier DEPTH_RESOLVE_MSAA = id("deferred/depth_resolve_msaa");
    private static final Identifier DEPTH_PYRAMID_COPY = id("deferred/depth_pyramid_copy");
    private static final Identifier DEPTH_PYRAMID_REDUCE = id("deferred/depth_pyramid_reduce");
    private static final Identifier VELOCITY_CAMERA = id("deferred/velocity_camera");

    private static final Std430StructLayout TEMPORAL_CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("depthNdcTransform", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout SAMPLED_TO_IMAGE = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout IMAGE_TO_IMAGE = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout VELOCITY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline depthSingle;
    private RhiComputePipeline depthMsaa;
    private RhiComputePipeline pyramidCopy;
    private RhiComputePipeline pyramidReduce;
    private RhiComputePipeline velocityCamera;
    private RhiStorageBuffer temporalCameraBuffer;
    private final DeferredSecondaryShadowCasterSource secondaryShadowCasters = new DeferredSecondaryShadowCasterSource();
    private final DeferredShadowCascadeSource shadowCascades = new DeferredShadowCascadeSource();
    private final DeferredShadowMapSource shadowMaps = new DeferredShadowMapSource(secondaryShadowCasters);
    private final DeferredShadowResolveSource shadowResolve = new DeferredShadowResolveSource();
    private final DeferredAmbientOcclusionSource ambientOcclusion = new DeferredAmbientOcclusionSource();
    private final DeferredSurfaceWeatherSource surfaceWeather = new DeferredSurfaceWeatherSource();
    private final DeferredColoredBlockLightSource coloredBlockLight = new DeferredColoredBlockLightSource();
    private final DeferredSkyEnvironmentSource skyEnvironment = new DeferredSkyEnvironmentSource();
    private final DeferredEnvironmentIrradianceSource environmentIrradiance = new DeferredEnvironmentIrradianceSource();
    private final DeferredDynamicLightSource dynamicLights = new DeferredDynamicLightSource(secondaryShadowCasters);
    private final DeferredSceneRadianceSource sceneRadiance = new DeferredSceneRadianceSource();
    private final DeferredIndirectLightSource indirectLight = new DeferredIndirectLightSource();
    private final DeferredReflectionCascadeSource reflectionCascades = new DeferredReflectionCascadeSource();
    private final DeferredReflectionSource reflections = new DeferredReflectionSource();
    private final DeferredReactiveMaskSource reactiveMask = new DeferredReactiveMaskSource();
    private final DeferredFinalTemporalCoverageSource finalTemporalCoverage = new DeferredFinalTemporalCoverageSource();
    private final DeferredDisocclusionSource disocclusion = new DeferredDisocclusionSource();
    private final DeferredTemporalSignalSource temporalSignals = new DeferredTemporalSignalSource();
    private final DeferredReflectionDenoiseSource reflectionDenoise = new DeferredReflectionDenoiseSource();
    private final DeferredOpaqueCompositeSource opaqueComposite = new DeferredOpaqueCompositeSource();
    private final DeferredSkyCompositeSource skyComposite = new DeferredSkyCompositeSource();
    private final DeferredCloudFieldSource cloudField = new DeferredCloudFieldSource();
    private final DeferredCloudOccupancySource cloudOccupancy = new DeferredCloudOccupancySource(cloudField);
    private final DeferredCloudSource clouds = new DeferredCloudSource(cloudField, cloudOccupancy);
    private final DeferredCloudTemporalSource cloudTemporal = new DeferredCloudTemporalSource();
    private final DeferredCloudShadowSource cloudShadows = new DeferredCloudShadowSource(cloudField, cloudOccupancy);
    private final DeferredFroxelMediaSource froxelMedia = new DeferredFroxelMediaSource(cloudField, cloudShadows, cloudOccupancy);
    private final DeferredAtmosphereCompositeSource atmosphereComposite = new DeferredAtmosphereCompositeSource(froxelMedia);
    private final DeferredTemporalHistorySource temporalHistory = new DeferredTemporalHistorySource();
    private final DeferredTemporalResolveSource temporalResolve = new DeferredTemporalResolveSource();
    private final DeferredHdrPostSource hdrPost = new DeferredHdrPostSource();
    private final DeferredCameraPostSource cameraPost = new DeferredCameraPostSource();
    private final DeferredPatchSurfaceSource patchSurfaces = new DeferredPatchSurfaceSource(reflectionCascades);
    private final DeferredWaterReflectionTemporalSource waterReflectionTemporal = new DeferredWaterReflectionTemporalSource();
    private final DeferredDebugCompositorSource debugCompositor = new DeferredDebugCompositorSource();

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.shadow.cascades", DeferredStage.SHADOW_PREPARE)
                .feature(DeferredFeature.SHADOWS)
                .when(context -> context.featureEnabled(DeferredFeature.SHADOWS)
                        && context.primaryView().current() != null
                        && context.worldState().directionalLight().shadowValid())
                .execute(shadowCascades::prepare)
                .build());
        passes.add(DeferredPassSpec.builder("world.shadow.map", DeferredStage.SHADOW_MAP)
                .feature(DeferredFeature.SHADOWS)
                .write(DeferredResource.SHADOW_DEPTH)
                .write(DeferredResource.SHADOW_CASCADE_DATA)
                .when(context -> context.featureEnabled(DeferredFeature.SHADOWS) && shadowMaps.available(context))
                .execute(shadowMaps::render)
                .build());
        passes.add(DeferredPassSpec.builder("world.depth.resolve", DeferredStage.DEPTH_RESOLVE)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.RESOLVED_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.resources().texture(DeferredResource.MAIN_DEPTH) != null)
                .execute(this::resolveDepth)
                .build());
        passes.add(DeferredPassSpec.builder("world.post_translucency.depth.resolve", DeferredStage.POST_TRANSLUCENCY)
                .priority(-200)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.FINAL_RESOLVED_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.resources().texture(DeferredResource.MAIN_DEPTH) != null)
                .execute(context -> resolveDepth(context, DeferredResource.FINAL_RESOLVED_DEPTH,
                        "Combatant final depth resolve"))
                .build());
        passes.add(DeferredPassSpec.builder("world.gbuffer.depth.capture", DeferredStage.DEPTH_RESOLVE)
                .priority(100)
                .read(DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.GBUFFER_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::captureGbufferDepth)
                .build());
        passes.add(DeferredPassSpec.builder("world.velocity.camera", DeferredStage.VELOCITY_RESOLVE)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH)
                .write(DeferredResource.VELOCITY, DeferredResource.MOTION_VALIDITY)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.primaryView().current() != null)
                .execute(this::resolveCameraVelocity)
                .build());
        passes.add(DeferredPassSpec.builder("world.depth.pyramid", DeferredStage.DEPTH_PYRAMID)
                .read(DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.DEPTH_PYRAMID)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::buildDepthPyramid)
                .build());
        // Refresh primary screen-space inputs after forward opaque/entity rendering. These are
        // distinct graph passes rather than manually re-running the early geometry stages, so
        // WRITE_AFTER_* hazards from FORWARD_OPAQUE are represented explicitly by the frame graph.
        passes.add(DeferredPassSpec.builder("world.pre_translucency.depth.resolve", DeferredStage.PRE_TRANSLUCENCY_DEPTH_RESOLVE)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.RESOLVED_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.resources().texture(DeferredResource.MAIN_DEPTH) != null)
                .execute(this::resolveDepth)
                .build());
        passes.add(DeferredPassSpec.builder("world.pre_translucency.velocity.camera", DeferredStage.PRE_TRANSLUCENCY_VELOCITY_RESOLVE)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH)
                .write(DeferredResource.VELOCITY, DeferredResource.MOTION_VALIDITY)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.primaryView().current() != null)
                .execute(this::resolveCameraVelocity)
                .build());
        passes.add(DeferredPassSpec.builder("world.pre_translucency.depth.pyramid", DeferredStage.PRE_TRANSLUCENCY_DEPTH_PYRAMID)
                .read(DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.DEPTH_PYRAMID)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::buildDepthPyramid)
                .build());
        shadowResolve.install(passes);
        ambientOcclusion.install(passes);
        surfaceWeather.install(passes);
        coloredBlockLight.install(passes);
        skyEnvironment.install(passes);
        environmentIrradiance.install(passes);
        dynamicLights.install(passes);
        sceneRadiance.install(passes);
        indirectLight.install(passes);
        reflectionCascades.install(passes);
        reflections.install(passes);
        reactiveMask.install(passes);
        finalTemporalCoverage.install(passes);
        disocclusion.install(passes);
        temporalSignals.install(passes);
        reflectionDenoise.install(passes);
        opaqueComposite.install(passes);
        skyComposite.install(passes);
        cloudOccupancy.install(passes);
        cloudShadows.install(passes);
        clouds.install(passes);
        cloudTemporal.install(passes);
        froxelMedia.install(passes);
        atmosphereComposite.install(passes);
        temporalHistory.install(passes);
        temporalResolve.install(passes);
        hdrPost.install(passes);
        cameraPost.install(passes);
        patchSurfaces.install(passes);
        waterReflectionTemporal.install(passes);
        debugCompositor.install(passes);
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        depthSingle();
        depthMsaa();
        pyramidCopy();
        pyramidReduce();
        velocityCamera();
        temporalCameraBuffer();
        shadowResolve.prepare(rhi);
        ambientOcclusion.prepare(rhi);
        surfaceWeather.prepare(rhi);
        coloredBlockLight.prepare(rhi);
        skyEnvironment.prepare(rhi);
        environmentIrradiance.prepare(rhi);
        dynamicLights.prepare(rhi);
        sceneRadiance.prepare(rhi);
        indirectLight.prepare(rhi);
        reflections.prepare(rhi);
        reactiveMask.prepare(rhi);
        finalTemporalCoverage.prepare(rhi);
        disocclusion.prepare(rhi);
        temporalSignals.prepare(rhi);
        reflectionDenoise.prepare(rhi);
        opaqueComposite.prepare(rhi);
        skyComposite.prepare(rhi);
        cloudField.prepare(rhi);
        cloudOccupancy.prepare(rhi);
        clouds.prepare(rhi);
        cloudTemporal.prepare(rhi);
        cloudShadows.prepare(rhi);
        froxelMedia.prepare(rhi);
        atmosphereComposite.prepare(rhi);
        temporalHistory.prepare(rhi);
        temporalResolve.prepare(rhi);
        hdrPost.prepare(rhi);
        cameraPost.prepare(rhi);
        patchSurfaces.prepare(rhi);
        waterReflectionTemporal.prepare(rhi);
        debugCompositor.prepare(rhi);
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closePipelines();
        CombatantRhi releaseOwner = currentOwner != null ? currentOwner : owner;
        shadowMaps.release(releaseOwner);
        secondaryShadowCasters.close();
        combatant.client.render.sodium.SodiumSecondaryTerrainSource.invalidate();
        shadowResolve.release(releaseOwner);
        ambientOcclusion.release(releaseOwner);
        surfaceWeather.release(releaseOwner);
        coloredBlockLight.release(releaseOwner);
        skyEnvironment.release(releaseOwner);
        environmentIrradiance.release(releaseOwner);
        dynamicLights.release(releaseOwner);
        sceneRadiance.release(releaseOwner);
        indirectLight.release(releaseOwner);
        reflectionCascades.release(releaseOwner);
        reflections.release(releaseOwner);
        reactiveMask.release(releaseOwner);
        finalTemporalCoverage.release(releaseOwner);
        disocclusion.release(releaseOwner);
        temporalSignals.release(releaseOwner);
        reflectionDenoise.release(releaseOwner);
        opaqueComposite.release(releaseOwner);
        skyComposite.release(releaseOwner);
        clouds.release(releaseOwner);
        cloudShadows.release(releaseOwner);
        cloudOccupancy.release(releaseOwner);
        cloudField.release(releaseOwner);
        cloudTemporal.release(releaseOwner);
        froxelMedia.release(releaseOwner);
        atmosphereComposite.release(releaseOwner);
        temporalHistory.release(releaseOwner);
        temporalResolve.release(releaseOwner);
        hdrPost.release(releaseOwner);
        cameraPost.release(releaseOwner);
        patchSurfaces.release(releaseOwner);
        waterReflectionTemporal.release(releaseOwner);
        debugCompositor.release(releaseOwner);
        owner = null;
    }

    private void resolveDepth(DeferredPassContext context) {
        resolveDepth(context, DeferredResource.RESOLVED_DEPTH, "Combatant depth resolve");
    }

    private void resolveDepth(DeferredPassContext context, DeferredResource outputResource, String label) {
        ensureOwner(context.rhi());
        GpuTextureView source = requireTexture(context, DeferredResource.MAIN_DEPTH);
        RhiStorageImage output = requireImage(context, outputResource);
        int samples = samples(source);
        RhiComputePipeline pipeline = samples > 1 ? depthMsaa() : depthSingle();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label,
                pipeline,
                groups(output.descriptor().width()),
                groups(output.descriptor().height()),
                1,
                List.of(),
                List.of(new SampledTextureBinding(0, source, sampler)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void captureGbufferDepth(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView source = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.GBUFFER_DEPTH);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant G-buffer depth capture",
                pyramidCopy(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(),
                List.of(new SampledTextureBinding(0, source, nearest)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void resolveCameraVelocity(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        if (current == null) return;

        boolean historyValid = context.primaryView().hasTemporalHistory() && previous != null;
        DeferredPrimaryViewSource.FrameView reprojectionPrevious = historyValid ? previous : current;
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.VELOCITY);
        RhiStorageImage validity = requireImage(context, DeferredResource.MOTION_VALIDITY);
        RhiStorageBuffer cameraBuffer = temporalCameraBuffer();

        Vec3 delta = historyValid
                ? current.cameraPosition().subtract(reprojectionPrevious.cameraPosition())
                : Vec3.ZERO;
        boolean zeroToOneNdc = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(TEMPORAL_CAMERA_LAYOUT, 1)
                .putMat4(0, "currentInverseProjection", current.inverseProjection())
                .putMat4(0, "currentInverseView", current.inverseView())
                .putMat4(0, "previousView", reprojectionPrevious.view())
                .putMat4(0, "previousProjection", reprojectionPrevious.projection())
                .putVec4(0, "cameraDelta", (float) delta.x, (float) delta.y, (float) delta.z, 0.0f)
                .putVec4(0, "depthNdcTransform",
                        zeroToOneNdc ? 1.0f : 2.0f, zeroToOneNdc ? 0.0f : -1.0f,
                        historyValid ? 1.0f : 0.0f, DeferredVelocityContract.VERSION);
        cameraBuffer.upload(writer.buffer(), 0L);

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant camera velocity",
                velocityCamera(),
                groups(output.descriptor().width()),
                groups(output.descriptor().height()),
                1,
                List.of(new StorageBinding(2, cameraBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, resolvedDepth, sampler),
                        new SampledTextureBinding(4, gbufferDepth, sampler)
                ),
                List.of(
                        new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(3, validity, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void buildDepthPyramid(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView resolved = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageImage pyramid = requireImage(context, DeferredResource.DEPTH_PYRAMID);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant depth pyramid mip 0",
                pyramidCopy(),
                groups(pyramid.descriptor().width()),
                groups(pyramid.descriptor().height()),
                1,
                List.of(),
                List.of(new SampledTextureBinding(0, resolved, sampler)),
                List.of(new StorageImageBinding(1, pyramid, StorageAccess.WRITE_ONLY, 0))
        ));

        int mipLevels = pyramid.descriptor().mipLevels();
        for (int mip = 1; mip < mipLevels; mip++) {
            computeToComputeBarrier(context, pyramid);
            int width = Math.max(1, pyramid.descriptor().width() >> mip);
            int height = Math.max(1, pyramid.descriptor().height() >> mip);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant depth pyramid mip " + mip,
                    pyramidReduce(),
                    groups(width),
                    groups(height),
                    1,
                    List.of(),
                    List.of(),
                    List.of(
                            new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, mip - 1),
                            new StorageImageBinding(1, pyramid, StorageAccess.WRITE_ONLY, mip)
                    )
            ));
        }
    }

    private static void computeToComputeBarrier(DeferredPassContext context, RhiStorageImage image) {
        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.READ,
                List.of(),
                List.of(image)
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        CombatantRhi previous = owner;
        closePipelines();
        if (previous != null) {
            shadowMaps.release(previous);
            shadowResolve.release(previous);
            ambientOcclusion.release(previous);
            surfaceWeather.release(previous);
            coloredBlockLight.release(previous);
            skyEnvironment.release(previous);
            environmentIrradiance.release(previous);
            dynamicLights.release(previous);
            sceneRadiance.release(previous);
            indirectLight.release(previous);
            reflectionCascades.release(previous);
            reflections.release(previous);
            reactiveMask.release(previous);
            finalTemporalCoverage.release(previous);
            disocclusion.release(previous);
            temporalSignals.release(previous);
            reflectionDenoise.release(previous);
            opaqueComposite.release(previous);
            skyComposite.release(previous);
            clouds.release(previous);
            cloudShadows.release(previous);
            cloudOccupancy.release(previous);
            cloudField.release(previous);
            cloudTemporal.release(previous);
            froxelMedia.release(previous);
            atmosphereComposite.release(previous);
            temporalHistory.release(previous);
            temporalResolve.release(previous);
            hdrPost.release(previous);
            cameraPost.release(previous);
            patchSurfaces.release(previous);
            waterReflectionTemporal.release(previous);
        }
        owner = rhi;
    }

    private RhiComputePipeline depthSingle() {
        if (depthSingle == null) depthSingle = pipeline("combatant-depth-resolve-single", DEPTH_RESOLVE_SINGLE, SAMPLED_TO_IMAGE);
        return depthSingle;
    }

    private RhiComputePipeline depthMsaa() {
        if (depthMsaa == null) depthMsaa = pipeline("combatant-depth-resolve-msaa", DEPTH_RESOLVE_MSAA, SAMPLED_TO_IMAGE);
        return depthMsaa;
    }

    private RhiComputePipeline pyramidCopy() {
        if (pyramidCopy == null) pyramidCopy = pipeline("combatant-depth-pyramid-copy", DEPTH_PYRAMID_COPY, SAMPLED_TO_IMAGE);
        return pyramidCopy;
    }

    private RhiComputePipeline pyramidReduce() {
        if (pyramidReduce == null) pyramidReduce = pipeline("combatant-depth-pyramid-reduce", DEPTH_PYRAMID_REDUCE, IMAGE_TO_IMAGE);
        return pyramidReduce;
    }

    private RhiComputePipeline velocityCamera() {
        if (velocityCamera == null) {
            velocityCamera = pipeline("combatant-velocity-camera", VELOCITY_CAMERA, VELOCITY_LAYOUT);
        }
        return velocityCamera;
    }

    private RhiStorageBuffer temporalCameraBuffer() {
        if (owner == null) throw new IllegalStateException("Deferred backend pass has no RHI owner");
        if (temporalCameraBuffer == null) {
            temporalCameraBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-temporal-camera", TEMPORAL_CAMERA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return temporalCameraBuffer;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Deferred backend pass has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView texture = context.resources().texture(resource);
        if (texture == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return texture;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage image = context.resources().storageImage(resource);
        if (image == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return image;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    @Override
    public void close() {
        closePipelines();
        shadowMaps.close();
        secondaryShadowCasters.close();
        shadowResolve.close();
        ambientOcclusion.close();
        surfaceWeather.close();
        coloredBlockLight.close();
        skyEnvironment.close();
        environmentIrradiance.close();
        dynamicLights.close();
        sceneRadiance.close();
        indirectLight.close();
        reflectionCascades.close();
        reflections.close();
        reactiveMask.close();
        finalTemporalCoverage.close();
        disocclusion.close();
        temporalSignals.close();
        reflectionDenoise.close();
        opaqueComposite.close();
        skyComposite.close();
        clouds.close();
        cloudShadows.close();
        cloudOccupancy.close();
        cloudField.close();
        cloudTemporal.close();
        froxelMedia.close();
        atmosphereComposite.close();
        temporalHistory.close();
        temporalResolve.close();
        hdrPost.close();
        cameraPost.close();
        patchSurfaces.close();
        waterReflectionTemporal.close();
        owner = null;
    }

    private void closePipelines() {
        depthSingle = close(depthSingle);
        depthMsaa = close(depthMsaa);
        pyramidCopy = close(pyramidCopy);
        pyramidReduce = close(pyramidReduce);
        velocityCamera = close(velocityCamera);
        if (temporalCameraBuffer != null) {
            try {
                temporalCameraBuffer.close();
            } catch (Throwable ignored) {
                // Device teardown remains authoritative if explicit close is no longer legal.
            }
            temporalCameraBuffer = null;
        }
    }

    private static RhiComputePipeline close(RhiComputePipeline pipeline) {
        if (pipeline == null) return null;
        try {
            pipeline.close();
        } catch (Throwable ignored) {
            // Backend/device teardown owns the final native cleanup if explicit close is no longer legal.
        }
        return null;
    }
}
