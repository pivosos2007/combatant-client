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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Output-resolution HDR camera post foundation.
 *
 * <p>This source deliberately consumes the canonical post-TAA HDR handoff and FINAL_* motion
 * contracts. It has no dependency on the legacy PostProcessManager, Iris depth surfaces or camera
 * yaw/pitch deltas. Bloom remains a side product; the color chain is TAA/passthrough -> DoF ->
 * velocity-buffer motion blur -> POST_HDR_COLOR.</p>
 */
final class DeferredCameraPostSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;

    private static final Identifier DEPTH_RECONSTRUCT = id("deferred/post_depth_reconstruct");
    private static final Identifier DOF_FOCUS = id("deferred/dof_focus");
    private static final Identifier DOF = id("deferred/dof");
    private static final Identifier DOF_PASSTHROUGH = id("deferred/dof_passthrough");
    private static final Identifier MOTION_TILE_MAX = id("deferred/motion_tile_max");
    private static final Identifier MOTION_NEIGHBOR_MAX = id("deferred/motion_neighbor_max");
    private static final Identifier MOTION_BLUR = id("deferred/motion_blur");
    private static final Identifier POST_COPY = id("deferred/post_copy");

    private static final Std430StructLayout FOCUS_STATE = Std430StructLayout.builder()
            .member("focus", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout FOCUS_PARAMS = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout DOF_PARAMS = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("focusAndRange", Std430Type.VEC4)
            .member("quality", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout MOTION_PARAMS = Std430StructLayout.builder()
            .member("extent", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("inverseProjection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout DEPTH_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout FOCUS_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout DOF_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout DOF_PASSTHROUGH_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout MOTION_TILE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout MOTION_NEIGHBOR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout MOTION_BLUR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout COPY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline depthPipeline;
    private RhiComputePipeline focusPipeline;
    private RhiComputePipeline dofPipeline;
    private RhiComputePipeline dofPassthroughPipeline;
    private RhiComputePipeline motionTilePipeline;
    private RhiComputePipeline motionNeighborPipeline;
    private RhiComputePipeline motionBlurPipeline;
    private RhiComputePipeline copyPipeline;
    private RhiStorageBuffer focusState;
    private RhiStorageBuffer focusParams;
    private RhiStorageBuffer dofParams;
    private RhiStorageBuffer motionParams;
    private long focusProducedFrame = Long.MIN_VALUE;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.post.depth.reconstruct", DeferredStage.PRE_POST_PROCESS)
                .priority(0)
                .read(DeferredResource.FINAL_RESOLVED_DEPTH)
                .write(DeferredResource.POST_RESOLVED_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredPostHdrContract.HDR_INPUT)
                        && context.isValid(DeferredResource.FINAL_RESOLVED_DEPTH)
                        && context.resources().texture(DeferredResource.FINAL_RESOLVED_DEPTH) != null)
                .execute(this::reconstructDepth)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.dof.focus", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.DEPTH_OF_FIELD)
                .priority(10)
                .read(DeferredResource.POST_RESOLVED_DEPTH)
                .readWrite(DeferredResource.DOF_FOCUS_STATE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.POST_RESOLVED_DEPTH)
                        && context.primaryView().current() != null)
                .execute(this::resolveFocus)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.dof.resolve", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.DEPTH_OF_FIELD)
                .priority(20)
                .read(DeferredPostHdrContract.HDR_INPUT, DeferredResource.POST_RESOLVED_DEPTH,
                        DeferredResource.DOF_FOCUS_STATE)
                .write(DeferredResource.DOF_COLOR, DeferredResource.DOF_COC)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::dofAvailable)
                .execute(this::resolveDof)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.dof.passthrough", DeferredStage.PRE_POST_PROCESS)
                .priority(20)
                .read(DeferredPostHdrContract.HDR_INPUT)
                .write(DeferredResource.DOF_COLOR, DeferredResource.DOF_COC)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> postInputAvailable(context) && !dofAvailable(context))
                .execute(this::passthroughDof)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.motion.tile-max", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.MOTION_BLUR)
                .priority(40)
                .read(DeferredResource.FINAL_VELOCITY, DeferredResource.FINAL_MOTION_VALIDITY)
                .write(DeferredResource.MOTION_TILE_MAX)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::motionSignalsAvailable)
                .execute(this::tileMax)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.motion.neighbor-max", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.MOTION_BLUR)
                .priority(50)
                .read(DeferredResource.MOTION_TILE_MAX)
                .write(DeferredResource.MOTION_NEIGHBOR_MAX)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.MOTION_TILE_MAX))
                .execute(this::neighborMax)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.motion.resolve", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.MOTION_BLUR)
                .priority(60)
                .read(DeferredResource.DOF_COLOR, DeferredResource.POST_RESOLVED_DEPTH,
                        DeferredResource.FINAL_VELOCITY, DeferredResource.FINAL_MOTION_VALIDITY,
                        DeferredResource.MOTION_NEIGHBOR_MAX)
                .write(DeferredResource.POST_HDR_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::motionBlurAvailable)
                .execute(this::resolveMotionBlur)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.motion.passthrough", DeferredStage.PRE_POST_PROCESS)
                .priority(60)
                .read(DeferredResource.DOF_COLOR)
                .write(DeferredResource.POST_HDR_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.DOF_COLOR) && !motionBlurAvailable(context))
                .execute(this::copyDofToPost)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.camera.present", DeferredStage.PRE_POST_PROCESS)
                .priority(90)
                .read(DeferredResource.POST_HDR_COLOR)
                .readWrite(DeferredResource.SCENE_COLOR)
                .when(this::canPresentToSceneTarget)
                .execute(this::presentToSceneTarget)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        depthPipeline();
        focusPipeline();
        dofPipeline();
        dofPassthroughPipeline();
        motionTilePipeline();
        motionNeighborPipeline();
        motionBlurPipeline();
        copyPipeline();
        focusState();
        focusParams();
        dofParams();
        motionParams();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private boolean postInputAvailable(DeferredPassContext context) {
        return context.isValid(DeferredPostHdrContract.HDR_INPUT)
                && context.resources().texture(DeferredPostHdrContract.HDR_INPUT) != null;
    }

    private boolean dofAvailable(DeferredPassContext context) {
        return context.featureEnabled(DeferredFeature.DEPTH_OF_FIELD)
                && postInputAvailable(context)
                && context.isValid(DeferredResource.POST_RESOLVED_DEPTH)
                && focusProducedFrame == context.frame().frameId()
                && context.resources().buffer(DeferredResource.DOF_FOCUS_STATE) != null;
    }

    private boolean motionSignalsAvailable(DeferredPassContext context) {
        return context.featureEnabled(DeferredFeature.MOTION_BLUR)
                && context.isValid(DeferredResource.DOF_COLOR)
                && context.isValid(DeferredResource.POST_RESOLVED_DEPTH)
                && context.isValid(DeferredResource.FINAL_VELOCITY)
                && context.isValid(DeferredResource.FINAL_MOTION_VALIDITY)
                && context.resources().texture(DeferredResource.FINAL_VELOCITY) != null
                && context.resources().texture(DeferredResource.FINAL_MOTION_VALIDITY) != null;
    }

    private boolean motionBlurAvailable(DeferredPassContext context) {
        return motionSignalsAvailable(context)
                && context.isValid(DeferredResource.MOTION_NEIGHBOR_MAX)
                && context.resources().texture(DeferredResource.MOTION_NEIGHBOR_MAX) != null;
    }

    private void reconstructDepth(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView depth = requireTexture(context, DeferredResource.FINAL_RESOLVED_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.POST_RESOLVED_DEPTH);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant post output depth reconstruct", depthPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(new SampledTextureBinding(0, depth, nearest)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void resolveFocus(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        GpuTextureView depth = requireTexture(context, DeferredResource.POST_RESOLVED_DEPTH);
        RhiStorageBuffer state = focusState();
        context.resources().bindBuffer(DeferredResource.DOF_FOCUS_STATE, state);
        DeferredCameraPostConfig.Snapshot config = DeferredCameraPostConfig.current();
        boolean zeroToOne = zeroToOneDepth(context);
        float dt = frameDeltaSeconds(context);
        float reset = context.primaryView().hasTemporalHistory() ? 0.0f : 1.0f;
        Std430Writer writer = new Std430Writer(FOCUS_PARAMS, 1)
                .putMat4(0, "inverseProjection", view.inverseUnjitteredProjection())
                .putVec4(0, "depthTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f, dt, reset)
                .putVec4(0, "policy", config.depthOfFieldAutofocus() ? 1.0f : 0.0f,
                        config.depthOfFieldFocusSmoothing(), view.farPlane(), 0.0f);
        RhiStorageBuffer params = focusParams();
        params.upload(writer.buffer(), 0L);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant DoF autofocus", focusPipeline(), 1, 1, 1,
                List.of(
                        new StorageBinding(1, state, 0L, state.descriptor().byteSize(), StorageAccess.READ_WRITE),
                        new StorageBinding(2, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(new SampledTextureBinding(0, depth, nearest)), List.of()
        ));
        focusProducedFrame = context.frame().frameId();
    }

    private void resolveDof(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        GpuTextureView color = requireTexture(context, DeferredPostHdrContract.HDR_INPUT);
        GpuTextureView depth = requireTexture(context, DeferredResource.POST_RESOLVED_DEPTH);
        RhiStorageBuffer focus = requireBuffer(context, DeferredResource.DOF_FOCUS_STATE);
        RhiStorageImage output = requireImage(context, DeferredResource.DOF_COLOR);
        RhiStorageImage coc = requireImage(context, DeferredResource.DOF_COC);
        DeferredCameraPostConfig.Snapshot config = DeferredCameraPostConfig.current();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(DOF_PARAMS, 1)
                .putMat4(0, "inverseProjection", view.inverseUnjitteredProjection())
                .putVec4(0, "depthTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f, 0.0f, 0.0f)
                .putVec4(0, "focusAndRange", config.depthOfFieldFarStart(), config.depthOfFieldFarTransition(),
                        config.depthOfFieldStrength(), config.depthOfFieldMaxRadiusPixels())
                .putVec4(0, "quality", config.depthOfFieldSampleCount(), config.depthOfFieldEdgeProtection(),
                        config.depthOfFieldEnabled() ? 1.0f : 0.0f,
                        config.depthOfFieldAutofocus() ? 1.0f : 0.0f);
        RhiStorageBuffer params = dofParams();
        params.upload(writer.buffer(), 0L);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR depth of field", dofPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(2, focus, 0L, focus.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(5, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(new SampledTextureBinding(0, color, linear), new SampledTextureBinding(1, depth, nearest)),
                List.of(new StorageImageBinding(3, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, coc, StorageAccess.WRITE_ONLY))
        ));
    }

    private void passthroughDof(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView color = requireTexture(context, DeferredPostHdrContract.HDR_INPUT);
        RhiStorageImage output = requireImage(context, DeferredResource.DOF_COLOR);
        RhiStorageImage coc = requireImage(context, DeferredResource.DOF_COC);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant DoF HDR passthrough", dofPassthroughPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(new SampledTextureBinding(0, color, linear)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(2, coc, StorageAccess.WRITE_ONLY))
        ));
    }

    private void tileMax(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage output = requireImage(context, DeferredResource.MOTION_TILE_MAX);
        GpuTextureView velocity = requireTexture(context, DeferredResource.FINAL_VELOCITY);
        GpuTextureView validity = requireTexture(context, DeferredResource.FINAL_MOTION_VALIDITY);
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        RhiStorageBuffer params = uploadMotionParams(context, view);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant motion tile max", motionTilePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(3, params, 0L, params.descriptor().byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, velocity, nearest), new SampledTextureBinding(1, validity, nearest)),
                List.of(new StorageImageBinding(2, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void neighborMax(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage input = requireImage(context, DeferredResource.MOTION_TILE_MAX);
        RhiStorageImage output = requireImage(context, DeferredResource.MOTION_NEIGHBOR_MAX);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant motion neighbor max", motionNeighborPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, input, StorageAccess.READ_ONLY),
                        new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void resolveMotionBlur(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        GpuTextureView color = requireTexture(context, DeferredResource.DOF_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.POST_RESOLVED_DEPTH);
        GpuTextureView velocity = requireTexture(context, DeferredResource.FINAL_VELOCITY);
        GpuTextureView validity = requireTexture(context, DeferredResource.FINAL_MOTION_VALIDITY);
        GpuTextureView neighbor = requireTexture(context, DeferredResource.MOTION_NEIGHBOR_MAX);
        RhiStorageImage output = requireImage(context, DeferredResource.POST_HDR_COLOR);
        RhiStorageBuffer params = uploadMotionParams(context, view);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant deferred motion blur", motionBlurPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(6, params, 0L, params.descriptor().byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, color, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, velocity, nearest),
                        new SampledTextureBinding(3, validity, nearest),
                        new SampledTextureBinding(4, neighbor, nearest)
                ),
                List.of(new StorageImageBinding(5, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void copyDofToPost(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView color = requireTexture(context, DeferredResource.DOF_COLOR);
        RhiStorageImage output = requireImage(context, DeferredResource.POST_HDR_COLOR);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant camera post HDR passthrough", copyPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(new SampledTextureBinding(0, color, linear)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private boolean canPresentToSceneTarget(DeferredPassContext context) {
        if (!context.isValid(DeferredResource.POST_HDR_COLOR)) return false;
        GpuTextureView source = context.resources().texture(DeferredResource.POST_HDR_COLOR);
        GpuTextureView target = context.resources().texture(DeferredResource.SCENE_COLOR);
        return source != null && target != null
                && source.getWidth(0) == target.getWidth(0)
                && source.getHeight(0) == target.getHeight(0);
    }

    private void presentToSceneTarget(DeferredPassContext context) {
        GpuTextureView source = requireTexture(context, DeferredResource.POST_HDR_COLOR);
        GpuTextureView target = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant camera post HDR publish")
                        .colorAttachment(target)
                        .pipeline(DeferredRuntimeAssets.temporalPresent())
                        .sampler("u_Source", source, linear)
                        .build()
        );
    }

    private RhiStorageBuffer uploadMotionParams(DeferredPassContext context, DeferredPrimaryViewSource.FrameView view) {
        DeferredCameraPostConfig.Snapshot config = DeferredCameraPostConfig.current();
        int outputWidth = Math.max(1, view.outputWidth() > 0 ? view.outputWidth() : view.renderWidth());
        int outputHeight = Math.max(1, view.outputHeight() > 0 ? view.outputHeight() : view.renderHeight());
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(MOTION_PARAMS, 1)
                .putVec4(0, "extent", outputWidth, outputHeight, DeferredCameraPostConfig.MOTION_TILE_SIZE,
                        config.motionBlurSampleCount())
                .putVec4(0, "policy", config.motionBlurStrength(), config.motionBlurMaxPixels(),
                        config.motionBlurMinMotionPixels(), config.motionBlurShutterScale())
                .putMat4(0, "inverseProjection", view.inverseUnjitteredProjection())
                .putVec4(0, "depthTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        config.motionBlurDepthEdgeProtection(), 0.0f);
        RhiStorageBuffer params = motionParams();
        params.upload(writer.buffer(), 0L);
        return params;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiStorageBuffer focusState() {
        if (focusState == null) {
            focusState = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-dof-focus-state", FOCUS_STATE, 1, StorageAccess.READ_WRITE, false));
            focusState.upload(ByteBuffer.allocateDirect(FOCUS_STATE.arrayStride()).order(ByteOrder.nativeOrder()), 0L);
        }
        return focusState;
    }

    private RhiStorageBuffer focusParams() {
        if (focusParams == null) focusParams = createBuffer("combatant-dof-focus-params", FOCUS_PARAMS);
        return focusParams;
    }

    private RhiStorageBuffer dofParams() {
        if (dofParams == null) dofParams = createBuffer("combatant-dof-params", DOF_PARAMS);
        return dofParams;
    }

    private RhiStorageBuffer motionParams() {
        if (motionParams == null) motionParams = createBuffer("combatant-motion-blur-params", MOTION_PARAMS);
        return motionParams;
    }

    private RhiStorageBuffer createBuffer(String label, Std430StructLayout layout) {
        return owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                label, layout, 1, StorageAccess.READ_ONLY, false));
    }

    private RhiComputePipeline depthPipeline() { return pipeline("combatant-post-depth", DEPTH_RECONSTRUCT, DEPTH_LAYOUT, depthPipeline, v -> depthPipeline = v); }
    private RhiComputePipeline focusPipeline() { return pipeline("combatant-dof-focus", DOF_FOCUS, FOCUS_LAYOUT, focusPipeline, v -> focusPipeline = v); }
    private RhiComputePipeline dofPipeline() { return pipeline("combatant-dof", DOF, DOF_LAYOUT, dofPipeline, v -> dofPipeline = v); }
    private RhiComputePipeline dofPassthroughPipeline() { return pipeline("combatant-dof-passthrough", DOF_PASSTHROUGH, DOF_PASSTHROUGH_LAYOUT, dofPassthroughPipeline, v -> dofPassthroughPipeline = v); }
    private RhiComputePipeline motionTilePipeline() { return pipeline("combatant-motion-tile-max", MOTION_TILE_MAX, MOTION_TILE_LAYOUT, motionTilePipeline, v -> motionTilePipeline = v); }
    private RhiComputePipeline motionNeighborPipeline() { return pipeline("combatant-motion-neighbor-max", MOTION_NEIGHBOR_MAX, MOTION_NEIGHBOR_LAYOUT, motionNeighborPipeline, v -> motionNeighborPipeline = v); }
    private RhiComputePipeline motionBlurPipeline() { return pipeline("combatant-motion-blur", MOTION_BLUR, MOTION_BLUR_LAYOUT, motionBlurPipeline, v -> motionBlurPipeline = v); }
    private RhiComputePipeline copyPipeline() { return pipeline("combatant-post-copy", POST_COPY, COPY_LAYOUT, copyPipeline, v -> copyPipeline = v); }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout,
                                        RhiComputePipeline existing,
                                        java.util.function.Consumer<RhiComputePipeline> setter) {
        if (owner == null) throw new IllegalStateException("Camera post source has no RHI owner");
        if (existing != null) return existing;
        RhiComputePipeline created = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor(label, shader, layout));
        setter.accept(created);
        return created;
    }

    private static float frameDeltaSeconds(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        double value = current != null && previous != null
                ? current.frameTimeSeconds() - previous.frameTimeSeconds() : 1.0 / 60.0;
        if (!Double.isFinite(value) || value <= 0.0) value = 1.0 / 60.0;
        return (float) Math.max(1.0 / 240.0, Math.min(0.25, value));
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
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

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred storage buffer is not bound: " + resource);
        return value;
    }

    private static int groups(int size) {
        return Math.max(1, (size + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private void closeOwned() {
        depthPipeline = close(depthPipeline);
        focusPipeline = close(focusPipeline);
        dofPipeline = close(dofPipeline);
        dofPassthroughPipeline = close(dofPassthroughPipeline);
        motionTilePipeline = close(motionTilePipeline);
        motionNeighborPipeline = close(motionNeighborPipeline);
        motionBlurPipeline = close(motionBlurPipeline);
        copyPipeline = close(copyPipeline);
        focusState = close(focusState);
        focusParams = close(focusParams);
        dofParams = close(dofParams);
        motionParams = close(motionParams);
        focusProducedFrame = Long.MIN_VALUE;
    }

    private static RhiComputePipeline close(RhiComputePipeline value) {
        if (value != null) try { value.close(); } catch (Throwable ignored) { }
        return null;
    }

    private static RhiStorageBuffer close(RhiStorageBuffer value) {
        if (value != null) try { value.close(); } catch (Throwable ignored) { }
        return null;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }
}
