/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.config.MainConfig;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.mainmenu.CombatantMainMenuScreen;
import combatant.client.runtime.RuntimeGate;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void combatant$replaceTitleScreen(CallbackInfo ci) {
        if (!RuntimeGate.canRunRender()) return;
        if (!MainConfig.get().isCombatantMainMenuEnabled()) return;
        if (CombatantMainMenuScreen.shouldUseVanillaTitleScreen()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (ClientScreen.current() instanceof CombatantMainMenuScreen) return;
        ClientScreen.show(mc, new CombatantMainMenuScreen());
        ci.cancel();
    }
}




