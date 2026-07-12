/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.BetterMinecraft;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookScreenMixin {

    @Final
    @Shadow
    private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void combatant$disableRecipeBook(CallbackInfo ci) {
        BetterMinecraft module = Modules.get(BetterMinecraft.class);
        if (module != null && module.isHideRecipeBookEnabled()) {
            ci.cancel();
        }
    }
}

