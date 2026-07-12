/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.util.NarratorBlocker;

@Mixin(GameNarrator.class)
public class NarratorManagerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "sayChatQueued(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteChat(Component message, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "saySystemChatQueued(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteNarrator(Component message, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "saySystemQueued(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteSystem(Component message, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "saySystemNow(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteImmediateText(Component message, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "saySystemNow(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteImmediateString(String message, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "narrateMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void combatant$muteSay(String message, boolean interrupt, CallbackInfo ci) {
        if (shouldMute()) {
            NarratorBlocker.enforce(this.minecraft);
            ci.cancel();
        }
    }

    @Inject(method = "updateNarratorStatus", at = @At("HEAD"), cancellable = true)
    private void combatant$blockToggle(NarratorStatus mode, CallbackInfo ci) {
        if (!shouldMute()) return;
        NarratorBlocker.enforce(this.minecraft);
        ci.cancel();
    }

    @Inject(method = "checkStatus", at = @At("HEAD"), cancellable = true)
    private void combatant$skipNarratorLibraryCheck(boolean narratorEnabled, CallbackInfo ci) {
        if (!shouldMute()) return;
        NarratorBlocker.enforce(this.minecraft);
        ci.cancel();
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void combatant$reportInactive(CallbackInfoReturnable<Boolean> cir) {
        if (!shouldMute()) return;
        cir.setReturnValue(false);
    }

    @Unique
    private boolean shouldMute() {
        return NarratorBlocker.isBlocked();
    }
}

