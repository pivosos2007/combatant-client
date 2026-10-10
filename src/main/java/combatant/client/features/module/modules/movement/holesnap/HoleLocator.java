/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.holesnap;

/**
 * Finds the nearest one-block "safe hole" (floor and four walls of bedrock or blast-proof blocks, three
 * blocks of headroom), the same shape HoleESP draws. The world is reached through {@link World}, so the
 * search itself runs in plain unit tests.
 */
public final class HoleLocator {

    public enum Cell {
        /** Walkable space: air, replaceable plants, fluids. Out-of-world cells are {@link #OTHER}. */
        AIR,
        BEDROCK,
        /** Obsidian, crying obsidian, netherite block, respawn anchor. */
        BLAST_PROOF,
        OTHER
    }

    public enum Kind {
        BEDROCK,
        BLAST_PROOF
    }

    @FunctionalInterface
    public interface World {
        Cell at(int x, int y, int z);
    }

    public record Hole(int x, int y, int z, Kind kind) {
        public double centerX() {
            return x + 0.5;
        }

        public double centerZ() {
            return z + 0.5;
        }
    }

    private HoleLocator() {
    }

    /** The hole whose feet cell is {@code (x, y, z)}, or null when that cell is not a hole. */
    public static Hole classify(World world, int x, int y, int z) {
        if (world.at(x, y, z) != Cell.AIR
                || world.at(x, y + 1, z) != Cell.AIR
                || world.at(x, y + 2, z) != Cell.AIR) {
            return null;
        }

        Cell floor = world.at(x, y - 1, z);
        Cell north = world.at(x, y, z - 1);
        Cell south = world.at(x, y, z + 1);
        Cell east = world.at(x + 1, y, z);
        Cell west = world.at(x - 1, y, z);
        if (!safe(floor) || !safe(north) || !safe(south) || !safe(east) || !safe(west)) return null;

        boolean allBedrock = floor == Cell.BEDROCK && north == Cell.BEDROCK && south == Cell.BEDROCK
                && east == Cell.BEDROCK && west == Cell.BEDROCK;
        return new Hole(x, y, z, allBedrock ? Kind.BEDROCK : Kind.BLAST_PROOF);
    }

    /**
     * Nearest hole by horizontal distance, ties broken toward the flatter route.
     *
     * @param maxDrop how many blocks below the player's feet block a hole may sit
     * @param maxRise how many blocks above it
     */
    public static Hole nearest(World world, double px, double py, double pz,
                               int rangeXZ, int maxDrop, int maxRise,
                               boolean allowBedrock, boolean allowBlastProof) {
        int baseX = (int) Math.floor(px);
        int baseY = (int) Math.floor(py);
        int baseZ = (int) Math.floor(pz);
        double rangeSq = (double) rangeXZ * rangeXZ;

        Hole best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -rangeXZ; dx <= rangeXZ; dx++) {
            for (int dz = -rangeXZ; dz <= rangeXZ; dz++) {
                double offsetX = baseX + dx + 0.5 - px;
                double offsetZ = baseZ + dz + 0.5 - pz;
                double distSq = offsetX * offsetX + offsetZ * offsetZ;
                if (distSq > rangeSq) continue;

                for (int dy = maxRise; dy >= -maxDrop; dy--) {
                    Hole hole = classify(world, baseX + dx, baseY + dy, baseZ + dz);
                    if (hole == null) continue;
                    // The first hit from the top is the reachable one; anything beneath it is walled in.
                    if (hole.kind() == Kind.BEDROCK ? allowBedrock : allowBlastProof) {
                        double score = distSq + Math.abs(dy) * 0.01;
                        if (score < bestScore) {
                            bestScore = score;
                            best = hole;
                        }
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static boolean safe(Cell cell) {
        return cell == Cell.BEDROCK || cell == Cell.BLAST_PROOF;
    }
}
