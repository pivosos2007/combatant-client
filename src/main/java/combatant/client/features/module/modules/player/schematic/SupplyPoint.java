/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The container the schematic builder fetches materials from: marked by looking at it (the builder's keybind or
 * {@code @schematic supply}), remembered for the dimension it was marked in until it is cleared.
 */
public final class SupplyPoint {

    public enum Result {
        SET,
        NOT_LOOKING,
        NOT_CONTAINER,
        NO_WORLD
    }

    public static final SupplyPoint INSTANCE = new SupplyPoint();

    private BlockPos pos;
    private ResourceKey<Level> dimension;
    private int revision;

    private SupplyPoint() {
    }

    /** The marked container, or {@code null} when none is marked in the current dimension. */
    public BlockPos pos() {
        ClientLevel level = Minecraft.getInstance().level;
        return pos != null && level != null && level.dimension().equals(dimension) ? pos : null;
    }

    /** Changes with every mark or clear: a builder paused on an empty container resumes when it moves. */
    public int revision() {
        return revision;
    }

    public void clear() {
        pos = null;
        dimension = null;
        revision++;
    }

    /** Marks the container the player is looking at. */
    public Result markLooked() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return Result.NO_WORLD;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Result.NOT_LOOKING;
        }
        BlockPos target = hit.getBlockPos();
        if (!isContainer(level, target)) return Result.NOT_CONTAINER;
        pos = target.immutable();
        dimension = level.dimension();
        revision++;
        return Result.SET;
    }

    public static boolean isContainer(ClientLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock
                || state.getBlock() instanceof EnderChestBlock
                || level.getBlockEntity(at) instanceof Container;
    }
}
