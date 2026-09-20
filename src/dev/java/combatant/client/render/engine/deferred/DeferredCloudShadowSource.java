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
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Produces a camera-centered world-space cloud shadow field and resolves it for opaque lighting. */
final class DeferredCloudShadowSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier MAP_SHADER = id("deferred/cloud_shadow_map");
    private static final Identifier RESOLVE_SHADER = id("deferred/cloud_shadow_resolve");

    private static final Std430StructLayout MAP_DATA_LAYOUT = Std430StructLayout.builder()
            .member("cameraTime", Std430Type.VEC4)
            .member("grid", Std430Type.VEC4)
            .member("macroGrid", Std430Type.VEC4)
            .member("macroOrigin", Std430Type.VEC4)
            .member("counts", Std430Type.VEC4)
            .member("sunDirection", Std430Type.VEC4)
            .member("noiseDomain", Std430Type.VEC4)
            .member("mapDomain", Std430Type.VEC4)
            .member("occupancyDomain", Std430Type.VEC4)
            .member("occupancyPolicy", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout RESOLVE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("camera", Std430Type.VEC4)
            .member("mapDomain", Std430Type.VEC4)
            .member("sunDirection", Std430Type.VEC4)
            .member("cloudBounds", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout MAP_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private final DeferredCloudFieldSource fieldSource;
    private final DeferredCloudOccupancySource occupancySource;
    private CombatantRhi owner;
    private RhiComputePipeline mapPipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiStorageBuffer mapData;
    private RhiStorageBuffer resolveData;

    record FrameState(float mapOriginRelativeX,
                      float mapOriginRelativeZ,
                      float spanBlocks,
                      float referenceY,
                      float sunX,
                      float sunY,
                      float sunZ,
                      boolean active,
                      float minCloudY,
                      float maxCloudY,
                      int altitudeSlices) { }

    DeferredCloudShadowSource(DeferredCloudFieldSource fieldSource, DeferredCloudOccupancySource occupancySource) {
        if (fieldSource == null) throw new IllegalArgumentException("fieldSource");
        if (occupancySource == null) throw new IllegalArgumentException("occupancySource");
        this.fieldSource = fieldSource;
        this.occupancySource = occupancySource;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.cloud.shadow-map", DeferredStage.PRE_LIGHTING)
                .priority(700)
                .feature(DeferredFeature.CLOUDS)
                .read(DeferredResource.CLOUD_OCCUPANCY)
                .write(DeferredResource.CLOUD_SHADOW_MAP)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.CLOUD_OCCUPANCY))
                .execute(this::renderShadowMap)
                .build());
        passes.add(DeferredPassSpec.builder("world.cloud.shadow-resolve", DeferredStage.PRE_LIGHTING)
                .priority(710)
                .feature(DeferredFeature.CLOUDS)
                .read(DeferredResource.CLOUD_SHADOW_MAP, DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.CLOUD_SHADOW_VISIBILITY)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.CLOUD_SHADOW_MAP)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::resolveVisibility)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        mapPipeline();
        resolvePipeline();
        mapData();
        resolveData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void renderShadowMap(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        RhiStorageImage shadowMap = requireImage(context, DeferredResource.CLOUD_SHADOW_MAP);
        DeferredCloudFieldSource.FrameData cloudField = fieldSource.prepareFrame(context);
        WeatherFieldState field = cloudField.field();
        WeatherFieldState macro = cloudField.macroField();
        WeatherState weather = cloudField.weather();
        Vec3 camera = view.cameraPosition();

        float spacing = field.valid() ? Math.max(1, field.spacingBlocks()) : 1.0f;
        int width = field.valid() ? field.gridWidth() : 0;
        int depth = field.valid() ? field.gridDepth() : 0;
        float originX = field.valid() ? field.originBlockX() : (float) camera.x;
        float originZ = field.valid() ? field.originBlockZ() : (float) camera.z;
        float timeSeconds = weather.valid() ? (float) weather.renderAdvectionSeconds() : 0.0f;
        float macroSpacing = macro.valid() ? Math.max(1, macro.spacingBlocks()) : 1.0f;
        int macroWidth = macro.valid() ? macro.gridWidth() : 0;
        int macroDepth = macro.valid() ? macro.gridDepth() : 0;
        float macroOriginX = macro.valid() ? macro.originBlockX() - originX : 0.0f;
        float macroOriginZ = macro.valid() ? macro.originBlockZ() - originZ : 0.0f;
        FrameState shadow = frameState(context, view, cloudField);
        DeferredCloudOccupancySource.FrameState occupancyState = occupancySource.current();
        RhiStorageVolume occupancyVolume = requireVolume(context, DeferredResource.CLOUD_OCCUPANCY);

        Std430Writer writer = new Std430Writer(MAP_DATA_LAYOUT, 1)
                .putVec4(0, "cameraTime", (float) (camera.x - originX), (float) camera.y,
                        (float) (camera.z - originZ), timeSeconds)
                .putVec4(0, "grid", spacing, width, depth, cloudField.weatherCount())
                .putVec4(0, "macroGrid", macroSpacing, macroWidth, macroDepth, cloudField.macroWeatherCount())
                .putVec4(0, "macroOrigin", macroOriginX, macroOriginZ, 0.0f, 0.0f)
                .putVec4(0, "counts", cloudField.domainCount(), config.shadowSteps(), shadow.active() ? 1.0f : 0.0f,
                        shadow.altitudeSlices())
                .putVec4(0, "sunDirection", shadow.sunX(), shadow.sunY(), shadow.sunZ(), shadow.active() ? 1.0f : 0.0f)
                .putVec4(0, "noiseDomain", wrapOrigin(field.valid() ? field.originBlockX() : 0),
                        wrapOrigin(field.valid() ? field.originBlockZ() : 0), seedPhase(weather.modelSeed()), 0.0f)
                .putVec4(0, "mapDomain", shadow.mapOriginRelativeX(), shadow.mapOriginRelativeZ(),
                        shadow.spanBlocks(), shadow.referenceY())
                .putVec4(0, "occupancyDomain", occupancyState.originLocalX(), occupancyState.originLocalZ(),
                        occupancyState.minimumY(), occupancyState.maximumY())
                .putVec4(0, "occupancyPolicy", occupancyState.spanXZ(), occupancyState.active() ? 1.0f : 0.0f,
                        0.0f, 0.0f);
        RhiStorageBuffer data = mapData();
        data.upload(writer.buffer(), 0L);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud shadow map", mapPipeline(),
                groups(shadowMap.descriptor().width()),
                groups(Math.max(1, shadowMap.descriptor().height() / config.shadowAltitudeSlices())), 1,
                List.of(
                        new StorageBinding(1, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(2, fieldSource.weatherData(), 0L,
                                fieldSource.weatherData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(3, fieldSource.domainData(), 0L,
                                fieldSource.domainData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(4, fieldSource.macroWeatherData(), 0L,
                                fieldSource.macroWeatherData().descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(),
                List.of(new StorageImageBinding(0, shadowMap, StorageAccess.WRITE_ONLY)),
                List.of(),
                List.of(new SampledVolumeBinding(5, occupancyVolume,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)))
        ));
    }

    private void resolveVisibility(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        DeferredCloudFieldSource.FrameData cloudField = fieldSource.prepareFrame(context);
        Vec3 camera = view.cameraPosition();
        FrameState shadow = frameState(context, view, cloudField);

        Std430Writer writer = new Std430Writer(RESOLVE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "camera", (float) camera.x, (float) camera.y, (float) camera.z, 0.0f)
                .putVec4(0, "mapDomain", shadow.mapOriginRelativeX(), shadow.mapOriginRelativeZ(),
                        shadow.spanBlocks(), shadow.referenceY())
                .putVec4(0, "sunDirection", shadow.sunX(), shadow.sunY(), shadow.sunZ(), shadow.active() ? 1.0f : 0.0f)
                .putVec4(0, "cloudBounds", shadow.minCloudY(), shadow.maxCloudY(), zeroToOneDepth(context) ? 1.0f : 0.0f,
                        shadow.altitudeSlices());
        RhiStorageBuffer data = resolveData();
        data.upload(writer.buffer(), 0L);

        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView shadowMap = requireTexture(context, DeferredResource.CLOUD_SHADOW_MAP);
        RhiStorageImage visibility = requireImage(context, DeferredResource.CLOUD_SHADOW_VISIBILITY);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud shadow resolve", resolvePipeline(),
                groups(visibility.descriptor().width()), groups(visibility.descriptor().height()), 1,
                List.of(new StorageBinding(3, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, resolvedDepth, nearest),
                        new SampledTextureBinding(1, shadowMap, linear)
                ),
                List.of(new StorageImageBinding(2, visibility, StorageAccess.WRITE_ONLY))
        ));
    }

    FrameState frameState(DeferredPassContext context,
                          DeferredPrimaryViewSource.FrameView view,
                          DeferredCloudFieldSource.FrameData cloudField) {
        DirectionalLightDescriptor sun = context.worldState().directionalLight();
        Vec3 camera = view.cameraPosition();
        float span = config.shadowMapSpanBlocks();
        float texel = config.shadowTexelBlocks();
        double snappedWorldX = Math.floor(camera.x / texel) * texel;
        double snappedWorldZ = Math.floor(camera.z / texel) * texel;
        float mapOriginRelativeX = (float) (snappedWorldX - camera.x - span * 0.5);
        float mapOriginRelativeZ = (float) (snappedWorldZ - camera.z - span * 0.5);
        CloudProfile profile = cloudField.profile();
        float minCloudY = minimumCloudAltitude(profile, cloudField.domainCount());
        float maxCloudY = maximumCloudAltitude(profile, cloudField.domainCount());
        if (!Float.isFinite(minCloudY) || !Float.isFinite(maxCloudY) || maxCloudY <= minCloudY) {
            minCloudY = 0.0f;
            maxCloudY = 1.0f;
        }
        boolean active = cloudField.active() && sun.valid() && sun.directionY() > 0.02f;
        return new FrameState(
                mapOriginRelativeX, mapOriginRelativeZ, span, 0.0f,
                sun.directionX(), sun.directionY(), sun.directionZ(), active,
                minCloudY, maxCloudY, config.shadowAltitudeSlices()
        );
    }

    private RhiComputePipeline mapPipeline() {
        if (owner == null) throw new IllegalStateException("Cloud shadow source has no RHI owner");
        if (mapPipeline == null) mapPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-shadow-map", MAP_SHADER, MAP_LAYOUT)
        );
        return mapPipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (owner == null) throw new IllegalStateException("Cloud shadow source has no RHI owner");
        if (resolvePipeline == null) resolvePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-shadow-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT)
        );
        return resolvePipeline;
    }

    private RhiStorageBuffer mapData() {
        if (owner == null) throw new IllegalStateException("Cloud shadow source has no RHI owner");
        if (mapData == null) mapData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-shadow-map-data", MAP_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return mapData;
    }

    private RhiStorageBuffer resolveData() {
        if (owner == null) throw new IllegalStateException("Cloud shadow source has no RHI owner");
        if (resolveData == null) resolveData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-shadow-resolve-data", RESOLVE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return resolveData;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void closeOwned() {
        close(mapPipeline); mapPipeline = null;
        close(resolvePipeline); resolvePipeline = null;
        close(mapData); mapData = null;
        close(resolveData); resolveData = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static float minimumCloudAltitude(CloudProfile profile, int domainCount) {
        float result = Float.POSITIVE_INFINITY;
        int count = Math.min(domainCount, profile.domains().size());
        for (int i = 0; i < count; i++) result = Math.min(result, profile.domains().get(i).minimumAltitudeBlocks());
        return Float.isFinite(result) ? result : Float.POSITIVE_INFINITY;
    }

    private static float maximumCloudAltitude(CloudProfile profile, int domainCount) {
        float result = Float.NEGATIVE_INFINITY;
        int count = Math.min(domainCount, profile.domains().size());
        for (int i = 0; i < count; i++) result = Math.max(result, profile.domains().get(i).maximumAltitudeBlocks());
        return Float.isFinite(result) ? result : Float.NEGATIVE_INFINITY;
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

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume volume = context.resources().storageVolume(resource);
        if (volume == null) throw new IllegalStateException("Missing storage volume for " + resource);
        return volume;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
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
}
