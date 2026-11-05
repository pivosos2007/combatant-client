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
import combatant.client.render.engine.world.environment.MinecraftBaselineLightState;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable environment-light contract for deferred shading.
 *
 * <p>Advanced sky and colored-light producers remain independent additions. The neutral Minecraft
 * sky/block light baseline is reconstructed from the authoritative G-buffer light coordinates plus
 * frame-local environment semantics; the renderer never samples or bakes the vanilla lightmap into
 * albedo. Missing advanced producers therefore degrade to the baseline instead of black ambient.</p>
 */
final class DeferredEnvironmentIrradianceSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier ZERO_IRRADIANCE_SHADER = id("deferred/environment_zero_irradiance");
    private static final Identifier ENVIRONMENT_COMPOSE_SHADER = id("deferred/environment_irradiance_compose");

    private static final Std430StructLayout BASELINE_LIGHT_LAYOUT = Std430StructLayout.builder()
            .member("factors", Std430Type.VEC4)      // x=sky factor, y=dimension ambient, z=has sky, w=state valid
            .member("skyColor", Std430Type.VEC4)
            .member("blockColor", Std430Type.VEC4)
            .member("ambientColor", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout ZERO_IRRADIANCE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout ENVIRONMENT_COMPOSE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline zeroIrradiancePipeline;
    private RhiComputePipeline environmentComposePipeline;
    private RhiStorageBuffer baselineLightData;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.sky-diffuse.fallback", DeferredStage.PRE_LIGHTING)
                .priority(900)
                .write(DeferredResource.SKY_DIFFUSE_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> !context.isValid(DeferredResource.SKY_DIFFUSE_IRRADIANCE))
                .execute(context -> writeZeroAdvancedContribution(
                        context, DeferredResource.SKY_DIFFUSE_IRRADIANCE,
                        "world.environment.sky-diffuse.fallback", "advanced_sky_unavailable",
                        "Combatant zero advanced sky irradiance fallback"))
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.block-light.fallback", DeferredStage.PRE_LIGHTING)
                .priority(950)
                .write(DeferredResource.BLOCK_LIGHT_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> !context.isValid(DeferredResource.BLOCK_LIGHT_IRRADIANCE))
                .execute(context -> writeZeroAdvancedContribution(
                        context, DeferredResource.BLOCK_LIGHT_IRRADIANCE,
                        "world.environment.block-light.fallback", "colored_block_light_unavailable",
                        "Combatant zero advanced block-light irradiance fallback"))
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.irradiance.compose", DeferredStage.PRE_LIGHTING)
                .priority(1000)
                .read(DeferredResource.SKY_DIFFUSE_IRRADIANCE, DeferredResource.BLOCK_LIGHT_IRRADIANCE,
                        DeferredResource.GBUFFER_GEOMETRY)
                .write(DeferredResource.ENVIRONMENT_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.SKY_DIFFUSE_IRRADIANCE)
                        && context.isValid(DeferredResource.BLOCK_LIGHT_IRRADIANCE)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null)
                .execute(this::composeEnvironment)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        zeroIrradiancePipeline();
        environmentComposePipeline();
        baselineLightData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void writeZeroAdvancedContribution(DeferredPassContext context,
                                               DeferredResource resource,
                                               String producerId,
                                               String reason,
                                               String label) {
        ensureOwner(context.rhi());
        RhiStorageImage output = requireImage(context, resource);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label, zeroIrradiancePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, output, StorageAccess.WRITE_ONLY))
        ));
        context.resources().publishStatus(resource, producerId, DeferredResourceStatus.FALLBACK,
                reason, "neutral baseline remains available in environment compose", null);
    }

    private void composeEnvironment(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView sky = requireTexture(context, DeferredResource.SKY_DIFFUSE_IRRADIANCE);
        GpuTextureView blockLight = requireTexture(context, DeferredResource.BLOCK_LIGHT_IRRADIANCE);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        RhiStorageImage output = requireImage(context, DeferredResource.ENVIRONMENT_IRRADIANCE);

        MinecraftBaselineLightState baseline = context.worldState().baselineLightState();
        Std430Writer writer = new Std430Writer(BASELINE_LIGHT_LAYOUT, 1)
                .putVec4(0, "factors", baseline.skyFactor(), baseline.dimensionAmbient(),
                        baseline.hasSkyLight() ? 1.0f : 0.0f, baseline.valid() ? 1.0f : 0.0f)
                .putVec4(0, "skyColor", baseline.skyLightR(), baseline.skyLightG(), baseline.skyLightB(), 0.0f)
                .putVec4(0, "blockColor", baseline.blockLightR(), baseline.blockLightG(), baseline.blockLightB(), 0.0f)
                .putVec4(0, "ambientColor", baseline.ambientR(), baseline.ambientG(), baseline.ambientB(), 0.0f);
        RhiStorageBuffer baselineBuffer = baselineLightData();
        baselineBuffer.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant environment irradiance compose",
                environmentComposePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(4, baselineBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, sky, linear),
                        new SampledTextureBinding(1, blockLight, linear),
                        new SampledTextureBinding(2, geometry, nearest)
                ),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY))
        ));

        DeferredResourceProvenance skyStatus = context.resources().provenance(DeferredResource.SKY_DIFFUSE_IRRADIANCE);
        String reason = skyStatus != null && skyStatus.status() == DeferredResourceStatus.FALLBACK
                ? "minecraft_baseline_with_sky_fallback" : "minecraft_baseline_plus_environment";
        DeferredResourceStatus outputStatus = baseline.valid()
                ? DeferredResourceStatus.PRODUCED : DeferredResourceStatus.FALLBACK;
        context.resources().publishStatus(DeferredResource.ENVIRONMENT_IRRADIANCE,
                "world.environment.irradiance.compose", outputStatus,
                baseline.valid() ? reason : "minecraft_baseline_capture_failed",
                baseline.valid() ? "" : "Minecraft environment attributes unavailable; using dimension baseline",
                DeferredResource.GBUFFER_GEOMETRY);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline zeroIrradiancePipeline() {
        if (owner == null) throw new IllegalStateException("Environment irradiance source has no RHI owner");
        if (zeroIrradiancePipeline == null) {
            zeroIrradiancePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-environment-zero-irradiance", ZERO_IRRADIANCE_SHADER, ZERO_IRRADIANCE_LAYOUT
            ));
        }
        return zeroIrradiancePipeline;
    }

    private RhiComputePipeline environmentComposePipeline() {
        if (owner == null) throw new IllegalStateException("Environment irradiance source has no RHI owner");
        if (environmentComposePipeline == null) {
            environmentComposePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-environment-irradiance-compose", ENVIRONMENT_COMPOSE_SHADER, ENVIRONMENT_COMPOSE_LAYOUT
            ));
        }
        return environmentComposePipeline;
    }

    private RhiStorageBuffer baselineLightData() {
        if (owner == null) throw new IllegalStateException("Environment irradiance source has no RHI owner");
        if (baselineLightData == null) {
            baselineLightData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-environment-baseline-light", BASELINE_LIGHT_LAYOUT, 1,
                    StorageAccess.READ_ONLY, false
            ));
        }
        return baselineLightData;
    }

    private void closeOwned() {
        if (zeroIrradiancePipeline != null) {
            try { zeroIrradiancePipeline.close(); } catch (Throwable ignored) { }
            zeroIrradiancePipeline = null;
        }
        if (environmentComposePipeline != null) {
            try { environmentComposePipeline.close(); } catch (Throwable ignored) { }
            environmentComposePipeline = null;
        }
        if (baselineLightData != null) {
            try { baselineLightData.close(); } catch (Throwable ignored) { }
            baselineLightData = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
