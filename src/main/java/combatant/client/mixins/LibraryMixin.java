/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.util.wav.CustomSoundEngine;

/**
 * Hooks into Minecraft SoundEngine lifecycle to reset our WAV caches
 * when audio is (re)initialized or closed.
 */
@Mixin(Library.class)
public class LibraryMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void combatant$resetOnInit(@Nullable String deviceSpecifier, DeviceList deviceList, boolean directionalAudio, CallbackInfo ci) {
        CustomSoundEngine.get().reset();
    }

    @Inject(method = "cleanup", at = @At("HEAD"))
    private void combatant$resetOnClose(CallbackInfo ci) {
        CustomSoundEngine.get().reset();
    }
}



