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

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.security.BackdoorProtection;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {

    @Shadow
    private SignText text;

    @Shadow
    private String[] messages;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN")
    )
    private void combatant$backdoor$filterSignMessages(SignBlockEntity blockEntity, boolean front, boolean filtered, Component title, CallbackInfo ci) {
        for (int line = 0; line < this.messages.length; line++) {
            Component safeText = BackdoorProtection.filterText(this.text.getMessage(line, filtered));
            this.messages[line] = safeText.getString();
        }
    }
}
