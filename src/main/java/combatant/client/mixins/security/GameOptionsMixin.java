/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 *
 * Original portions remain under the MIT License.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

/*
 * Portions of this file adapt protection logic from ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 * Licensed under MIT; see THIRD_PARTY_NOTICES.md.
 */
package combatant.client.mixins.security;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.security.BackdoorProtection;

import java.io.File;

@Mixin(Options.class)
public abstract class GameOptionsMixin {

    @Shadow
    @Final
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("TAIL"))
    private void combatant$backdoor$registerVanillaKeybinds(Minecraft client, File optionsFile, CallbackInfo ci) {
        BackdoorProtection.resetVanillaKeybinds();
        for (KeyMapping keyBinding : keyMappings) {
            BackdoorProtection.registerVanillaKeybind(keyBinding.getName());
        }
    }
}
