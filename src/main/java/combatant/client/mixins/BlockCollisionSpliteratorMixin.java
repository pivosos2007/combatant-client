/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.events.Events;
import combatant.client.events.impl.EventCollision;

@Mixin(value = BlockCollisions.class, priority = 800)
public abstract class BlockCollisionSpliteratorMixin {

    @Redirect(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState combatant$collisionHook(BlockGetter instance, BlockPos blockPos) {
        BlockState state = instance.getBlockState(blockPos);
        if (!Events.BUS.hasListeners(EventCollision.class)) {
            return state;
        }
        EventCollision event = new EventCollision(state, blockPos);
        Events.BUS.post(event);
        return event.getState();
    }
}
