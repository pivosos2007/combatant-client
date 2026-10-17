/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.minecraft;

import combatant.client.input.event.TextCompositionEvent;

public interface CombatantTextInputTarget {
    boolean insertText(String text);

    default void updateComposition(TextCompositionEvent event) {
    }
}
