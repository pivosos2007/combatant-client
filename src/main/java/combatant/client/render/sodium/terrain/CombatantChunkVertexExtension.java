/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium.terrain;

public interface CombatantChunkVertexExtension {
    void combatant$setSurfaceFlags(int surfaceFlags);

    int combatant$getSurfaceFlags();

    void combatant$copyData(CombatantChunkVertexExtension dest);
}
