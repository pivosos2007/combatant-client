/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.text.backend.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.UiRenderDispatcher;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiBlurQuality;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.resource.GlyphAtlasManager;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.ShaderUniformBindings;
import combatant.client.render.engine.uniform.impl.MsdfTextUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 10 text owner: command buffer, backend router and RHI mesh submission for glyph text.
 */
public enum TextRenderSystem {
    ;
    private static final TextCommandStats STATS = new TextCommandStats();
    private static final TextCommandBuffer COMMANDS = new TextCommandBuffer(STATS);
    private static final TextBackendRouter ROUTER = new TextBackendRouter(STATS);
    private static final ShaderUniformBindings.Block UI_BLEND_BLOCK = ShaderUniformBindings.block("UIBlend");
    private static final ShaderUniformBindings.Writer UI_BLEND = UI_BLEND_BLOCK.writer();

    static {
        ROUTER.add(new MsdfTextBackend(STATS));
        ROUTER.add(new BitmapAtlasTextBackend(STATS));
        ROUTER.add(new VanillaSodiumTextBackend(STATS));
    }

    public static TextCommandBuffer commands() {
        return COMMANDS;
    }

    public static TextBackendRouter router() {
        return ROUTER;
    }

    public static GlyphAtlasManager glyphAtlases() {
        return CombatantRenderSystem.resources().glyphAtlases();
    }

    public static void beginFrame() {
        COMMANDS.clear();
        STATS.reset();
    }

    public static void record(TextDrawCommand command) {
        COMMANDS.record(command);
    }

    public static void flush() {
        COMMANDS.flush(ROUTER);
    }

    public static TextCommandStatsSnapshot statsSnapshot() {
        return STATS.snapshot();
    }

    public static void noteDirectAdjacentTextBatch(int commands) {
        STATS.directAdjacentTextBatch(commands);
    }

    public static void submitLiquidGlassGlyphMesh(String label,
                                                   GlyphFont font,
                                                   MeshBuilder mesh,
                                                   RenderPipeline pipeline,
                                                   TextPlacementMode placement,
                                                   double boundsX,
                                                   double boundsY,
                                                   double boundsWidth,
                                                   double boundsHeight) {
        if (font == null || mesh == null || pipeline == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0) return;

        UiRect bounds = UiRect.of(boundsX, boundsY, boundsWidth, boundsHeight);
        TextPlacementMode uiPlacement = placement != null ? placement : TextPlacementMode.UI;
        if (uiPlacement == TextPlacementMode.UI || uiPlacement == TextPlacementMode.SCREEN_SPACE) {
            if (Renderer2D.enqueueLiquidGlassTextMesh(label, font, mesh, pipeline, uiPlacement, bounds)) {
                return;
            }
            boolean auto = UiRenderDispatcher.beginAutoBatch();
            if (auto) {
                try {
                    if (Renderer2D.enqueueLiquidGlassTextMesh(label, font, mesh, pipeline, uiPlacement, bounds)) {
                        return;
                    }
                } finally {
                    UiRenderDispatcher.endAutoBatch(true);
                }
            }
        }

        // Glass text is a UI backdrop material. Outside an ordered UI batch, preserve readable
        // output rather than sampling the active color attachment recursively.
        submitGlyphMeshImmediate(label, font, mesh,
                font.isMsdf() ? CombatantRenderPipelines.UI_TEXT_MSDF_FAST : CombatantRenderPipelines.UI_TEXT_FAST,
                uiPlacement);
    }

