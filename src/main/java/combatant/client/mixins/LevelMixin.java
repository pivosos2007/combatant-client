/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.features.module.modules.visuals.WorldTweaks.WeatherMode;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void picker$rainGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        WorldTweaks wt = Modules.get(WorldTweaks.class);
        if (wt == null || !wt.isWeatherControlEnabled()) return;

        WeatherMode mode = wt.getWeatherMode();
        cir.setReturnValue(
                (mode == WeatherMode.RAIN || mode == WeatherMode.THUNDER) ? 1.0F : 0.0F
        );
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void picker$thunderGradient(float tickDelta, CallbackInfoReturnable<Float> cir) {
        WorldTweaks wt = Modules.get(WorldTweaks.class);
        if (wt == null || !wt.isWeatherControlEnabled()) return;

        cir.setReturnValue(
                wt.getWeatherMode() == WeatherMode.THUNDER ? 1.0F : 0.0F
        );
    }

}
