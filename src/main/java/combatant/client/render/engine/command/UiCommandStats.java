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
    private int primitiveCommands;
    private int textureCommands;
    private int textCommands;
    private int itemCommands;
    private int effectCommands;
    private int compiledPasses;
    private int compiledOrderedBatches;
    private int compiledLegacySpecialPasses;
    private int rhiDrawCommands;
    private int backendDrawCalls;

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        recordedCommands = shapeCommands = pathCommands = primitiveCommands = textureCommands = 0;
        textCommands = itemCommands = effectCommands = 0;
        compiledPasses = compiledOrderedBatches = compiledLegacySpecialPasses = rhiDrawCommands = backendDrawCalls = 0;
    }

    public void record(UiCommand command) {
        if (command == null) return;
        recordedCommands++;
        switch (command.kind()) {
            case SHAPE -> shapeCommands++;
            case PATH -> pathCommands++;
            case PRIMITIVE -> primitiveCommands++;
            case TEXTURE -> textureCommands++;
            case TEXT -> textCommands++;
            case ITEM -> itemCommands++;
            case BLUR_REGION, LIQUID_GLASS_REGION, EFFECT_REGION -> effectCommands++;
        }
    }

    public void addCompiledPasses(int count) {
        compiledPasses += Math.max(0, count);
    }

    public void addCompiledOrderedBatches(int count) {
        compiledOrderedBatches += Math.max(0, count);
    }

    public void addCompiledLegacySpecialPasses(int count) {
        compiledLegacySpecialPasses += Math.max(0, count);
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
                primitiveCommands,
                textureCommands,
                textCommands,
                itemCommands,
                effectCommands,
                compiledPasses,
                compiledOrderedBatches,
                compiledLegacySpecialPasses,
                rhiDrawCommands,
                backendDrawCalls
        );
    }
}
