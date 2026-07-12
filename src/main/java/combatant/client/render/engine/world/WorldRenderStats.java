/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

/**
 * Lightweight Renderer3D/World command stats. RHI still owns GPU upload/draw counters.
 */
public final class WorldRenderStats {
    private long frameId;
    private long recordedCommands;
    private long submittedCommands;
    private long skippedEmptyCommands;
    private long submittedVertices;
    private long submittedIndices;
    private long fogBindings;
    private long depthPrePassBindings;
    private long depthMainBindings;
    private long depthDisabledBindings;

    public void beginFrame(long frameId) {
        this.frameId = frameId;
        recordedCommands = 0L;
        submittedCommands = 0L;
        skippedEmptyCommands = 0L;
        submittedVertices = 0L;
        submittedIndices = 0L;
        fogBindings = 0L;
        depthPrePassBindings = 0L;
        depthMainBindings = 0L;
        depthDisabledBindings = 0L;
    }

    public void recordedCommand() {
        recordedCommands++;
    }

    public void submittedCommand(int vertices, int indices) {
        submittedCommands++;
        submittedVertices += Math.max(0, vertices);
        submittedIndices += Math.max(0, indices);
    }

    public void skippedEmptyCommand() {
        skippedEmptyCommands++;
    }

    public void fogBinding() {
        fogBindings++;
    }

    public void depthPrePassBinding() {
        depthPrePassBindings++;
    }

    public void depthMainBinding() {
        depthMainBindings++;
    }

    public void depthDisabledBinding() {
        depthDisabledBindings++;
    }

    public WorldRenderStatsSnapshot snapshot() {
        return new WorldRenderStatsSnapshot(frameId, recordedCommands, submittedCommands, skippedEmptyCommands,
                submittedVertices, submittedIndices, fogBindings, depthPrePassBindings, depthMainBindings, depthDisabledBindings);
    }
}
