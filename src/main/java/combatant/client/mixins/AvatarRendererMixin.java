/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ViewModel;
import combatant.client.features.playeranimator.PlayerAnimator;
import combatant.client.features.playeranimator.PlayerRigRenderState;
import combatant.client.features.playermodel.PlayerModelRenderState;
import combatant.client.features.playermodel.PlayerModels;
import combatant.client.features.module.modules.visuals.PlayerModel;
import combatant.client.render.helpers.TickDelta;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the rig only after AvatarRenderer has populated the complete Humanoid/Avatar render state.
 * Doing this from LivingEntityRenderer.extractRenderState is too early: swim, arm-pose and flight
 * fields are filled by AvatarRenderer only after its super call returns.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void combatant$extractPlayerRig(Avatar entity, AvatarRenderState state,
                                            float tickProgress, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || !(state instanceof PlayerRigRenderState rigState)) {
            return;
        }
        ViewModel viewModel = Modules.get(ViewModel.class);
        PlayerModelRenderState replacementState = (PlayerModelRenderState) state;
        replacementState.combatant$setReplacement(null);
        PlayerModel replacement = Modules.get(PlayerModel.class);
        PlayerModel.Target target = replacement != null ? replacement.targetFor(player) : PlayerModel.Target.SELF;
        PlayerModels.Ready asset = PlayerModels.applies(player, replacement, target) ? PlayerModels.prepare(replacement, target) : null;
        boolean viewRig = viewModel != null && viewModel.isPlayerRigActive();
        if (!viewRig && asset == null) {
            rigState.combatant$setPlayerRig(null);
            return;
        }
        rigState.combatant$setPlayerRig(PlayerAnimator.animate(
                player,
                state,
                tickProgress,
                TickDelta.frameDeltaSeconds(),
                viewRig ? viewModel.playerRigStyle() : "Hybrid",
                viewRig ? viewModel.playerRigStrength() : 1f
        ));
        if (asset != null) {
            try {
                var fadeState = (combatant.client.render.ViewObstructionFadeState) state;
                PlayerModels.Snapshot snapshot = PlayerModels.snapshot(
                        asset, rigState.combatant$getPlayerRig(), replacement, target, player, fadeState);
                replacementState.combatant$setReplacement(snapshot);
                if (snapshot != null && snapshot.faded()) {
                    fadeState.combatant$setViewObstructionFadeActive(true);
                    fadeState.combatant$setViewObstructionFadeAlpha(snapshot.opacity());
                }
            } catch (IllegalArgumentException failure) {
                PlayerModels.reportFailure(failure);
                if (!viewRig) rigState.combatant$setPlayerRig(null);
            }
        }
    }
}
