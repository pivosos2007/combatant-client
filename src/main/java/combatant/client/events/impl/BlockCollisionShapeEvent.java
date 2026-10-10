/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.events.impl;

import combatant.client.events.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The computed collision shape, independent of virtual block-state replacement. */
public final class BlockCollisionShapeEvent extends Event {
    private final BlockState state;
    private final BlockPos pos;
    private VoxelShape shape;
    private final CollisionGetter world;
    private final CollisionContext context;

    public BlockCollisionShapeEvent(BlockState state, BlockPos pos, VoxelShape shape,
                                    CollisionGetter world, CollisionContext context) {
        this.state = state;
        this.pos = pos;
        this.shape = shape;
        this.world = world;
        this.context = context;
    }

    public BlockState getState() { return state; }
    public BlockPos getPos() { return pos; }
    public VoxelShape getShape() { return shape; }
    public CollisionGetter getWorld() { return world; }
    public CollisionContext getContext() { return context; }
    public void setShape(VoxelShape shape) {
        if (shape != null) this.shape = shape;
    }
}
