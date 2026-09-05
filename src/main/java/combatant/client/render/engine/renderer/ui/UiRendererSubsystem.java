/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiStatsSnapshot;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.helpers.ScissorFunction;

/**
 * Single UI scheduling gateway. Normalized commands provide semantic/clip metadata while concrete
 * production work is owned by {@link UiPassCompiler} and executed only by {@link UiPassExecutor}.
 * {@link OrderedUiBatcher} remains an internal geometry/batching lowering component during migration.
 */
public final class UiRendererSubsystem {
    private final UiCommandBuffer commands = new UiCommandBuffer();
    private final UiPassCompiler compiler = new UiPassCompiler();
    private final UiPassExecutor executor = new UiPassExecutor();
    private UiBatchPlan lastPlan = UiBatchPlan.EMPTY;
    private boolean executing;

    public UiCommandBuffer commands() {
        return commands;
    }

    public void beginFrame(RenderFrameContext context) {
        commands.beginFrame(context);
    }

    public void record(UiCommand command) {
        commands.add(command, ScissorFunction.currentSnapshot(), ClipFunction.currentSnapshot());
    }

    public boolean hasPendingCommands() {
        return commands.size() > 0 || compiler.hasPendingWork();
    }

    public void submitOrdered(OrderedUiBatcher batcher, boolean finish) {
        if (batcher == null) return;
        compiler.enqueueOrdered(batcher, finish);
    }

    public void submitImmediate(String label, int orderedBatchCount, UiBatchPlan.Work work) {
        compiler.enqueue(label, orderedBatchCount, work);
    }

    public void flush(RenderFrameContext context, CombatantRhi rhi) {
        commands.beginFrame(context);
        if (executing) return;
        if (commands.size() == 0 && !compiler.hasPendingWork()) return;

        executing = true;
        try {
            while (commands.size() > 0 || compiler.hasPendingWork()) {
                UiBatchPlan compiled = compiler.compile(commands);

                // Clear before execution so re-entrant semantic recording belongs to the next plan.
                // The compiler likewise drains its work queue before returning this plan.
                commands.clear();

                UiBatchPlan executed = executor.execute(compiled, context, rhi);
                commands.stats().addExecutionStats(
                        executed.rhiDrawCommandCount(),
                        executed.backendDrawCallCount()
                );
                lastPlan = executed;
            }
        } finally {
            executing = false;
        }
    }

    public UiBatchPlan lastPlan() {
        return lastPlan;
    }

    public UiStatsSnapshot statsSnapshot() {
        return commands.statsSnapshot();
    }
}
