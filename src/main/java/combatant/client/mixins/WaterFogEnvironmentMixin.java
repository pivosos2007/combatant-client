/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.WorldTweaks;

@Mixin(value = WaterFogEnvironment.class, priority = 1200)
public class WaterFogEnvironmentMixin {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void combatant$noWaterFog(FogData data,
                                      Camera camera,
                                      ClientLevel world,
                                      float viewDistance,
                                      DeltaTracker tickCounter,
                                      CallbackInfo ci) {
        WorldTweaks module = Modules.get(WorldTweaks.class);
        if (module == null || !module.isFogControlEnabled()) return;
        if (module.isFogModifyEnabled()) {
            module.applyFogOverride(data);
            ci.cancel();
            return;
        }
        if (!module.disableWaterFog()) return;

        float far = Math.max(viewDistance, 16.0f);
        data.environmentalStart = far;
        data.environmentalEnd = far;
        data.renderDistanceStart = far;
        data.renderDistanceEnd = far;
        data.skyEnd = far;
        data.cloudEnd = far;
        ci.cancel();
    }
}

