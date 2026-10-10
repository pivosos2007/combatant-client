/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.modules.misc.NameProtect;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MutableComponent.class)
public abstract class MutableComponentMixin {

    @Shadow
    private FormattedCharSequence visualOrderText;

    @Shadow
    private Language decomposedWith;

    @Unique
    private long combatant$nameProtectRevision = Long.MIN_VALUE;

    @Inject(method = "getVisualOrderText", at = @At("HEAD"))
    private void combatant$invalidateNameProtectedVisualOrder(CallbackInfoReturnable<FormattedCharSequence> cir) {
        long revision = NameProtect.revision();
        if (revision == combatant$nameProtectRevision) return;
        combatant$nameProtectRevision = revision;
        visualOrderText = null;
        decomposedWith = null;
    }
}
