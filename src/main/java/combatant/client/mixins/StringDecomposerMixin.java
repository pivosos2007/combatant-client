/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.modules.misc.NameProtect;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringDecomposer.class)
public abstract class StringDecomposerMixin {

    @Unique
    private static final ThreadLocal<Boolean> COMBATANT$REPLACING = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void combatant$nameProtectPlain(String text,
                                                    Style style,
                                                    FormattedCharSink sink,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (COMBATANT$REPLACING.get()) return;
        String replaced = NameProtect.applyDisplay(text);
        if (replaced == text) return;
        COMBATANT$REPLACING.set(true);
        try {
            cir.setReturnValue(StringDecomposer.iterate(replaced, style, sink));
        } finally {
            COMBATANT$REPLACING.set(false);
        }
    }

    @Inject(
            method = "iterateBackwards(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void combatant$nameProtectBackwards(String text,
                                                        Style style,
                                                        FormattedCharSink sink,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (COMBATANT$REPLACING.get()) return;
        String replaced = NameProtect.applyDisplay(text);
        if (replaced == text) return;
        COMBATANT$REPLACING.set(true);
        try {
            cir.setReturnValue(StringDecomposer.iterateBackwards(replaced, style, sink));
        } finally {
            COMBATANT$REPLACING.set(false);
        }
    }

    @Inject(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void combatant$nameProtectFormatted(String text,
                                                        int offset,
                                                        Style startStyle,
                                                        Style resetStyle,
                                                        FormattedCharSink sink,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (offset != 0 || COMBATANT$REPLACING.get()) return;
        String replaced = NameProtect.applyDisplay(text);
        if (replaced == text) return;

        COMBATANT$REPLACING.set(true);
        try {
            cir.setReturnValue(StringDecomposer.iterateFormatted(replaced, 0, startStyle, resetStyle, sink));
        } finally {
            COMBATANT$REPLACING.set(false);
        }
    }
}
