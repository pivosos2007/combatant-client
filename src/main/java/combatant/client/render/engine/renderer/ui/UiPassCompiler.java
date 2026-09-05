/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiCommandKind;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.text.TextRenderSystem;
import combatant.client.render.engine.uniform.impl.MsdfTextUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the production UI work queue and compiles it into one ordered executable plan.
 *
 * <p>Normal draw/text-only {@link OrderedUiBatcher} submissions are lowered here to concrete
 * {@link RhiDrawCommand} sequences. Item/effect/glass submissions keep the existing ordered
 * lowering temporarily because they have explicit preparation/capture barriers, but they are still
 * scheduled and executed only through {@link UiPassExecutor}.</p>
 */
public final class UiPassCompiler {
    private final ArrayList<Object> pending = new ArrayList<>(8);

    private record OrderedSubmission(OrderedUiBatcher batcher, boolean finish) {
    }

    public void enqueue(String label, int orderedBatchCount, UiBatchPlan.Work work) {
        if (work == null) return;
        pending.add(new UiBatchPlan.Pass(label, orderedBatchCount, work));
    }

    public void enqueueOrdered(OrderedUiBatcher batcher, boolean finish) {
        if (batcher == null) return;
        pending.add(new OrderedSubmission(batcher, finish));
    }

    public boolean hasPendingWork() {
        return !pending.isEmpty();
    }

    public UiBatchPlan compile(UiCommandBuffer commands) {
        // Drain first. Work recorded while this plan executes belongs to the next compile iteration.
        List<Object> queued;
        if (pending.isEmpty()) {
            queued = List.of();
        } else {
            queued = List.copyOf(pending);
            pending.clear();
        }

        ArrayList<UiBatchPlan.Pass> executablePasses = new ArrayList<>(queued.size());
        for (Object work : queued) {
            if (work instanceof UiBatchPlan.Pass pass) {
                executablePasses.add(pass);
            } else if (work instanceof OrderedSubmission ordered) {
                executablePasses.add(compileOrdered(ordered));
            }
        }

        int shapes = 0;
        int paths = 0;
        int primitives = 0;
        int textures = 0;
        int text = 0;
        int items = 0;
        int effects = 0;
        int commandCount = 0;

        if (commands != null) {
            commandCount = commands.size();
            for (UiCommand command : commands.commands()) {
                UiCommandKind kind = command.kind();
                switch (kind) {
                    case SHAPE -> shapes++;
                    case PATH -> paths++;
                    case PRIMITIVE -> primitives++;
                    case TEXTURE -> textures++;
                    case TEXT -> text++;
                    case ITEM -> items++;
                    case BLUR_REGION, LIQUID_GLASS_REGION, EFFECT_REGION -> effects++;
                }
            }
        }

        int orderedBatches = 0;
        for (UiBatchPlan.Pass pass : executablePasses) {
            orderedBatches += pass.orderedBatchCount();
        }

        if (commands != null) {
            commands.stats().addCompiledPasses(executablePasses.size());
            commands.stats().addCompiledOrderedBatches(orderedBatches);
        }

        return new UiBatchPlan(
                executablePasses,
                commandCount,
                shapes,
                paths,
                primitives,
                textures,
                text,
                items,
                effects,
                orderedBatches,
                0,
                0
        );
    }