    public static void submitLiquidGlassBlendGlyphMesh(String label,
                                                        GlyphFont font,
                                                        MeshBuilder mesh,
                                                        TextPlacementMode placement,
                                                        double boundsX,
                                                        double boundsY,
                                                        double boundsWidth,
                                                        double boundsHeight,
                                                        UiBackdropBlendSpec blend,
                                                        UiBackdropRequest backdrop) {
        if (font == null || mesh == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0) return;

        UiRect bounds = UiRect.of(boundsX, boundsY, boundsWidth, boundsHeight);
        UiBackdropRequest request = backdrop != null
                ? backdrop.withCaptureBounds(bounds)
                : UiBackdropRequest.currentTargetGlass(
                bounds, UiBlurQuality.LIQUID_GLASS, Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX);
        UiBackdropBlendSpec material = blend != null ? blend : UiBackdropBlendSpec.NORMAL;
        TextPlacementMode uiPlacement = placement != null ? placement : TextPlacementMode.UI;
        RenderPipeline pipeline = CombatantRenderPipelines.UI_TEXT_MSDF_GLASS_BLEND_FAST;

        if (uiPlacement == TextPlacementMode.UI || uiPlacement == TextPlacementMode.SCREEN_SPACE) {
            if (Renderer2D.enqueueLiquidGlassTextMesh(
                    label, font, mesh, pipeline, uiPlacement, bounds, material, request)) {
                return;
            }
            boolean auto = UiRenderDispatcher.beginAutoBatch();
            if (auto) {
                try {
                    if (Renderer2D.enqueueLiquidGlassTextMesh(
                            label, font, mesh, pipeline, uiPlacement, bounds, material, request)) {
                        return;
                    }
                } finally {
                    UiRenderDispatcher.endAutoBatch(true);
                }
            }
        }

        // A backdrop operator needs a stable destination image. Outside ordered UI replay, fall
        // back to ordinary MSDF text rather than pretending that a recursive attachment sample is valid.
        submitGlyphMeshImmediate(label, font, mesh,
                font.isMsdf() ? CombatantRenderPipelines.UI_TEXT_MSDF_FAST : CombatantRenderPipelines.UI_TEXT_FAST,
                uiPlacement);
    }

    public static void submitGlyphMesh(String label,
                                       GlyphFont font,
                                       MeshBuilder mesh,
                                       RenderPipeline pipeline,
                                       TextPlacementMode placement) {
        if (font == null || mesh == null || pipeline == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0) return;

        // When UI rendering is inside a Renderer2D batch, text becomes an ordered batch entry.
        // It may merge only with adjacent compatible text runs; it must never be moved across shapes,
        // items, scissor/marquee boundaries or world/placement-specific text.
        if (placement == null || placement == TextPlacementMode.UI || placement == TextPlacementMode.SCREEN_SPACE) {
            TextPlacementMode uiPlacement = placement != null ? placement : TextPlacementMode.UI;
            boolean enqueued = Renderer2D.enqueueTextMesh(label, font, mesh, pipeline, uiPlacement);
            if (enqueued) return;

            // UI text must not bypass the UI compiler/executor just because no caller-owned
            // Renderer2D batch is active. Create the same short-lived ordered batch used by
            // ordinary auto-batched primitives; deferred recording is handled by that batcher too.
            boolean auto = UiRenderDispatcher.beginAutoBatch();
            if (auto) {
                try {
                    if (Renderer2D.enqueueTextMesh(label, font, mesh, pipeline, uiPlacement)) {
                        return;
                    }
                } finally {
                    UiRenderDispatcher.endAutoBatch(true);
                }
            }
        }

        submitGlyphMeshImmediate(label, font, mesh, pipeline, placement);
    }

    public static void submitGlyphMeshImmediate(String label,
                                                GlyphFont font,
                                                MeshBuilder mesh,
                                                RenderPipeline pipeline,
                                                TextPlacementMode placement) {
        List<RhiDrawCommand> commands = new ArrayList<>(1);
        appendGlyphMeshCommand(commands, label, font, mesh, pipeline, placement);
        CombatantRenderSystem.rhi().drawMeshes(commands);
    }

    /** Appends glyph geometry to an existing ordered pass stream without changing painter order. */
    public static void appendGlyphMeshCommand(List<RhiDrawCommand> commands,
                                              String label,
                                              GlyphFont font,
                                              MeshBuilder mesh,
                                              RenderPipeline pipeline,
                                              TextPlacementMode placement) {
        appendGlyphMeshCommand(commands, label, font, mesh, pipeline, placement, UiClipSnapshot.NONE);
    }

