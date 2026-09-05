/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;

import java.util.List;

/**
 * Ordered executable UI plan. The normalized command counts are diagnostics; {@link #passes()}
 * is the production work stream consumed by {@link UiPassExecutor}.
 */
public final class UiBatchPlan {
    public static final UiBatchPlan EMPTY = new UiBatchPlan(
            List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, UiBackdropPlan.EMPTY
    );

    @FunctionalInterface
    public interface Work {
        void execute(RenderFrameContext context, CombatantRhi rhi);
    }

    public record Pass(String label,
                       int orderedBatchCount,
                       List<RhiDrawCommand> drawCommands,
                       List<TransientTargetDescriptor> transientTargets,
                       Work work) {
        public Pass(String label, int orderedBatchCount, Work work) {
            this(label, orderedBatchCount, List.of(), List.of(), work);
        }

        public Pass(String label,
                    int orderedBatchCount,
                    List<RhiDrawCommand> drawCommands,
                    Work work) {
            this(label, orderedBatchCount, drawCommands, List.of(), work);
        }

        public Pass {
            label = label != null && !label.isBlank() ? label : "ui";
            orderedBatchCount = Math.max(0, orderedBatchCount);
            drawCommands = drawCommands == null || drawCommands.isEmpty() ? List.of() : List.copyOf(drawCommands);
            transientTargets = transientTargets == null || transientTargets.isEmpty()
                    ? List.of()
                    : List.copyOf(transientTargets);
            if (work == null) throw new IllegalArgumentException("work");
        }

        void executeWork(RenderFrameContext context, CombatantRhi rhi) {
            work.execute(context, rhi);
        }
    }

    private final List<Pass> passes;
    private final int commandCount;
    private final int shapeCount;
    private final int pathCount;
    private final int primitiveCount;
    private final int textureCount;
    private final int textCount;
    private final int itemCount;
    private final int effectCount;
    private final int orderedBatchCount;
    private final int rhiDrawCommandCount;
    private final int backendDrawCallCount;
    private final UiBackdropPlan backdropPlan;

    public UiBatchPlan(List<Pass> passes,
                       int commandCount,
                       int shapeCount,
                       int pathCount,
                       int primitiveCount,
                       int textureCount,
                       int textCount,
                       int itemCount,
                       int effectCount,
                       int orderedBatchCount,
                       int rhiDrawCommandCount,
                       int backendDrawCallCount) {
        this(passes, commandCount, shapeCount, pathCount, primitiveCount, textureCount, textCount,
                itemCount, effectCount, orderedBatchCount, rhiDrawCommandCount, backendDrawCallCount,
                UiBackdropPlan.EMPTY);
    }

    public UiBatchPlan(List<Pass> passes,
                       int commandCount,
                       int shapeCount,
                       int pathCount,
                       int primitiveCount,
                       int textureCount,
                       int textCount,
                       int itemCount,
                       int effectCount,
                       int orderedBatchCount,
                       int rhiDrawCommandCount,
                       int backendDrawCallCount,
                       UiBackdropPlan backdropPlan) {
        this.passes = passes == null || passes.isEmpty() ? List.of() : List.copyOf(passes);
        this.commandCount = Math.max(0, commandCount);
        this.shapeCount = Math.max(0, shapeCount);
        this.pathCount = Math.max(0, pathCount);
        this.primitiveCount = Math.max(0, primitiveCount);
        this.textureCount = Math.max(0, textureCount);
        this.textCount = Math.max(0, textCount);
        this.itemCount = Math.max(0, itemCount);
        this.effectCount = Math.max(0, effectCount);
        this.orderedBatchCount = Math.max(0, orderedBatchCount);
        this.rhiDrawCommandCount = Math.max(0, rhiDrawCommandCount);
        this.backendDrawCallCount = Math.max(0, backendDrawCallCount);
        this.backdropPlan = backdropPlan != null ? backdropPlan : UiBackdropPlan.EMPTY;
    }

    public List<Pass> passes() {
        return passes;
    }

    public int commandCount() {
        return commandCount;
    }

    public int shapeCount() {
        return shapeCount;
    }

    public int pathCount() {
        return pathCount;
    }

    public int primitiveCount() {
        return primitiveCount;
    }

    public int textureCount() {
        return textureCount;
    }

    public int textCount() {
        return textCount;
    }

    public int itemCount() {
        return itemCount;
    }

    public int effectCount() {
        return effectCount;
    }

    public int compiledPassCount() {
        return passes.size();
    }

    public int orderedBatchCount() {
        return orderedBatchCount;
    }

    /** Compatibility accessor; unlike the old implementation this is a real ordered-batch count. */
    public int batchCount() {
        return orderedBatchCount;
    }

    public int rhiDrawCommandCount() {
        return rhiDrawCommandCount;
    }

    public int backendDrawCallCount() {
        return backendDrawCallCount;
    }

    public UiBackdropPlan backdropPlan() {
        return backdropPlan;
    }

    UiBatchPlan withExecutionStats(int rhiDrawCommands, int backendDrawCalls) {
        return new UiBatchPlan(
                passes,
                commandCount,
                shapeCount,
                pathCount,
                primitiveCount,
                textureCount,
                textCount,
                itemCount,
                effectCount,
                orderedBatchCount,
                rhiDrawCommands,
                backendDrawCalls,
                backdropPlan
        );
    }
}
