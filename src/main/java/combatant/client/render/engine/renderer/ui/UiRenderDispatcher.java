/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiStatsSnapshot;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.GlyphFont;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import combatant.client.render.engine.uniform.MeshBuilder;

/** Command-stream and executable-pass gateway used by the Renderer2D facade. */
public final class UiRenderDispatcher {
    private static final UiRendererSubsystem SUBSYSTEM = new UiRendererSubsystem();

    private UiRenderDispatcher() {
    }

    public static UiStatsSnapshot statsSnapshot() {
        return SUBSYSTEM.statsSnapshot();
    }

    public static void beginLayer() {
        SUBSYSTEM.beginFrame(CombatantRenderSystem.ensureFrameContext());
    }

    public static void record(UiCommand command) {
        SUBSYSTEM.record(command);
    }

    public static boolean hasPendingCommands() {
        return SUBSYSTEM.hasPendingCommands();
    }

    public static void flushLayer() {
        if (UiDeferredScheduler.shouldDefer()) return;
        SUBSYSTEM.flush(CombatantRenderSystem.ensureFrameContext(), CombatantRenderSystem.rhi());
    }

    static void submitOrderedBatcher(OrderedUiBatcher batcher, boolean finish) {
        SUBSYSTEM.submitOrdered(batcher, finish);
    }

    public static void submitImmediate(String label, int orderedBatchCount, UiBatchPlan.Work work) {
        SUBSYSTEM.submitImmediate(label, orderedBatchCount, work);
    }

    public static boolean enqueueTextMesh(
            String label,
            GlyphFont font,
            MeshBuilder sourceMesh,
            RenderPipeline pipeline,
            TextPlacementMode placement) {
        OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
        if (!batcher.isActive() || batcher.isFlushing()) return false;
        if (font == null || sourceMesh == null || pipeline == null) return false;
        if (sourceMesh.isBuilding()) sourceMesh.end();
        if (sourceMesh.getIndicesCount() <= 0) return true;
        TextBatch batch = batcher.getOrCreateTextBatch(
                label,
                font,
                pipeline,
                placement != null ? placement : TextPlacementMode.UI
        );
        if (batch == null) return false;
        batch.append(sourceMesh);
        return true;
    }

    public static boolean beginAutoBatch() {
        if (Renderer2D.UI_BATCHER.isActive()) return false;
        Renderer2D.UI_BATCHER.begin();
        return true;
    }

    public static void endAutoBatch(boolean auto) {
        if (!auto) return;
        Renderer2D.BATCH_STATS.noteFlushReason(Renderer2D.FlushReason.AUTO_BATCH);
        Renderer2D.UI_BATCHER.flush(true);
        flushLayer();
    }

    public static void flushBatch(Renderer2D.FlushReason reason) {
        OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
        boolean pending = batcher.hasPendingWork() || SUBSYSTEM.hasPendingCommands();
        if (pending) Renderer2D.BATCH_STATS.noteFlushReason(reason);
        if (batcher.isActive()) batcher.flush(false);
        flushLayer();
    }

    public static boolean isFlushingBatch() {
        return Renderer2D.UI_BATCHER.isFlushing();
    }
}
