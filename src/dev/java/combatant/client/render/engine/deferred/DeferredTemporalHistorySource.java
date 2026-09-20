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

/** Captures canonical scene/depth history after translucency and before post-processing. */
final class DeferredTemporalHistorySource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier HISTORY_CAPTURE = id("deferred/history_capture");
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.history.capture", DeferredStage.TEMPORAL_RESOLVE)
                .read(DeferredResource.SCENE_COLOR, DeferredResource.FINAL_RESOLVED_DEPTH)
                .write(DeferredResource.HISTORY_COLOR, DeferredResource.HISTORY_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.resources().texture(DeferredResource.SCENE_COLOR) != null
                        && context.isValid(DeferredResource.FINAL_RESOLVED_DEPTH)
                        && context.resources().texture(DeferredResource.FINAL_RESOLVED_DEPTH) != null)
                .execute(this::capture)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void capture(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView scene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.FINAL_RESOLVED_DEPTH);
        RhiStorageImage historyColor = requireImage(context, DeferredResource.HISTORY_COLOR);
        RhiStorageImage historyDepth = requireImage(context, DeferredResource.HISTORY_DEPTH);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal history capture",
                pipeline(),
                groups(historyColor.descriptor().width()), groups(historyColor.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, scene, nearest),
                        new SampledTextureBinding(1, depth, nearest)
                ),
                List.of(
                        new StorageImageBinding(2, historyColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(3, historyDepth, StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(DeferredTemporalHistoryId.SCENE);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Temporal history source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-history-capture", HISTORY_CAPTURE, LAYOUT
            ));
        }
        return pipeline;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
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
