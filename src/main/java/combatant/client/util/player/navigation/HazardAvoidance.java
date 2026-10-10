/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 *
 * Hazard categories adapted from LiquidBounce's AvoidHazards (GPL-3.0-or-later).
 * https://github.com/CCBlueX/LiquidBounce
 */
package combatant.client.util.player.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Shared, read-only hazard classification; never substitutes world block states. */
public final class HazardAvoidance {
    public enum Kind {
        FIRE, LAVA, COBWEB, CACTUS, BERRY_BUSH, MAGMA, PRESSURE_PLATE,
        CAMPFIRE, POWDER_SNOW, WITHER_ROSE
    }

    private HazardAvoidance() {}

    /** Returns a kind for a harmful block or liquid encountered by the player's feet/body. */
    public static Kind classify(Level world, BlockPos pos, BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return Kind.FIRE;
        if (state.is(Blocks.LAVA) || !state.getFluidState().isEmpty()
                && (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA
                    || state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.FLOWING_LAVA)) return Kind.LAVA;
        if (state.is(Blocks.COBWEB)) return Kind.COBWEB;
        if (state.is(Blocks.CACTUS)) return Kind.CACTUS;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) return Kind.BERRY_BUSH;
        if (state.is(Blocks.MAGMA_BLOCK)) return Kind.MAGMA;
        if (state.getBlock() instanceof BasePressurePlateBlock) return Kind.PRESSURE_PLATE;
        if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) return Kind.CAMPFIRE;
        if (state.is(Blocks.POWDER_SNOW)) return Kind.POWDER_SNOW;
        if (state.is(Blocks.WITHER_ROSE)) return Kind.WITHER_ROSE;
        return null;
    }

    /** AABB touch test, including hazards in the block immediately under the feet. */
    public static int exposure(Level world, AABB box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = (int) Math.floor(box.minX + 0.015);
        int maxX = (int) Math.floor(box.maxX - 0.015);
        int minZ = (int) Math.floor(box.minZ + 0.015);
        int maxZ = (int) Math.floor(box.maxZ - 0.015);
        int minY = (int) Math.floor(box.minY - 0.12);
        int maxY = (int) Math.floor(box.maxY - 0.015);
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    Kind kind = classify(world, pos, world.getBlockState(pos));
                    if (kind == null) continue;
                    // A plate or magma block is hazardous underfoot, but an adjacent one is not.
                    if ((kind == Kind.MAGMA || kind == Kind.PRESSURE_PLATE)
                            && y + 1.0 < box.minY - 0.13) continue;
                    count += switch (kind) {
                        case LAVA -> 100;
                        case FIRE -> 64;
                        case CAMPFIRE -> 40;
                        case CACTUS, MAGMA -> 24;
                        case WITHER_ROSE -> 20;
                        case POWDER_SNOW -> 12;
                        case BERRY_BUSH -> 8;
                        case PRESSURE_PLATE -> 2;
                        case COBWEB -> 1;
                    };
                }
            }
        }
        return count;
    }
    /** Detect whether the player already intersects hazards of a particular kind. */
    public static boolean contains(Level world, AABB box, Kind kind) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX + 0.01); x <= (int) Math.floor(box.maxX - 0.01); x++) {
            for (int z = (int) Math.floor(box.minZ + 0.01); z <= (int) Math.floor(box.maxZ - 0.01); z++) {
                for (int y = (int) Math.floor(box.minY + 0.01); y <= (int) Math.floor(box.maxY - 0.01); y++) {
                    pos.set(x, y, z);
                    if (classify(world, pos, world.getBlockState(pos)) == kind) return true;
                }
            }
        }
        return false;
    }

}
