/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.ClipFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.GlyphFont;
import combatant.client.render.engine.text.TextRenderSystem;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.MsdfTextUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.render.engine.uniform.impl.UIBlurUniforms;


public final class OrderedUiBatcher {
    private static final OrderedUiBatcher LIQUID_GLASS_PREWARMER = new OrderedUiBatcher();
    final ObjectArrayList<Object> order = new ObjectArrayList<>(128);
    @SuppressWarnings("unchecked")
    final ObjectArrayList<DrawBatch>[] pools = new ObjectArrayList[UiBatchType.values().length];
    final ObjectArrayList<ItemBatch> itemPool = new ObjectArrayList<>(32);
    final ObjectArrayList<TextBatch> textPool = new ObjectArrayList<>(32);
    // Text must remain in the exact draw order. Chat, nametags, layered HUD widgets and
    // marquee/clip stacks rely on text being interleaved with shapes/items. Only adjacent
    // compatible text runs are merged; text is never moved across another draw entry.
    final int[] poolCursor = new int[UiBatchType.values().length];
    int itemPoolCursor;
    int textPoolCursor;
    boolean active;
    boolean flushing;
    @Nullable GpuTextureView sharedBlurSourceView;
    @Nullable GpuSampler sharedBlurSourceSampler;
    @Nullable GpuTextureView sharedBlurredView;
    @Nullable GpuSampler sharedBlurredSampler;
    Renderer2D.BlurQuality sharedBlurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
    float sharedBlurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;

    public OrderedUiBatcher() {
        for (int i = 0; i < pools.length; i++) {
            pools[i] = new ObjectArrayList<>();
        }
    }

    public static void prewarmLiquidGlassBlur(Minecraft mc,
                                                @Nullable GpuTextureView sourceView,
                                                @Nullable GpuSampler sourceSampler,
                                                float screenW,
                                                float screenH,
                                                float uiScale) {
        if (mc == null || sourceView == null || sourceSampler == null) return;
        LIQUID_GLASS_PREWARMER.resetSharedBlur();
        LIQUID_GLASS_PREWARMER.prepareSharedBlurInternal(
                mc,
                sourceView,
                sourceSampler,
                screenW,
                screenH,
                uiScale,
                Renderer2D.DEFAULT_LIQUID_GLASS_BLUR_QUALITY,
                Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX
        );
        LIQUID_GLASS_PREWARMER.resetSharedBlur();
    }

    public void begin() {
        resetOrder();
        resetSharedBlur();
        active = true;
        Renderer2D.BATCH_STATS.setActive(true);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFlushing() {
        return flushing;
    }

    public boolean hasPendingWork() {
        return !order.isEmpty();
    }

    public DrawBatch getOrCreate(UiBatchType type, GpuTextureView view, GpuSampler sampler) {
        if (!active) return null;
        boolean shapeClipActive = ClipFunction.isShapeClipActive();
        if (!order.isEmpty()) {
            Object last = order.get(order.size() - 1);
            if (last instanceof DrawBatch drawBatch && drawBatch.canMerge(type, view, sampler, shapeClipActive)) return drawBatch;
        }
        DrawBatch batch = obtain(type);
        batch.begin(view, sampler, shapeClipActive);
        order.add(batch);
        UiRenderDispatcher.recordBackendCommand(type);
        return batch;
    }

    public DrawBatch getOrCreateBlur(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                                     Renderer2D.BlurQuality quality, float offsetPx) {
        if (!active) return null;
        if (type == UiBatchType.LIQUID_GLASS) {
            UiBlurResources.requestLiquidGlassBlur();
        }
        boolean shapeClipActive = ClipFunction.isShapeClipActive();
        Renderer2D.BlurQuality normalizedQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        float normalizedOffset = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        if (!order.isEmpty()) {
            Object last = order.get(order.size() - 1);
            if (last instanceof DrawBatch drawBatch
                    && drawBatch.canMergeBlur(type, view, sampler, normalizedQuality, normalizedOffset, shapeClipActive)) {
                return drawBatch;
            }
        }
        DrawBatch batch = obtain(type);
        batch.beginBlur(view, sampler, normalizedQuality, normalizedOffset, shapeClipActive);
        order.add(batch);
        UiRenderDispatcher.recordBackendCommand(type);
        return batch;
    }

    public DrawBatch getOrCreateMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight) {
        if (!active) return null;
        boolean shapeClipActive = ClipFunction.isShapeClipActive();
        if (!order.isEmpty()) {
            Object last = order.get(order.size() - 1);
            if (last instanceof DrawBatch drawBatch && drawBatch.canMergeMsdf(view, sampler, pxRange, atlasWidth, atlasHeight, shapeClipActive)) {
                return drawBatch;
            }
        }
        DrawBatch batch = obtain(UiBatchType.SVG_MSDF);
        batch.beginMsdf(view, sampler, pxRange, atlasWidth, atlasHeight, shapeClipActive);
        order.add(batch);
        UiRenderDispatcher.recordBackendCommand(UiBatchType.SVG_MSDF);
        return batch;
    }

