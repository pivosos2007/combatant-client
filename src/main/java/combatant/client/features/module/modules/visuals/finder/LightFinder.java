/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.finder.ChunkBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds underground bases by their light. Anti-xray hides blocks, but block light is computed
 * on the server from the real blocks and sent with the chunk, so torches behind "stone" still
 * light the cave around them. Port of OpenDqrkis' LightFinder, done per chunk.
 */
@ModuleInfo(
        id = "lightfinder",
        displayName = "LightFinder",
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.lightfinder.description")
public final class LightFinder extends ChunkFinderModule<LightFinder.Lit> {

    // Sampling every second block keeps a chunk under ~3.5k light lookups and still hits
    // any torch-lit room, since light spreads over several blocks.
    private static final int STEP = 2;
    // Sky light above this means the spot is open to the surface, not a hidden base.
    private static final int MAX_SKY_LIGHT = 4;
    // Lava lights up to ~7 blocks at level 8+; ignore candidates that close to any lava.
    private static final int LAVA_REACH = 7;
    private static final int MAX_LAVA_SAMPLES = 256;

    private final NumberValue<Integer> minY = num("min_y", -60, -64, 60);
    private final NumberValue<Integer> maxY = num("max_y", 50, -40, 120);
    private final NumberValue<Integer> minLight = num("min_light", 8, 1, 15);
    private final NumberValue<Integer> minLitSpots = num("min_lit_spots", 5, 2, 60);
    private final BooleanValue ignoreLava = bool("ignore_lava", true);

    public LightFinder() {
        super(4, "#40FFB000", "#FFFFB000", 2, 3000L);
    }

    @Override
    protected String analysisSignature() {
        return minY.get() + "|" + maxY.get() + "|" + minLight.get() + "|" + minLitSpots.get() + "|" + ignoreLava.get();
    }

    @Override
    protected Lit analyzeChunk(ClientLevel level, LevelChunk chunk) {
        int bottom = Math.min(minY.get(), maxY.get());
        int top = Math.max(minY.get(), maxY.get());
        List<BlockPos> lava = ignoreLava.get() ? lavaIn(chunk, bottom - LAVA_REACH, top + LAVA_REACH) : List.of();

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int threshold = minLight.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int count = 0;
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        for (int x = baseX; x < baseX + 16; x += STEP) {
            for (int z = baseZ; z < baseZ + 16; z += STEP) {
                for (int y = bottom; y <= top; y += STEP) {
                    cursor.set(x, y, z);
                    if (level.getBrightness(LightLayer.BLOCK, cursor) < threshold) continue;
                    if (level.getBrightness(LightLayer.SKY, cursor) > MAX_SKY_LIGHT) continue;
                    if (nearLava(cursor, lava)) continue;
                    count++;
                    sumX += x;
                    sumY += y;
                    sumZ += z;
                }
            }
        }

        if (count < minLitSpots.get()) return null;
        BlockPos center = new BlockPos((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count));
        return new Lit(count + " lit spots underground", center);
    }

    private static List<BlockPos> lavaIn(LevelChunk chunk, int minY, int maxY) {
        List<BlockPos> out = new ArrayList<>();
        ChunkBlocks.forEach(chunk, minY, maxY, state -> state.getFluidState().is(FluidTags.LAVA), (x, y, z, state) -> {
            out.add(new BlockPos(x, y, z));
            return out.size() < MAX_LAVA_SAMPLES;
        });
        return out;
    }

    private static boolean nearLava(BlockPos pos, List<BlockPos> lava) {
        for (BlockPos source : lava) {
            if (pos.distManhattan(source) <= LAVA_REACH) return true;
        }
        return false;
    }

    public record Lit(String reason, BlockPos evidence) implements ChunkFinderModule.Hit {
    }
}
