/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.printer;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Pure ordering and batching of missing cells; no world access. */
public final class PrinterTargets {

    private PrinterTargets() {
    }

    /**
     * Build order: lowest layer first (upper blocks need what is below them), then nearest to the player, ties by
     * position so the order is stable between scans.
     */
    public static <T> void order(List<T> cells, Function<T, BlockPos> pos, double px, double py, double pz) {
        cells.sort(Comparator
                .<T>comparingInt(c -> pos.apply(c).getY())
                .thenComparingDouble(c -> distanceSq(pos.apply(c), px, py, pz))
                .thenComparingLong(c -> pos.apply(c).asLong()));
    }

    /**
     * The first cell plus up to {@code max - 1} following ones that need the same item and the same click mode:
     * one batch is placed with one hotbar slot and one constraint set.
     */
    public static <T, K> List<T> batch(List<T> ordered, Function<T, K> key, int max) {
        List<T> out = new ArrayList<>();
        if (ordered.isEmpty()) return out;
        K first = key.apply(ordered.get(0));
        for (T cell : ordered) {
            if (out.size() >= Math.max(1, max)) break;
            if (key.apply(cell).equals(first)) out.add(cell);
        }
        return out;
    }

    public static double distanceSq(BlockPos pos, double px, double py, double pz) {
        double dx = pos.getX() + 0.5 - px;
        double dy = pos.getY() + 0.5 - py;
        double dz = pos.getZ() + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
