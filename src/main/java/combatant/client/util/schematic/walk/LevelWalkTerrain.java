/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The client world as a {@link WalkTerrain}: classifies block states into walk cells and fills a
 * {@link TerrainCache} a whole chunk section at a time straight from the section palette.
 *
 * <p>Reads {@code Minecraft.getInstance().level} at call time, so one instance survives world changes (clear the
 * cache that wraps it when the level changes). Client thread only.</p>
 */
public final class LevelWalkTerrain implements TerrainCache.SectionSource {

    /** Classes of block states whose collision shape never depends on the position: most of them. */
    private final Map<BlockState, Byte> stateClasses = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

    @Override
    public byte cell(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || y < level.getMinY() || y >= level.getMaxY()) return WalkTerrain.UNKNOWN;
        probe.set(x, y, z);
        if (!level.isLoaded(probe)) return WalkTerrain.UNKNOWN;
        return classifyCached(level, probe, level.getBlockState(probe));
    }

    @Override
    public void fillSection(int sectionX, int sectionY, int sectionZ, byte[] out) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            Arrays.fill(out, WalkTerrain.UNKNOWN);
            return;
        }
        int index = level.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= level.getSectionsCount() || !level.hasChunk(sectionX, sectionZ)) {
            Arrays.fill(out, WalkTerrain.UNKNOWN);
            return;
        }
        LevelChunk chunk = level.getChunk(sectionX, sectionZ);
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir()) {
            Arrays.fill(out, WalkTerrain.OPEN);
            return;
        }
        int baseX = sectionX << 4, baseY = sectionY << 4, baseZ = sectionZ << 4;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    at.set(baseX + x, baseY + y, baseZ + z);
                    out[y << 8 | z << 4 | x] = classifyCached(level, at, state);
                }
            }
        }
    }

    private byte classifyCached(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock().hasDynamicShape()) return classify(level, pos, state);
        Byte known = stateClasses.get(state);
        if (known != null) return known;
        byte value = classify(level, pos, state);
        stateClasses.put(state, value);
        return value;
    }

    /** The walk cell class of the block at {@code pos}; unloaded and out-of-world positions are UNKNOWN. */
    public static byte classify(ClientLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() || !level.isLoaded(pos)) return WalkTerrain.UNKNOWN;
        return classify(level, pos, level.getBlockState(pos));
    }

    private static byte classify(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (isDangerous(block)) return WalkTerrain.DANGER;
        FluidState fluid = state.getFluidState();
        boolean water = !fluid.isEmpty();
        if (water && !fluid.is(FluidTags.WATER)) return WalkTerrain.DANGER;
        if (block == Blocks.COBWEB) return WalkTerrain.WEB;
        if (state.is(BlockTags.CLIMBABLE)) return WalkTerrain.CLIMB;
        if (block instanceof DoorBlock) return state.getValue(DoorBlock.OPEN) ? WalkTerrain.OPEN : WalkTerrain.SOLID;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return water ? WalkTerrain.WATER : WalkTerrain.OPEN;
        double top = shape.max(Direction.Axis.Y);
        if (top > 1.0) return WalkTerrain.UNKNOWN;
        if (top < 0.2) return water ? WalkTerrain.WATER : WalkTerrain.OPEN;
        // The feet cell is floor(y + 0.5): floors under half a block keep the feet in this cell, the rest lift
        // them into the cell above.
        if (top < 0.5 - 1.0e-6) return WalkTerrain.RAISED;
        if (top <= 0.5 + 1.0e-6) return WalkTerrain.STEP;
        if (block.getSpeedFactor() < 1.0f) return WalkTerrain.SLOW;
        if (block.getFriction() > 0.6f) return WalkTerrain.ICE;
        return WalkTerrain.SOLID;
    }

    private static boolean isDangerous(Block block) {
        return block instanceof BaseFireBlock
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.POWDER_SNOW
                || block == Blocks.WITHER_ROSE
                || block == Blocks.POINTED_DRIPSTONE;
    }
}
