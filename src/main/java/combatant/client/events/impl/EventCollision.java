/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.events.Event;

/**
 * Replaces the block state observed by the block-collision iterator.
 * This occurs before collision-shape resolution and does not change the world
 * block state. For shape substitution, use {@link BlockCollisionShapeEvent}.
 */
@Getter
public class EventCollision extends Event {
    private final BlockPos pos;
    @Setter
    private BlockState state;

    public EventCollision(BlockState state, BlockPos pos) {
        this.state = state;
        this.pos = pos;
    }

}
