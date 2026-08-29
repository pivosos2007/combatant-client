/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

/**
 * Explicit render phase used by the Combatant frame graph.
 */
public enum RenderPhase {
    NONE,
    WORLD_BEFORE_ENTITIES,
    WORLD_AFTER_ENTITIES,
    WORLD_BEFORE_TRANSLUCENT,
    WORLD_AFTER_TRANSLUCENT,
    WORLD_POST_PRE_HAND,
    WORLD_POST_HAND,
    HUD_CAPTURE,
    HUD_MAIN,
    HUD_EFFECTS,
    /**
     * Dedicated topmost in-game screen layer. Used for Combatant ClickGUI so the whole
     * vanilla InGameHud is below it even on Minecraft versions where Screen rendering
     * and HUD rendering interleave differently.
     */
    SCREEN_TOP,
    SCREEN,
    LEGACY_SHADOW_TERRAIN
}
