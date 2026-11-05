/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.profiler.DevTracyProfiler;

@Mixin(Profiler.class)
public abstract class ProfilersMixin {

    @Inject(method = "getDefaultFiller", at = @At("HEAD"), cancellable = true)
    private static void combatant$limitVanillaTracyToRenderThread(CallbackInfoReturnable<ProfilerFiller> cir) {
        if (!DevTracyProfiler.isEnabled()) {
            return;
        }
        if (DevTracyProfiler.shouldTraceCurrentThread()) {
            return;
        }
        cir.setReturnValue(InactiveProfiler.INSTANCE);
    }
}
