/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.audio.Channel;
import combatant.client.util.sound.SoundSystem;
import combatant.client.util.sound.acoustics.AcousticChannelAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes native EFX bindings before Minecraft releases the OpenAL source ID. */
@Mixin(Channel.class)
public abstract class ChannelAcousticsMixin implements AcousticChannelAccess {
    @Shadow @Final private int source;

    @Override
    public int combatant$acousticSource() {
        return source;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void combatant$releaseAcoustics(CallbackInfo ci) {
        SoundSystem.get().onVanillaChannelDestroyed(source);
    }
}