    public ItemBatch getOrCreateItemBatch(@Nullable GuiGraphicsExtractor context,
                                   ItemStack stack) {
        if (!active) return null;
        ScreenRectangle scissor = context != null ? context.scissorStack.peek() : null;
        if (!order.isEmpty()) {
            Object last = order.get(order.size() - 1);
            if (last instanceof ItemBatch itemBatch && itemBatch.canMerge(context, scissor)) {
                return itemBatch;
            }
        }
        ItemBatch batch = obtainItemBatch();
        batch.begin(context, scissor);
        order.add(batch);
        UiRenderDispatcher.recordBackendCommand("ITEM");
        return batch;
    }

    public TextBatch getOrCreateTextBatch(String label, GlyphFont font, RenderPipeline pipeline,
                                   TextPlacementMode placement, boolean liquidGlassText) {
        if (!active) return null;
        if (liquidGlassText) {
            UiBlurResources.requestLiquidGlassBlur();
        }
        TextPlacementMode normalizedPlacement = placement != null ? placement : TextPlacementMode.UI;
        boolean shapeClipActive = ClipFunction.isShapeClipActive();

        if (!order.isEmpty()) {
            Object last = order.get(order.size() - 1);
            if (last instanceof TextBatch textBatch
                    && textBatch.canMerge(font, pipeline, normalizedPlacement, liquidGlassText, shapeClipActive)) {
                return textBatch;
            }
        }

        TextBatch batch = obtainTextBatch();
        batch.begin(label, font, pipeline, normalizedPlacement, liquidGlassText, shapeClipActive);
        order.add(batch);
        UiRenderDispatcher.recordBackendCommand(liquidGlassText ? "LIQUID_GLASS_TEXT" : "TEXT");
        return batch;
    }

    DrawBatch obtain(UiBatchType type) {
        int idx = type.ordinal();
        ObjectArrayList<DrawBatch> pool = pools[idx];
        int cursor = poolCursor[idx];
        DrawBatch batch;
        if (cursor < pool.size()) {
            batch = pool.get(cursor);
        } else {
            batch = new DrawBatch(type);
            pool.add(batch);
        }
        poolCursor[idx] = cursor + 1;
        return batch;
    }

    ItemBatch obtainItemBatch() {
        int cursor = itemPoolCursor;
        ItemBatch batch;
        if (cursor < itemPool.size()) {
            batch = itemPool.get(cursor);
        } else {
            batch = new ItemBatch();
            itemPool.add(batch);
        }
        itemPoolCursor = cursor + 1;
        return batch;
    }

    TextBatch obtainTextBatch() {
        int cursor = textPoolCursor;
        TextBatch batch;
        if (cursor < textPool.size()) {
            batch = textPool.get(cursor);
        } else {
            batch = new TextBatch();
            textPool.add(batch);
        }
        textPoolCursor = cursor + 1;
        return batch;
    }

