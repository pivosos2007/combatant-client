/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

public enum FogPolicy {
    NONE,
    VANILLA,
    COMBATANT,
    SHADERPACK;

    public boolean requiresWorldFogUniform() {
        return this == VANILLA || this == COMBATANT || this == SHADERPACK;
    }
}
