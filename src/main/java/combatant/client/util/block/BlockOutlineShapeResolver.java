/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import combatant.client.events.Events;
import combatant.client.events.impl.BlockOutlineShapeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockOutlineShapeResolver {
    private BlockOutlineShapeResolver() { }

    public static VoxelShape resolve(BlockState state, BlockGetter world, BlockPos pos,
                                     CollisionContext context, VoxelShape shape) {
        if (shape == null || !Events.BUS.hasListeners(BlockOutlineShapeEvent.class)) return shape;
        BlockOutlineShapeEvent event = new BlockOutlineShapeEvent(state, world, pos, context, shape);
        Events.BUS.post(event);
        return event.getShape();
    }
}