    int flushLiquidGlassTextBatch(TextBatch textBatch,
                                          Minecraft mc,
                                          GpuTextureView mainColorView,
                                          @Nullable GpuTextureView liquidSourceView,
                                          @Nullable GpuSampler liquidSourceSampler,
                                          float screenW,
                                          float screenH,
                                          float uiScale) {
        if (textBatch == null || textBatch.mesh == null || textBatch.font == null) return 0;
        if (!textBatch.font.isReady()) return 0;

        AbstractTexture glyphTexture = textBatch.font.getTexture();
        if (glyphTexture == null || glyphTexture.getTextureView() == null || glyphTexture.getSampler() == null) return 0;

        GpuTextureView sourceView = liquidSourceView;
        GpuSampler sourceSampler = liquidSourceSampler;
        if (sourceView == null || sourceSampler == null || sourceView == mainColorView) return 0;

        int blurPassCalls = 0;
        if (textBatch.shapeClipActive) {
            adoptFrameBlurCache(sourceView, sourceSampler, screenW, screenH, uiScale,
                    Renderer2D.DEFAULT_LIQUID_GLASS_BLUR_QUALITY, Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX);
        } else {
            blurPassCalls = prepareSharedBlur(mc, sourceView, sourceSampler, screenW, screenH, uiScale,
                    Renderer2D.DEFAULT_LIQUID_GLASS_BLUR_QUALITY, Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX);
        }
        GpuTextureView blurView = matchingSharedBlurView(sourceView, sourceSampler);
        GpuSampler blurSampler = matchingSharedBlurSampler(sourceView, sourceSampler);
        if (blurView == null || blurSampler == null) {
            blurView = sourceView;
            blurSampler = sourceSampler;
        }
        if (blurView == mainColorView) return 0;

        UIBatchUniforms.update(screenW, screenH);
        MeshRenderer builder = MeshRenderer.begin()
                .attachments(mainColorView, null)
                .pipeline(textBatch.pipeline)
                .mesh(textBatch.mesh)
                .uniform("UIBatch", UIBatchUniforms.get())
                .sampler("u_Texture", glyphTexture.getTextureView(), glyphTexture.getSampler())
                .sampler("u_SceneTexture", sourceView, sourceSampler)
                .sampler("u_BlurTexture", blurView, blurSampler);

        if (textBatch.font.isMsdf()) {
            MsdfTextUniforms.update(textBatch.font.getPxRange(), textBatch.font.getAtlasWidth(), textBatch.font.getAtlasHeight());
            builder.uniform("MsdfText", MsdfTextUniforms.get());
        }

        builder.end();
        return 1 + blurPassCalls;
    }