    /** Appends glyph geometry with the immutable clip state captured when the text was enqueued. */
    public static void appendGlyphMeshCommand(List<RhiDrawCommand> commands,
                                              String label,
                                              GlyphFont font,
                                              MeshBuilder mesh,
                                              RenderPipeline pipeline,
                                              TextPlacementMode placement,
                                              UiClipSnapshot clipSnapshot) {
        if (commands == null) return;
        if (font == null || mesh == null || pipeline == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0) return;
        if (!font.isReady()) return;

        UiClipSnapshot clip = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        RenderPipeline resolvedPipeline = pipeline;
        if (clip.usesAnalyticPipeline()) {
            resolvedPipeline = CombatantRenderPipelines.analyticClipTextPipeline(pipeline);
            if (resolvedPipeline == null) {
                throw new IllegalStateException("UI text pipeline " + pipeline.getLocation()
                        + " has no ANALYTIC_CLIP variant for clip snapshot " + clip.id());
            }
        }

        AbstractTexture texture = font.getTexture();
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer.mainRenderTarget() == null) return;

        int vertexBytes = mesh.getVertexBytes();
        int indexBytes = mesh.getIndexBytes();
        GpuMeshHandle handle = null;
        try {
            handle = CombatantRenderSystem.rhi().dynamicMeshes().upload(mesh);
            STATS.meshUpload(vertexBytes, indexBytes);
            STATS.glyphs(Math.max(0, mesh.getVertexCount() / 4));
            if (placement != null && placement.world()) STATS.worldPlacement();
            STATS.backend(font.isMsdf() ? TextBackendPreference.MSDF : TextBackendPreference.BITMAP_ATLAS);

            RhiDrawCommand.Builder command = RhiDrawCommand.builder(label != null ? label : "Combatant Text")
                    .pipeline(resolvedPipeline)
                    .colorAttachment(UiMsaaClipLayer.currentColorAttachment(
                            mc.gameRenderer.mainRenderTarget().getColorTextureView()))
                    .mesh(handle)
                    .sampler("u_Texture", texture.getTextureView(), texture.getSampler());

            if (clip.usesAnalyticPipeline()) {
                command.uniform("UIClip", UiClipUniforms.write(clip));
            }

            if (font.isMsdf()) {
                MsdfTextUniforms.update(font.getPxRange(), font.getAtlasWidth(), font.getAtlasHeight());
                command.uniform("MsdfText", MsdfTextUniforms.get());
            }

            commands.add(command.build());
            handle = null;
        } finally {
            if (handle != null) handle.close();
        }
    }

    public static void appendLiquidGlassGlyphMeshCommand(List<RhiDrawCommand> commands,
                                                         String label,
                                                         GlyphFont font,
                                                         MeshBuilder mesh,
                                                         RenderPipeline pipeline,
                                                         TextPlacementMode placement,
                                                         UiClipSnapshot clipSnapshot,
                                                         GpuTextureView sceneView,
                                                         GpuSampler sceneSampler,
                                                         GpuTextureView blurView,
                                                         GpuSampler blurSampler,
                                                         float framebufferWidth,
                                                         float framebufferHeight) {
        if (commands == null || font == null || mesh == null || pipeline == null) return;
        if (sceneView == null || sceneSampler == null || blurView == null || blurSampler == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0 || !font.isReady() || !font.isMsdf()) return;

        AbstractTexture texture = font.getTexture();
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer.mainRenderTarget() == null) return;

        int vertexBytes = mesh.getVertexBytes();
        int indexBytes = mesh.getIndexBytes();
        GpuMeshHandle handle = null;
        try {
            handle = CombatantRenderSystem.rhi().dynamicMeshes().upload(mesh);
            STATS.meshUpload(vertexBytes, indexBytes);
            STATS.glyphs(Math.max(0, mesh.getVertexCount() / 4));
            STATS.backend(TextBackendPreference.MSDF);

            UIBatchUniforms.update(framebufferWidth, framebufferHeight);
            MsdfTextUniforms.update(font.getPxRange(), font.getAtlasWidth(), font.getAtlasHeight());

            RhiDrawCommand.Builder command = RhiDrawCommand.builder(
                            label != null ? label : "Combatant Liquid Glass Text")
                    .pipeline(pipeline)
                    .colorAttachment(UiMsaaClipLayer.currentColorAttachment(
                            mc.gameRenderer.mainRenderTarget().getColorTextureView()))
                    .mesh(handle)
                    .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                    .sampler("u_SceneTexture", sceneView, sceneSampler)
                    .sampler("u_BlurTexture", blurView, blurSampler)
                    .uniform("UIBatch", UIBatchUniforms.get())
                    .uniform("MsdfText", MsdfTextUniforms.get());

            commands.add(command.build());
            handle = null;
        } finally {
            if (handle != null) handle.close();
        }
    }

    public static void appendLiquidGlassBlendGlyphMeshCommand(List<RhiDrawCommand> commands,
                                                              String label,
                                                              GlyphFont font,
                                                              MeshBuilder mesh,
                                                              TextPlacementMode placement,
                                                              UiClipSnapshot clipSnapshot,
                                                              GpuTextureView sceneView,
                                                              GpuSampler sceneSampler,
                                                              GpuTextureView blurView,
                                                              GpuSampler blurSampler,
                                                              float framebufferWidth,
                                                              float framebufferHeight,
                                                              UiBackdropBlendSpec blend) {
        if (commands == null || font == null || mesh == null) return;
        if (sceneView == null || sceneSampler == null || blurView == null || blurSampler == null) return;
        if (mesh.isBuilding()) mesh.end();
        if (mesh.getIndicesCount() <= 0 || !font.isReady() || !font.isMsdf()) return;

        AbstractTexture texture = font.getTexture();
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer.mainRenderTarget() == null) return;

        int vertexBytes = mesh.getVertexBytes();
        int indexBytes = mesh.getIndexBytes();
        GpuMeshHandle handle = null;
        try {
            handle = CombatantRenderSystem.rhi().dynamicMeshes().upload(mesh);
            STATS.meshUpload(vertexBytes, indexBytes);
            STATS.glyphs(Math.max(0, mesh.getVertexCount() / 4));
            STATS.backend(TextBackendPreference.MSDF);

            UIBatchUniforms.update(framebufferWidth, framebufferHeight);
            MsdfTextUniforms.update(font.getPxRange(), font.getAtlasWidth(), font.getAtlasHeight());

            RhiDrawCommand.Builder command = RhiDrawCommand.builder(
                            label != null ? label : "Combatant Backdrop Blend Text")
                    .pipeline(CombatantRenderPipelines.UI_TEXT_MSDF_GLASS_BLEND_FAST)
                    .colorAttachment(UiMsaaClipLayer.currentColorAttachment(
                            mc.gameRenderer.mainRenderTarget().getColorTextureView()))
                    .mesh(handle)
                    .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                    .sampler("u_SceneTexture", sceneView, sceneSampler)
                    .sampler("u_BlurTexture", blurView, blurSampler)
                    .uniform("UIBatch", UIBatchUniforms.get())
                    .uniform(UI_BLEND_BLOCK.name(), uploadUiBlend(blend))
                    .uniform("MsdfText", MsdfTextUniforms.get());

            commands.add(command.build());
            handle = null;
        } finally {
            if (handle != null) handle.close();
        }
    }

    private static GpuBufferSlice uploadUiBlend(UiBackdropBlendSpec requested) {
        UiBackdropBlendSpec spec = requested != null ? requested : UiBackdropBlendSpec.NORMAL;
        int tone0 = spec.tone0Argb();
        int tone1 = spec.tone1Argb();
        return UI_BLEND
                .vec4("uBlendParams", spec.mode().shaderId(), spec.strength(), spec.pivot(), spec.softness())
                .vec4("uBlendTone0",
                        ((tone0 >>> 16) & 0xFF) / 255.0f,
                        ((tone0 >>> 8) & 0xFF) / 255.0f,
                        (tone0 & 0xFF) / 255.0f,
                        ((tone0 >>> 24) & 0xFF) / 255.0f)
                .vec4("uBlendTone1",
                        ((tone1 >>> 16) & 0xFF) / 255.0f,
                        ((tone1 >>> 8) & 0xFF) / 255.0f,
                        (tone1 & 0xFF) / 255.0f,
                        ((tone1 >>> 24) & 0xFF) / 255.0f)
                .upload(64);
    }

    public static RenderPipeline uiPipelineFor(GlyphFont font) {
        return font != null && font.isMsdf() ? CombatantRenderPipelines.UI_TEXT_MSDF : CombatantRenderPipelines.UI_TEXT;
    }
}
