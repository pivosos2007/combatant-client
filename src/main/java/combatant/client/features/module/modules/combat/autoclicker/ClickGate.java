/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoclicker;

/**
 * Decides what a scheduled AutoClicker click does. Kept apart from Minecraft types so every rule
 * (never touch blocks, honor the weapon and cooldown options, spare friends) has a unit test.
 */
public final class ClickGate {

    public enum Verdict {
        /** Do nothing this click. */
        SKIP,
        /** Swing at the air. */
        SWING,
        /** Attack the entity under the crosshair. */
        ATTACK
    }

    /** Attack-strength fraction at which a swing counts as charged. */
    public static final float CHARGED = 0.9f;

    private ClickGate() {
    }

    public static Verdict decide(boolean holdToClick, boolean buttonHeld,
                                 boolean lookingAtBlock, boolean lookingAtEntity, boolean lookingAtFriend,
                                 boolean entitiesOnly,
                                 boolean weaponOnly, boolean holdingWeapon,
                                 boolean respectCooldown, float attackStrength) {
        if (holdToClick && !buttonHeld) return Verdict.SKIP;
        // Breaking a block is vanilla's job; clicking here would only interrupt it.
        if (lookingAtBlock) return Verdict.SKIP;
        if (weaponOnly && !holdingWeapon) return Verdict.SKIP;
        if (respectCooldown && attackStrength < CHARGED) return Verdict.SKIP;

        if (lookingAtEntity) return lookingAtFriend ? Verdict.SKIP : Verdict.ATTACK;
        return entitiesOnly ? Verdict.SKIP : Verdict.SWING;
    }
}