    public void flush(boolean finish) {
        if (!active) return;
        if (this == Renderer2D.UI_BATCHER && UiDeferredScheduler.shouldDefer()) {
            deferCurrent(finish);
            return;
        }
        flushing = true;
        try {
            if (order.isEmpty()) {
                Renderer2D.BATCH_STATS.noteEmpty(active, poolTotal());
                if (finish) {
                    resetSharedBlur();
                    active = false;
                    Renderer2D.BATCH_STATS.setActive(false);
                }
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                Renderer2D.BATCH_STATS.noteFailure("mc null");
                resetOrder();
                resetSharedBlur();
                if (finish) {
                    active = false;
                    Renderer2D.BATCH_STATS.setActive(false);
                }
                return;
            }
            RenderTarget fb = mc.gameRenderer.mainRenderTarget();
            if (fb == null) {
                Renderer2D.BATCH_STATS.noteFailure("framebuffer null");
                resetOrder();
                resetSharedBlur();
                if (finish) {
                    active = false;
                    Renderer2D.BATCH_STATS.setActive(false);
                }
                return;
            }
            GpuTextureView mainColorView = fb.getColorTextureView();
            if (mainColorView == null) {
                Renderer2D.BATCH_STATS.noteFailure("framebuffer color view null");
                resetOrder();
                resetSharedBlur();
                if (finish) {
                    active = false;
                    Renderer2D.BATCH_STATS.setActive(false);
                }
                return;
            }

            float screenW = mc.getWindow().getWidth();
            float screenH = mc.getWindow().getHeight();
            float uiScale = ViewportContext.getUiScale();
            if (uiScale <= 0.0f) uiScale = 1.0f;
            com.mojang.blaze3d.buffers.GpuBufferSlice uiBatch = null;
            resetSharedBlur();

            boolean hasLiquidGlass = false;
            for (Object orderedEntry : order) {
                if (orderedEntry instanceof DrawBatch orderedBatch && orderedBatch.type == UiBatchType.LIQUID_GLASS) {
                    hasLiquidGlass = true;
                    break;
                }
                if (orderedEntry instanceof TextBatch orderedText && orderedText.liquidGlassText) {
                    hasLiquidGlass = true;
                    break;
                }
            }

            GpuTextureView liquidSourceView = null;
            GpuSampler liquidSourceSampler = null;

            int drawCalls = 0;
            int vertices = 0;
            int indices = 0;

            TextureTarget fxBuffer = null;
            GpuTextureView fxView = null;
            GpuSampler fxSampler = null;
            MeshBuilder fxCompositeMesh = null;

            if (hasLiquidGlass) {
                /*
                 * The HUD/world glass source is captured before GUI extraction. Never perform a
                 * fallback copy of the active main framebuffer from inside a batch flush: doing so
                 * inserts a render-target transition in the middle of the HUD phase and can stall the
                 * frame or temporarily discard already submitted lower layers.
                 */
                TextureTarget sourceBuffer = UiBlurResources.ensureGlassSource(mc);
                GpuSampler copySampler = PostProcessManager.getSampler();
                if (UiBlurResources.isWorldSourceReady() && sourceBuffer != null && copySampler != null) {
                    GpuTextureView sourceTargetView = sourceBuffer.getColorTextureView();
                    if (sourceTargetView != null && sourceTargetView != mainColorView) {
                        liquidSourceView = sourceTargetView;
                        liquidSourceSampler = copySampler;
                    }
                }
            }

            for (Object entry : order) {
                if (entry instanceof ItemBatch itemBatch) {
                    if (itemBatch.isEmpty()) {
                        continue;
                    }
                    drawCalls += ItemBatchRenderer.flush(itemBatch);
                    continue;
                }
                if (entry instanceof TextBatch textBatch) {
                    if (!textBatch.isEmpty()) {
                        vertices += textBatch.mesh.getVertexCount();
                        indices += textBatch.mesh.getIndicesCount();
                        if (textBatch.liquidGlassText) {
                            drawCalls += flushLiquidGlassTextBatch(textBatch, mc, mainColorView,
                                    liquidSourceView, liquidSourceSampler, screenW, screenH, uiScale);
                        } else {
                            drawCalls++;
                            TextRenderSystem.submitGlyphMeshImmediate(textBatch.label, textBatch.font, textBatch.mesh, textBatch.pipeline, textBatch.placement);
                        }
                    }
                    continue;
                }

                DrawBatch batch = (DrawBatch) entry;
                if (batch.mesh.isBuilding()) batch.mesh.end();
                if (batch.mesh.getIndicesCount() <= 0) continue;

                drawCalls++;
                vertices += batch.mesh.getVertexCount();
                indices += batch.mesh.getIndicesCount();

                if (batch.type == UiBatchType.BLUR || batch.type == UiBatchType.GLASS_BLUR || batch.type == UiBatchType.BLUR_COMPLEX) {
                    int blurPassCalls = prepareSharedBlur(mc, batch.view, batch.sampler, screenW, screenH, uiScale,
                            batch.blurQuality, batch.blurOffsetPx);
                    if (sharedBlurredView != null && sharedBlurredSampler != null) {
                        MeshRenderer builder = MeshRenderer.begin()
                                .attachments(mainColorView, null)
                                .pipeline(batch.type.pipeline)
                                .mesh(batch.mesh);

                        if (uiBatch == null) {
                            UIBatchUniforms.update(screenW, screenH);
                            uiBatch = UIBatchUniforms.get();
                        }
                        builder.uniform("UIBatch", uiBatch);
                        builder.sampler("u_Texture", sharedBlurredView, sharedBlurredSampler);
                        builder.end();

                        drawCalls += blurPassCalls;
                        continue;
                    }
                    if (batch.view == mainColorView) {
                        continue;
                    }
                }

                if (batch.type == UiBatchType.LIQUID_GLASS) {
                    GpuTextureView sourceView = liquidSourceView != null ? liquidSourceView : batch.view;
                    GpuSampler sourceSampler = liquidSourceSampler != null ? liquidSourceSampler : batch.sampler;
                    boolean clippedComposite = batch.shapeClipActive;
                    if (clippedComposite) {
                        adoptFrameBlurCache(sourceView, sourceSampler, screenW, screenH, uiScale,
                                batch.blurQuality, batch.blurOffsetPx);
                    } else {
                        drawCalls += prepareSharedBlur(mc, sourceView, sourceSampler, screenW, screenH, uiScale,
                                batch.blurQuality, batch.blurOffsetPx);
                    }
                    GpuTextureView liquidBlurView = matchingSharedBlurView(sourceView, sourceSampler);
                    GpuSampler liquidBlurSampler = matchingSharedBlurSampler(sourceView, sourceSampler);
                    if (liquidBlurView == null || liquidBlurSampler == null) {
                        liquidBlurView = sourceView;
                        liquidBlurSampler = sourceSampler;
                    }

                    if (sourceView == null || sourceSampler == null
                            || liquidBlurView == null || liquidBlurSampler == null
                            || sourceView == mainColorView
                            || liquidBlurView == mainColorView) {
                        continue;
                    }

                    MeshRenderer directBuilder = MeshRenderer.begin()
                            .attachments(mainColorView, null)
                            .pipeline(batch.type.pipeline)
                            .mesh(batch.mesh);

                    if (uiBatch == null) {
                        UIBatchUniforms.update(screenW, screenH);
                        uiBatch = UIBatchUniforms.get();
                    }
                    directBuilder.uniform("UIBatch", uiBatch);
                    directBuilder.sampler("u_Texture", sourceView, sourceSampler);
                    directBuilder.sampler("u_BlurTexture", liquidBlurView, liquidBlurSampler);
                    directBuilder.end();
                    continue;
                }

                boolean isFx = batch.type == UiBatchType.BLUR || batch.type == UiBatchType.GLASS_BLUR || batch.type == UiBatchType.BLUR_COMPLEX;
                if (isFx) {
                    if (fxBuffer == null) {
                        fxBuffer = UiBlurResources.ensureEffects(mc);
                        if (fxBuffer != null) {
                            fxView = fxBuffer.getColorTextureView();
                            fxSampler = PostProcessManager.getSampler();
                            int compW = Math.max(1, Math.round(screenW / uiScale));
                            int compH = Math.max(1, Math.round(screenH / uiScale));
                            fxCompositeMesh = UiBlurResources.ensureCompositeMesh(compW, compH);
                        }
                    }
                    if (fxBuffer != null && fxView != null && fxSampler != null && fxCompositeMesh != null) {
                        MeshRenderer fxBuilder = MeshRenderer.begin()
                                .attachments(fxBuffer)
                                .clearColor(0x00000000)
                                .pipeline(batch.type.pipeline)
                                .mesh(batch.mesh);

                        if (batch.type.needsUiBatch) {
                            if (uiBatch == null) {
                                UIBatchUniforms.update(screenW, screenH);
                                uiBatch = UIBatchUniforms.get();
                            }
                            fxBuilder.uniform("UIBatch", uiBatch);
                        }
                        if (batch.type.usesSampler) {
                            GpuTextureView sourceView = batch.type == UiBatchType.LIQUID_GLASS && liquidSourceView != null ? liquidSourceView : batch.view;
                            GpuSampler sourceSampler = batch.type == UiBatchType.LIQUID_GLASS && liquidSourceSampler != null ? liquidSourceSampler : batch.sampler;
                            fxBuilder.sampler("u_Texture", sourceView, sourceSampler);
                        }
                        if (batch.type == UiBatchType.LIQUID_GLASS) {
                            GpuTextureView liquidBlurView = sharedBlurredView != null ? sharedBlurredView : batch.view;
                            GpuSampler liquidBlurSampler = sharedBlurredSampler != null ? sharedBlurredSampler : batch.sampler;
                            if (liquidBlurView != null && liquidBlurSampler != null) {
                                fxBuilder.sampler("u_BlurTexture", liquidBlurView, liquidBlurSampler);
                            }
                        }

                        fxBuilder.end();

                        MeshRenderer.begin()
                                .attachments(mainColorView, null)
                                .pipeline(CombatantRenderPipelines.UI_TEXTURED)
                                .mesh(fxCompositeMesh)
                                .sampler("u_Texture", fxView, fxSampler)
                                .end();

                        drawCalls++;
                        continue;
                    }

                    // Never sample from the main color attachment while writing to it.
                    // If offscreen FX target is unavailable, skip this pass to avoid undefined behavior.
                    if (batch.type.usesSampler
                            && batch.view == mainColorView
                            && !(batch.type == UiBatchType.LIQUID_GLASS && liquidSourceView != null)) {
                        continue;
                    }
                }

                if ((batch.type == UiBatchType.BLUR || batch.type == UiBatchType.GLASS_BLUR || batch.type == UiBatchType.BLUR_COMPLEX || batch.type == UiBatchType.LIQUID_GLASS)
                        && batch.type.usesSampler
                        && batch.view == mainColorView
                        && !(batch.type == UiBatchType.LIQUID_GLASS && liquidSourceView != null)) {
                    continue;
                }

                MeshRenderer builder = MeshRenderer.begin()
                        .attachments(mainColorView, null)
                        .pipeline(batch.type.pipeline)
                        .mesh(batch.mesh);

                if (batch.type.needsUiBatch) {
                    if (uiBatch == null) {
                        UIBatchUniforms.update(screenW, screenH);
                        uiBatch = UIBatchUniforms.get();
                    }
                    builder.uniform("UIBatch", uiBatch);
                }
                if (batch.type.usesSampler) {
                    GpuTextureView sourceView = batch.type == UiBatchType.LIQUID_GLASS && liquidSourceView != null ? liquidSourceView : batch.view;
                    GpuSampler sourceSampler = batch.type == UiBatchType.LIQUID_GLASS && liquidSourceSampler != null ? liquidSourceSampler : batch.sampler;
                    builder.sampler("u_Texture", sourceView, sourceSampler);
                }
                if (batch.type == UiBatchType.SVG_MSDF) {
                    MsdfTextUniforms.update(batch.msdfPxRange, batch.msdfAtlasWidth, batch.msdfAtlasHeight);
                    builder.uniform("MsdfText", MsdfTextUniforms.get());
                }
                if (batch.type == UiBatchType.LIQUID_GLASS) {
                    GpuTextureView liquidBlurView = sharedBlurredView != null ? sharedBlurredView : batch.view;
                    GpuSampler liquidBlurSampler = sharedBlurredSampler != null ? sharedBlurredSampler : batch.sampler;
                    if (liquidBlurView != null && liquidBlurSampler != null) {
                        builder.sampler("u_BlurTexture", liquidBlurView, liquidBlurSampler);
                    }
                }

                builder.end();
            }

            int batches = order.size();
            Renderer2D.BATCH_STATS.update(active, batches, drawCalls, vertices, indices, poolTotal());
            Renderer2D.BATCH_STATS.addFrame(batches, drawCalls, vertices, indices);

            resetOrder();
            if (finish) {
                resetSharedBlur();
                active = false;
                Renderer2D.BATCH_STATS.setActive(false);
            }
        } finally {
            flushing = false;
        }
    }

