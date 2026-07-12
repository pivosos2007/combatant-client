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
    private int textCommands;
    private int itemCommands;
    private int effectCommands;
    private int compiledBatches;
    private int backendCommands;

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        recordedCommands = shapeCommands = pathCommands = textureCommands = textCommands = itemCommands = effectCommands = 0;
        compiledBatches = backendCommands = 0;
    }

    public void record(UiCommand command) {
        if (command == null) return;
        recordedCommands++;
        switch (command.kind()) {
            case SHAPE -> shapeCommands++;
            case PATH -> pathCommands++;
            case TEXTURE -> textureCommands++;
            case TEXT -> textCommands++;
            case ITEM -> itemCommands++;
            case BLUR_REGION, LIQUID_GLASS_REGION, EFFECT_REGION -> effectCommands++;
            case PRIMITIVE -> backendCommands++;
            default -> {
            }
        }
    }

    public void addCompiledBatches(int count) {
        compiledBatches += Math.max(0, count);
    }

    public void addBackendCommand() {
        backendCommands++;
    }

    public UiStatsSnapshot snapshot() {
        return new UiStatsSnapshot(frameId, recordedCommands, shapeCommands, pathCommands, textureCommands,
                textCommands, itemCommands, effectCommands, compiledBatches, backendCommands);
    }
}
