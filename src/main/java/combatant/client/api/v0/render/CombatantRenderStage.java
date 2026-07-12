/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.render;

public enum CombatantRenderStage {
    HUD_RAW,
    HUD_SCALED,
    HUD_LOGICAL,
    HUD_RAW_FOREGROUND,
    HUD_SCALED_FOREGROUND,
    HUD_LOGICAL_FOREGROUND,
    WORLD_BEFORE_TRANSLUCENT,
    WORLD_END_MAIN,
    WORLD_POST_PROCESS,
    SCREEN_BEFORE_VANILLA_GUI,
    SCREEN_AFTER_VANILLA_GUI
}
