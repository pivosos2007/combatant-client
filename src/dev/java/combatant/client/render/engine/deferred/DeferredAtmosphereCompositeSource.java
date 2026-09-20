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
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.StorageVolumeBinding;
import combatant.client.render.engine.world.AerialPerspectiveLayout;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Composes opaque/sky/cloud radiance through the integrated participating-media froxel volume. */
final class DeferredAtmosphereCompositeSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/atmosphere_cloud_composite");
    private static final Identifier CLOUD_ONLY_SHADER = id("deferred/cloud_only_composite");
    private static final Identifier BYPASS_SHADER = id("deferred/bloom_copy");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("froxel", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("aerialLayout", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(12, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(13, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));

    private static final Std430StructLayout CLOUD_ONLY_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("policy", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout CLOUD_ONLY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout BYPASS_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private final DeferredFroxelMediaSource froxelMedia;
    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiComputePipeline cloudOnlyPipeline;
    private RhiComputePipeline bypassPipeline;
    private RhiStorageBuffer data;
    private RhiStorageBuffer cloudOnlyData;

    DeferredAtmosphereCompositeSource(DeferredFroxelMediaSource froxelMedia) {
        if (froxelMedia == null) throw new IllegalArgumentException("froxelMedia");
        this.froxelMedia = froxelMedia;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.media.bypass", DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE)
                .priority(-100)
                .read(DeferredResource.SKY_COMPOSITED_RADIANCE)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.SKY_COMPOSITED_RADIANCE)
                        && (!context.featureEnabled(DeferredFeature.PARTICIPATING_MEDIA)
                        || !mediaInputsValid(context))
                        && (!context.featureEnabled(DeferredFeature.CLOUDS) || cloudLayerMask(context) == 0))
                .execute(this::bypass)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.cloud-only.composite", DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE)
                .priority(-50)
                .feature(DeferredFeature.CLOUDS)
                .read(DeferredResource.SKY_COMPOSITED_RADIANCE, DeferredResource.RESOLVED_DEPTH)
                .optionalRead(DeferredResource.CLOUD_TEMPORAL_RADIANCE, DeferredResource.CLOUD_TEMPORAL_DEPTH,
                        DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE, DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH,
                        DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.SKY_COMPOSITED_RADIANCE)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && (!context.featureEnabled(DeferredFeature.PARTICIPATING_MEDIA)
                        || !mediaInputsValid(context))
                        && cloudLayerMask(context) != 0)
                .execute(this::compositeCloudsWithoutMedia)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.media.composite", DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE)
                .feature(DeferredFeature.PARTICIPATING_MEDIA)
                .read(DeferredResource.SKY_COMPOSITED_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.AERIAL_PERSPECTIVE, DeferredResource.AERIAL_TRANSMITTANCE,
                        DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE,
                        DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE)
                .optionalRead(DeferredResource.CLOUD_TEMPORAL_RADIANCE, DeferredResource.CLOUD_TEMPORAL_DEPTH,
                        DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE, DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH,
                        DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH)
                .write(DeferredResource.SCENE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.SKY_COMPOSITED_RADIANCE)
                        && mediaInputsValid(context))
                .execute(this::composite)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        cloudOnlyPipeline();
        bypassPipeline();
        data();
        cloudOnlyData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void bypass(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage source = requireImage(context, DeferredResource.SKY_COMPOSITED_RADIANCE);
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant participating-media neutral bypass", bypassPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(
                        new StorageImageBinding(0, source, StorageAccess.READ_ONLY),
                        new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void compositeCloudsWithoutMedia(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView base = requireTexture(context, DeferredResource.SKY_COMPOSITED_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        int cloudMask = cloudLayerMask(context);
        GpuTextureView cloudRadiance = (cloudMask & 1) != 0
                ? requireTexture(context, DeferredResource.CLOUD_TEMPORAL_RADIANCE) : base;
        GpuTextureView cloudDepth = (cloudMask & 1) != 0
                ? requireTexture(context, DeferredResource.CLOUD_TEMPORAL_DEPTH) : depth;
        GpuTextureView highCloudRadiance = (cloudMask & 2) != 0
                ? requireTexture(context, DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE) : base;
        GpuTextureView highCloudDepth = (cloudMask & 2) != 0
                ? requireTexture(context, DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH) : depth;
        GpuTextureView convectiveCloudRadiance = (cloudMask & 4) != 0
                ? requireTexture(context, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE) : base;
        GpuTextureView convectiveCloudDepth = (cloudMask & 4) != 0
                ? requireTexture(context, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH) : depth;
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);

        Std430Writer writer = new Std430Writer(CLOUD_ONLY_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putVec4(0, "policy", zeroToOneDepth(context) ? 1.0f : 0.0f, cloudMask, 0.0f, 0.0f);
        RhiStorageBuffer buffer = cloudOnlyData();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud-only composite", cloudOnlyPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(9, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, cloudRadiance, linear),
                        new SampledTextureBinding(3, cloudDepth, nearest),
                        new SampledTextureBinding(4, highCloudRadiance, linear),
                        new SampledTextureBinding(5, highCloudDepth, nearest),
                        new SampledTextureBinding(6, convectiveCloudRadiance, linear),
                        new SampledTextureBinding(7, convectiveCloudDepth, nearest)
                ),
                List.of(new StorageImageBinding(8, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void composite(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView base = requireTexture(context, DeferredResource.SKY_COMPOSITED_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        int cloudMask = cloudLayerMask(context);
        // Cloud layers are independent optional producers. Keep descriptor slots populated for the
        // canonical shader layout, while policy.z tells the shader exactly which typed pairs exist.
        GpuTextureView cloudRadiance = (cloudMask & 1) != 0
                ? requireTexture(context, DeferredResource.CLOUD_TEMPORAL_RADIANCE) : base;
        GpuTextureView cloudDepth = (cloudMask & 1) != 0
                ? requireTexture(context, DeferredResource.CLOUD_TEMPORAL_DEPTH) : depth;
        GpuTextureView highCloudRadiance = (cloudMask & 2) != 0
                ? requireTexture(context, DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE) : base;
        GpuTextureView highCloudDepth = (cloudMask & 2) != 0
                ? requireTexture(context, DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH) : depth;
        GpuTextureView convectiveCloudRadiance = (cloudMask & 4) != 0
                ? requireTexture(context, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE) : base;
        GpuTextureView convectiveCloudDepth = (cloudMask & 4) != 0
                ? requireTexture(context, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH) : depth;
        GpuTextureView aerialRadiance = requireTexture(context, DeferredResource.AERIAL_PERSPECTIVE);
        GpuTextureView aerialTransmittance = requireTexture(context, DeferredResource.AERIAL_TRANSMITTANCE);
        RhiStorageVolume integratedRadiance = requireVolume(context, DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE);
        RhiStorageVolume integratedTransmittance = requireVolume(context, DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE);
        RhiStorageImage output = requireImage(context, DeferredResource.SCENE_RADIANCE);

        DeferredFroxelConfig.Grid grid = froxelMedia.currentGrid();
        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "froxel", grid.width(), grid.height(), grid.depth(), grid.maxDistanceBlocks())
                .putVec4(0, "policy", grid.depthExponent(), zeroToOneDepth(context) ? 1.0f : 0.0f,
                        cloudMask, 0.0f)
                .putVec4(0, "aerialLayout", AerialPerspectiveLayout.ZENITH_SLICES,
                        AerialPerspectiveLayout.AZIMUTH_SLICES,
                        Math.max(0.064f, grid.maxDistanceBlocks() * 0.001f), 0.0f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant participating-media and cloud composite", pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(7, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, cloudRadiance, linear),
                        new SampledTextureBinding(3, cloudDepth, nearest),
                        new SampledTextureBinding(8, aerialRadiance, nearest),
                        new SampledTextureBinding(9, aerialTransmittance, nearest),
                        new SampledTextureBinding(10, highCloudRadiance, linear),
                        new SampledTextureBinding(11, highCloudDepth, nearest),
                        new SampledTextureBinding(12, convectiveCloudRadiance, linear),
                        new SampledTextureBinding(13, convectiveCloudDepth, nearest)
                ),
                List.of(new StorageImageBinding(6, output, StorageAccess.WRITE_ONLY)),
                List.of(
                        new StorageVolumeBinding(4, integratedRadiance, StorageAccess.READ_ONLY),
                        new StorageVolumeBinding(5, integratedTransmittance, StorageAccess.READ_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline cloudOnlyPipeline() {
        if (owner == null) throw new IllegalStateException("Atmosphere composite source has no RHI owner");
        if (cloudOnlyPipeline == null) cloudOnlyPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-only-composite", CLOUD_ONLY_SHADER, CLOUD_ONLY_LAYOUT));
        return cloudOnlyPipeline;
    }

    private RhiComputePipeline bypassPipeline() {
        if (owner == null) throw new IllegalStateException("Atmosphere composite source has no RHI owner");
        if (bypassPipeline == null) bypassPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-participating-media-bypass", BYPASS_SHADER, BYPASS_LAYOUT));
        return bypassPipeline;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-atmosphere-cloud-composite", SHADER, LAYOUT)
        );
        return pipeline;
    }

    private RhiStorageBuffer cloudOnlyData() {
        if (cloudOnlyData == null) cloudOnlyData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-only-composite-data", CLOUD_ONLY_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return cloudOnlyData;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-atmosphere-cloud-composite-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return data;
    }

    private void closeOwned() {
        close(pipeline); pipeline = null;
        close(cloudOnlyPipeline); cloudOnlyPipeline = null;
        close(bypassPipeline); bypassPipeline = null;
        close(data); data = null;
        close(cloudOnlyData); cloudOnlyData = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static boolean mediaInputsValid(DeferredPassContext context) {
        // Aerial resources are persistent. Do not consume a previous sky frame after SKY is disabled.
        return context.featureEnabled(DeferredFeature.SKY)
                && context.isValid(DeferredResource.RESOLVED_DEPTH)
                && context.isValid(DeferredResource.AERIAL_PERSPECTIVE)
                && context.isValid(DeferredResource.AERIAL_TRANSMITTANCE)
                && context.isValid(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE)
                && context.isValid(DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE)
                && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null
                && context.resources().texture(DeferredResource.AERIAL_PERSPECTIVE) != null
                && context.resources().texture(DeferredResource.AERIAL_TRANSMITTANCE) != null
                && context.resources().storageVolume(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE) != null
                && context.resources().storageVolume(DeferredResource.FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE) != null;
    }

    private static int cloudLayerMask(DeferredPassContext context) {
        int mask = 0;
        if (cloudLayerValid(context, DeferredResource.CLOUD_TEMPORAL_RADIANCE,
                DeferredResource.CLOUD_TEMPORAL_DEPTH)) mask |= 1;
        if (cloudLayerValid(context, DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE,
                DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH)) mask |= 2;
        if (cloudLayerValid(context, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE,
                DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH)) mask |= 4;
        return mask;
    }

    private static boolean cloudLayerValid(DeferredPassContext context,
                                           DeferredResource radiance,
                                           DeferredResource depth) {
        return context.isValid(radiance) && context.isValid(depth)
                && context.resources().texture(radiance) != null
                && context.resources().texture(depth) != null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume value = context.resources().storageVolume(resource);
        if (value == null) throw new IllegalStateException("Deferred storage volume is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
