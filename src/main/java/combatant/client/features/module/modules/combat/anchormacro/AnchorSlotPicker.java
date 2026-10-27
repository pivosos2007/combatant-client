/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

/** Pure hotbar slot choice over item categories; {@link AnchorSteps} classifies the real stacks. */
public final class AnchorSlotPicker {
    private AnchorSlotPicker() {
    }

    public enum ItemKind {
        EMPTY,
        ANCHOR,
        GLOWSTONE,
        TOTEM,
        SWORD,
        OTHER
    }

    /** What the hand should hold while the anchor detonates. */
    public enum DetonatorPreference {
        TOTEM,
        SWORD,
        /** Keep whatever non-glowstone item is already selected. */
        CURRENT
    }

    /** The first slot holding {@code wanted}, the selected one winning ties; -1 when there is none. */
    public static int pick(ItemKind[] hotbar, int selected, ItemKind wanted) {
        if (selected >= 0 && selected < hotbar.length && hotbar[selected] == wanted) return selected;
        for (int slot = 0; slot < hotbar.length; slot++) {
            if (hotbar[slot] == wanted) return slot;
        }
        return -1;
    }

    /**
     * The slot to detonate from: any item except glowstone (it would charge again) and anchors (they would be placed
     * if the click missed). The preferred kind wins, then the other of totem/sword, then anything else, an empty hand
     * last; the selected slot wins ties. -1 when no slot qualifies.
     */
    public static int pickDetonator(ItemKind[] hotbar, int selected, DetonatorPreference preference) {
        int best = -1;
        int bestScore = 0;
        for (int slot = 0; slot < hotbar.length; slot++) {
            int score = detonatorScore(hotbar[slot], preference, slot == selected);
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    private static int detonatorScore(ItemKind kind, DetonatorPreference preference, boolean selected) {
        int base = switch (kind) {
            case GLOWSTONE, ANCHOR -> 0;
            case EMPTY -> 1;
            case OTHER -> 2;
            case TOTEM, SWORD -> 3;
        };
        if (base == 0) return 0;
        boolean preferred = switch (preference) {
            case TOTEM -> kind == ItemKind.TOTEM;
            case SWORD -> kind == ItemKind.SWORD;
            case CURRENT -> selected;
        };
        // Scores are scaled so the selected-slot tie-break never outranks a category difference.
        return (preferred ? 100 : 0) + base * 10 + (selected ? 1 : 0);
    }
}