    private static UiBatchPlan.Pass compileOrdered(OrderedSubmission submission) {
        OrderedUiBatcher batcher = submission.batcher();
        boolean finish = submission.finish();
        int orderedBatchCount = batcher.pendingBatchCount();

        if (!canLowerDirectly(batcher)) {
            return legacyOrderedPass(batcher, finish, orderedBatchCount);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return legacyOrderedPass(batcher, finish, orderedBatchCount);
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return legacyOrderedPass(batcher, finish, orderedBatchCount);
        GpuTextureView mainColorView = UiMsaaClipLayer.currentColorAttachment(framebuffer.getColorTextureView());
        if (mainColorView == null) return legacyOrderedPass(batcher, finish, orderedBatchCount);

        float screenW = mc.getWindow().getWidth();
        float screenH = mc.getWindow().getHeight();
        ArrayList<RhiDrawCommand> draws = new ArrayList<>(Math.max(1, orderedBatchCount));
        GpuBufferSlice uiBatch = null;
        int logicalDraws = 0;
        int vertices = 0;
        int indices = 0;

        // executeCompiled() resets this before replay. Direct compiler lowering has the same
        // lifetime semantics even though these batches do not consume blur resources.
        batcher.resetSharedBlur();

        try {
            for (Object entry : batcher.order) {
                if (entry instanceof TextBatch textBatch) {
                    if (textBatch.isEmpty()) continue;
                    vertices += textBatch.mesh.getVertexCount();
                    indices += textBatch.mesh.getIndicesCount();
                    logicalDraws++;
                    TextRenderSystem.appendGlyphMeshCommand(
                            draws,
                            textBatch.label,
                            textBatch.font,
                            textBatch.mesh,
                            textBatch.pipeline,
                            textBatch.placement,
                            textBatch.clipSnapshot
                    );
                    continue;
                }

                DrawBatch batch = (DrawBatch) entry;
                if (batch.mesh.isBuilding()) batch.mesh.end();
                if (batch.mesh.getIndicesCount() <= 0) continue;

                logicalDraws++;
                vertices += batch.mesh.getVertexCount();
                indices += batch.mesh.getIndicesCount();

                MeshRenderer builder = MeshRenderer.begin()
                        .attachments(mainColorView, null)
                        .clearColor(UiMsaaClipLayer.consumePendingColorClear(mainColorView))
                        .pipeline(pipelineFor(batch))
                        .mesh(batch.mesh);

                if (batch.type.needsUiBatch) {
                    if (uiBatch == null) {
                        UIBatchUniforms.update(screenW, screenH);
                        uiBatch = UIBatchUniforms.get();
                    }
                    builder.uniform("UIBatch", uiBatch);
                }
                if (batch.clipSnapshot.usesAnalyticPipeline()) {
                    builder.uniform("UIClip", UiClipUniforms.write(batch.clipSnapshot));
                }
                if (batch.type.usesSampler) {
                    builder.sampler("u_Texture", batch.view, batch.sampler);
                }
                if (batch.type == UiBatchType.SVG_MSDF) {
                    MsdfTextUniforms.update(batch.msdfPxRange, batch.msdfAtlasWidth, batch.msdfAtlasHeight);
                    builder.uniform("MsdfText", MsdfTextUniforms.get());
                }

                builder.endTo(draws);
            }
        } catch (Throwable failure) {
            closeDraws(draws);
            throw failure;
        }

        List<RhiDrawCommand> compiledDraws = draws.isEmpty() ? List.of() : List.copyOf(draws);
        int compiledLogicalDraws = logicalDraws;
        int compiledVertices = vertices;
        int compiledIndices = indices;

        return new UiBatchPlan.Pass(
                "Renderer2D.RhiDrawSequence",
                orderedBatchCount,
                compiledDraws,
                (frame, rhi) -> completeDirect(
                        batcher,
                        finish,
                        orderedBatchCount,
                        compiledLogicalDraws,
                        compiledVertices,
                        compiledIndices
                )
        );
    }

    private static boolean canLowerDirectly(OrderedUiBatcher batcher) {
        if (batcher == null || !batcher.active || batcher.order.isEmpty()) return false;
        for (Object entry : batcher.order) {
            if (entry instanceof TextBatch) continue;
            if (!(entry instanceof DrawBatch batch)) return false;
            if (batch.type == UiBatchType.BLUR || batch.type == UiBatchType.BLUR_CORNERS) return false;
            if (batch.type.usesPreparedGlass()) return false;
        }
        return true;
    }

    private static UiBatchPlan.Pass legacyOrderedPass(OrderedUiBatcher batcher,
                                                       boolean finish,
                                                       int orderedBatchCount) {
        return new UiBatchPlan.Pass(
                "Renderer2D.OrderedSpecial",
                orderedBatchCount,
                (frame, rhi) -> batcher.executeCompiled(finish)
        );
    }

    private static void completeDirect(OrderedUiBatcher batcher,
                                       boolean finish,
                                       int orderedBatchCount,
                                       int logicalDraws,
                                       int vertices,
                                       int indices) {
        if (batcher == null || !batcher.active) return;

        Renderer2D.BATCH_STATS.update(
                batcher.active,
                orderedBatchCount,
                logicalDraws,
                vertices,
                indices,
                batcher.poolTotal()
        );
        Renderer2D.BATCH_STATS.addFrame(orderedBatchCount, logicalDraws, vertices, indices);

        batcher.resetOrder();
        if (finish) {
            batcher.resetSharedBlur();
            batcher.active = false;
            Renderer2D.BATCH_STATS.setActive(false);
        }
    }

    private static com.mojang.blaze3d.pipeline.RenderPipeline pipelineFor(DrawBatch batch) {
        if (batch.clipSnapshot.usesAnalyticPipeline() && !batch.type.supportsAnalyticClip()) {
            throw new IllegalStateException("UI batch " + batch.type
                    + " has no ANALYTIC_CLIP pipeline for clip snapshot " + batch.clipSnapshot.id());
        }
        return batch.type.pipelineFor(batch.clipSnapshot);
    }

    private static void closeDraws(List<RhiDrawCommand> draws) {
        if (draws == null || draws.isEmpty()) return;
        for (RhiDrawCommand command : draws) {
            if (command != null && command.mesh != null) command.mesh.close();
        }
    }
}
