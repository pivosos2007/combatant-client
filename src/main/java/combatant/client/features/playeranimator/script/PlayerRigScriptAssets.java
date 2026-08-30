/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import combatant.client.util.resources.asset.ScriptAsset;
import combatant.client.util.resources.asset.ScriptCatalog;

/** Declarative player-animation script stack discovered by {@code AssetAutoLoader}. */
@ScriptCatalog(namespace = "combatant", root = "playeranimator")
public enum PlayerRigScriptAssets {
    @ScriptAsset(value = "player_animation_library.js", order = 0)
    LIBRARY,

    @ScriptAsset(value = "animations", tree = true, order = 100)
    ANIMATIONS,

    @ScriptAsset(value = "rig", tree = true, order = 200)
    RIG,

    @ScriptAsset(value = "player_rig.js", addon = "player_rig_addon.js", order = 300)
    ENTRY
}
