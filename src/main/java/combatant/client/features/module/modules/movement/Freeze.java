/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputScope;
import combatant.client.util.anticheat.AntiCheatPreset;

/**
 * Freezes the local player in place and stops the position packets (AirStuck). With a hold key set it only freezes
 * while that key is down; without one it freezes for as long as the module is on. The Grim preset turns it off: a
 * client that stops sending movement is a Timer/Simulation flag there.
 */
@ModuleInfo(
        id = "freeze",
        displayName = "Freeze",
        aliases = {"AirStuck"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.freeze.description")
public final class Freeze extends Module {

    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final KeyBindValue holdKey = bind("hold_key", "NONE", BindMode.HOLD);

    private String boundCombo = "NONE";

    public Freeze() {
        setDefaultBind("COMMA");
    }

    /** True while the player and its movement packets must be held still. */
    public static boolean isActive() {
        Freeze freeze = Modules.get(Freeze.class);
        return freeze != null && freeze.frozenNow();
    }

    private boolean frozenNow() {
        if (!isEnabled() || anticheat.get() == AntiCheatPreset.GRIM) return false;
        return holdKey.isNone() || isHoldKeyDown();
    }

    @Override
    public void onEnable() {
        ensureBind();
    }

    @Override
    public void onDisable() {
        CombatantInput.bindings().unbind(bindingName());
        boundCombo = "NONE";
    }

    @Override
    public void onTick() {
        ensureBind();
    }

    private String bindingName() {
        return name() + ":" + holdKey.getName();
    }

    private void ensureBind() {
        String combo = holdKey.get();
        if (combo == null) combo = "NONE";
        if (combo.equalsIgnoreCase(boundCombo)) return;
        CombatantInput.bindings().unbind(bindingName());
        if (!holdKey.isNone()) {
            CombatantInput.bindings().bind(bindingName(), combo);
        }
        boundCombo = combo;
    }

    private boolean isHoldKeyDown() {
        return CombatantInput.bindings().isHeld(bindingName(), InputScope.GAME);
    }
}