    boolean isPureItemBatchOrder() {
        if (order.isEmpty()) return false;
        for (int i = 0, size = order.size(); i < size; i++) {
            if (!(order.get(i) instanceof ItemBatch)) {
                return false;
            }
        }
        return true;
    }

    void deferCurrent(boolean finish) {
        if (order.isEmpty()) {
            Renderer2D.BATCH_STATS.noteEmpty(active, poolTotal());
            if (finish) {
                resetSharedBlur();
                active = false;
                Renderer2D.BATCH_STATS.setActive(false);
            }
            return;
        }

        int batches = order.size();
        int vertices = 0;
        int indices = 0;
        for (int i = 0; i < order.size(); i++) {
            Object entry = order.get(i);
            if (entry instanceof DrawBatch drawBatch) {
                if (drawBatch.mesh.isBuilding()) drawBatch.mesh.end();
                vertices += drawBatch.mesh.getVertexCount();
                indices += drawBatch.mesh.getIndicesCount();
            } else if (entry instanceof TextBatch textBatch && textBatch.mesh != null) {
                if (textBatch.mesh.isBuilding()) textBatch.mesh.end();
                vertices += textBatch.mesh.getVertexCount();
                indices += textBatch.mesh.getIndicesCount();
            }
        }

        OrderedUiBatcher submitted = this;
        UiDeferredScheduler.enqueue(new DeferredOrderedSubmit(
                UiDeferredScheduler.layerForCurrentPhase(isPureItemBatchOrder()),
                UiDeferredScheduler.snapshotViewport(),
                ScissorFunction.currentFramebufferScissor(),
                submitted
        ));

        OrderedUiBatcher replacement = UiDeferredScheduler.obtainBatcher();
        replacement.prepareAsReplacement(!finish);
        Renderer2D.UI_BATCHER = replacement;

        Renderer2D.BATCH_STATS.update(active, batches, 0, vertices, indices, poolTotal());
        Renderer2D.BATCH_STATS.setActive(!finish);
    }

