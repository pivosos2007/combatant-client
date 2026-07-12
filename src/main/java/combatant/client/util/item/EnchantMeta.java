/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

public record EnchantMeta(
        String key,      // path: "lunge", "sharpness"
        String label,    // "Рыв", "Ос"
        String category  // "armor", "melee", "other"
) {
}
