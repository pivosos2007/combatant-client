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
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Backend-owned neutral producers required by later deferred consumers.
 *
 * <p>No visual policy lives here: depth resolve preserves reversed-Z depth and the Hi-Z chain stores
 * the conservative nearest depth (MAX for Minecraft's reversed-Z convention).</p>
 */
final class DeferredBackendPasses implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;

    private static final Identifier DEPTH_RESOLVE_SINGLE = id("deferred/depth_resolve_single");
    private static final Identifier DEPTH_RESOLVE_MSAA = id("deferred/depth_resolve_msaa");
    private static final ShaderResourceLayout SAMPLED_TO_IMAGE = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private CombatantRhi owner;
    private RhiComputePipeline depthSingle;
    private RhiComputePipeline depthMsaa;
    private final DeferredTemporalHistorySource temporalHistory = new DeferredTemporalHistorySource();
    private final DeferredTemporalResolveSource temporalResolve = new DeferredTemporalResolveSource();
    private final DeferredHdrPostSource hdrPost = new DeferredHdrPostSource();
    private final DeferredCameraPostSource cameraPost = new DeferredCameraPostSource();
    private final DeferredDebugCompositorSource debugCompositor = new DeferredDebugCompositorSource();

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.post_translucency.depth.resolve", DeferredStage.POST_TRANSLUCENCY)
                .priority(-200)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.FINAL_RESOLVED_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.resources().texture(DeferredResource.MAIN_DEPTH) != null)
                .execute(context -> resolveDepth(context, DeferredResource.FINAL_RESOLVED_DEPTH,
                        "Combatant final depth resolve"))
                .build());
        temporalHistory.install(passes);
        temporalResolve.install(passes);
        hdrPost.install(passes);
        cameraPost.install(passes);
        debugCompositor.install(passes);
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        depthSingle();
        depthMsaa();
        temporalHistory.prepare(rhi);
        temporalResolve.prepare(rhi);
        hdrPost.prepare(rhi);
        cameraPost.prepare(rhi);
        debugCompositor.prepare(rhi);
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closePipelines();
        CombatantRhi releaseOwner = currentOwner != null ? currentOwner : owner;
        temporalHistory.release(releaseOwner);
        temporalResolve.release(releaseOwner);
        hdrPost.release(releaseOwner);
        cameraPost.release(releaseOwner);
        debugCompositor.release(releaseOwner);
        owner = null;
    }

    private void resolveDepth(DeferredPassContext context, DeferredResource outputResource, String label) {
        ensureOwner(context.rhi());
        GpuTextureView source = requireTexture(context, DeferredResource.MAIN_DEPTH);
        RhiStorageImage output = requireImage(context, outputResource);
        int samples = samples(source);
        RhiComputePipeline pipeline = samples > 1 ? depthMsaa() : depthSingle();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label,
                pipeline,
                groups(output.descriptor().width()),
                groups(output.descriptor().height()),
                1,
                List.of(),
                List.of(new SampledTextureBinding(0, source, sampler)),
                List.of(new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        CombatantRhi previous = owner;
        closePipelines();
        if (previous != null) {
            temporalHistory.release(previous);
            temporalResolve.release(previous);
            hdrPost.release(previous);
            cameraPost.release(previous);
        }
        owner = rhi;
    }

    private RhiComputePipeline depthSingle() {
        if (depthSingle == null) depthSingle = pipeline("combatant-depth-resolve-single", DEPTH_RESOLVE_SINGLE, SAMPLED_TO_IMAGE);
        return depthSingle;
    }

    private RhiComputePipeline depthMsaa() {
        if (depthMsaa == null) depthMsaa = pipeline("combatant-depth-resolve-msaa", DEPTH_RESOLVE_MSAA, SAMPLED_TO_IMAGE);
        return depthMsaa;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Deferred backend pass has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView texture = context.resources().texture(resource);
        if (texture == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return texture;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage image = context.resources().storageImage(resource);
        if (image == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return image;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    @Override
    public void close() {
        closePipelines();
        temporalHistory.close();
        temporalResolve.close();
        hdrPost.close();
        cameraPost.close();
        owner = null;
    }

    private void closePipelines() {
        depthSingle = close(depthSingle);
        depthMsaa = close(depthMsaa);
    }

    private static RhiComputePipeline close(RhiComputePipeline pipeline) {
        if (pipeline == null) return null;
        try {
            pipeline.close();
        } catch (Throwable ignored) {
            // Backend/device teardown owns the final native cleanup if explicit close is no longer legal.
        }
        return null;
    }
}
