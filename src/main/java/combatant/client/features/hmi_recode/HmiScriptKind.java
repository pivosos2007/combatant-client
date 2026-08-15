/*
 * HoldMyItems compatibility subsystem.
 * Ported for Combatant from Hold My Items by sapling (CC0-1.0).
 */
package combatant.client.features.hmi_recode;

import net.minecraft.resources.Identifier;

public enum HmiScriptKind {
    HAND_POSE("hand_pose.js", "hand_addon.js", "context"),
    HAND_RELATIVE_POSE("hand_relative_pose.js", "hand_relative_addon.js", "context"),
    ITEM_POSE("item_pose.js", "item_addon.js", "context"),
    ITEM_MODEL("item_model.js", "item_model_addon.js", "data");

    private final String fileName;
    private final String addonFileName;
    private final String argumentName;

    HmiScriptKind(String fileName, String addonFileName, String argumentName) {
        this.fileName = fileName;
        this.addonFileName = addonFileName;
        this.argumentName = argumentName;
    }

    public Identifier resourceId() {
        return Identifier.fromNamespaceAndPath("minecraft", "holdmyitems/" + fileName);
    }

    public Identifier addonResourceId() {
        return Identifier.fromNamespaceAndPath("minecraft", "holdmyitems/" + addonFileName);
    }

    public String argumentName() {
        return argumentName;
    }
}
