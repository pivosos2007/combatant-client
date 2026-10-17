/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The A* route must cost exactly what a plain Dijkstra over the same moves and cell costs finds: an overestimating
 * heuristic or a closed node reached again more cheaply would show up as a dearer A* route on some random grid.
 */
class WalkPathfinderOptimalityTest {

    private static final int SIZE = 12;
    private static final int NODES = 200_000;
    private static final int MAX_DROP = 2;
    private static final int GRIDS = 300;

    @Test
    void aStarMatchesDijkstraOnRandomGrids() {
        int compared = 0;
        int unreachable = 0;
        for (int seed = 0; seed < GRIDS; seed++) {
            Random random = new Random(seed);
            GridWorld world = randomWorld(random);
            List<Long> feet = standableCells(world);
            if (feet.size() < 2) continue;
            long start = feet.get(random.nextInt(feet.size()));
            long goal = feet.get(random.nextInt(feet.size()));

            double reference = dijkstra(world, start, goal);
            WalkPathfinder.Search search = new WalkPathfinder.Search(world);
            search.startPath(start, goal, MAX_DROP, NODES, null);
            search.run();
            long[] path = search.path();
            if (reference == Double.MAX_VALUE) {
                assertNull(path, "seed " + seed + ": A* found a route Dijkstra could not");
                unreachable++;
                continue;
            }
            assertNotNull(path, "seed " + seed + ": A* missed a route of cost " + reference);
            assertEquals(start, path[0]);
            assertEquals(goal, path[path.length - 1]);
            assertEquals(reference, search.costOf(search.nodeOf(goal)), 1e-9, "seed " + seed);
            compared++;
        }
        // The generator must actually produce both outcomes, or the test proves little.
        assertTrue(compared > GRIDS / 3, "compared " + compared);
        assertTrue(unreachable > 0, "no unreachable pair in " + GRIDS + " grids");
    }

    /** Terraced ground (0..2 blocks high), slabs, slow and icy floors, water, a few hazards; walled in. */
    private static GridWorld randomWorld(Random random) {
        GridWorld world = new GridWorld();
        // Walls too high to jump keep both searches inside the grid.
        world.fill(-1, -1, -1, SIZE, 6, -1, WalkTerrain.SOLID).fill(-1, -1, SIZE, SIZE, 6, SIZE, WalkTerrain.SOLID)
                .fill(-1, -1, -1, -1, 6, SIZE, WalkTerrain.SOLID).fill(SIZE, -1, -1, SIZE, 6, SIZE, WalkTerrain.SOLID);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int height = random.nextInt(10) < 6 ? 0 : random.nextInt(3);
                for (int y = 0; y < height; y++) world.block(x, y, z);
                int roll = random.nextInt(100);
                if (roll < 6) {
                    world.fill(x, height, z, x, height + 3, z, WalkTerrain.SOLID);
                } else if (roll < 14) {
                    world.set(x, height, z, WalkTerrain.STEP);
                } else if (roll < 20) {
                    world.set(x, height - 1, z, WalkTerrain.SLOW);
                } else if (roll < 25) {
                    world.set(x, height - 1, z, WalkTerrain.ICE);
                } else if (roll < 29) {
                    world.set(x, height, z, WalkTerrain.WATER);
                } else if (roll < 31) {
                    world.set(x, height, z, WalkTerrain.DANGER);
                }
            }
        }
        return world;
    }

    private static List<Long> standableCells(GridWorld world) {
        List<Long> out = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y <= 5; y++) {
                    if (world.standable(x, y, z) && WalkPathfinder.cellCost(world, x, y, z, MAX_DROP) >= 0.0) {
                        out.add(pack(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /** Textbook Dijkstra with lazy deletion over {@link WalkPathfinder#forEachMove} and {@link WalkPathfinder#cellCost}. */
    private static double dijkstra(WalkTerrain world, long start, long goal) {
        Map<Long, Double> best = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.cost, b.cost));
        best.put(start, 0.0);
        queue.add(new Entry(0.0, start));
        while (!queue.isEmpty()) {
            Entry top = queue.poll();
            long key = top.key;
            double cost = top.cost;
            if (cost > best.getOrDefault(key, Double.MAX_VALUE)) continue;
            if (key == goal) return cost;
            WalkPathfinder.forEachMove(world, WalkTerrain.unpackX(key), WalkTerrain.unpackY(key), WalkTerrain.unpackZ(key),
                    MAX_DROP, (x, y, z, move) -> {
                        double cell = WalkPathfinder.cellCost(world, x, y, z, MAX_DROP);
                        if (cell < 0.0) return;
                        long next = pack(x, y, z);
                        double total = cost + move + cell;
                        if (total < best.getOrDefault(next, Double.MAX_VALUE)) {
                            best.put(next, total);
                            queue.add(new Entry(total, next));
                        }
                    });
        }
        return Double.MAX_VALUE;
    }

    private record Entry(double cost, long key) {
    }
}
