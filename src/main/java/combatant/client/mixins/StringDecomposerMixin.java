/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.modules.misc.NameProtect;

@Mixin(StringDecomposer.class)
public abstract class StringDecomposerMixin {

    // The re-entrant call below would otherwise run the replacement again on its own output.
    @Unique
    private static final ThreadLocal<Boolean> COMBATANT$REPLACING = ThreadLocal.withInitial(() -> false);

    // The other String overloads delegate here, so this one hook covers them all.
    @Inject(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void combatant$nameProtect(String text,
                                              int offset,
                                              Style startStyle,
                                              Style resetStyle,
                                              FormattedCharSink sink,
                                              CallbackInfoReturnable<Boolean> cir) {
        // A non-zero offset indexes into the original string; shifting it would garble text.
        if (offset != 0) return;
        String replaced = NameProtect.apply(text);
        if (replaced == text || COMBATANT$REPLACING.get()) return;

        COMBATANT$REPLACING.set(true);
        try {
            cir.setReturnValue(StringDecomposer.iterateFormatted(replaced, 0, startStyle, resetStyle, sink));
        } finally {
            COMBATANT$REPLACING.set(false);
        }
    }
}
