/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

public final class UiCommandStats {
    private long frameId;
    private int recordedCommands;
    private int shapeCommands;
    private int pathCommands;
    private int textureCommands;
    private int itemCommands;
    private int effectCommands;
    private int compiledPasses;
    private int compiledOrderedBatches;
    private int compiledCaptureAwarePasses;
    private int rhiDrawCommands;
    private int backendDrawCalls;

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        recordedCommands = shapeCommands = pathCommands = textureCommands = 0;
        itemCommands = effectCommands = 0;
        compiledPasses = compiledOrderedBatches = compiledCaptureAwarePasses = rhiDrawCommands = backendDrawCalls = 0;
    }

    public void record(UiCommandKind kind) {
        if (kind == null) return;
        recordedCommands++;
        switch (kind) {
            case SHAPE -> shapeCommands++;
            case PATH -> pathCommands++;
            case TEXTURE -> textureCommands++;
            case ITEM -> itemCommands++;
            case EFFECT_REGION -> effectCommands++;
        }
    }

    public void addCompiledPasses(int count) {
        compiledPasses += Math.max(0, count);
    }

    public void addCompiledOrderedBatches(int count) {
        compiledOrderedBatches += Math.max(0, count);
    }

    public void addCompiledCaptureAwarePasses(int count) {
        compiledCaptureAwarePasses += Math.max(0, count);
    }

    public void addExecutionStats(int drawCommands, int drawCalls) {
        rhiDrawCommands += Math.max(0, drawCommands);
        backendDrawCalls += Math.max(0, drawCalls);
    }

    public UiStatsSnapshot snapshot() {
        return new UiStatsSnapshot(
                frameId,
                recordedCommands,
                shapeCommands,
                pathCommands,
                textureCommands,
                itemCommands,
                effectCommands,
                compiledPasses,
                compiledOrderedBatches,
                compiledCaptureAwarePasses,
                rhiDrawCommands,
                backendDrawCalls
        );
    }
}
