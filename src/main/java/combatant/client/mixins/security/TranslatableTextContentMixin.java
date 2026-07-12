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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.features.security.FilteredTranslatableTextContent;

@Mixin(TranslatableContents.class)
public abstract class TranslatableTextContentMixin {

    @WrapOperation(method = "decompose", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;"))
    private String combatant$backdoor$resolveNoFallback(Language language, String key, Operation<String> original) {
        if ((Object) this instanceof FilteredTranslatableTextContent filtered && filtered.getResolved() != null) {
            return filtered.getResolved();
        }
        return original.call(language, key);
    }

    @WrapOperation(method = "decompose", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private String combatant$backdoor$resolveFallback(Language language, String key, String fallback, Operation<String> original) {
        if ((Object) this instanceof FilteredTranslatableTextContent filtered && filtered.getResolved() != null) {
            return filtered.getResolved();
        }
        return original.call(language, key, fallback);
    }
}
