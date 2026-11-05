/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.AerialPerspectiveLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
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
import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.SkyEnvironmentDescriptor;
import combatant.client.render.engine.world.SkyEnvironmentProvider;
import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Neutral clear-atmosphere Overworld sky producer. This class only solves optical transport from
 * the explicit atmosphere/celestial contracts; artistic grading, clouds and biome palettes live in
 * later providers/passes.
 */
public final class OverworldAtmosphereSkyProvider implements SkyEnvironmentProvider {
    public static final OverworldAtmosphereSkyProvider INSTANCE = new OverworldAtmosphereSkyProvider();

    private static final int LOCAL_SIZE = 8;
    private static final Identifier TRANSMITTANCE_SHADER = id("deferred/atmosphere_transmittance");
    private static final Identifier MULTISCATTER_SHADER = id("deferred/atmosphere_multiscatter");
    private static final Identifier SKY_SHADER = id("deferred/atmosphere_sky_radiance");
    private static final Identifier AERIAL_SHADER = id("deferred/atmosphere_aerial_perspective");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("radii", Std430Type.VEC4)
            .member("rayleigh", Std430Type.VEC4)
            .member("mieScattering", Std430Type.VEC4)
            .member("mieExtinction", Std430Type.VEC4)
            .member("ozone", Std430Type.VEC4)
            .member("ozoneShapeGroundR", Std430Type.VEC4)
            .member("groundGB", Std430Type.VEC4)
            .member("sunDirection", Std430Type.VEC4)
            .member("sunRadiance", Std430Type.VEC4)
            .member("moonDirection", Std430Type.VEC4)
            .member("moonRadiance", Std430Type.VEC4)
            .member("aerial", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout TRANSMITTANCE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout MULTISCATTER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout SKY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout AERIAL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiStorageBuffer params;
    private RhiComputePipeline transmittancePipeline;
    private RhiComputePipeline multiscatterPipeline;
    private RhiComputePipeline skyPipeline;
    private RhiComputePipeline aerialPipeline;
    private long opticalSignature = Long.MIN_VALUE;

    private OverworldAtmosphereSkyProvider() {
    }

    @Override
    public Identifier id() {
        return DimensionRenderProfileRegistry.OVERWORLD_SKY;
    }

    @Override
    public SkyEnvironmentDescriptor describe(WorldRenderState worldState, long frameId) {
        boolean valid = worldState != null
                && id().equals(worldState.skyProvider())
                && worldState.atmosphereState().valid()
                && worldState.celestialState().valid();
        return new SkyEnvironmentDescriptor(id(), frameId, valid, SkyEnvironmentDescriptor.UpdatePolicy.EVERY_FRAME);
    }

    @Override
    public void render(RenderContext context, RhiStorageImage target) {
        throw new UnsupportedOperationException("Overworld atmosphere requires the canonical environment TargetSet");
    }

    @Override
    public void render(RenderContext context, TargetSet targets) {
        if (context == null || context.rhi() == null || targets == null) return;
        AtmosphereState atmosphere = context.worldState().atmosphereState();
        if (!atmosphere.valid()) return;

        ensureOwner(context.rhi());
        ensureResources();
        uploadParams(context, atmosphere);

        long signature = opticalSignature(atmosphere);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (signature != opticalSignature) {
            dispatchTransmittance(context.rhi(), targets.atmosphereTransmittance());
            barrier(context.rhi(), targets.atmosphereTransmittance());
            dispatchMultiscatter(context.rhi(), targets.atmosphereTransmittance(), targets.atmosphereMultiScattering(), linear);
            barrier(context.rhi(), targets.atmosphereMultiScattering());
            opticalSignature = signature;
        }

        dispatchSky(context.rhi(), targets.atmosphereTransmittance(), targets.atmosphereMultiScattering(),
                targets.skyRadiance(), linear);
        dispatchAerial(context.rhi(), targets.atmosphereTransmittance(), targets.atmosphereMultiScattering(),
                targets.aerialPerspective(), targets.aerialTransmittance(), linear);
    }

    @Override
    public void releaseBackendResources(CombatantRhi releaseOwner) {
        if (owner != null && releaseOwner != null && owner != releaseOwner) return;
        closeOwned();
        owner = null;
    }

    private void uploadParams(RenderContext context, AtmosphereState a) {
        DirectionalLightDescriptor sun = context.worldState().celestialState().sun();
        DirectionalLightDescriptor moon = context.worldState().celestialState().moon();
        double cameraY = context.view() != null ? context.view().cameraPosition().y : 0.0;
        float viewFarPlane = context.view() != null ? context.view().farPlane() : 0.0f;
        float aerialDistanceKm = Math.max(0.064f,
                ParticipatingMediaRange.resolveBlocks(context.worldState(), viewFarPlane) * 0.001f);
        float observerRadius = a.planetRadiusKm() + (float) Math.max(0.0, cameraY * 0.001);
        observerRadius = Math.min(observerRadius, a.atmosphereRadiusKm() - 0.001f);

        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "radii", a.planetRadiusKm(), a.atmosphereRadiusKm(), observerRadius, 0.001f)
                .putVec4(0, "rayleigh", a.rayleighRed(), a.rayleighGreen(), a.rayleighBlue(), a.rayleighScaleHeightKm())
                .putVec4(0, "mieScattering", a.mieScatteringRed(), a.mieScatteringGreen(), a.mieScatteringBlue(), a.mieScaleHeightKm())
                .putVec4(0, "mieExtinction", a.mieExtinctionRed(), a.mieExtinctionGreen(), a.mieExtinctionBlue(), a.mieAnisotropy())
                .putVec4(0, "ozone", a.ozoneAbsorptionRed(), a.ozoneAbsorptionGreen(), a.ozoneAbsorptionBlue(), a.ozoneCenterKm())
                .putVec4(0, "ozoneShapeGroundR", a.ozoneWidthKm(), a.groundAlbedoRed(), 0.0f, 0.0f)
                .putVec4(0, "groundGB", a.groundAlbedoGreen(), a.groundAlbedoBlue(), 0.0f, 0.0f)
                .putVec4(0, "sunDirection", sun.directionX(), sun.directionY(), sun.directionZ(), sun.angularRadiusRadians())
                .putVec4(0, "sunRadiance", sun.radianceRed(), sun.radianceGreen(), sun.radianceBlue(), sun.valid() ? 1.0f : 0.0f)
                .putVec4(0, "moonDirection", moon.directionX(), moon.directionY(), moon.directionZ(), moon.angularRadiusRadians())
                .putVec4(0, "moonRadiance", moon.radianceRed(), moon.radianceGreen(), moon.radianceBlue(), moon.valid() ? 1.0f : 0.0f)
                .putVec4(0, "aerial", aerialDistanceKm, AerialPerspectiveLayout.ZENITH_SLICES,
                        AerialPerspectiveLayout.AZIMUTH_SLICES, 0.0f);
        params.upload(writer.buffer(), 0L);
    }

