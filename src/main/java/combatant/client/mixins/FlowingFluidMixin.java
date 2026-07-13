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
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoPush;

import static net.minecraft.world.level.material.FlowingFluid.FALLING;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Shadow
    protected abstract boolean isSolidFace(BlockGetter world, BlockPos pos, Direction direction);

    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void combatant$getVelocity(BlockGetter world, BlockPos pos, FluidState state, CallbackInfoReturnable<Vec3> cir) {
        NoPush np = Modules.get(NoPush.class);
        if (np == null || !np.liquidsEnabled()) return;

        double d = 0.0;
        double e = 0.0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Vec3 vec3d = new Vec3(d, 0.0, e);
        if (state.getValue(FALLING)) {
            for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                mutable.setWithOffset(pos, direction2);
                if (!this.isSolidFace(world, mutable, direction2) && !this.isSolidFace(world, mutable.above(), direction2)) {
                    continue;
                }
                vec3d = vec3d.normalize().add(0.0, -6.0, 0.0);
                break;
            }
        }
        cir.setReturnValue(vec3d.normalize());
    }
}
