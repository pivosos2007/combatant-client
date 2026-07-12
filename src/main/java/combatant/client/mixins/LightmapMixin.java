/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import combatant.client.events.Events;
import combatant.client.events.impl.LightmapEvent;
import combatant.client.render.engine.light.LightmapState;

@Mixin(Lightmap.class)
public abstract class LightmapMixin {

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 5
            ),
            index = 0
    )
    private float combatant$ambientLightEvent(float original) {
        LightmapEvent event = new LightmapEvent(original);
        Events.BUS.post(event);
        float value = event.getAmbientLight();

        float base = original;
        float previousOverride = LightmapState.getOverrideAmbient();
        if (previousOverride >= 0.0f && value >= previousOverride - 1.0e-4f) {
            base = LightmapState.getBaseAmbient();
        }
        LightmapState.setAmbient(base, value);
        return value;
    }
}
