/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
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
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import combatant.client.render.engine.world.AerialPerspectiveLayout;
import combatant.client.render.engine.world.SkyEnvironmentDescriptor;
import combatant.client.render.engine.world.SkyEnvironmentProvider;
import combatant.client.render.engine.world.SkyEnvironmentRegistry;
import combatant.client.render.engine.world.environment.MinecraftBaselineLightState;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer-owned HDR sky environment boundary.
 *
 * <p>World/art systems only produce {@link DeferredResource#SKY_RADIANCE}. Diffuse SH9,
 * roughness-prefiltered specular mips and screen-space irradiance are canonical consumers of that
 * resource. No consumer knows whether radiance came from atmosphere LUTs, a custom dimension or a
 * neutral fallback.</p>
 */
final class DeferredSkyEnvironmentSource implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant");
    private static final int LOCAL_SIZE = 8;
    private static final int SKY_WIDTH = 256;
    private static final int SKY_HEIGHT = 128;
    private static final int SKY_MIPS = 9;
    private static final int TRANSMITTANCE_WIDTH = 256;
    private static final int TRANSMITTANCE_HEIGHT = 64;
    private static final int MULTISCATTER_WIDTH = 32;
    private static final int MULTISCATTER_HEIGHT = 32;

    private static final Identifier CLEAR_SHADER = id("deferred/sky_environment_clear");
    private static final Identifier IDENTITY_CLEAR_SHADER = id("deferred/environment_identity_clear");
    private static final Identifier SH_SHADER = id("deferred/sky_diffuse_sh");
    private static final Identifier SPECULAR_SHADER = id("deferred/sky_specular_prefilter");
    private static final Identifier DIFFUSE_RESOLVE_SHADER = id("deferred/sky_diffuse_resolve");

    private static final Std430StructLayout SH_COEFFICIENT_LAYOUT = Std430StructLayout.builder()
            .member("coefficient", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout SKY_STATE_LAYOUT = Std430StructLayout.builder()
            .member("state0", Std430Type.VEC4)
            .member("state1", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout SPECULAR_PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout RESOLVE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseView", Std430Type.MAT4)
            .member("flags", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout CLEAR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout SH_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout SPECULAR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout DIFFUSE_RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiStorageImage skyRadiance;
    private RhiStorageImage skySpecular;
    private RhiStorageImage atmosphereTransmittance;
    private RhiStorageImage atmosphereMultiScattering;
    private RhiStorageImage aerialPerspective;
    private RhiStorageImage aerialTransmittance;
    private RhiStorageBuffer skyDiffuseSh;
    private RhiStorageBuffer skyState;
    private RhiStorageBuffer specularParams;
    private RhiStorageBuffer resolveData;
    private RhiComputePipeline clearPipeline;
    private RhiComputePipeline identityClearPipeline;
    private RhiComputePipeline shPipeline;
    private RhiComputePipeline specularPipeline;
    private RhiComputePipeline diffuseResolvePipeline;

    private SkyEnvironmentProvider renderedProvider;
    private SkyEnvironmentDescriptor descriptor = SkyEnvironmentDescriptor.NONE;
    private long renderedVersion = Long.MIN_VALUE;
    private long environmentGeneration;
    private long shGeneration = Long.MIN_VALUE;
    private long specularGeneration = Long.MIN_VALUE;
    private boolean environmentUsable;
    private boolean fallbackActive;
    private String fallbackReason = "";
    private String lastProviderFailure = "";

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.environment.sky.prepare", DeferredStage.PRE_LIGHTING)
                .priority(820)
                .feature(DeferredFeature.SKY)
                .write(DeferredResource.SKY_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE,
                        DeferredResource.ATMOSPHERE_TRANSMITTANCE,
                        DeferredResource.ATMOSPHERE_MULTI_SCATTERING,
                        DeferredResource.AERIAL_PERSPECTIVE,
                        DeferredResource.AERIAL_TRANSMITTANCE)
                .requires(RhiShaderStage.COMPUTE)
                .execute(this::prepareSky)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.sky.diffuse-sh", DeferredStage.PRE_LIGHTING)
                .priority(830)
                .feature(DeferredFeature.SKY)
                .read(DeferredResource.SKY_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE)
                .write(DeferredResource.SKY_DIFFUSE_SH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.SKY_RADIANCE))
                .execute(this::buildDiffuseSh)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.sky.specular-prefilter", DeferredStage.PRE_LIGHTING)
                .priority(840)
                .feature(DeferredFeature.SKY)
                .read(DeferredResource.SKY_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE)
                .write(DeferredResource.SKY_SPECULAR_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.SKY_RADIANCE))
                .execute(this::buildSpecular)
                .build());
        passes.add(DeferredPassSpec.builder("world.environment.sky.diffuse-resolve", DeferredStage.PRE_LIGHTING)
                .priority(890)
                .feature(DeferredFeature.SKY)
                .read(DeferredResource.SKY_DIFFUSE_SH, DeferredResource.SKY_ENVIRONMENT_STATE,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH)
                .optionalRead(DeferredResource.AMBIENT_BENT_NORMAL)
                .write(DeferredResource.SKY_DIFFUSE_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.SKY_DIFFUSE_SH)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null)
                .execute(this::resolveDiffuse)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        ensureResources();
        clearPipeline();
        identityClearPipeline();
        shPipeline();
        specularPipeline();
        diffuseResolvePipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        SkyEnvironmentRegistry.releaseBackendResources(currentOwner != null ? currentOwner : owner);
        closeOwned();
        owner = null;
    }

    SkyEnvironmentDescriptor descriptor() {
        return descriptor;
    }

    private void prepareSky(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureResources();
        bindResources(context);

        SkyEnvironmentProvider provider = SkyEnvironmentRegistry.resolve(context.worldState().skyProvider());
        SkyEnvironmentDescriptor next = SkyEnvironmentDescriptor.NONE;
        String failureReason = "";
        Throwable providerFailure = null;

        if (provider != null) {
            try {
                next = provider.describe(context.worldState(), context.frame().frameId());
                if (next == null || !provider.id().equals(next.providerId())) {
                    failureReason = next == null ? "descriptor_null" : "descriptor_provider_mismatch";
                    next = SkyEnvironmentDescriptor.NONE;
                    provider = null;
                }
            } catch (Throwable failure) {
                providerFailure = failure;
                failureReason = "provider_describe_exception";
                lastProviderFailure = logProviderFailure(provider, failure, lastProviderFailure);
                next = SkyEnvironmentDescriptor.NONE;
                provider = null;
            }
        } else {
            failureReason = "provider_unavailable";
        }

        // A fallback sky is cheap and frame-local because Minecraft's sky color/light factor can
        // change continuously with time/weather. Native providers retain their own update policy.
        boolean changed = provider == null
                || !next.valid()
                || provider != renderedProvider
                || descriptor.valid() != next.valid()
                || !descriptor.providerId().equals(next.providerId())
                || next.requiresUpdate(renderedVersion);
        if (changed) {
            boolean produced = false;
            if (provider != null && next.valid()) {
                try {
                    provider.render(new SkyEnvironmentProvider.RenderContext(
                            context.rhi(), context.worldState(), context.primaryView().current(), context.frame().frameId()
                    ), new SkyEnvironmentProvider.TargetSet(
                            skyRadiance, atmosphereTransmittance, atmosphereMultiScattering,
                            aerialPerspective, aerialTransmittance
                    ));
                    produced = true;
                    environmentUsable = true;
                    fallbackActive = false;
                    fallbackReason = "";
                    lastProviderFailure = "";
                } catch (Throwable failure) {
                    providerFailure = failure;
                    failureReason = "provider_render_exception";
                    lastProviderFailure = logProviderFailure(provider, failure, lastProviderFailure);
                    next = SkyEnvironmentDescriptor.NONE;
                    provider = null;
                }
            } else if (provider != null && !next.valid()) {
                failureReason = "descriptor_unavailable";
            }

            if (!produced) {
                clearEnvironment(context);
                environmentUsable = true;
                fallbackActive = true;
                fallbackReason = failureReason.isBlank() ? "provider_unavailable" : failureReason;
            }
            renderedProvider = provider;
            descriptor = next;
            renderedVersion = next.contentVersion();
            environmentGeneration++;
        }
        uploadState();
        publishEnvironmentStatus(context, providerFailure);
    }

    private void publishEnvironmentStatus(DeferredPassContext context, Throwable providerFailure) {
        DeferredResourceStatus status = fallbackActive ? DeferredResourceStatus.FALLBACK : DeferredResourceStatus.PRODUCED;
        String reason = fallbackActive ? fallbackReason : "";
        String message = providerFailure == null ? "" : providerFailure.getClass().getSimpleName()
                + (providerFailure.getMessage() == null ? "" : ": " + providerFailure.getMessage());
        DeferredResource[] outputs = {
                DeferredResource.SKY_RADIANCE,
                DeferredResource.SKY_ENVIRONMENT_STATE,
                DeferredResource.ATMOSPHERE_TRANSMITTANCE,
                DeferredResource.ATMOSPHERE_MULTI_SCATTERING,
                DeferredResource.AERIAL_PERSPECTIVE,
                DeferredResource.AERIAL_TRANSMITTANCE
        };
        for (DeferredResource output : outputs) {
            context.resources().publishStatus(output, "world.environment.sky.prepare",
                    status, reason, message, null);
        }
    }

    private static String logProviderFailure(SkyEnvironmentProvider provider, Throwable failure, String previousKey) {
        String providerId = provider == null ? "<unavailable>" : provider.id().toString();
        String key = providerId + "|" + failure.getClass().getName() + "|" + String.valueOf(failure.getMessage());
        if (!key.equals(previousKey)) {
            LOGGER.warn("[Combatant][Renderer] sky provider {} failed; using declared fallback", providerId, failure);
        }
        return key;
    }

    private void buildDiffuseSh(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureResources();
        bindResources(context);
        publishDerivedSkyStatus(context, DeferredResource.SKY_DIFFUSE_SH, "world.environment.sky.diffuse-sh");
        if (shGeneration == environmentGeneration) return;

        GpuSampler skySampler = skySampler(false);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant sky diffuse SH9",
                shPipeline(), 9, 1, 1,
                List.of(new StorageBinding(1, skyDiffuseSh, 0L,
                        (long) SH_COEFFICIENT_LAYOUT.arrayStride() * 9L, StorageAccess.WRITE_ONLY)),
                List.of(new SampledTextureBinding(0, skyRadiance.view(), skySampler)),
                List.of()
        ));
        shGeneration = environmentGeneration;
    }

    private void buildSpecular(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureResources();
        bindResources(context);
        publishDerivedSkyStatus(context, DeferredResource.SKY_SPECULAR_RADIANCE, "world.environment.sky.specular-prefilter");
        if (specularGeneration == environmentGeneration) return;

        Std430Writer params = new Std430Writer(SPECULAR_PARAMS_LAYOUT, SKY_MIPS);
        for (int mip = 0; mip < SKY_MIPS; mip++) {
            float roughness = SKY_MIPS <= 1 ? 0.0f : (float) mip / (float) (SKY_MIPS - 1);
            params.putVec4(mip, "params", roughness, mip, SKY_MIPS, 0.0f);
        }
        specularParams.upload(params.buffer(), 0L);

        GpuSampler skySampler = skySampler(false);
        int stride = SPECULAR_PARAMS_LAYOUT.arrayStride();
        for (int mip = 0; mip < SKY_MIPS; mip++) {
            int width = Math.max(1, SKY_WIDTH >> mip);
            int height = Math.max(1, SKY_HEIGHT >> mip);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant sky specular prefilter mip " + mip,
                    specularPipeline(), groups(width), groups(height), 1,
                    List.of(new StorageBinding(2, specularParams, (long) stride * mip,
                            stride, StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(0, skyRadiance.view(), skySampler)),
                    List.of(new StorageImageBinding(1, skySpecular, StorageAccess.WRITE_ONLY, mip))
            ));
        }
        specularGeneration = environmentGeneration;
    }

    private void resolveDiffuse(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureResources();
        bindResources(context);
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        boolean hasBentNormal = context.isValid(DeferredResource.AMBIENT_BENT_NORMAL)
                && context.resources().texture(DeferredResource.AMBIENT_BENT_NORMAL) != null;
        GpuTextureView bentNormal = hasBentNormal
                ? requireTexture(context, DeferredResource.AMBIENT_BENT_NORMAL) : geometry;
        RhiStorageImage output = requireImage(context, DeferredResource.SKY_DIFFUSE_IRRADIANCE);

        Std430Writer writer = new Std430Writer(RESOLVE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "flags", hasBentNormal ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
        resolveData.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant sky diffuse irradiance resolve",
                diffuseResolvePipeline(), groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(5, skyDiffuseSh, 0L,
                                (long) SH_COEFFICIENT_LAYOUT.arrayStride() * 9L, StorageAccess.READ_ONLY),
                        new StorageBinding(6, skyState, 0L, SKY_STATE_LAYOUT.arrayStride(), StorageAccess.READ_ONLY),
                        new StorageBinding(7, resolveData, 0L, RESOLVE_DATA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, geometry, nearest),
                        new SampledTextureBinding(1, bentNormal, nearest),
                        new SampledTextureBinding(2, depth, nearest),
                        new SampledTextureBinding(3, gbufferDepth, nearest)
                ),
                List.of(new StorageImageBinding(4, output, StorageAccess.WRITE_ONLY))
        ));
        publishDerivedSkyStatus(context, DeferredResource.SKY_DIFFUSE_IRRADIANCE,
                "world.environment.sky.diffuse-resolve");
    }

    private void publishDerivedSkyStatus(DeferredPassContext context,
                                         DeferredResource output,
                                         String producerId) {
        DeferredResourceProvenance source = context.resources().provenance(DeferredResource.SKY_RADIANCE);
        DeferredResourceStatus status = source != null && source.status() == DeferredResourceStatus.FALLBACK
                ? DeferredResourceStatus.FALLBACK : DeferredResourceStatus.PRODUCED;
        String reason = status == DeferredResourceStatus.FALLBACK ? "sky_radiance_fallback" : "";
        String message = source == null ? "" : source.message();
        context.resources().publishStatus(output, producerId, status, reason, message, DeferredResource.SKY_RADIANCE);
    }

    private void clearEnvironment(DeferredPassContext context) {
        MinecraftBaselineLightState baseline = context.worldState().baselineLightState();
        float red = baseline.skyBackgroundR();
        float green = baseline.skyBackgroundG();
        float blue = baseline.skyBackgroundB();
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                skyRadiance.view().texture(), new Vector4f(red, green, blue, 1.0f));
        clearIdentityImage(context, "Combatant neutral atmosphere transmittance", atmosphereTransmittance);
        clearImage(context, "Combatant neutral atmosphere multiscattering", atmosphereMultiScattering);
        clearImage(context, "Combatant neutral aerial radiance", aerialPerspective);
        clearIdentityImage(context, "Combatant neutral aerial transmittance", aerialTransmittance);
    }

    private void clearImage(DeferredPassContext context, String label, RhiStorageImage image) {
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label, clearPipeline(), groups(image.descriptor().width()), groups(image.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, image, StorageAccess.WRITE_ONLY))
        ));
    }

    private void clearIdentityImage(DeferredPassContext context, String label, RhiStorageImage image) {
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label, identityClearPipeline(), groups(image.descriptor().width()), groups(image.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, image, StorageAccess.WRITE_ONLY))
        ));
    }

    private void uploadState() {
        Std430Writer writer = new Std430Writer(SKY_STATE_LAYOUT, 1)
                .putVec4(0, "state0", descriptor.valid() ? 1.0f : 0.0f, SKY_MIPS,
                        descriptor.updatePolicy().ordinal(), environmentUsable ? 1.0f : 0.0f)
                .putVec4(0, "state1", SKY_WIDTH, SKY_HEIGHT,
                        (float) (descriptor.contentVersion() & 0x00FFFFFFL), 0.0f);
        skyState.upload(writer.buffer(), 0L);
    }

    private void bindResources(DeferredPassContext context) {
        context.resources().bindStorageImage(DeferredResource.SKY_RADIANCE, skyRadiance);
        context.resources().bindStorageImage(DeferredResource.ATMOSPHERE_TRANSMITTANCE, atmosphereTransmittance);
        context.resources().bindStorageImage(DeferredResource.ATMOSPHERE_MULTI_SCATTERING, atmosphereMultiScattering);
        context.resources().bindStorageImage(DeferredResource.AERIAL_PERSPECTIVE, aerialPerspective);
        context.resources().bindStorageImage(DeferredResource.AERIAL_TRANSMITTANCE, aerialTransmittance);
        context.resources().bindBuffer(DeferredResource.SKY_DIFFUSE_SH, skyDiffuseSh);
        context.resources().bindStorageImage(DeferredResource.SKY_SPECULAR_RADIANCE, skySpecular);
        context.resources().bindBuffer(DeferredResource.SKY_ENVIRONMENT_STATE, skyState);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureResources() {
        if (owner == null) throw new IllegalStateException("Sky environment source has no RHI owner");
        if (skyRadiance == null) {
            skyRadiance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-sky-radiance", SKY_WIDTH, SKY_HEIGHT, GpuFormat.RGBA16_FLOAT,
                    StorageAccess.READ_WRITE, true, false, 1
            ));
        }
        if (skySpecular == null) {
            skySpecular = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-sky-specular", SKY_WIDTH, SKY_HEIGHT, GpuFormat.RGBA16_FLOAT,
                    StorageAccess.READ_WRITE, true, false, SKY_MIPS
            ));
        }
        if (atmosphereTransmittance == null) {
            atmosphereTransmittance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-atmosphere-transmittance", TRANSMITTANCE_WIDTH, TRANSMITTANCE_HEIGHT,
                    GpuFormat.RGBA16_FLOAT, StorageAccess.READ_WRITE, true, false, 1
            ));
        }
        if (atmosphereMultiScattering == null) {
            atmosphereMultiScattering = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-atmosphere-multiscattering", MULTISCATTER_WIDTH, MULTISCATTER_HEIGHT,
                    GpuFormat.RGBA16_FLOAT, StorageAccess.READ_WRITE, true, false, 1
            ));
        }
        if (aerialPerspective == null) {
            aerialPerspective = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-atmosphere-aerial-perspective", AerialPerspectiveLayout.WIDTH, AerialPerspectiveLayout.HEIGHT,
                    GpuFormat.RGBA16_FLOAT, StorageAccess.READ_WRITE, true, false, 1
            ));
        }
        if (aerialTransmittance == null) {
            aerialTransmittance = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-atmosphere-aerial-transmittance", AerialPerspectiveLayout.WIDTH, AerialPerspectiveLayout.HEIGHT,
                    GpuFormat.RGBA16_FLOAT, StorageAccess.READ_WRITE, true, false, 1
            ));
        }
        if (skyDiffuseSh == null) {
            skyDiffuseSh = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-sky-diffuse-sh", SH_COEFFICIENT_LAYOUT, 9, StorageAccess.READ_WRITE, false
            ));
        }
        if (skyState == null) {
            skyState = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-sky-state", SKY_STATE_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        if (specularParams == null) {
            specularParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-sky-specular-params", SPECULAR_PARAMS_LAYOUT, SKY_MIPS, StorageAccess.READ_ONLY, false
            ));
        }
        if (resolveData == null) {
            resolveData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-sky-resolve-data", RESOLVE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
    }

    private RhiComputePipeline clearPipeline() {
        if (clearPipeline == null) clearPipeline = pipeline("combatant-sky-clear", CLEAR_SHADER, CLEAR_LAYOUT);
        return clearPipeline;
    }

    private RhiComputePipeline identityClearPipeline() {
        if (identityClearPipeline == null) {
            identityClearPipeline = pipeline("combatant-environment-identity-clear", IDENTITY_CLEAR_SHADER, CLEAR_LAYOUT);
        }
        return identityClearPipeline;
    }

    private RhiComputePipeline shPipeline() {
        if (shPipeline == null) shPipeline = pipeline("combatant-sky-diffuse-sh", SH_SHADER, SH_LAYOUT);
        return shPipeline;
    }

    private RhiComputePipeline specularPipeline() {
        if (specularPipeline == null) {
            specularPipeline = pipeline("combatant-sky-specular-prefilter", SPECULAR_SHADER, SPECULAR_LAYOUT);
        }
        return specularPipeline;
    }

    private RhiComputePipeline diffuseResolvePipeline() {
        if (diffuseResolvePipeline == null) {
            diffuseResolvePipeline = pipeline(
                    "combatant-sky-diffuse-resolve", DIFFUSE_RESOLVE_SHADER, DIFFUSE_RESOLVE_LAYOUT
            );
        }
        return diffuseResolvePipeline;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Sky environment source has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private GpuSampler skySampler(boolean mipmap) {
        return RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, mipmap
        );
    }

    private void closeOwned() {
        close(clearPipeline); clearPipeline = null;
        close(identityClearPipeline); identityClearPipeline = null;
        close(shPipeline); shPipeline = null;
        close(specularPipeline); specularPipeline = null;
        close(diffuseResolvePipeline); diffuseResolvePipeline = null;
        close(skyRadiance); skyRadiance = null;
        close(skySpecular); skySpecular = null;
        close(atmosphereTransmittance); atmosphereTransmittance = null;
        close(atmosphereMultiScattering); atmosphereMultiScattering = null;
        close(aerialPerspective); aerialPerspective = null;
        close(aerialTransmittance); aerialTransmittance = null;
        close(skyDiffuseSh); skyDiffuseSh = null;
        close(skyState); skyState = null;
        close(specularParams); specularParams = null;
        close(resolveData); resolveData = null;
        renderedProvider = null;
        descriptor = SkyEnvironmentDescriptor.NONE;
        renderedVersion = Long.MIN_VALUE;
        environmentUsable = false;
        fallbackActive = false;
        fallbackReason = "";
        lastProviderFailure = "";
        environmentGeneration = 0L;
        shGeneration = Long.MIN_VALUE;
        specularGeneration = Long.MIN_VALUE;
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
