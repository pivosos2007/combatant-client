/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiTextureUsage;
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
import combatant.client.render.engine.rhi.shader.StorageVolumeBinding;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import combatant.client.render.engine.world.environment.CloudDomainProfile;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Camera-centered hierarchical occupancy shared by cloud traversal, shadows and participating media. */
final class DeferredCloudOccupancySource implements AutoCloseable {
    private static final int LOCAL_SIZE = 4;
    private static final Identifier BUILD_SHADER = id("deferred/cloud_occupancy_build");
    private static final Identifier REDUCE_SHADER = id("deferred/cloud_occupancy_reduce");

    private static final Std430StructLayout BUILD_DATA_LAYOUT = Std430StructLayout.builder()
            .member("grid", Std430Type.VEC4)
            .member("macroGrid", Std430Type.VEC4)
            .member("macroOrigin", Std430Type.VEC4)
            .member("counts", Std430Type.VEC4)
            .member("cameraTime", Std430Type.VEC4)
            .member("noiseDomain", Std430Type.VEC4)
            .member("occupancyDomain", Std430Type.VEC4)
            .member("occupancyExtent", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout BUILD_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout REDUCE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.WRITE_ONLY)
    ));

    record FrameState(RhiStorageVolume volume,
                      float originLocalX,
                      float originLocalZ,
                      float minimumY,
                      float maximumY,
                      float spanXZ,
                      boolean active) {
        static final FrameState NONE = new FrameState(null, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, false);
    }

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private final DeferredCloudFieldSource fieldSource;
    private CombatantRhi owner;
    private RhiComputePipeline buildPipeline;
    private RhiComputePipeline reducePipeline;
    private RhiStorageBuffer buildData;
    private RhiStorageVolume occupancy;
    private FrameState current = FrameState.NONE;

    DeferredCloudOccupancySource(DeferredCloudFieldSource fieldSource) {
        if (fieldSource == null) throw new IllegalArgumentException("fieldSource");
        this.fieldSource = fieldSource;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.cloud.occupancy", DeferredStage.PRE_LIGHTING)
                .priority(650)
                .feature(DeferredFeature.CLOUDS)
                .write(DeferredResource.CLOUD_OCCUPANCY)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null)
                .execute(this::build)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        buildPipeline();
        reducePipeline();
        ensureResources();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
        current = FrameState.NONE;
    }

    FrameState current() {
        return current;
    }

    private void build(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureResources();
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        DeferredCloudFieldSource.FrameData field = fieldSource.prepareFrame(context);
        FrameState state = frameState(view.cameraPosition(), field);
        current = state;
        context.resources().bindStorageVolume(DeferredResource.CLOUD_OCCUPANCY, occupancy);

        Std430Writer writer = buildWriter(view.cameraPosition(), field, state);
        buildData.upload(writer.buffer(), 0L);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud occupancy mip0", buildPipeline(),
                groups(config.occupancyWidth()), groups(config.occupancyHeight()), groups(config.occupancyDepth()),
                List.of(
                        new StorageBinding(1, buildData, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(2, fieldSource.weatherData(), 0L,
                                fieldSource.weatherData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(3, fieldSource.domainData(), 0L,
                                fieldSource.domainData().descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(4, fieldSource.macroWeatherData(), 0L,
                                fieldSource.macroWeatherData().descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(), List.of(),
                List.of(new StorageVolumeBinding(0, occupancy, StorageAccess.WRITE_ONLY, 0)),
                List.of()
        ));

        GpuSampler nearest = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                .getClampToEdge(FilterMode.NEAREST);
        for (int mip = 1; mip < occupancy.descriptor().mipLevels(); mip++) {
            barrier(context);
            int width = occupancy.descriptor().mipWidth(mip);
            int height = occupancy.descriptor().mipHeight(mip);
            int depth = occupancy.descriptor().mipDepth(mip);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant cloud occupancy reduce mip " + mip, reducePipeline(),
                    groups(width), groups(height), groups(depth),
                    List.of(), List.of(), List.of(),
                    List.of(new StorageVolumeBinding(1, occupancy, StorageAccess.WRITE_ONLY, mip)),
                    List.of(new SampledVolumeBinding(0, occupancy.sampledMipView(mip - 1), nearest))
            ));
        }
        barrier(context);
    }

    private FrameState frameState(Vec3 camera, DeferredCloudFieldSource.FrameData field) {
        CloudProfile profile = field.profile();
        if (!field.active() || profile.domains().isEmpty()) {
            return new FrameState(occupancy, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, false);
        }
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (CloudDomainProfile domain : profile.domains()) {
            minY = Math.min(minY, domain.minimumAltitudeBlocks());
            maxY = Math.max(maxY, domain.maximumAltitudeBlocks());
        }
        if (!Float.isFinite(minY) || !Float.isFinite(maxY) || maxY <= minY) {
            minY = 0.0f;
            maxY = 1.0f;
        }
        float span = Math.max(256.0f, profile.maxRayDistanceBlocks() * 2.0f);
        float cell = span / config.occupancyWidth();
        float originWorldX = snap((float) camera.x - span * 0.5f, cell);
        float originWorldZ = snap((float) camera.z - span * 0.5f, cell);
        WeatherFieldState local = field.field();
        float weatherOriginX = local.valid() ? local.originBlockX() : (float) camera.x;
        float weatherOriginZ = local.valid() ? local.originBlockZ() : (float) camera.z;
        return new FrameState(occupancy,
                originWorldX - weatherOriginX,
                originWorldZ - weatherOriginZ,
                minY, maxY, span, true);
    }

    private Std430Writer buildWriter(Vec3 camera,
                                     DeferredCloudFieldSource.FrameData field,
                                     FrameState state) {
        WeatherFieldState local = field.field();
        WeatherFieldState macro = field.macroField();
        WeatherState weather = field.weather();
        float localOriginX = local.valid() ? local.originBlockX() : (float) camera.x;
        float localOriginZ = local.valid() ? local.originBlockZ() : (float) camera.z;
        float localSpacing = local.valid() ? Math.max(1, local.spacingBlocks()) : 1.0f;
        float macroSpacing = macro.valid() ? Math.max(1, macro.spacingBlocks()) : 1.0f;
        float macroOriginX = macro.valid() ? macro.originBlockX() - localOriginX : 0.0f;
        float macroOriginZ = macro.valid() ? macro.originBlockZ() - localOriginZ : 0.0f;
        float timeSeconds = weather.valid() ? (float) weather.renderAdvectionSeconds() : 0.0f;

        return new Std430Writer(BUILD_DATA_LAYOUT, 1)
                .putVec4(0, "grid", localSpacing,
                        local.valid() ? local.gridWidth() : 0,
                        local.valid() ? local.gridDepth() : 0,
                        field.weatherCount())
                .putVec4(0, "macroGrid", macroSpacing,
                        macro.valid() ? macro.gridWidth() : 0,
                        macro.valid() ? macro.gridDepth() : 0,
                        field.macroWeatherCount())
                .putVec4(0, "macroOrigin", macroOriginX, macroOriginZ, 0.0f, 0.0f)
                .putVec4(0, "counts", field.domainCount(), state.active() ? 1.0f : 0.0f, 0.0f, 0.0f)
                .putVec4(0, "cameraTime", (float) (camera.x - localOriginX), (float) camera.y,
                        (float) (camera.z - localOriginZ), timeSeconds)
                .putVec4(0, "noiseDomain", wrapOrigin(local.valid() ? local.originBlockX() : 0),
                        wrapOrigin(local.valid() ? local.originBlockZ() : 0), seedPhase(weather.modelSeed()), 0.0f)
                .putVec4(0, "occupancyDomain", state.originLocalX(), state.originLocalZ(),
                        state.minimumY(), state.maximumY())
                .putVec4(0, "occupancyExtent", state.spanXZ(),
                        Math.max(1.0f, state.maximumY() - state.minimumY()),
                        config.occupancyWidth(), config.occupancyHeight());
    }

    private void barrier(DeferredPassContext context) {
        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ_WRITE,
                List.of(), List.of(), List.of(occupancy)
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureResources() {
        if (owner == null) throw new IllegalStateException("Cloud occupancy source has no RHI owner");
        if (buildData == null) buildData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-occupancy-data", BUILD_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        if (occupancy == null) occupancy = owner.advancedShaders().createStorageVolume(new StorageVolumeDescriptor(
                "combatant-cloud-occupancy",
                config.occupancyWidth(), config.occupancyHeight(), config.occupancyDepth(),
                GpuFormat.R32_FLOAT, StorageAccess.READ_WRITE,
                RhiTextureUsage.STORAGE_IMAGE | GpuTexture.USAGE_TEXTURE_BINDING,
                config.occupancyMipLevels()));
    }

    private RhiComputePipeline buildPipeline() {
        if (buildPipeline == null) buildPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-occupancy-build", BUILD_SHADER, BUILD_LAYOUT));
        return buildPipeline;
    }

    private RhiComputePipeline reducePipeline() {
        if (reducePipeline == null) reducePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-occupancy-reduce", REDUCE_SHADER, REDUCE_LAYOUT));
        return reducePipeline;
    }

    private void closeOwned() {
        close(buildPipeline); buildPipeline = null;
        close(reducePipeline); reducePipeline = null;
        close(buildData); buildData = null;
        close(occupancy); occupancy = null;
        current = FrameState.NONE;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static int groups(int value) {
        return Math.max(1, (value + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static float snap(float value, float step) {
        if (!Float.isFinite(value) || !Float.isFinite(step) || step <= 0.0f) return value;
        return (float) Math.floor(value / step) * step;
    }

    private static float seedPhase(long seed) {
        long mixed = seed ^ (seed >>> 33) ^ (seed << 11);
        return (float) ((mixed & 0x00ffffffL) / (double) 0x01000000L);
    }

    private static float wrapOrigin(int coordinate) {
        return Math.floorMod(coordinate, 1 << 20);
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) { }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
