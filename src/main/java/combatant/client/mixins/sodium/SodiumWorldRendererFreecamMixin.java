/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererFreecamMixin {

    @ModifyVariable(
            method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private boolean combatant$freecamUsesSpectatorOcclusionRules(boolean spectator) {
        Freecam freecam = Modules.get(Freecam.class);
        return spectator || (freecam != null && freecam.isEnabled() && freecam.camEntity != null);
    }
}
