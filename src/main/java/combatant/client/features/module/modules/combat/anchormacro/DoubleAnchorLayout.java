/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.Predicate;

/** Where the second anchor of a double anchor goes relative to the first. */
public final class DoubleAnchorLayout {
    private static final Direction[] HORIZONTAL_FIRST = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final Direction[] ABOVE_FIRST = {
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};

    private DoubleAnchorLayout() {
    }

    public enum Layout {
        /** The first usable neighbour on top, then the sides. */
        STACKED,
        /** The first usable side neighbour, then above and below. */
        SIDE
    }

    /** The first neighbour of {@code first} accepted by {@code usable}; null when none is. */
    public static BlockPos chooseSecond(BlockPos first, Layout layout, Predicate<BlockPos> usable) {
        Direction[] order = layout == Layout.STACKED ? ABOVE_FIRST : HORIZONTAL_FIRST;
        for (Direction direction : order) {
            BlockPos candidate = first.relative(direction);
            if (usable.test(candidate)) return candidate;
        }
        return null;
    }
}
