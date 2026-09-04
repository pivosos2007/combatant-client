/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiBatchBackendCommand;
import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiStatsSnapshot;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.helpers.ScissorFunction;

/**
 * UI rendering gateway: Renderer2D records normalized commands here, the compiler builds a
 * UI pass plan, and the executor owns the future RHI execution point. Renderer2D batcher mesh
 * draws are represented explicitly as backend commands.
 */
public final class UiRendererSubsystem {
    private final UiCommandBuffer commands = new UiCommandBuffer();
    private final UiPassCompiler compiler = new UiPassCompiler();
    private final UiPassExecutor executor = new UiPassExecutor();
    private final UiEffectGraph effects = new UiEffectGraph();
    private UiBatchPlan lastPlan = UiBatchPlan.EMPTY;

    public UiCommandBuffer commands() {
        return commands;
    }

    public void beginFrame(RenderFrameContext context) {
        commands.beginFrame(context);
    }

    public void record(UiCommand command) {
        commands.add(command, ScissorFunction.currentSnapshot(), ClipFunction.currentSnapshot());
    }

    public void recordBackendCommand(String backend, String batchType) {
        record(new UiBatchBackendCommand(backend, batchType));
    }

    public boolean hasPendingCommands() {
        return commands.size() > 0;
    }

    public void flush(RenderFrameContext context, CombatantRhi rhi) {
        commands.beginFrame(context);
        if (commands.size() == 0) {
            lastPlan = UiBatchPlan.EMPTY;
            executor.execute(lastPlan, context, rhi);
            return;
        }
        lastPlan = compiler.compile(commands);
        effects.execute(commands, context, rhi);
        executor.execute(lastPlan, context, rhi);
        commands.clear();
    }

    public UiBatchPlan lastPlan() {
        return lastPlan;
    }

    public UiStatsSnapshot statsSnapshot() {
        return commands.statsSnapshot();
    }
}
