/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions derived from ThunderHack Recode, copyright (c) 2023-2024 Pan4ur & 06ED.
 * Upstream: https://github.com/Pan4ur/ThunderHack-Recode
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.events.Events;
import combatant.client.events.impl.EventCollision;
import combatant.client.events.impl.BlockCollisionShapeEvent;

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
    // CollisionContext is the actual shape provider in Minecraft 26.2.
    // Use a distinct event: state substitution in EventCollision remains untouched.
    @Redirect(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private VoxelShape combatant$shapeHook(CollisionContext context, BlockState state,
                                           CollisionGetter collisionGetter, BlockPos pos) {
        VoxelShape original = context.getCollisionShape(state, collisionGetter, pos);
        if (!Events.BUS.hasListeners(BlockCollisionShapeEvent.class)) return original;
        BlockCollisionShapeEvent event = new BlockCollisionShapeEvent(state, pos, original, collisionGetter, context);
        Events.BUS.post(event);
        return event.getShape();
    }

}
