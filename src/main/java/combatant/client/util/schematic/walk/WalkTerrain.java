/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

/**
 * A block grid as the schematic walker sees it: one class per cell, nothing else.
 *
 * <p>Kept free of game types so the pathfinder, the standing-spot chooser and the pillar planner can be tested
 * against small hand-drawn worlds.</p>
 *
 * <p>Feet cells follow the player's own rounding ({@code floor(y + 0.5)}): a floor lower than half a block
 * ({@link #RAISED}) keeps the feet in its own cell, a floor of half a block or more ({@link #STEP} and the full
 * blocks) puts them in the cell above.</p>
 */
@FunctionalInterface
public interface WalkTerrain {

    /** No collision: the body can pass through (air, grass, an open door, a carpet). */
    byte OPEN = 0;
    /** Full collision that can be stood on and clicked against. */
    byte SOLID = 1;
    /** Never enter or stand on: lava, fire, cactus, magma, powder snow and the like. */
    byte DANGER = 2;
    /** Unloaded, outside the world or an odd shape (fence, wall): treated as an obstacle that cannot be stood on. */
    byte UNKNOWN = 3;
    /** Water: the body wades through it slowly; standing still needs a floor below. */
    byte WATER = 4;
    /** Ladder, vine, scaffolding: the body passes and holds on, climbing up and down needs no floor. */
    byte CLIMB = 5;
    /** A floor half a block high (bottom slab): stood on from the cell above, walked onto without a jump. */
    byte STEP = 6;
    /** Ground that slows walking down (soul sand, honey): stood on like solid ground, costed higher. */
    byte SLOW = 7;
    /** Ice: stood on like solid ground, but slides and stops late. */
    byte ICE = 8;
    /** Cobweb: passable but slows the body to a crawl. */
    byte WEB = 9;
    /** A floor lower than half a block (snow layers, a daylight sensor): the feet stay in this cell, a bit higher. */
    byte RAISED = 10;

    /**
     * An open door: passable, but its leaf takes a strip along one side, so the body goes straight through the
     * middle and never cuts a corner or a diagonal across it.
     */
    byte NARROW = 11;

    /** How high the feet stand above the cell floor on a {@link #RAISED} block, roughly. */
    double RAISED_HEIGHT = 0.4;
    /** How far below the cell floor the feet stand on a {@link #STEP} block in the cell underneath. */
    double STEP_SINK = 0.5;

    byte cell(int x, int y, int z);

    default boolean open(int x, int y, int z) {
        return cell(x, y, z) == OPEN;
    }

    /** A block with collision that can be stood on and clicked against. */
    default boolean solid(int x, int y, int z) {
        return support(cell(x, y, z));
    }

    /** The body fits into the cell (perhaps slowly). */
    default boolean passable(int x, int y, int z) {
        return passable(cell(x, y, z));
    }

    /** Feet cell and head cell free and something to stand on or hold on to. */
    default boolean standable(int x, int y, int z) {
        byte feet = cell(x, y, z);
        if (!passable(feet) || !passable(cell(x, y + 1, z))) return false;
        if (feet == RAISED) return passable(cell(x, y + 2, z));
        return feet == CLIMB || support(cell(x, y - 1, z));
    }

    /** Height of the feet above the bottom of feet cell {@code y} when standing there. */
    default double floorOffset(int x, int y, int z) {
        byte feet = cell(x, y, z);
        if (feet == RAISED) return RAISED_HEIGHT;
        if (feet != CLIMB && cell(x, y - 1, z) == STEP) return -STEP_SINK;
        return 0.0;
    }

    static boolean passable(byte cell) {
        return cell == OPEN || cell == WATER || cell == CLIMB || cell == WEB || cell == RAISED || cell == NARROW;
    }

    static boolean support(byte cell) {
        return cell == SOLID || cell == STEP || cell == SLOW || cell == ICE;
    }

    /** Packs a cell the way {@code BlockPos.asLong} does, so keys can be shared with game code. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    static int unpackX(long key) {
        return (int) (key >> 38);
    }

    static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
