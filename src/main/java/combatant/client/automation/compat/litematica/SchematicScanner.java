/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Finds the cells around a point where the world differs from the schematic, without rescanning the schematic
 * every time. The expected non-air blocks of each 16-cube section are cached (keyed by the source's
 * {@link SchematicSource#revision()}); a scan then only compares cached cells with the world, nearest sections
 * first, under a per-call budget. Sections whose schematic column is not materialised yet are not cached, and
 * sections found empty are looked at again after {@link #EMPTY_RECHECK_TICKS}.
 */
public final class SchematicScanner {

    public static final int EMPTY_RECHECK_TICKS = 40;
    /** Sections whose content is built per scan call (4096 schematic lookups each). */
    public static final int BUILDS_PER_SCAN = 2;
    private static final int MAX_CACHED = 768;

    public enum Verdict { OK, MISSING, WRONG }

    /** Compares the world at a position with the expected state; null when the world is not loaded there. */
    @FunctionalInterface
    public interface Judge {
        Verdict judge(BlockState expected, int x, int y, int z);
    }

    public record Cell(BlockPos pos, BlockState expected) {
    }

    /**
     * @param missing  cells to place, nearest section first
     * @param wrong    cells occupied by something else (never touched)
     * @param examined cells compared
     * @param complete false when the budget ran out or a section was not ready, so more may exist
     */
    public record Result(List<Cell> missing, int wrong, int examined, boolean complete) {
    }

    private static final class Section {
        final long revision;
        final long builtTick;
        final int[] x;
        final int[] y;
        final int[] z;
        final BlockState[] states;
        long lastUsed;

        Section(long revision, long builtTick, int[] x, int[] y, int[] z, BlockState[] states) {
            this.revision = revision;
            this.builtTick = builtTick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.states = states;
        }
    }

    private final SchematicSource source;
    private final Map<Long, Section> cache = new HashMap<>();

    public SchematicScanner(SchematicSource source) {
        this.source = source;
    }

    public void clear() {
        cache.clear();
    }

    public int cachedSections() {
        return cache.size();
    }

    public Result scan(Judge judge, double px, double py, double pz, double radius,
                       int checkBudget, int maxResults, long tick) {
        List<Cell> missing = new ArrayList<>();
        if (!source.isReady()) return new Result(missing, 0, 0, true);
        long revision = source.revision();
        int minSx = SectionPos.blockToSectionCoord((int) Math.floor(px - radius));
        int maxSx = SectionPos.blockToSectionCoord((int) Math.floor(px + radius));
        int minSy = SectionPos.blockToSectionCoord((int) Math.floor(py - radius));
        int maxSy = SectionPos.blockToSectionCoord((int) Math.floor(py + radius));
        int minSz = SectionPos.blockToSectionCoord((int) Math.floor(pz - radius));
        int maxSz = SectionPos.blockToSectionCoord((int) Math.floor(pz + radius));

        List<long[]> order = new ArrayList<>();
        for (int sx = minSx; sx <= maxSx; sx++) {
            for (int sy = minSy; sy <= maxSy; sy++) {
                for (int sz = minSz; sz <= maxSz; sz++) {
                    double dx = distanceToSpan(px, sx << 4);
                    double dy = distanceToSpan(py, sy << 4);
                    double dz = distanceToSpan(pz, sz << 4);
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d > radius * radius) continue;
                    order.add(new long[]{(long) (d * 16.0), sx, sy, sz});
                }
            }
        }
        order.sort((a, b) -> Long.compare(a[0], b[0]));

        double radiusSq = radius * radius;
        int builds = 0;
        int examined = 0;
        int wrong = 0;
        boolean complete = true;
        for (long[] entry : order) {
            int sx = (int) entry[1];
            int sy = (int) entry[2];
            int sz = (int) entry[3];
            long key = SectionPos.asLong(sx, sy, sz);
            Section section = cache.get(key);
            boolean stale = section == null || section.revision != revision
                    || section.states.length == 0 && tick - section.builtTick >= EMPTY_RECHECK_TICKS;
            if (stale) {
                if (builds >= BUILDS_PER_SCAN) {
                    complete = false;
                    continue;
                }
                builds++;
                section = build(sx, sy, sz, revision, tick);
                if (section == null) {
                    cache.remove(key);
                    complete &= !pending(sx, sy, sz);
                    continue;
                }
                cache.put(key, section);
            }
            section.lastUsed = tick;
            for (int i = 0; i < section.states.length; i++) {
                double dx = section.x[i] + 0.5 - px;
                double dy = section.y[i] + 0.5 - py;
                double dz = section.z[i] + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                if (examined >= checkBudget || missing.size() >= maxResults) return finish(missing, wrong, examined, false);
                examined++;
                Verdict verdict = judge.judge(section.states[i], section.x[i], section.y[i], section.z[i]);
                if (verdict == Verdict.MISSING) {
                    missing.add(new Cell(new BlockPos(section.x[i], section.y[i], section.z[i]), section.states[i]));
                } else if (verdict == Verdict.WRONG) {
                    wrong++;
                }
            }
        }
        if (cache.size() > MAX_CACHED) evict(tick);
        return new Result(missing, wrong, examined, complete);
    }

    private Result finish(List<Cell> missing, int wrong, int examined, boolean complete) {
        return new Result(missing, wrong, examined, complete);
    }

    /** A section inside a placement whose column is not ready: worth retrying. */
    private boolean pending(int sx, int sy, int sz) {
        SchematicBox box = new SchematicBox(sx << 4, sy << 4, sz << 4, (sx << 4) + 15, (sy << 4) + 15, (sz << 4) + 15);
        return source.overlapsPlacement(box);
    }

    private Section build(int sx, int sy, int sz, long revision, long tick) {
        SchematicBox box = new SchematicBox(sx << 4, sy << 4, sz << 4, (sx << 4) + 15, (sy << 4) + 15, (sz << 4) + 15);
        if (!source.overlapsPlacement(box)) {
            return new Section(revision, Long.MAX_VALUE / 2, new int[0], new int[0], new int[0], new BlockState[0]);
        }
        if (!source.isColumnReady(sx, sz)) return null;
        List<int[]> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        source.forEachExpected(box, (x, y, z, state) -> {
            positions.add(new int[]{x, y, z});
            states.add(state);
        });
        int n = states.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            int[] p = positions.get(i);
            xs[i] = p[0];
            ys[i] = p[1];
            zs[i] = p[2];
        }
        return new Section(revision, tick, xs, ys, zs, states.toArray(new BlockState[0]));
    }

    private void evict(long tick) {
        Iterator<Map.Entry<Long, Section>> it = cache.entrySet().iterator();
        while (it.hasNext() && cache.size() > MAX_CACHED / 2) {
            if (it.next().getValue().lastUsed < tick) it.remove();
        }
    }

    private static double distanceToSpan(double v, int spanMin) {
        if (v < spanMin) return spanMin - v;
        if (v > spanMin + 16) return v - (spanMin + 16);
        return 0.0;
    }
}