    private void dispatchTransmittance(CombatantRhi rhi, RhiStorageImage target) {
        rhi.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant atmosphere transmittance LUT", transmittancePipeline(),
                groups(target.descriptor().width()), groups(target.descriptor().height()), 1,
                List.of(new StorageBinding(1, params, 0L, PARAMS_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                List.of(), List.of(new StorageImageBinding(0, target, StorageAccess.WRITE_ONLY))
        ));
    }

    private void dispatchMultiscatter(CombatantRhi rhi, RhiStorageImage transmittance, RhiStorageImage target, GpuSampler sampler) {
        rhi.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant atmosphere multiple scattering LUT", multiscatterPipeline(),
                groups(target.descriptor().width()), groups(target.descriptor().height()), 1,
                List.of(new StorageBinding(2, params, 0L, PARAMS_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, transmittance.view(), sampler)),
                List.of(new StorageImageBinding(1, target, StorageAccess.WRITE_ONLY))
        ));
    }

    private void dispatchSky(CombatantRhi rhi, RhiStorageImage transmittance, RhiStorageImage multiscatter,
                             RhiStorageImage target, GpuSampler sampler) {
        rhi.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant atmosphere sky radiance", skyPipeline(),
                groups(target.descriptor().width()), groups(target.descriptor().height()), 1,
                List.of(new StorageBinding(3, params, 0L, PARAMS_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, transmittance.view(), sampler),
                        new SampledTextureBinding(1, multiscatter.view(), sampler)
                ),
                List.of(new StorageImageBinding(2, target, StorageAccess.WRITE_ONLY))
        ));
    }

    private void dispatchAerial(CombatantRhi rhi, RhiStorageImage transmittance, RhiStorageImage multiscatter,
                                RhiStorageImage radianceTarget, RhiStorageImage transmittanceTarget,
                                GpuSampler sampler) {
        rhi.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant atmosphere aerial perspective LUT", aerialPipeline(),
                groups(radianceTarget.descriptor().width()), groups(radianceTarget.descriptor().height()), 1,
                List.of(new StorageBinding(3, params, 0L, PARAMS_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, transmittance.view(), sampler),
                        new SampledTextureBinding(1, multiscatter.view(), sampler)
                ),
                List.of(
                        new StorageImageBinding(2, radianceTarget, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, transmittanceTarget, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureResources() {
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-overworld-atmosphere-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
    }

    private RhiComputePipeline transmittancePipeline() {
        if (transmittancePipeline == null) transmittancePipeline = pipeline("combatant-atmosphere-transmittance", TRANSMITTANCE_SHADER, TRANSMITTANCE_LAYOUT);
        return transmittancePipeline;
    }

    private RhiComputePipeline multiscatterPipeline() {
        if (multiscatterPipeline == null) multiscatterPipeline = pipeline("combatant-atmosphere-multiscatter", MULTISCATTER_SHADER, MULTISCATTER_LAYOUT);
        return multiscatterPipeline;
    }

    private RhiComputePipeline skyPipeline() {
        if (skyPipeline == null) skyPipeline = pipeline("combatant-atmosphere-sky", SKY_SHADER, SKY_LAYOUT);
        return skyPipeline;
    }

    private RhiComputePipeline aerialPipeline() {
        if (aerialPipeline == null) aerialPipeline = pipeline("combatant-atmosphere-aerial", AERIAL_SHADER, AERIAL_LAYOUT);
        return aerialPipeline;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private static void barrier(CombatantRhi rhi, RhiStorageImage image) {
        rhi.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of(image)
        ));
    }

    private void closeOwned() {
        close(transmittancePipeline); transmittancePipeline = null;
        close(multiscatterPipeline); multiscatterPipeline = null;
        close(skyPipeline); skyPipeline = null;
        close(aerialPipeline); aerialPipeline = null;
        close(params); params = null;
        opticalSignature = Long.MIN_VALUE;
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static long opticalSignature(AtmosphereState a) {
        long h = 1469598103934665603L;
        h = mix(h, a.planetRadiusKm()); h = mix(h, a.atmosphereTopKm());
        h = mix(h, a.rayleighRed()); h = mix(h, a.rayleighGreen()); h = mix(h, a.rayleighBlue()); h = mix(h, a.rayleighScaleHeightKm());
        h = mix(h, a.mieScatteringRed()); h = mix(h, a.mieScatteringGreen()); h = mix(h, a.mieScatteringBlue());
        h = mix(h, a.mieExtinctionRed()); h = mix(h, a.mieExtinctionGreen()); h = mix(h, a.mieExtinctionBlue());
        h = mix(h, a.mieScaleHeightKm()); h = mix(h, a.mieAnisotropy());
        h = mix(h, a.ozoneAbsorptionRed()); h = mix(h, a.ozoneAbsorptionGreen()); h = mix(h, a.ozoneAbsorptionBlue());
        h = mix(h, a.ozoneCenterKm()); h = mix(h, a.ozoneWidthKm());
        return h;
    }

    private static long mix(long h, float value) {
        h ^= Float.floatToIntBits(value);
        return h * 1099511628211L;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
