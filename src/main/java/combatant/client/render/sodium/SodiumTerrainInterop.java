/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

/**
 * Central terrain interop state for Sodium mixins and Combatant render code.
 */
public final class SodiumTerrainInterop {
    private final SodiumRenderBridge bridge;

    private long terrainUpdatesScheduled;
    private long rebuildsScheduled;
    private long interopErrors;

    SodiumTerrainInterop(SodiumRenderBridge bridge) {
        this.bridge = bridge;
    }

    public void beginFrame() {
        terrainUpdatesScheduled = 0L;
        rebuildsScheduled = 0L;
        interopErrors = 0L;
    }

    public void scheduleTerrainUpdate() {
        bridge.scheduleTerrainUpdate();
    }

    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        bridge.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, important);
    }

    public void recordTerrainUpdateScheduled() {
        terrainUpdatesScheduled++;
    }

    public void recordRebuildScheduled() {
        rebuildsScheduled++;
    }

    public void recordInteropError() {
        interopErrors++;
    }

    public SodiumTerrainInteropStatsSnapshot statsSnapshot() {
        return new SodiumTerrainInteropStatsSnapshot(
                terrainUpdatesScheduled,
                rebuildsScheduled,
                interopErrors
        );
    }
}
