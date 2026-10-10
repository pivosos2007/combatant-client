/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.function.Predicate;

/**
 * Section-aware block walks for chunk analyzers.
 *
 * <p>{@link LevelChunkSection#maybeHas} checks only the section palette, so sections that
 * cannot contain a match are skipped without touching their 4096 states. That keeps most
 * analyzers under a few microseconds per chunk.</p>
 */
public final class ChunkBlocks {

    @FunctionalInterface
    public interface Visitor {
        /** @return false to stop the walk early */
        boolean visit(int x, int y, int z, BlockState state);
    }

    private ChunkBlocks() {
    }

    /** Counts matching blocks with {@code minY <= y <= maxY}, stopping once {@code stopAt} is reached. */
    public static int count(LevelChunk chunk, int minY, int maxY, Predicate<BlockState> match, int stopAt) {
        int[] found = {0};
        forEach(chunk, minY, maxY, match, (x, y, z, state) -> ++found[0] < stopAt);
        return found[0];
    }

    /** First matching block position, or null. */
    public static BlockPos first(LevelChunk chunk, int minY, int maxY, Predicate<BlockState> match) {
        BlockPos[] hit = {null};
        forEach(chunk, minY, maxY, match, (x, y, z, state) -> {
            hit[0] = new BlockPos(x, y, z);
            return false;
        });
        return hit[0];
    }

    /** Visits every block matching {@code match} in world coordinates, bottom section first. */
    public static void forEach(LevelChunk chunk, int minY, int maxY, Predicate<BlockState> match, Visitor visitor) {
        LevelChunkSection[] sections = chunk.getSections();
        int chunkMinY = chunk.getMinY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int index = 0; index < sections.length; index++) {
            int sectionBottom = chunkMinY + (index << 4);
            if (sectionBottom > maxY) return;
            if (sectionBottom + 15 < minY) continue;

            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(match)) continue;

            int fromLocalY = Math.max(0, minY - sectionBottom);
            int toLocalY = Math.min(15, maxY - sectionBottom);
            for (int ly = fromLocalY; ly <= toLocalY; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (match.test(state)
                                && !visitor.visit(baseX + lx, sectionBottom + ly, baseZ + lz, state)) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
