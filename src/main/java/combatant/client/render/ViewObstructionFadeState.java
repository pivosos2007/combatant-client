/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render;

public interface ViewObstructionFadeState {
    boolean combatant$isViewObstructionFadeActive();

    float combatant$getViewObstructionFadeAlpha();

    void combatant$setViewObstructionFadeActive(boolean active);

    void combatant$setViewObstructionFadeAlpha(float alpha);

    boolean combatant$isSeeInvisibleFadeActive();

    void combatant$setSeeInvisibleFadeActive(boolean active);
}
