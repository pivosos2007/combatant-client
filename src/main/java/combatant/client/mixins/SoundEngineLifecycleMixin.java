/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.util.sound.SoundSystem;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Supplies the real Minecraft audio executor; never creates another OpenAL context. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineLifecycleMixin {

    @Shadow @Final private SoundEngineExecutor executor;
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void combatant$attachAudioExecutor(CallbackInfo ci) {
        SoundSystem.get().attachExecutor(executor);
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void combatant$initialAcoustics(SoundInstance sound,
                                            CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        ChannelAccess.ChannelHandle handle = instanceToChannel.get(sound);
        if (handle != null) SoundSystem.get().onVanillaSoundStarted(sound, handle);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void combatant$updateAcoustics(boolean paused, CallbackInfo ci) {
        SoundSystem.get().updateVanillaAcoustics(instanceToChannel);
    }
}