    void prepareAsReplacement(boolean active) {
        resetOrder();
        resetSharedBlur();
        this.active = active;
    }

    public void releaseAfterDeferred() {
        resetOrder();
        resetSharedBlur();
        active = false;
        flushing = false;
    }

    private boolean adoptFrameBlurCache(@Nullable GpuTextureView sourceView,
                                        @Nullable GpuSampler sourceSampler,
                                        float screenW,
                                        float screenH,
                                        float uiScale,
                                        Renderer2D.BlurQuality quality,
                                        float offsetPx) {
        if (sourceView == null || sourceSampler == null) return false;
        FrameBlurCacheEntry cachedBlur = UiBlurResources.findReusable(
                UiBlurResources.currentFrameId(),
                UiBlurResources.currentPhase(),
                sourceView,
                sourceSampler,
                screenW,
                screenH,
                uiScale,
                quality,
                offsetPx
        );
        if (cachedBlur == null || cachedBlur.blurredView == null || cachedBlur.blurredSampler == null) {
            return false;
        }
        sharedBlurSourceView = sourceView;
        sharedBlurSourceSampler = sourceSampler;
        sharedBlurredView = cachedBlur.blurredView;
        sharedBlurredSampler = cachedBlur.blurredSampler;
        sharedBlurQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        sharedBlurOffsetPx = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        return true;
    }

