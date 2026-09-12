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
import combatant.client.util.sound.SoundSystem;

/**
 * Publishes the real OpenAL lifecycle to the custom sound system.
 * Cleanup is performed before Minecraft destroys the current context.
 */
@Mixin(Library.class)
public class LibraryMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void combatant$resetOnInit(@Nullable String deviceSpecifier, DeviceList deviceList, boolean directionalAudio, CallbackInfo ci) {
        SoundSystem.get().onLibraryReady();
    }

    @Inject(method = "cleanup", at = @At("HEAD"))
    private void combatant$resetOnClose(CallbackInfo ci) {
        SoundSystem.get().onLibraryClosing();
    }
}

