/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode;

import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.util.resources.asset.ScriptAsset;
import combatant.client.util.resources.asset.ScriptCatalog;
import net.minecraft.resources.Identifier;

@ScriptCatalog(namespace = "minecraft", root = "holdmyitems")
public enum HmiScriptKind {
    @ScriptAsset(value = "hand_pose.js", addon = "hand_addon.js")
    HAND_POSE("context"),
    @ScriptAsset(value = "hand_relative_pose.js", addon = "hand_relative_addon.js")
    HAND_RELATIVE_POSE("context"),
    @ScriptAsset(value = "item_pose.js", addon = "item_addon.js")
    ITEM_POSE("context"),
    @ScriptAsset(value = "item_model.js", addon = "item_model_addon.js")
    ITEM_MODEL("data");

    private final String argumentName;

    HmiScriptKind(String argumentName) {
        this.argumentName = argumentName;
    }

    public Identifier resourceId() {
        return AssetAutoLoader.scriptAsset(this).resource();
    }

    public Identifier addonResourceId() {
        return AssetAutoLoader.scriptAsset(this).addonResource();
    }

    public String argumentName() {
        return argumentName;
    }
}
