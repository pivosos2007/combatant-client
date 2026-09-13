/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

/** Non-cancelling observer for Sodium terrain submissions. */
public interface SodiumTerrainObserver {
    default void beforeTerrainDraw(SodiumTerrainSubmission submission) {
    }

    default void afterTerrainDraw(SodiumTerrainSubmission submission) {
    }
}
