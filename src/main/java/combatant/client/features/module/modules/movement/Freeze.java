/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.KeyBindValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerFreezeQueryEvent;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;

/**
 * Freezes the local player in place and stops the position packets (AirStuck). With a hold key set it only freezes
 * while that key is down; without one it freezes for as long as the module is on.
 */
@ModuleInfo(
        id = "freeze",
        displayName = "Freeze",
        aliases = {"AirStuck"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.freeze.description")
public final class Freeze extends Module {

    private final FunctionBindSetting holdKey = action("hold_key", new KeyBindValue("hold_key", "NONE"), BindMode.HOLD);

    public Freeze() {
        setDefaultBind("COMMA");
    }

    @EventHandler
    private void onFreezeQuery(PlayerFreezeQueryEvent event) {
        if (event.getPlayer() != null && frozenNow()) event.setFrozen(true);
    }

    private boolean frozenNow() {
        if (!isEnabled()) return false;
        return "NONE".equalsIgnoreCase(holdKey.get()) || isActionHeld("hold_key");
    }

}
