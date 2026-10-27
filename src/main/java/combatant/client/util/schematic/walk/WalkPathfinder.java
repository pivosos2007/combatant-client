/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * Walking routes over a {@link WalkTerrain}, in feet cells, using only moves a player makes with the movement
 * keys: a step to a neighbour on the same level, a step onto a half block, a jump up one block, a drop of up to
 * {@code maxDrop} blocks, a running jump over a one-block gap, climbing a ladder, and a diagonal step when the two
 * cells whose corners the 0.6-wide body sweeps are clear and not a pit.
 *
 * <p>The search is A* with an octile-plus-height heuristic that never overestimates (every move pays at least its
 * horizontal distance, a level up at least {@link #STEP_UP_COST}, a level down at least {@link #DROP_COST}), so the
 * first route to reach the goal is the cheapest. Ties go to the node nearer the goal, then to the older node, so
 * the same world always yields the same route. Nodes live in flat arrays indexed through an open-addressing table:
 * nothing is boxed per node, and a {@link Search} can run a few hundred expansions per tick under a time budget
 * and pick up where it stopped on the next one.</p>
 */
public final class WalkPathfinder {

    static final double STEP_COST = 1.0;
    static final double DIAGONAL_COST = Math.sqrt(2.0);
    static final double JUMP_COST = 1.0;
    /** Walking up onto a half block needs no jump, only a slightly slower step. */
    static final double STEP_UP_COST = 0.2;
    static final double DROP_COST = 0.5;
    static final double CLIMB_UP_COST = 1.3;
    static final double CLIMB_DOWN_COST = 1.0;
    /** Two cells of distance plus the jump, and a little more because a short take-off can miss. */
    static final double GAP_JUMP_COST = 2.0 * STEP_COST + JUMP_COST + 0.5;
    /** Walking next to lava or fire one level down is legal but one overshoot away from a burn; keep some distance. */
    static final double DANGER_NEIGHBOUR_COST = 2.0;
    /** Walking along the rim of a pit deeper than a safe drop. */
    static final double PIT_EDGE_COST = 0.3;
    static final double WATER_COST = 2.0;
    static final double SUBMERGED_COST = 4.0;
    static final double WEB_COST = 8.0;
    static final double SLOW_COST = 1.0;
    static final double ICE_COST = 0.3;
    static final double CLIMB_COST = 0.3;
    /** Standing in a cell the build is about to fill gets in the way of the next clicks. */
    public static final double RESERVED_COST = 3.0;
    /** A rise the player walks up without jumping (vanilla step height). */
    static final double STEP_HEIGHT = 0.6;
    /** The highest rise a standing jump clears (apex of a vanilla jump is about 1.25). */
    static final double JUMP_HEIGHT = 1.25;
    /** Falls up to three blocks never hurt. */
    public static final int MAX_SAFE_DROP = 3;

    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private WalkPathfinder() {
    }

    @FunctionalInterface
    interface MoveSink {
        void accept(int x, int y, int z, double cost);
    }

    /** Every move out of the feet cell {@code (x, y, z)}, with its base cost (cell costs come on top). */
    static void forEachMove(WalkTerrain terrain, int x, int y, int z, int maxDrop, MoveSink sink) {
        int drop = Math.min(Math.max(maxDrop, 0), MAX_SAFE_DROP);
        double here = terrain.floorOffset(x, y, z);
        byte feet = terrain.cell(x, y, z);
        // A jump needs room above the head at the take-off as well as at the landing.
        boolean headRoom = terrain.passable(x, y + 2, z);
        for (int d = 0; d < 4; d++) {
            straightMoves(terrain, x, y, z, DX[d], DZ[d], here, headRoom || feet == WalkTerrain.CLIMB, drop, sink);
        }
        if (feet == WalkTerrain.CLIMB && terrain.cell(x, y + 1, z) == WalkTerrain.CLIMB && terrain.passable(x, y + 2, z)) {
            sink.accept(x, y + 1, z, CLIMB_UP_COST);
        }
        if (terrain.cell(x, y - 1, z) == WalkTerrain.CLIMB) sink.accept(x, y - 1, z, CLIMB_DOWN_COST);
        for (int d = 0; d < 4; d++) {
            int ax = DX[d], az = DZ[d];
            int bx = DX[(d + 1) & 3], bz = DZ[(d + 1) & 3];
            int nx = x + ax + bx;
            int nz = z + az + bz;
            if (!terrain.standable(nx, y, nz) || Math.abs(terrain.floorOffset(nx, y, nz) - here) > STEP_HEIGHT) continue;
            // The body sweeps a corner of both side cells: they must be free, and not a hole to slip into.
            if (cornerClear(terrain, x + ax, y, z + az, drop) && cornerClear(terrain, x + bx, y, z + bz, drop)) {
                sink.accept(nx, y, nz, DIAGONAL_COST);
            }
        }
    }

    private static void straightMoves(WalkTerrain terrain, int x, int y, int z, int dx, int dz, double here,
                                      boolean canJump, int maxDrop, MoveSink sink) {
        int nx = x + dx, nz = z + dz;
        if (terrain.standable(nx, y, nz)) {
            double rise = terrain.floorOffset(nx, y, nz) - here;
            if (rise <= STEP_HEIGHT) sink.accept(nx, y, nz, STEP_COST);
            else if (rise <= JUMP_HEIGHT && canJump) sink.accept(nx, y, nz, STEP_COST + JUMP_COST);
            return;
        }
        if (terrain.standable(nx, y + 1, nz)) {
            double rise = 1.0 + terrain.floorOffset(nx, y + 1, nz) - here;
            if (rise <= STEP_HEIGHT) sink.accept(nx, y + 1, nz, STEP_COST + STEP_UP_COST);
            else if (rise <= JUMP_HEIGHT && canJump) sink.accept(nx, y + 1, nz, STEP_COST + JUMP_COST);
            return;
        }
        if (!terrain.passable(nx, y, nz) || !terrain.passable(nx, y + 1, nz)) return;
        for (int k = 1; k <= maxDrop; k++) {
            if (terrain.standable(nx, y - k, nz)) {
                sink.accept(nx, y - k, nz, STEP_COST + DROP_COST * k);
                return;
            }
            if (!terrain.passable(nx, y - k, nz)) break;
        }
        // Nothing to land on within a safe drop: a running jump over a one-block gap, never over a hazard.
        int fx = nx + dx, fz = nz + dz;
        if (canJump && terrain.passable(nx, y + 2, nz) && terrain.standable(fx, y, fz) && terrain.passable(fx, y + 2, fz)
                && Math.abs(terrain.floorOffset(fx, y, fz) - here) <= STEP_HEIGHT && !hazardBelow(terrain, nx, y - 1, nz)) {
            sink.accept(fx, y, fz, GAP_JUMP_COST);
        }
    }

    /** A side cell a diagonal step clips: body room, and either a floor or a harmless drop below it. */
    private static boolean cornerClear(WalkTerrain terrain, int x, int y, int z, int maxDrop) {
        byte feet = terrain.cell(x, y, z);
        if (!WalkTerrain.passable(feet) || feet == WalkTerrain.WEB || !terrain.passable(x, y + 1, z)) return false;
        if (feet == WalkTerrain.RAISED) return terrain.passable(x, y + 2, z);
        return terrain.standable(x, y, z) || !pit(terrain, x, y - 1, z, maxDrop);
    }

    private static boolean hazardBelow(WalkTerrain terrain, int x, int y, int z) {
        for (int k = 0; k <= MAX_SAFE_DROP + 1; k++) {
            byte cell = terrain.cell(x, y - k, z);
            if (cell == WalkTerrain.DANGER || cell == WalkTerrain.UNKNOWN) return true;
            if (!WalkTerrain.passable(cell)) return false;
        }
        return false;
    }

    /**
     * Column below the floor cell {@code (x, y, z)} offers nothing to land on within {@code maxDrop + 1}: walking
     * into it means a fall that hurts, a hazard or the void.
     */
    static boolean pit(WalkTerrain terrain, int x, int y, int z, int maxDrop) {
        for (int k = 0; k <= maxDrop; k++) {
            byte cell = terrain.cell(x, y - k, z);
            if (WalkTerrain.support(cell) || cell == WalkTerrain.CLIMB || cell == WalkTerrain.WATER) return false;
            if (cell == WalkTerrain.DANGER || cell == WalkTerrain.UNKNOWN) return true;
        }
        return true;
    }

    /**
     * Extra cost of standing in feet cell {@code (x, y, z)} on top of the move into it, or {@code -1} when the cell
     * must never be entered: a hazard touches the body (cactus, fire or lava beside the feet or the head).
     */
    static double cellCost(WalkTerrain terrain, int x, int y, int z, int maxDrop) {
        byte feet = terrain.cell(x, y, z);
        byte head = terrain.cell(x, y + 1, z);
        byte floor = terrain.cell(x, y - 1, z);
        double extra = 0.0;
        if (feet == WalkTerrain.WATER) extra += WATER_COST;
        if (head == WalkTerrain.WATER) extra += SUBMERGED_COST;
        if (feet == WalkTerrain.WEB || head == WalkTerrain.WEB) extra += WEB_COST;
        if (feet == WalkTerrain.CLIMB) extra += CLIMB_COST;
        else if (floor == WalkTerrain.SLOW) extra += SLOW_COST;
        else if (floor == WalkTerrain.ICE) extra += ICE_COST;
        boolean margin = false;
        boolean rim = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean side = dx == 0 || dz == 0;
                byte f = terrain.cell(x + dx, y, z + dz);
                byte h = terrain.cell(x + dx, y + 1, z + dz);
                if (f == WalkTerrain.DANGER || h == WalkTerrain.DANGER) {
                    if (side) return -1.0;
                    margin = true;
                }
                if (terrain.cell(x + dx, y - 1, z + dz) == WalkTerrain.DANGER) margin = true;
                if (side && !rim && WalkTerrain.passable(f) && WalkTerrain.passable(h)
                        && !terrain.standable(x + dx, y, z + dz) && pit(terrain, x + dx, y - 1, z + dz, maxDrop)) {
                    rim = true;
                }
            }
        }
        if (margin) extra += DANGER_NEIGHBOUR_COST;
        if (rim) extra += PIT_EDGE_COST;
        return extra;
    }

    /**
     * Cheapest route from {@code start} to {@code goal}, both feet cells packed with {@link WalkTerrain#pack}.
     *
     * @return the cells from start to goal inclusive, or {@code null} when the goal is not reachable within
     * {@code maxNodes} expansions
     */
    public static List<Long> find(WalkTerrain terrain, long start, long goal, int maxDrop, int maxNodes) {
        return find(terrain, start, goal, maxDrop, maxNodes, null);
    }

    /** As {@link #find(WalkTerrain, long, long, int, int)}, avoiding the {@code reserved} cells where it can. */
    public static List<Long> find(WalkTerrain terrain, long start, long goal, int maxDrop, int maxNodes,
                                  LongPredicate reserved) {
        Search search = new Search(terrain);
        search.startPath(start, goal, maxDrop, maxNodes, reserved);
        search.run();
        long[] path = search.path();
        return path == null ? null : boxed(path);
    }

    /**
     * Every feet cell reachable from {@code start} for at most {@code maxCost}, cheapest first. The result keeps
     * the parents, so the route to any reached cell comes for free.
     */
    public static Flood flood(WalkTerrain terrain, long start, int maxDrop, double maxCost, int maxNodes) {
        return flood(terrain, start, maxDrop, maxCost, maxNodes, null);
    }

    public static Flood flood(WalkTerrain terrain, long start, int maxDrop, double maxCost, int maxNodes,
                              LongPredicate reserved) {
        Search search = new Search(terrain);
        search.startFlood(start, maxDrop, maxCost, maxNodes, reserved);
        search.run();
        return search.flood();
    }

    static List<Long> boxed(long[] path) {
        List<Long> out = new ArrayList<>(path.length);
        for (long cell : path) out.add(cell);
        return out;
    }

    /**
     * One A* search or cost flood, resumable: {@link #step} runs until it finishes or a deadline passes, and the next
     * call carries on with the same open set. The arrays are kept between searches, so a long-lived instance stops
     * allocating once it has grown to the biggest search it ran.
     */
    public static final class Search implements MoveSink {
        public static final int RUNNING = 0;
        public static final int FOUND = 1;
        public static final int FAILED = 2;

        private static final int CLOSED = -2;
        private static final int NEW = -1;
        /** Expansions between two clock reads. */
        private static final int CLOCK_STRIDE = 8;

        private final WalkTerrain terrain;
        private final LongIndex index = new LongIndex();
        private long[] keys = new long[256];
        private double[] g = new double[256];
        private double[] h = new double[256];
        private double[] extra = new double[256];
        private int[] parent = new int[256];
        private int[] heapPos = new int[256];
        private int[] heap = new int[256];
        private int[] order = new int[256];
        private int size;
        private int heapSize;
        private int orderSize;

        private boolean flooding;
        private int maxDrop;
        private int maxNodes;
        private double maxCost;
        private LongPredicate reserved;
        private long goal;
        private int gx;
        private int gy;
        private int gz;
        private int expanded;
        private int found = -1;
        private int closest = -1;
        private int current;
        private int status = FAILED;

        public Search(WalkTerrain terrain) {
            this.terrain = terrain;
        }

        public void startPath(long start, long goal, int maxDrop, int maxNodes, LongPredicate reserved) {
            reset(maxDrop, maxNodes, reserved);
            this.flooding = false;
            this.goal = goal;
            this.gx = WalkTerrain.unpackX(goal);
            this.gy = WalkTerrain.unpackY(goal);
            this.gz = WalkTerrain.unpackZ(goal);
            this.maxCost = Double.MAX_VALUE;
            open(start);
        }

        public void startFlood(long start, int maxDrop, double maxCost, int maxNodes, LongPredicate reserved) {
            reset(maxDrop, maxNodes, reserved);
            this.flooding = true;
            this.maxCost = maxCost;
            open(start);
        }

        private void reset(int maxDrop, int maxNodes, LongPredicate reserved) {
            index.clear();
            size = 0;
            heapSize = 0;
            orderSize = 0;
            expanded = 0;
            found = -1;
            closest = -1;
            this.maxDrop = maxDrop;
            this.maxNodes = maxNodes;
            this.reserved = reserved;
            status = RUNNING;
        }

        private void open(long start) {
            int node = add(start);
            extra[node] = 0.0;
            g[node] = 0.0;
            push(node);
        }

        /** Runs to the end. */
        public int run() {
            return step(Long.MAX_VALUE);
        }

        /** Expands nodes until the search ends or {@code System.nanoTime()} passes {@code deadlineNanos}. */
        public int step(long deadlineNanos) {
            int batch = 0;
            while (status == RUNNING) {
                if (heapSize == 0 || expanded >= maxNodes) {
                    status = flooding ? FOUND : FAILED;
                    break;
                }
                if (++batch == CLOCK_STRIDE) {
                    batch = 0;
                    if (System.nanoTime() - deadlineNanos > 0) break;
                }
                int node = pop();
                heapPos[node] = CLOSED;
                if (!flooding && keys[node] == goal) {
                    found = node;
                    closest = node;
                    status = FOUND;
                    break;
                }
                if (orderSize == order.length) order = Arrays.copyOf(order, orderSize * 2);
                order[orderSize++] = node;
                expanded++;
                if (closest < 0 || h[node] < h[closest] || h[node] == h[closest] && g[node] < g[closest]) closest = node;
                current = node;
                long key = keys[node];
                forEachMove(terrain, WalkTerrain.unpackX(key), WalkTerrain.unpackY(key), WalkTerrain.unpackZ(key),
                        maxDrop, this);
            }
            return status;
        }

        @Override
        public void accept(int x, int y, int z, double cost) {
            long key = WalkTerrain.pack(x, y, z);
            int node = index.get(key);
            if (node < 0) {
                node = add(key);
                double cell = cellCost(terrain, x, y, z, maxDrop);
                if (cell >= 0.0 && reserved != null && (reserved.test(key) || reserved.test(WalkTerrain.pack(x, y + 1, z)))) {
                    cell += RESERVED_COST;
                }
                extra[node] = cell;
            }
            if (heapPos[node] == CLOSED || extra[node] < 0.0) return;
            double next = g[current] + cost + extra[node];
            if (next > maxCost || next >= g[node]) return;
            g[node] = next;
            parent[node] = current;
            if (heapPos[node] == NEW) push(node);
            else up(heapPos[node]);
        }

        private int add(long key) {
            if (size == keys.length) grow();
            int node = size++;
            keys[node] = key;
            g[node] = Double.MAX_VALUE;
            parent[node] = -1;
            heapPos[node] = NEW;
            h[node] = flooding ? 0.0 : heuristic(key);
            index.put(key, node);
            return node;
        }

        private void grow() {
            int n = keys.length * 2;
            keys = Arrays.copyOf(keys, n);
            g = Arrays.copyOf(g, n);
            h = Arrays.copyOf(h, n);
            extra = Arrays.copyOf(extra, n);
            parent = Arrays.copyOf(parent, n);
            heapPos = Arrays.copyOf(heapPos, n);
            heap = Arrays.copyOf(heap, n);
        }

        private double heuristic(long key) {
            double ax = Math.abs(WalkTerrain.unpackX(key) - gx);
            double az = Math.abs(WalkTerrain.unpackZ(key) - gz);
            int dy = WalkTerrain.unpackY(key) - gy;
            // Octile distance never overestimates a route of unit and diagonal steps; each level costs at least
            // the cheapest move that changes it.
            double flat = Math.max(ax, az) + (DIAGONAL_COST - 1.0) * Math.min(ax, az);
            return flat + (dy < 0 ? -dy * STEP_UP_COST : dy * DROP_COST);
        }

        private boolean before(int a, int b) {
            double fa = g[a] + h[a], fb = g[b] + h[b];
            if (fa != fb) return fa < fb;
            if (h[a] != h[b]) return h[a] < h[b];
            return a < b;
        }

        private void push(int node) {
            heap[heapSize] = node;
            heapPos[node] = heapSize;
            up(heapSize++);
        }

        private int pop() {
            int top = heap[0];
            int last = heap[--heapSize];
            if (heapSize > 0) {
                heap[0] = last;
                heapPos[last] = 0;
                down(0);
            }
            return top;
        }

        private void up(int i) {
            int node = heap[i];
            while (i > 0) {
                int p = (i - 1) >> 1;
                int other = heap[p];
                if (!before(node, other)) break;
                heap[i] = other;
                heapPos[other] = i;
                i = p;
            }
            heap[i] = node;
            heapPos[node] = i;
        }

        private void down(int i) {
            int node = heap[i];
            while (true) {
                int c = 2 * i + 1;
                if (c >= heapSize) break;
                if (c + 1 < heapSize && before(heap[c + 1], heap[c])) c++;
                if (!before(heap[c], node)) break;
                heap[i] = heap[c];
                heapPos[heap[i]] = i;
                i = c;
            }
            heap[i] = node;
            heapPos[node] = i;
        }

        public int status() {
            return status;
        }

        public int expanded() {
            return expanded;
        }

        /** The route to the goal, start and goal included, or {@code null} unless {@link #FOUND}. */
        public long[] path() {
            return found < 0 ? null : trace(found);
        }

        /** The route to the expanded node nearest the goal: the best the search has to offer when it gave up. */
        public long[] partialPath() {
            return closest < 0 ? null : trace(closest);
        }

        long[] trace(int node) {
            int length = 0;
            for (int n = node; n >= 0; n = parent[n]) length++;
            long[] out = new long[length];
            for (int n = node, i = length - 1; n >= 0; n = parent[n], i--) out[i] = keys[n];
            return out;
        }

        int nodeOf(long key) {
            return index.get(key);
        }

        double costOf(int node) {
            return g[node];
        }

        /** The flood result viewing this search's arrays; valid until the next start. */
        public Flood flood() {
            return new Flood(this);
        }
    }

    /** Result of {@link #flood}: the reached cells in cost order and the cheapest way to each. */
    public static final class Flood {
        private final Search search;
        private final long[] cells;
        private List<Long> boxed;

        Flood(Search search) {
            this.search = search;
            this.cells = new long[search.orderSize];
            for (int i = 0; i < cells.length; i++) cells[i] = search.keys[search.order[i]];
        }

        /** Number of reached cells. */
        public int size() {
            return cells.length;
        }

        /** The {@code i}-th reached cell, cheapest first. */
        public long cell(int i) {
            return cells[i];
        }

        /** Reached cells, cheapest first, the start included. */
        public List<Long> cells() {
            if (boxed == null) {
                boxed = new AbstractList<>() {
                    @Override
                    public Long get(int i) {
                        return cells[i];
                    }

                    @Override
                    public int size() {
                        return cells.length;
                    }
                };
            }
            return boxed;
        }

        public boolean reached(long cell) {
            int node = search.nodeOf(cell);
            return node >= 0 && search.costOf(node) < Double.MAX_VALUE;
        }

        public double cost(long cell) {
            int node = search.nodeOf(cell);
            return node < 0 ? Double.MAX_VALUE : search.costOf(node);
        }

        public List<Long> pathTo(long cell) {
            long[] path = route(cell);
            return path == null ? null : boxed(path);
        }

        /** The route to {@code cell} as packed cells, or {@code null} when it was not reached. */
        public long[] route(long cell) {
            if (!reached(cell)) return null;
            return search.trace(search.nodeOf(cell));
        }
    }

    /** Open-addressing map from a packed cell to a node index; {@link Long#MIN_VALUE} marks a free slot. */
    static final class LongIndex {
        private static final long FREE = Long.MIN_VALUE;
        private long[] keys = new long[512];
        private int[] values = new int[512];
        private int count;

        LongIndex() {
            Arrays.fill(keys, FREE);
        }

        void clear() {
            if (count == 0) return;
            Arrays.fill(keys, FREE);
            count = 0;
        }

        int get(long key) {
            int mask = keys.length - 1;
            for (int i = slot(key, mask); ; i = (i + 1) & mask) {
                long k = keys[i];
                if (k == key) return values[i];
                if (k == FREE) return -1;
            }
        }

        void put(long key, int value) {
            if ((count + 1) * 2 > keys.length) rehash();
            int mask = keys.length - 1;
            for (int i = slot(key, mask); ; i = (i + 1) & mask) {
                long k = keys[i];
                if (k == FREE || k == key) {
                    if (k == FREE) count++;
                    keys[i] = key;
                    values[i] = value;
                    return;
                }
            }
        }

        private void rehash() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            keys = new long[oldKeys.length * 2];
            values = new int[oldKeys.length * 2];
            Arrays.fill(keys, FREE);
            count = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != FREE) put(oldKeys[i], oldValues[i]);
            }
        }

        private static int slot(long key, int mask) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) (mixed ^ mixed >>> 32) & mask;
        }
    }
}
