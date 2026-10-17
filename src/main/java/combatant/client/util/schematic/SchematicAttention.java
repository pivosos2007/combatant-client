/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * States the builder cannot create exactly, with the reason: shown in the menu instead of being skipped
 * silently. Static entries come from the schematic alone (fluids, water, iron doors opened by hand); the
 * builder adds positional ones as it meets them (no reachable look for a piston, a block that would fire).
 */
public final class SchematicAttention {

    public enum Reason {
        /** A fluid or bubble column: needs a bucket, the builder leaves the cell. */
        FLUID,
        /** Waterlogged in the schematic: comes out dry unless the cell already holds water. */
        WATERLOGGED,
        /** Opened by hand in the schematic, but only power opens it (iron door or trapdoor). */
        NOT_BY_HAND,
        /** No reachable click produces the state (a piston that must face down from below); placed approximately. */
        APPROXIMATE,
        /** Placed in another state that clicking does not fix. */
        WRONG_STATE,
        /** Placing it now would fire, move or light it; waited, then placed anyway. */
        WOULD_TRIGGER,
        /** TNT that would be powered the moment it lands: not placed. */
        TNT_POWERED,
        /** Clicks on the placed block did not reach the wanted delay, mode, note or position. */
        ADJUST_FAILED,
        /** Still missing when the builder gave up waiting and placed power anyway. */
        BEFORE_POWER
    }

    /** {@code pos} is null for a static entry; {@code count} is the number of cells it stands for. */
    public record Entry(BlockPos pos, BlockState state, Reason reason, int count) {
    }

    private SchematicAttention() {
    }

    /** What the schematic asks for that no builder click can make, one entry per palette state. */
    public static List<Entry> staticIssues(Schematic schematic, int[] paletteHistogram) {
        Map<BlockState, Entry> out = new IdentityHashMap<>();
        for (int i = 1; i < paletteHistogram.length && i < schematic.paletteSize(); i++) {
            if (paletteHistogram[i] == 0) continue;
            BlockState state = schematic.paletteState(i);
            Reason reason = staticReason(state);
            if (reason != null) out.put(state, new Entry(null, state, reason, paletteHistogram[i]));
        }
        List<Entry> list = new ArrayList<>(out.values());
        list.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return list;
    }

    static Reason staticReason(BlockState state) {
        if (StateComparison.derived(state)) {
            return state.getFluidState().isEmpty() ? null : Reason.FLUID;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return Reason.WATERLOGGED;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
                && !StateComparison.ignored(state, BlockStateProperties.OPEN)
                && StateAdjustments.adjustableProperty(state) == null) {
            return Reason.NOT_BY_HAND;
        }
        return null;
    }
}
