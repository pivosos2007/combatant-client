/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

/**
 * Captured Sodium world-renderer state for the current Combatant frame.
 */
public record SodiumFrameContext(boolean available,
                                 boolean terrainRenderComplete,
                                 int visibleChunkCount,
                                 String debugString) {
    public static final SodiumFrameContext UNAVAILABLE = new SodiumFrameContext(false, false, 0, "unavailable");

    public static SodiumFrameContext available(boolean terrainRenderComplete, int visibleChunkCount, String debugString) {
        return new SodiumFrameContext(true, terrainRenderComplete, Math.max(0, visibleChunkCount), debugString != null ? debugString : "available");
    }
}
