/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WeatherEffectRenderer.class, priority = 1200)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderWeather(Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.offWorld("weather")) {
            ci.cancel();
        }
    }
}
