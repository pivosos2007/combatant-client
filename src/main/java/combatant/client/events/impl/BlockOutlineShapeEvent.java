/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Outline geometry for the targeted block, independent of collision shapes. */
@Getter
public final class BlockOutlineShapeEvent extends Event {
    private final BlockState state;
    private final BlockGetter world;
    private final BlockPos pos;
    private final CollisionContext context;
    private VoxelShape shape;

    public BlockOutlineShapeEvent(BlockState state, BlockGetter world, BlockPos pos,
                                  CollisionContext context, VoxelShape shape) {
        this.state = state;
        this.world = world;
        this.pos = pos;
        this.context = context;
        this.shape = shape;
    }

    public void setShape(VoxelShape shape) {
        if (shape != null) this.shape = shape;
    }
}
