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

/**
 * Command-stream and ordered-batch gateway used by the Renderer2D facade.
 */
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

    public static void recordBackendCommand(UiBatchType type) {
        if (type != null) recordBackendCommand(type.name());
    }

    public static void recordBackendCommand(String batchType) {
        if (batchType != null && !batchType.isEmpty()) {
            SUBSYSTEM.recordBackendCommand("Renderer2D.OrderedUiBatcher", batchType);
        }
    }

    public static boolean enqueueTextMesh(
            String label,
            GlyphFont font,
            MeshBuilder sourceMesh,
            RenderPipeline pipeline,
            TextPlacementMode placement,
            boolean liquidGlassText) {
        OrderedUiBatcher batcher = Renderer2D.UI_BATCHER;
        if (!batcher.isActive() || batcher.isFlushing()) return false;
        if (font == null || sourceMesh == null || pipeline == null) return false;
        if (sourceMesh.isBuilding()) sourceMesh.end();
        if (sourceMesh.getIndicesCount() <= 0) return true;
        TextBatch batch = batcher.getOrCreateTextBatch(
                label,
                font,
                pipeline,
                placement != null ? placement : TextPlacementMode.UI,
                liquidGlassText
        );
        if (batch == null) return false;
        batch.append(sourceMesh);
        return true;
    }

    public static boolean submitLiquidGlassTextMeshImmediate(
            String label,
            GlyphFont font,
            MeshBuilder sourceMesh,
            RenderPipeline pipeline,
            TextPlacementMode placement) {
        if (font == null || sourceMesh == null || pipeline == null) return false;
        if (sourceMesh.isBuilding()) sourceMesh.end();
        if (sourceMesh.getIndicesCount() <= 0) return true;

        OrderedUiBatcher batcher = UiDeferredScheduler.obtainBatcher();
        try {
            batcher.begin();
            TextBatch batch = batcher.getOrCreateTextBatch(
                    label,
                    font,
                    pipeline,
                    placement != null ? placement : TextPlacementMode.UI,
                    true
            );
            if (batch == null) return false;
            batch.append(sourceMesh);
            batcher.flush(true);
            return true;
        } finally {
            UiDeferredScheduler.releaseBatcher(batcher);
        }
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
