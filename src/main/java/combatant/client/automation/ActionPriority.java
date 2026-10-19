/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Named priority tiers for automation tasks. Higher wins. Values share the scale modules already
 * pass to {@code RotationManager.setRotationTarget}, so a task's rotation competes fairly with
 * modules that are not migrated yet (KillAura 10, AutoCrystal 35, AutoPotion 50, NoFall 100).
 * Modules may add small offsets within a tier to order their own tasks.
 */
public final class ActionPriority {
    /** Idle chores that should never take control from anything else. */
    public static final int BACKGROUND = 5;
    /** Building, printing, surround/trap placement. */
    public static final int BUILD = 10;
    /** Player-requested helpers: tool selection, hole snapping, walking. */
    public static final int UTILITY = 20;
    /** Offensive combat automation. */
    public static final int COMBAT = 35;
    /** Self-sustain: healing, mending, potions. */
    public static final int SUPPORT = 50;
    /** Evasion that must override combat. */
    public static final int EVASION = 80;
    /** Life-saving actions (MLG, emergency totem). */
    public static final int SAFETY = 100;

    private ActionPriority() {
    }
}
