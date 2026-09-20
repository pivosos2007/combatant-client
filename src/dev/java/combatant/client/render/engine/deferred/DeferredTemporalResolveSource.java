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
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
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
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Final scene TAA/TAAU consumer.
 *
 * <p>The algorithm consumes only the post-translucency FINAL_* temporal contract. The common
 * history lifecycle decides whether the resolve may read previous color/confidence/lock at all;
 * reset frames take a separate initialization path with no previous-history bindings. Filtering
 * remains local to this consumer so clouds/SSR/AO keep their own temporal policies.</p>
 */
final class DeferredTemporalResolveSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier INITIALIZE = id("deferred/taa_initialize");
    private static final Identifier RESOLVE = id("deferred/taa_resolve");
    private static final Identifier HISTORY_STORE = id("deferred/taa_history_store");

    private static final float MAX_HISTORY_WEIGHT = 0.96f;
    private static final float DEPTH_THRESHOLD = 0.006f;
    private static final float LOCK_GROWTH = 1.0f / 32.0f;
    private static final float MOTION_FALLOFF = 0.035f;
    private static final float REACTIVE_HISTORY_FLOOR = 0.12f;
    private static final float VARIANCE_GAMMA = 1.50f;
    private static final float REACTIVE_VARIANCE_GAMMA = 0.75f;

    private static final Std430StructLayout INIT_PARAMS = Std430StructLayout.builder()
            .member("currentJitterAndRender", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout RESOLVE_PARAMS = Std430StructLayout.builder()
            .member("jitterPixels", Std430Type.VEC4)
            .member("extents", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("response", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout INIT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(12, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(13, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(14, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(15, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout HISTORY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline initializePipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline historyPipeline;
    private RhiStorageBuffer initParams;
    private RhiStorageBuffer resolveParams;
    private long temporalPolicyGeneration = Long.MIN_VALUE;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.temporal.taa.policy", DeferredStage.TEMPORAL_RESOLVE)
                .priority(-500)
                .execute(this::syncPolicy)
                .build());
        // Neutral final-temporal fallback: TAA OFF still publishes the canonical output-resolution
        // HDR post input without reading or advancing TAA history. The initialization kernel also
        // performs render->output reconstruction for TAAU extents.
        passes.add(DeferredPassSpec.builder("world.temporal.taa.passthrough", DeferredStage.TEMPORAL_RESOLVE)
                .priority(-400)
                .read(DeferredResource.SCENE_COLOR)
                .write(DeferredResource.TAA_RESOLVED_COLOR,
                        DeferredResource.TAA_CONFIDENCE,
                        DeferredResource.TAA_LOCK,
                        DeferredResource.TAA_HISTORY_WEIGHT,
                        DeferredResource.TAA_REJECTION_MASK)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> !context.featureEnabled(DeferredFeature.TAA) && baseAvailable(context))
                .execute(this::initialize)
                .build());
        // Reset/invalid frames use a graph contract that does not declare previous histories at
        // all. This makes "no previous history read after reset" true both in the shader bindings
        // and in the frame-graph resource contract.
        passes.add(DeferredPassSpec.builder("world.temporal.taa.initialize", DeferredStage.TEMPORAL_RESOLVE)
                .feature(DeferredFeature.TAA)
                .priority(-300)
                .read(DeferredResource.SCENE_COLOR)
                .write(DeferredResource.TAA_RESOLVED_COLOR,
                        DeferredResource.TAA_CONFIDENCE,
                        DeferredResource.TAA_LOCK,
                        DeferredResource.TAA_HISTORY_WEIGHT,
                        DeferredResource.TAA_REJECTION_MASK)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> baseAvailable(context) && !resolveAvailable(context))
                .execute(this::initialize)
                .build());

        passes.add(DeferredPassSpec.builder("world.temporal.taa.resolve", DeferredStage.TEMPORAL_RESOLVE)
                .feature(DeferredFeature.TAA)
                .priority(-200)
                .read(DeferredResource.SCENE_COLOR,
                        DeferredResource.FINAL_VELOCITY,
                        DeferredResource.FINAL_MOTION_VALIDITY,
                        DeferredResource.FINAL_REACTIVE_MASK,
                        DeferredResource.FINAL_DISOCCLUSION_MASK,
                        DeferredResource.FINAL_RESOLVED_DEPTH,
                        DeferredResource.HISTORY_DEPTH,
                        DeferredResource.HISTORY_TAA_COLOR,
                        DeferredResource.HISTORY_TAA_CONFIDENCE,
                        DeferredResource.HISTORY_TAA_LOCK)
                .write(DeferredResource.TAA_RESOLVED_COLOR,
                        DeferredResource.TAA_CONFIDENCE,
                        DeferredResource.TAA_LOCK,
                        DeferredResource.TAA_HISTORY_WEIGHT,
                        DeferredResource.TAA_REJECTION_MASK)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::resolveAvailable)
                .execute(this::resolve)
                .build());

        // Generic render-space scene/depth history capture has priority 0. TAA reads previous
        // HISTORY_DEPTH at -200; the ordered graph therefore emits an explicit write-after-read
        // dependency before current-frame depth replaces the persistent snapshot.
        passes.add(DeferredPassSpec.builder("world.temporal.taa.present", DeferredStage.TEMPORAL_RESOLVE)
                .priority(100)
                .read(DeferredResource.TAA_RESOLVED_COLOR)
                .write(DeferredResource.SCENE_COLOR)
                .when(this::canPresentToSceneTarget)
                .execute(this::present)
                .build());

        passes.add(DeferredPassSpec.builder("world.temporal.taa.history", DeferredStage.TEMPORAL_RESOLVE)
                .feature(DeferredFeature.TAA)
                .priority(200)
                .read(DeferredResource.TAA_RESOLVED_COLOR,
                        DeferredResource.TAA_CONFIDENCE,
                        DeferredResource.TAA_LOCK)
                .write(DeferredResource.HISTORY_TAA_COLOR,
                        DeferredResource.HISTORY_TAA_CONFIDENCE,
                        DeferredResource.HISTORY_TAA_LOCK)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.TAA_RESOLVED_COLOR)
                        && context.isValid(DeferredResource.TAA_CONFIDENCE)
                        && context.isValid(DeferredResource.TAA_LOCK))
                .execute(this::storeHistory)
                .build());
    }

    private void syncPolicy(DeferredPassContext context) {
        long generation = DeferredTemporalConfig.generation();
        if (generation == temporalPolicyGeneration) return;
        if (temporalPolicyGeneration != Long.MIN_VALUE) {
            context.temporalHistory().invalidate(DeferredTemporalHistoryId.TAA, DeferredHistoryResetReason.POLICY_CHANGE);
        }
        temporalPolicyGeneration = generation;
    }

    private boolean baseAvailable(DeferredPassContext context) {
        return context.resources().texture(DeferredResource.SCENE_COLOR) != null
                && context.primaryView().current() != null;
    }

    private boolean resolveAvailable(DeferredPassContext context) {
        if (!context.featureEnabled(DeferredFeature.TAA)
                || !baseAvailable(context) || !historyReadable(context)) return false;
        DeferredTemporalConsumerContract contract = DeferredTemporalConsumerContract.finalFrame();
        return context.isValid(contract.velocity())
                && context.isValid(contract.motionValidity())
                && context.isValid(contract.reactiveMask())
                && context.isValid(contract.depth())
                && context.isValid(DeferredResource.FINAL_DISOCCLUSION_MASK)
                && context.resources().texture(contract.velocity()) != null
                && context.resources().texture(contract.motionValidity()) != null
                && context.resources().texture(contract.reactiveMask()) != null
                && context.resources().texture(contract.depth()) != null
                && context.resources().texture(DeferredResource.FINAL_DISOCCLUSION_MASK) != null;
    }

    private boolean historyReadable(DeferredPassContext context) {
        return context.history(DeferredTemporalHistoryId.TAA).valid()
                && context.history(DeferredTemporalHistoryId.SCENE).valid();
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        initializePipeline();
        resolvePipeline();
        historyPipeline();
        initParams();
        resolveParams();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void initialize(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = requireCurrentView(context);
        RhiStorageImage output = requireImage(context, DeferredResource.TAA_RESOLVED_COLOR);
        RhiStorageImage confidence = requireImage(context, DeferredResource.TAA_CONFIDENCE);
        RhiStorageImage lock = requireImage(context, DeferredResource.TAA_LOCK);
        RhiStorageImage historyWeight = requireImage(context, DeferredResource.TAA_HISTORY_WEIGHT);
        RhiStorageImage rejection = requireImage(context, DeferredResource.TAA_REJECTION_MASK);
        GpuTextureView currentColor = requireTexture(context, DeferredResource.SCENE_COLOR);

        int renderWidth = renderWidth(current, currentColor);
        int renderHeight = renderHeight(current, currentColor);
        Vector2f jitter = current.jitter();
        Std430Writer writer = new Std430Writer(INIT_PARAMS, 1)
                .putVec4(0, "currentJitterAndRender", jitter.x(), jitter.y(), renderWidth, renderHeight);
        RhiStorageBuffer params = initParams();
        params.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant TAA history initialization",
                initializePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(6, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, currentColor, linear)),
                List.of(
                        new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(2, confidence, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(3, lock, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, historyWeight, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, rejection, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void resolve(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = requireCurrentView(context);
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        if (previous == null) {
            // The condition should make this impossible. Keep the failure explicit rather than
            // silently treating missing previous camera state as zero motion.
            throw new IllegalStateException("Readable TAA history has no previous primary view");
        }

        GpuTextureView currentColor = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView velocity = requireTexture(context, DeferredResource.FINAL_VELOCITY);
        GpuTextureView validity = requireTexture(context, DeferredResource.FINAL_MOTION_VALIDITY);
        GpuTextureView reactive = requireTexture(context, DeferredResource.FINAL_REACTIVE_MASK);
        GpuTextureView disocclusion = requireTexture(context, DeferredResource.FINAL_DISOCCLUSION_MASK);
        GpuTextureView currentDepth = requireTexture(context, DeferredResource.FINAL_RESOLVED_DEPTH);
        GpuTextureView historyDepth = requireTexture(context, DeferredResource.HISTORY_DEPTH);
        GpuTextureView historyColor = requireTexture(context, DeferredResource.HISTORY_TAA_COLOR);
        GpuTextureView historyConfidence = requireTexture(context, DeferredResource.HISTORY_TAA_CONFIDENCE);
        GpuTextureView historyLock = requireTexture(context, DeferredResource.HISTORY_TAA_LOCK);
        RhiStorageImage output = requireImage(context, DeferredResource.TAA_RESOLVED_COLOR);
        RhiStorageImage confidence = requireImage(context, DeferredResource.TAA_CONFIDENCE);
        RhiStorageImage lock = requireImage(context, DeferredResource.TAA_LOCK);
        RhiStorageImage historyWeight = requireImage(context, DeferredResource.TAA_HISTORY_WEIGHT);
        RhiStorageImage rejection = requireImage(context, DeferredResource.TAA_REJECTION_MASK);

        int renderWidth = renderWidth(current, currentColor);
        int renderHeight = renderHeight(current, currentColor);
        int outputWidth = current.hasOutputResolution() ? current.outputWidth() : output.descriptor().width();
        int outputHeight = current.hasOutputResolution() ? current.outputHeight() : output.descriptor().height();
        Vector2f currentJitter = current.jitter();
        Vector2f previousJitter = previous.jitter();
        int historyAge = context.history(DeferredTemporalHistoryId.TAA).age();
        float historyAgeNorm = Math.min(1.0f, historyAge / 32.0f);

        Std430Writer writer = new Std430Writer(RESOLVE_PARAMS, 1)
                .putVec4(0, "jitterPixels", currentJitter.x(), currentJitter.y(), previousJitter.x(), previousJitter.y())
                .putVec4(0, "extents", renderWidth, renderHeight, outputWidth, outputHeight)
                .putVec4(0, "policy", MAX_HISTORY_WEIGHT, historyAgeNorm, DEPTH_THRESHOLD, LOCK_GROWTH)
                .putVec4(0, "response", MOTION_FALLOFF, REACTIVE_HISTORY_FLOOR,
                        VARIANCE_GAMMA, REACTIVE_VARIANCE_GAMMA);
        RhiStorageBuffer params = resolveParams();
        params.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant final scene TAA/TAAU resolve",
                resolvePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(15, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, currentColor, linear),
                        new SampledTextureBinding(1, velocity, nearest),
                        new SampledTextureBinding(2, validity, nearest),
                        new SampledTextureBinding(3, reactive, linear),
                        new SampledTextureBinding(4, disocclusion, nearest),
                        new SampledTextureBinding(5, currentDepth, nearest),
                        new SampledTextureBinding(6, historyDepth, nearest),
                        new SampledTextureBinding(7, historyColor, linear),
                        new SampledTextureBinding(8, historyConfidence, linear),
                        new SampledTextureBinding(9, historyLock, linear)
                ),
                List.of(
                        new StorageImageBinding(10, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(11, confidence, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(12, lock, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(13, historyWeight, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(14, rejection, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void storeHistory(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView color = requireTexture(context, DeferredResource.TAA_RESOLVED_COLOR);
        GpuTextureView confidence = requireTexture(context, DeferredResource.TAA_CONFIDENCE);
        GpuTextureView lock = requireTexture(context, DeferredResource.TAA_LOCK);
        RhiStorageImage historyColor = requireImage(context, DeferredResource.HISTORY_TAA_COLOR);
        RhiStorageImage historyConfidence = requireImage(context, DeferredResource.HISTORY_TAA_CONFIDENCE);
        RhiStorageImage historyLock = requireImage(context, DeferredResource.HISTORY_TAA_LOCK);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant TAA history store",
                historyPipeline(),
                groups(historyColor.descriptor().width()), groups(historyColor.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, color, nearest),
                        new SampledTextureBinding(1, confidence, nearest),
                        new SampledTextureBinding(2, lock, nearest)
                ),
                List.of(
                        new StorageImageBinding(3, historyColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, historyConfidence, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, historyLock, StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(DeferredTemporalHistoryId.TAA);
    }

    private boolean canPresentToSceneTarget(DeferredPassContext context) {
        if (!context.isValid(DeferredResource.TAA_RESOLVED_COLOR)) return false;
        GpuTextureView source = context.resources().texture(DeferredResource.TAA_RESOLVED_COLOR);
        GpuTextureView target = context.resources().texture(DeferredResource.SCENE_COLOR);
        return source != null && target != null
                && source.getWidth(0) == target.getWidth(0)
                && source.getHeight(0) == target.getHeight(0);
    }

    private void present(DeferredPassContext context) {
        GpuTextureView source = requireTexture(context, DeferredResource.TAA_RESOLVED_COLOR);
        GpuTextureView target = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant final temporal scene publish")
                        .colorAttachment(target)
                        .pipeline(DeferredRuntimeAssets.temporalPresent())
                        .sampler("u_Source", source, linear)
                        .build()
        );
    }

    private static DeferredPrimaryViewSource.FrameView requireCurrentView(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) throw new IllegalStateException("TAA/TAAU requires current primary view");
        return current;
    }

    private static int renderWidth(DeferredPrimaryViewSource.FrameView current, GpuTextureView fallback) {
        return current.hasRenderResolution() ? current.renderWidth() : Math.max(1, fallback.getWidth(0));
    }

    private static int renderHeight(DeferredPrimaryViewSource.FrameView current, GpuTextureView fallback) {
        return current.hasRenderResolution() ? current.renderHeight() : Math.max(1, fallback.getHeight(0));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline initializePipeline() {
        if (owner == null) throw new IllegalStateException("TAA source has no RHI owner");
        if (initializePipeline == null) {
            initializePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-taa-initialize", INITIALIZE, INIT_LAYOUT
            ));
        }
        return initializePipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (owner == null) throw new IllegalStateException("TAA source has no RHI owner");
        if (resolvePipeline == null) {
            resolvePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-taa-resolve", RESOLVE, RESOLVE_LAYOUT
            ));
        }
        return resolvePipeline;
    }

    private RhiComputePipeline historyPipeline() {
        if (owner == null) throw new IllegalStateException("TAA source has no RHI owner");
        if (historyPipeline == null) {
            historyPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-taa-history", HISTORY_STORE, HISTORY_LAYOUT
            ));
        }
        return historyPipeline;
    }

    private RhiStorageBuffer initParams() {
        if (owner == null) throw new IllegalStateException("TAA source has no RHI owner");
        if (initParams == null) {
            initParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-taa-init-params", INIT_PARAMS, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return initParams;
    }

    private RhiStorageBuffer resolveParams() {
        if (owner == null) throw new IllegalStateException("TAA source has no RHI owner");
        if (resolveParams == null) {
            resolveParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-taa-resolve-params", RESOLVE_PARAMS, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return resolveParams;
    }

    private void closeOwned() {
        if (initializePipeline != null) {
            try { initializePipeline.close(); } catch (Throwable ignored) { }
            initializePipeline = null;
        }
        if (resolvePipeline != null) {
            try { resolvePipeline.close(); } catch (Throwable ignored) { }
            resolvePipeline = null;
        }
        if (historyPipeline != null) {
            try { historyPipeline.close(); } catch (Throwable ignored) { }
            historyPipeline = null;
        }
        if (initParams != null) {
            try { initParams.close(); } catch (Throwable ignored) { }
            initParams = null;
        }
        if (resolveParams != null) {
            try { resolveParams.close(); } catch (Throwable ignored) { }
            resolveParams = null;
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
