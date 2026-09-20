/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.minimap;

import combatant.client.config.subsystem.MapUiConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.state.RadarStateUpdater;

@Pseudo
@Mixin(RadarStateUpdater.class)
public abstract class RadarStateUpdaterMixin {

    /**
     * Xaero folds normal invisibility and its sneaking/team visibility rule into this one predicate.
     * Returning false here keeps every other radar gate intact while allowing both kinds of hidden
     * entities when the user explicitly opts in.
     */
    @Inject(
            method = "isInvisibleTo(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$showInvisibleAndSneaking(Entity entity,
                                                     Player viewer,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (MapUiConfig.get().showInvisibleRadar()) {
            cir.setReturnValue(false);
        }
    }
}