    @Nullable
    GpuTextureView matchingSharedBlurView(@Nullable GpuTextureView sourceView, @Nullable GpuSampler sourceSampler) {
        if (sourceView == null || sourceSampler == null) return null;
        if (sharedBlurredView == null || sharedBlurredSampler == null) return null;
        if (sharedBlurSourceView != sourceView || sharedBlurSourceSampler != sourceSampler) return null;
        return sharedBlurredView;
    }

    @Nullable
    GpuSampler matchingSharedBlurSampler(@Nullable GpuTextureView sourceView, @Nullable GpuSampler sourceSampler) {
        if (matchingSharedBlurView(sourceView, sourceSampler) == null) return null;
        return sharedBlurredSampler;
    }

    int prepareSharedBlur(Minecraft mc,
                                  @Nullable GpuTextureView sourceView,
                                  @Nullable GpuSampler sourceSampler,
                                  float screenW,
                                  float screenH,
                                  float uiScale) {
        return prepareSharedBlur(mc, sourceView, sourceSampler, screenW, screenH, uiScale,
                Renderer2D.DEFAULT_BLUR_QUALITY, Renderer2D.DEFAULT_KAWASE_OFFSET_PX);
    }

    int prepareSharedBlur(Minecraft mc,
                          @Nullable GpuTextureView sourceView,
                          @Nullable GpuSampler sourceSampler,
                          float screenW,
                          float screenH,
                          float uiScale,
                          Renderer2D.BlurQuality blurQuality,
                          float offsetPx) {
        if (CombatantRenderSystem.rhi().shapeClip().isActive()) {
            adoptFrameBlurCache(sourceView, sourceSampler, screenW, screenH, uiScale, blurQuality, offsetPx);
            return 0;
        }
        return prepareSharedBlurInternal(mc, sourceView, sourceSampler, screenW, screenH, uiScale, blurQuality, offsetPx);
    }

    private int prepareSharedBlurInternal(Minecraft mc,
                                          @Nullable GpuTextureView sourceView,
                                          @Nullable GpuSampler sourceSampler,
                                          float screenW,
                                          float screenH,
                                          float uiScale,
                                          Renderer2D.BlurQuality blurQuality,
                                          float offsetPx) {
        if (sourceView == null || sourceSampler == null) {
            return 0;
        }
        Renderer2D.BlurQuality quality = blurQuality != null ? blurQuality : Renderer2D.DEFAULT_BLUR_QUALITY;
        int iterations = Math.max(1, quality.iterations);
        float passOffset = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;

        if (sharedBlurredView != null && sharedBlurredSampler != null
                && sharedBlurSourceView == sourceView
                && sharedBlurSourceSampler == sourceSampler
                && sharedBlurQuality == quality
                && Float.compare(sharedBlurOffsetPx, passOffset) == 0) {
            return 0;
        }

        long frameId = UiBlurResources.currentFrameId();
        RenderPhase phase = UiBlurResources.currentPhase();
        FrameBlurCacheEntry cachedBlur = UiBlurResources.findReusable(frameId, phase, sourceView, sourceSampler,
                screenW, screenH, uiScale, quality, passOffset);
        if (cachedBlur != null) {
            sharedBlurSourceView = sourceView;
            sharedBlurSourceSampler = sourceSampler;
            sharedBlurredView = cachedBlur.blurredView;
            sharedBlurredSampler = cachedBlur.blurredSampler;
            sharedBlurQuality = quality;
            sharedBlurOffsetPx = passOffset;
            return 0;
        }

        resetSharedBlur();

        GpuSampler blurSampler = PostProcessManager.getSampler();
        if (blurSampler == null) {
            return 0;
        }

        GpuTextureView currentView = sourceView;
        GpuSampler currentSampler = sourceSampler;
        int sourceW = Math.max(1, Math.round(screenW));
        int sourceH = Math.max(1, Math.round(screenH));
        int drawCalls = 0;

        for (int level = 0; level < iterations; level++) {
            TextureTarget target = UiBlurResources.ensureKawaseDown(mc, level);
            if (target == null) return 0;
            if (!drawKawasePass(target, currentView, currentSampler, sourceW, sourceH, passOffset, false)) {
                return 0;
            }

            currentView = target.getColorTextureView();
            currentSampler = blurSampler;
            sourceW = Math.max(1, target.width);
            sourceH = Math.max(1, target.height);
            drawCalls++;
            if (currentView == null) return 0;
        }

        for (int level = iterations - 2; level >= 0; level--) {
            TextureTarget target = UiBlurResources.ensureKawaseUp(mc, level);
            if (target == null) return 0;
            if (!drawKawasePass(target, currentView, currentSampler, sourceW, sourceH, passOffset, true)) {
                return 0;
            }

            currentView = target.getColorTextureView();
            currentSampler = blurSampler;
            sourceW = Math.max(1, target.width);
            sourceH = Math.max(1, target.height);
            drawCalls++;
            if (currentView == null) return 0;
        }

        if (currentView == null) {
            return 0;
        }

        sharedBlurSourceView = sourceView;
        sharedBlurSourceSampler = sourceSampler;
        sharedBlurredView = currentView;
        sharedBlurredSampler = blurSampler;
        sharedBlurQuality = quality;
        sharedBlurOffsetPx = passOffset;
        UiBlurResources.remember(frameId, phase, sourceView, sourceSampler, currentView, blurSampler,
                screenW, screenH, uiScale, quality, passOffset);
        return drawCalls;
    }

