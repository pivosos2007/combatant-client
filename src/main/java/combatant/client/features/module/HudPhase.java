/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

public enum HudPhase {
    /**
     * The module/element is not rendered by the HUD phase dispatcher.
     */
    NONE,

    /**
     * First Fabric HUD entry. Use only for visuals that must sit behind vanilla HUD.
     */
    FIRST,

    /**
     * Last Fabric HUD entry. This is still vanilla HUD space, not the screen top layer.
     */
    LAST,

    /**
     * Before vanilla MISC_OVERLAYS: vignette, spyglass, portal and powder-snow style overlays.
     */
    BEFORE_MISC_OVERLAYS,

    /**
     * After vanilla MISC_OVERLAYS, before crosshair/hotbar strata.
     */
    AFTER_MISC_OVERLAYS,

    /**
     * Legacy name. In 26.2 this phase is anchored after vanilla SLEEP, so widgets here
     * are above sleep fade and earlier misc overlays but still below demo/debug/chat/title.
     */
    AFTER_BOSS_BAR,

    /**
     * Before vanilla DEMO_TIMER.
     */
    BEFORE_DEMO_TIMER,

    /**
     * Before vanilla CHAT. Scoreboard, overlay message and title are already below this.
     */
    BEFORE_CHAT,

    /**
     * After vanilla SUBTITLES. Still vanilla HUD space, below explicit SCREEN_TOP overlays.
     */
    AFTER_SUBTITLES
}
