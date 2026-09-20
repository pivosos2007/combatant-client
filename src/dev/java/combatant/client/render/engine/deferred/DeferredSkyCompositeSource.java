/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
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
import combatant.client.render.engine.world.environment.AtmosphereState;
import combatant.client.render.engine.world.environment.CelestialRenderProfile;
import combatant.client.render.engine.world.environment.CelestialRenderProfileRegistry;
import combatant.client.render.engine.world.environment.CelestialState;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Fills empty primary-view depth with the canonical HDR sky before media composition. */
final class DeferredSkyCompositeSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "shaders/deferred/sky_composite.comp");
    private static final Identifier BYPASS_SHADER = id("deferred/bloom_copy");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("flags", Std430Type.VEC4)
            .member("atmosphereRadii", Std430Type.VEC4)
            .member("sunDirection", Std430Type.VEC4)
            .member("sunAppearance", Std430Type.VEC4)
            .member("moonDirection", Std430Type.VEC4)
            .member("moonAppearance", Std430Type.VEC4)
            .member("moonPolicy", Std430Type.VEC4)
            .member("starPolicy0", Std430Type.VEC4)
            .member("starPolicy1", Std430Type.VEC4)
            .member("starWarm", Std430Type.VEC4)
            .member("starCool", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout SKY_STATE_LAYOUT = Std430StructLayout.builder()
            .member("state0", Std430Type.VEC4)
            .member("state1", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout BYPASS_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiComputePipeline bypassPipeline;
    private RhiStorageBuffer data;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.sky.bypass", DeferredStage.SKY_COMPOSITE)
                .priority(-10)
                .read(DeferredResource.OPAQUE_REFLECTED_RADIANCE)
                .write(DeferredResource.SKY_COMPOSITED_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.OPAQUE_REFLECTED_RADIANCE)
                        && (!context.featureEnabled(DeferredFeature.SKY) || !skyInputsValid(context)))
                .execute(this::bypass)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.sky.composite", DeferredStage.SKY_COMPOSITE)
                .feature(DeferredFeature.SKY)
                .read(DeferredResource.OPAQUE_REFLECTED_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.SKY_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE,
                        DeferredResource.ATMOSPHERE_TRANSMITTANCE)
                .write(DeferredResource.SKY_COMPOSITED_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.OPAQUE_REFLECTED_RADIANCE)
                        && skyInputsValid(context))
                .execute(this::composite)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        bypassPipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void bypass(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage source = requireImage(context, DeferredResource.OPAQUE_REFLECTED_RADIANCE);
        RhiStorageImage output = requireImage(context, DeferredResource.SKY_COMPOSITED_RADIANCE);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant sky neutral bypass", bypassPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(
                        new StorageImageBinding(0, source, StorageAccess.READ_ONLY),
                        new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void composite(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView base = requireTexture(context, DeferredResource.OPAQUE_REFLECTED_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView sky = requireTexture(context, DeferredResource.SKY_RADIANCE);
        GpuTextureView transmittance = requireTexture(context, DeferredResource.ATMOSPHERE_TRANSMITTANCE);
        RhiStorageBuffer skyState = requireBuffer(context, DeferredResource.SKY_ENVIRONMENT_STATE);
        RhiStorageImage output = requireImage(context, DeferredResource.SKY_COMPOSITED_RADIANCE);

        CelestialState celestial = context.worldState().celestialState();
        CelestialRenderProfile profile = CelestialRenderProfileRegistry.resolve(context.worldState().celestialModel());
        AtmosphereState atmosphere = context.worldState().atmosphereState();
        boolean celestialVisuals = celestial.valid() && profile != null && profile.valid();
        float observerRadius = atmosphere.valid()
                ? Math.min(atmosphere.atmosphereRadiusKm() - 0.001f,
                        atmosphere.planetRadiusKm() + (float) Math.max(0.0, view.cameraPosition().y * 0.001))
                : 0.0f;
        float atmosphereRange = atmosphere.valid() ? Math.max(atmosphere.atmosphereTopKm(), 0.001f) : 1.0f;

        float sunX = celestialVisuals ? celestial.sun().directionX() : 0.0f;
        float sunY = celestialVisuals ? celestial.sun().directionY() : 1.0f;
        float sunZ = celestialVisuals ? celestial.sun().directionZ() : 0.0f;
        float moonX = celestialVisuals ? celestial.moon().directionX() : 0.0f;
        float moonY = celestialVisuals ? celestial.moon().directionY() : -1.0f;
        float moonZ = celestialVisuals ? celestial.moon().directionZ() : 0.0f;

        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "flags", zeroToOneDepth(context) ? 1.0f : 0.0f, celestialVisuals ? 1.0f : 0.0f,
                        atmosphere.valid() ? 1.0f : 0.0f, celestial.environmentAttributesValid() ? 1.0f : 0.0f)
                .putVec4(0, "atmosphereRadii", atmosphere.valid() ? atmosphere.planetRadiusKm() : 0.0f,
                        atmosphere.valid() ? atmosphere.atmosphereRadiusKm() : 0.0f, observerRadius, atmosphereRange)
                .putVec4(0, "sunDirection", sunX, sunY, sunZ, celestialVisuals ? profile.sunAngularRadiusRadians() : 0.0f)
                .putVec4(0, "sunAppearance", celestialVisuals ? profile.sunRadianceRed() * profile.sunDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.sunRadianceGreen() * profile.sunDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.sunRadianceBlue() * profile.sunDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.sunLimbDarkening() : 0.0f)
                .putVec4(0, "moonDirection", moonX, moonY, moonZ, celestialVisuals ? profile.moonAngularRadiusRadians() : 0.0f)
                .putVec4(0, "moonAppearance", celestialVisuals ? profile.moonRadianceRed() * profile.moonDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.moonRadianceGreen() * profile.moonDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.moonRadianceBlue() * profile.moonDiskRadianceScale() : 0.0f,
                        celestialVisuals ? profile.moonEarthshine() : 0.0f)
                .putVec4(0, "moonPolicy", celestialVisuals ? celestial.moonPhase() : 0.0f,
                        celestialVisuals ? profile.moonSurfacePhaseExponent() : 1.0f,
                        celestialVisuals ? profile.moonDirectPhaseExponent() : 1.0f, 0.0f)
                .putVec4(0, "starPolicy0", celestialVisuals ? celestial.starAngleRadians() : 0.0f,
                        celestialVisuals ? celestial.starBrightness() : 0.0f,
                        celestialVisuals ? profile.starIntensity() : 0.0f,
                        celestialVisuals ? profile.starDensity() : 0.0f)
                .putVec4(0, "starPolicy1", celestialVisuals ? profile.starSize() : 0.0f,
                        celestialVisuals ? profile.starTwinkleStrength() : 0.0f,
                        celestialVisuals ? profile.starTwinkleSpeed() : 0.0f,
                        celestialVisuals ? (float) (celestial.worldClockTicks() / 20.0) : 0.0f)
                .putVec4(0, "starWarm", celestialVisuals ? profile.starWarmRed() : 0.0f,
                        celestialVisuals ? profile.starWarmGreen() : 0.0f,
                        celestialVisuals ? profile.starWarmBlue() : 0.0f,
                        celestialVisuals ? profile.starHorizonFadeStart() : 0.0f)
                .putVec4(0, "starCool", celestialVisuals ? profile.starCoolRed() : 0.0f,
                        celestialVisuals ? profile.starCoolGreen() : 0.0f,
                        celestialVisuals ? profile.starCoolBlue() : 0.0f,
                        celestialVisuals ? profile.starHorizonFadeEnd() : 0.0f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler skySampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, false
        );
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR sky composite", pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(4, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, skyState, 0L, SKY_STATE_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, sky, skySampler),
                        new SampledTextureBinding(6, transmittance, linear)
                ),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline bypassPipeline() {
        if (owner == null) throw new IllegalStateException("Sky composite source has no RHI owner");
        if (bypassPipeline == null) bypassPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-sky-bypass", BYPASS_SHADER, BYPASS_LAYOUT));
        return bypassPipeline;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) pipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-sky-composite", SHADER, LAYOUT)
        );
        return pipeline;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-sky-composite-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return data;
    }

    private void closeOwned() {
        close(pipeline); pipeline = null;
        close(bypassPipeline); bypassPipeline = null;
        close(data); data = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static boolean skyInputsValid(DeferredPassContext context) {
        return context.isValid(DeferredResource.RESOLVED_DEPTH)
                && context.isValid(DeferredResource.SKY_RADIANCE)
                && context.isValid(DeferredResource.SKY_ENVIRONMENT_STATE)
                && context.isValid(DeferredResource.ATMOSPHERE_TRANSMITTANCE)
                && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null
                && context.resources().texture(DeferredResource.SKY_RADIANCE) != null
                && context.resources().buffer(DeferredResource.SKY_ENVIRONMENT_STATE) != null
                && context.resources().texture(DeferredResource.ATMOSPHERE_TRANSMITTANCE) != null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
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

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