    private static boolean drawKawasePass(TextureTarget target,
                                          GpuTextureView sourceView,
                                          GpuSampler sourceSampler,
                                          int sourceW,
                                          int sourceH,
                                          float passOffset,
                                          boolean upPass) {
        if (target == null || sourceView == null || sourceSampler == null) return false;
        MeshBuilder compositeMesh = UiBlurResources.ensureCompositeMesh(target.width, target.height);
        if (compositeMesh == null) return false;

        Matrix4f previousProjection = MeshRenderer.projection();
        try {
            MeshRenderer.setProjection(orthoProjection(target.width, target.height));
            UIBlurUniforms.update(sourceW, sourceH, target.width, target.height,
                    0f, 0f, passOffset, upPass ? 1.0f : 0.0f, 1.0f, 1.0f, 0xFFFFFF);
            MeshRenderer.begin()
                    .attachments(target)
                    .clearColor(0x00000000)
                    .pipeline(CombatantRenderPipelines.UI_BLUR)
                    .mesh(compositeMesh)
                    .uniform("UIBlur", UIBlurUniforms.get())
                    .sampler("u_Texture", sourceView, sourceSampler)
                    .end();
            return target.getColorTextureView() != null;
        } finally {
            MeshRenderer.setProjection(previousProjection);
        }
    }

    private static Matrix4f orthoProjection(float width, float height) {
        Projection projection = new Projection();
        projection.setupOrtho(-1000.0f, 1000.0f, Math.max(1.0f, width), Math.max(1.0f, height), true);
        return projection.getMatrix(new Matrix4f());
    }

    void resetSharedBlur() {
        sharedBlurSourceView = null;
        sharedBlurSourceSampler = null;
        sharedBlurredView = null;
        sharedBlurredSampler = null;
        sharedBlurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
        sharedBlurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
    }

    void resetOrder() {
        order.clear();
        for (int i = 0; i < poolCursor.length; i++) {
            poolCursor[i] = 0;
        }
        itemPoolCursor = 0;
        textPoolCursor = 0;
    }


    public void closeRetainedBuffers() {
        resetOrder();
        resetSharedBlur();
        active = false;
        flushing = false;

        for (ObjectArrayList<DrawBatch> pool : pools) {
            for (int i = 0, size = pool.size(); i < size; i++) {
                DrawBatch batch = pool.get(i);
                if (batch != null && batch.mesh != null) {
                    batch.mesh.close();
                }
            }
        }

        for (int i = 0, size = textPool.size(); i < size; i++) {
            TextBatch batch = textPool.get(i);
            if (batch != null && batch.mesh != null) {
                batch.mesh.close();
            }
        }

        for (int i = 0, size = itemPool.size(); i < size; i++) {
            ItemBatch batch = itemPool.get(i);
            if (batch != null) {
                batch.commands.clear();
                batch.resolvedStates.clear();
                batch.context = null;
                batch.scissor = null;
            }
        }
    }

    int poolTotal() {
        int total = 0;
        for (ObjectArrayList<DrawBatch> pool : pools) {
            total += pool.size();
        }
        return total + itemPool.size() + textPool.size();
    }
}
