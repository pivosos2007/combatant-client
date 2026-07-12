/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.clip;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Backend-neutral hard shape clipping contract.
 *
 * <p>The caller controls the logical clip stack. The backend controls the render-pass attachment
 * model. GL uses stencil on the exact framebuffer bound by Mojang's render pass. A Vulkan backend
 * must implement the same methods by declaring/creating the proper stencil or mask attachment for
 * the pass before draw commands are recorded.</p>
 */
public interface ShapeClipBackend {
    boolean supported();

    boolean isActive();

    /**
     * Logical request made before a shape mask/content pass is emitted. This is the explicit
     * contract hook used by render-pass mixins/backends; unsupported backends must report failure
     * rather than silently drawing unclipped content.
     */
    void requireRenderPassAttachment(String reason);

    /**
     * Request a stencil/mask clear on the next render pass target prepared for shape clipping.
     */
    void requestClear(String reason);

    /**
     * Called by the command-encoder mixin immediately after a render pass has bound its target.
     */
    void beginRenderPass(String label, @Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView);

    /**
     * Called by the command-encoder mixin before the render pass is closed/unbound.
     */
    void endRenderPass();

    /**
     * Called by the pipeline-state backend before native state is applied.
     */
    void bindPipeline(RenderPipeline pipeline, ShapeClipRenderPassContract contract);

    /**
     * Legacy/manual preparation hook for non-Mojang pass paths. Prefer beginRenderPass.
     */
    boolean prepareMainTarget();

    /**
     * Legacy/manual preparation hook for non-Mojang pass paths. Prefer beginRenderPass.
     */
    boolean prepare(@Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView);

    /**
     * Legacy/manual clear hook for non-Mojang pass paths. Prefer requestClear + beginRenderPass.
     */
    boolean clearMainTarget();

    void beginWrite(int parentReference, int newReference);

    void beginRestore(int currentReference, int parentReference);

    void beginTest(int activeReference);

    /**
     * Temporarily excludes renderer-owned offscreen preparation passes (blur pyramids, captures)
     * from the active shape clip without changing the logical clip stack. The final composite pass
     * resumes the same mask/reference after the bypass is popped.
     */
    void pushOffscreenPassBypass();

    void popOffscreenPassBypass();

    void disable();

    /**
     * Applies native backend state for the current logical mode on the current render pass.
     */
    void applyNativeState();

    /**
     * Backend diagnostic string for DebugLog. This must stay backend-neutral for callers.
     */
    default String debugState() {
        return getClass().getSimpleName();
    }

    /**
     * Last prepare/apply failure, if the backend can expose one.
     */
    default String lastFailureReason() {
        return "";
    }
}
