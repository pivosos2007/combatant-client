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
import combatant.client.render.engine.rhi.shader.SampledVolumeBinding;
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
import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.environment.CloudDomainGroup;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** World-space volumetric cloud renderer split into independently temporal cloud-domain groups. */
final class DeferredCloudSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier RENDER_SHADER = id("deferred/cloud_render");

    private static final Std430StructLayout RENDER_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("cameraTime", Std430Type.VEC4)
            .member("grid", Std430Type.VEC4)
            .member("macroGrid", Std430Type.VEC4)
            .member("macroOrigin", Std430Type.VEC4)
            .member("counts", Std430Type.VEC4)
            .member("quality", Std430Type.VEC4)
            .member("lodPolicy", Std430Type.VEC4)
            .member("sunDirection", Std430Type.VEC4)
            .member("sunRadiance", Std430Type.VEC4)
            .member("noiseDomain", Std430Type.VEC4)
            .member("lightingPolicy", Std430Type.VEC4)
            .member("occupancyDomain", Std430Type.VEC4)
            .member("occupancyPolicy", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout RENDER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY)
    ));

    private static final GroupOutputs LOW_MID = new GroupOutputs(
            CloudDomainGroup.LOW_MID,
            "low_mid",
            DeferredResource.CLOUD_RADIANCE,
            DeferredResource.CLOUD_DEPTH,
            DeferredResource.CLOUD_REPROJECTION_DATA
    );
    private static final GroupOutputs HIGH = new GroupOutputs(
            CloudDomainGroup.HIGH,
            "high",
            DeferredResource.CLOUD_HIGH_RADIANCE,
            DeferredResource.CLOUD_HIGH_DEPTH,
            DeferredResource.CLOUD_HIGH_REPROJECTION_DATA
    );
    private static final GroupOutputs CONVECTIVE = new GroupOutputs(
            CloudDomainGroup.CONVECTIVE,
            "convective",
            DeferredResource.CLOUD_CONVECTIVE_RADIANCE,
            DeferredResource.CLOUD_CONVECTIVE_DEPTH,
            DeferredResource.CLOUD_CONVECTIVE_REPROJECTION_DATA
    );

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private final DeferredCloudFieldSource fieldSource;
    private final DeferredCloudOccupancySource occupancySource;
    private CombatantRhi owner;
    private RhiComputePipeline renderPipeline;
    private RhiStorageBuffer renderData;

    DeferredCloudSource(DeferredCloudFieldSource fieldSource, DeferredCloudOccupancySource occupancySource) {
        if (fieldSource == null) throw new IllegalArgumentException("fieldSource");
        if (occupancySource == null) throw new IllegalArgumentException("occupancySource");
        this.fieldSource = fieldSource;
        this.occupancySource = occupancySource;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        installGroup(passes, LOW_MID, 25);
        installGroup(passes, HIGH, 26);
        installGroup(passes, CONVECTIVE, 27);
    }

    private void installGroup(ArrayList<DeferredPassSpec> passes, GroupOutputs group, int priority) {
        passes.add(DeferredPassSpec.builder("world.cloud.render." + group.name(), DeferredStage.SKY_COMPOSITE)
                .priority(priority)
                .feature(DeferredFeature.CLOUDS)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.SKY_DIFFUSE_SH, DeferredResource.CLOUD_OCCUPANCY)
                .write(group.radiance(), group.depth(), group.reprojection())
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.SKY_DIFFUSE_SH)
                        && context.isValid(DeferredResource.CLOUD_OCCUPANCY))
                .execute(context -> render(context, group))
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        renderPipeline();
        ensureBuffers();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void render(DeferredPassContext context, GroupOutputs group) {
        ensureOwner(context.rhi());
        ensureBuffers();
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageBuffer skyDiffuseSh = requireBuffer(context, DeferredResource.SKY_DIFFUSE_SH);
        RhiStorageImage cloudRadiance = requireImage(context, group.radiance());
        RhiStorageImage cloudDepth = requireImage(context, group.depth());
        RhiStorageImage cloudReprojectionData = requireImage(context, group.reprojection());
        DeferredCloudFieldSource.FrameData cloudField = fieldSource.prepareFrame(context);
        DeferredCloudOccupancySource.FrameState occupancyState = occupancySource.current();
        RhiStorageVolume occupancyVolume = requireVolume(context, DeferredResource.CLOUD_OCCUPANCY);
        Std430Writer renderWriter = renderWriter(context, view, cloudField, occupancyState, group.group());
        renderData.upload(renderWriter.buffer(), 0L);

        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant volumetric cloud render " + group.name(), renderPipeline(),
                groups(cloudRadiance.descriptor().width()), groups(cloudRadiance.descriptor().height()), 1,
                List.of(
                        new StorageBinding(3, renderData, 0L, renderWriter.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(4, fieldSource.weatherData(), 0L,
                                fieldSource.weatherData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, fieldSource.domainData(), 0L,
                                fieldSource.domainData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(8, skyDiffuseSh, 0L,
                                skyDiffuseSh.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(9, fieldSource.macroWeatherData(), 0L,
                                fieldSource.macroWeatherData().descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(new SampledTextureBinding(6, resolvedDepth, depthSampler)),
                List.of(
                        new StorageImageBinding(1, cloudRadiance, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(2, cloudDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, cloudReprojectionData, StorageAccess.WRITE_ONLY)
                ),
                List.of(),
                List.of(new SampledVolumeBinding(10, occupancyVolume, depthSampler))
        ));
    }

    private Std430Writer renderWriter(DeferredPassContext context,
                                      DeferredPrimaryViewSource.FrameView view,
                                      DeferredCloudFieldSource.FrameData cloudField,
                                      DeferredCloudOccupancySource.FrameState occupancy,
                                      CloudDomainGroup group) {
        CloudProfile profile = cloudField.profile();
        WeatherState weather = cloudField.weather();
        WeatherFieldState field = cloudField.field();
        WeatherFieldState macro = cloudField.macroField();
        Vec3 camera = view.cameraPosition();
        float originX = field.valid() ? field.originBlockX() : (float) camera.x;
        float originZ = field.valid() ? field.originBlockZ() : (float) camera.z;
        float spacing = field.valid() ? Math.max(1, field.spacingBlocks()) : 1.0f;
        int width = field.valid() ? field.gridWidth() : 0;
        int depth = field.valid() ? field.gridDepth() : 0;
        float macroSpacing = macro.valid() ? Math.max(1, macro.spacingBlocks()) : 1.0f;
        int macroWidth = macro.valid() ? macro.gridWidth() : 0;
        int macroDepth = macro.valid() ? macro.gridDepth() : 0;
        float macroOriginX = macro.valid() ? macro.originBlockX() - originX : 0.0f;
        float macroOriginZ = macro.valid() ? macro.originBlockZ() - originZ : 0.0f;
        float timeSeconds = weather.valid() ? (float) weather.renderAdvectionSeconds() : 0.0f;
        DirectionalLightDescriptor sun = context.worldState().directionalLight();

        return new Std430Writer(RENDER_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "cameraTime", (float) (camera.x - originX), (float) camera.y,
                        (float) (camera.z - originZ), timeSeconds)
                .putVec4(0, "grid", spacing, width, depth, cloudField.weatherCount())
                .putVec4(0, "macroGrid", macroSpacing, macroWidth, macroDepth, cloudField.macroWeatherCount())
                .putVec4(0, "macroOrigin", macroOriginX, macroOriginZ, 0.0f, 0.0f)
                .putVec4(0, "counts", cloudField.domainCount(),
                        cloudField.active() ? profile.maxRayDistanceBlocks() : 0.0f,
                        zeroToOneDepth(context) ? 1.0f : 0.0f,
                        cloudField.active() ? 1.0f : 0.0f)
                .putVec4(0, "quality", config.primarySteps(), config.lightSteps(),
                        config.maxPrimaryStepBlocks(), framePhase(context.frame().frameId()))
                .putVec4(0, "lodPolicy", 192.0f, 768.0f, 0.35f, group.gpuCode())
                .putVec4(0, "sunDirection", sun.directionX(), sun.directionY(), sun.directionZ(), sun.valid() ? 1.0f : 0.0f)
                .putVec4(0, "sunRadiance", sun.radianceRed(), sun.radianceGreen(), sun.radianceBlue(), sun.angularRadiusRadians())
                .putVec4(0, "noiseDomain",
                        wrapOrigin(field.valid() ? field.originBlockX() : 0),
                        wrapOrigin(field.valid() ? field.originBlockZ() : 0),
                        seedPhase(weather.modelSeed()), 0.0f)
                .putVec4(0, "lightingPolicy", profile.ambientResponse(), profile.lightSampleDistanceBlocks(),
                        config.multiScatteringOrders(), 0.0f)
                .putVec4(0, "occupancyDomain", occupancy.originLocalX(), occupancy.originLocalZ(),
                        occupancy.minimumY(), occupancy.maximumY())
                .putVec4(0, "occupancyPolicy", occupancy.spanXZ(),
                        occupancy.active() ? 1.0f : 0.0f, 0.0f, 0.0f);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureBuffers() {
        if (owner == null) throw new IllegalStateException("Cloud source has no RHI owner");
        if (renderData == null) renderData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-render-data", RENDER_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
    }

    private RhiComputePipeline renderPipeline() {
        if (renderPipeline == null) renderPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-render", RENDER_SHADER, RENDER_LAYOUT)
        );
        return renderPipeline;
    }

    private void closeOwned() {
        close(renderPipeline); renderPipeline = null;
        close(renderData); renderData = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static float framePhase(long frameId) {
        return (float) Math.floorMod(frameId, 1024L) / 1024.0f;
    }

    private static float seedPhase(long seed) {
        long mixed = seed ^ (seed >>> 33) ^ (seed << 11);
        return (float) (mixed & 0x00FF_FFFFL) / 16777216.0f;
    }

    private static float wrapOrigin(int value) {
        return Math.floorMod(value, 65536);
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume volume = context.resources().storageVolume(resource);
        if (volume == null) throw new IllegalStateException("Missing storage volume for " + resource);
        return volume;
    }

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred buffer is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
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

    private record GroupOutputs(
            CloudDomainGroup group,
            String name,
            DeferredResource radiance,
            DeferredResource depth,
            DeferredResource reprojection
    ) {
    }
}
