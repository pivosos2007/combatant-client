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
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.security.BackdoorProtection;

import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public abstract class TranslationStorageMixin {

    @Inject(method = "loadFrom(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resources/language/ClientLanguage;", at = @At("HEAD"))
    private static void combatant$backdoor$resetLoadedTranslations(ResourceManager resourceManager, List<String> definitions, boolean rightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        BackdoorProtection.resetLoadedTranslations();
    }

    @WrapOperation(
            method = "appendFrom(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V")
    )
    private static void combatant$backdoor$captureTranslations(
            InputStream inputStream,
            BiConsumer<String, String> consumer,
            Operation<Void> original,
            @Local Resource resource
    ) {
        String packId = resource.sourcePackId();
        original.call(inputStream, (BiConsumer<String, String>) (key, value) -> {
            BackdoorProtection.registerTranslationKey(key, packId);
            consumer.accept(key, value);
        });
    }
}
