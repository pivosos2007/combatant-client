/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.surround;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Works out which blocks Surround and SelfTrap have to place and in what order. Everything here is plain
 * integer maths over a {@code replaceable} predicate, so the shapes and the support chains run in unit
 * tests without a world.
 */
public final class SurroundPlanner {

    public record Pos(int x, int y, int z) {
        public Pos above() {
            return new Pos(x, y + 1, z);
        }

        public Pos above(int blocks) {
            return new Pos(x, y + blocks, z);
        }

        public Pos below() {
            return new Pos(x, y - 1, z);
        }

        public Pos offset(int dx, int dz) {
            return new Pos(x + dx, y, z + dz);
        }
    }

    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private SurroundPlanner() {
    }

    /**
     * The blocks that wall in a footprint. {@code footprint} is the set of feet-level cells the player
     * stands in; the result is unordered and not yet filtered against the world.
     *
     * @param floor also fill the cells under the footprint and under every wall block
     * @param head  also wall the head-level cells beside the footprint (blocks crystal face placing)
     * @param roof  also cover the cells two above the footprint
     */
    public static Set<Pos> shell(Set<Pos> footprint, boolean floor, boolean head, boolean roof) {
        Set<Pos> sides = sides(footprint);
        Set<Pos> shell = new LinkedHashSet<>();
        for (Pos side : sides) {
            if (floor) shell.add(side.below());
            shell.add(side);
            if (head) shell.add(side.above());
        }
        for (Pos cell : footprint) {
            if (floor) shell.add(cell.below());
            if (roof) shell.add(cell.above(2));
        }
        return shell;
    }

    /**
     * The blocks that cap a footprint, for SelfTrap: a roof two above every cell, plus optionally the
     * head-level cells beside it ({@code face}) and the roof-level cells beside it ({@code roofSides}).
     */
    public static Set<Pos> trap(Set<Pos> footprint, boolean face, boolean roofSides) {
        Set<Pos> trap = new LinkedHashSet<>();
        for (Pos cell : footprint) trap.add(cell.above(2));
        if (face) {
            for (Pos side : sides(footprint)) {
                trap.add(side.above());
                if (roofSides) trap.add(side.above(2));
            }
        }
        return trap;
    }

    /** The feet-level cells next to the footprint that are not part of it. */
    public static Set<Pos> sides(Set<Pos> footprint) {
        Set<Pos> sides = new LinkedHashSet<>();
        for (Pos cell : footprint) {
            for (int[] step : HORIZONTAL) {
                Pos side = cell.offset(step[0], step[1]);
                if (!footprint.contains(side)) sides.add(side);
            }
        }
        return sides;
    }

    /** The cells the player's body occupies (feet and head) for a footprint; nothing may be placed there. */
    public static Set<Pos> bodyCells(Set<Pos> footprint) {
        Set<Pos> body = new LinkedHashSet<>();
        for (Pos cell : footprint) {
            body.add(cell);
            body.add(cell.above());
        }
        return body;
    }

    /**
     * Keeps the wanted positions that still need a block and puts them in placement order: lowest layer
     * first, so each layer has the one below it to place against.
     *
     * <p>With {@code supports} on, a block that has nothing to rest on (no solid neighbour in the world,
     * none planned before it) gets a short chain of extra blocks in front of it: below it, or beside it and
     * below that, whichever reaches something solid. The chain never enters {@code forbidden}, the cells the
     * player's own body occupies.
     */
    public static List<Pos> order(Collection<Pos> wanted, Predicate<Pos> replaceable,
                                  Set<Pos> forbidden, boolean supports) {
        List<Pos> needed = new ArrayList<>();
        Set<Pos> seen = new LinkedHashSet<>();
        for (Pos pos : wanted) {
            if (seen.add(pos) && replaceable.test(pos)) needed.add(pos);
        }
        needed.sort(Comparator.comparingInt(Pos::y));
        if (!supports) return needed;

        // Only blocks that can really be placed in this order count as anchors for later ones; a block
        // with nothing to rest on is parked in `orphans` so it cannot hold up a chain of supports.
        Set<Pos> planned = new LinkedHashSet<>();
        List<Pos> orphans = new ArrayList<>();
        for (Pos pos : needed) {
            if (!anchored(pos, replaceable, planned)) {
                List<Pos> chain = supportChain(pos, replaceable, planned, forbidden);
                if (chain.isEmpty()) {
                    orphans.add(pos);
                    continue;
                }
                planned.addAll(chain);
            }
            planned.add(pos);
        }

        // A later block can end up touching an orphan; keep promoting until nothing changes.
        boolean promoted = true;
        while (promoted && !orphans.isEmpty()) {
            promoted = false;
            for (Iterator<Pos> it = orphans.iterator(); it.hasNext(); ) {
                Pos orphan = it.next();
                if (anchored(orphan, replaceable, planned)) {
                    planned.add(orphan);
                    it.remove();
                    promoted = true;
                }
            }
        }

        List<Pos> ordered = new ArrayList<>(planned);
        ordered.addAll(orphans);
        return ordered;
    }

    /** True when something solid, or something already planned, touches the position. */
    private static boolean anchored(Pos pos, Predicate<Pos> replaceable, Set<Pos> planned) {
        for (Pos neighbour : neighbours(pos, true)) {
            if (!replaceable.test(neighbour) || planned.contains(neighbour)) return true;
        }
        return false;
    }

    /**
     * Extra blocks to place before {@code pos}, anchored end first; empty when none can be found. A chain
     * is one block, or two (a bridge beside the position and a pillar under or beside that): longer chains
     * mean the player is floating far from anything solid and a placement module is the wrong tool.
     */
    private static List<Pos> supportChain(Pos pos, Predicate<Pos> replaceable, Set<Pos> planned,
                                          Set<Pos> forbidden) {
        for (Pos first : neighbours(pos, false)) {
            if (usable(first, replaceable, forbidden) && anchored(first, replaceable, planned)) {
                return List.of(first);
            }
        }
        for (Pos bridge : neighbours(pos, false)) {
            if (!usable(bridge, replaceable, forbidden)) continue;
            for (Pos pillar : neighbours(bridge, false)) {
                if (!pillar.equals(pos) && usable(pillar, replaceable, forbidden)
                        && anchored(pillar, replaceable, planned)) {
                    return List.of(pillar, bridge);
                }
            }
        }
        return List.of();
    }

    private static boolean usable(Pos pos, Predicate<Pos> replaceable, Set<Pos> forbidden) {
        return replaceable.test(pos) && !forbidden.contains(pos);
    }

    /** Below first, then the four sides; {@code includeAbove} adds the cell on top. */
    private static List<Pos> neighbours(Pos pos, boolean includeAbove) {
        List<Pos> neighbours = new ArrayList<>(6);
        neighbours.add(pos.below());
        for (int[] step : HORIZONTAL) neighbours.add(pos.offset(step[0], step[1]));
        if (includeAbove) neighbours.add(pos.above());
        return neighbours;
    }
}
