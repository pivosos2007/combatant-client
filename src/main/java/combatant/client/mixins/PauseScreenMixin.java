/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Shadow
    @Nullable
    private Button disconnectButton;

    @Unique
    private Button combatant$lastExitButton;
    @Unique
    private boolean combatant$disconnectBlocked;
    @Unique
    private boolean combatant$previousExitActive = true;

    @Inject(method = "init", at = @At("TAIL"))
    private void combatant$blockDisconnectOnInit(CallbackInfo ci) {
        combatant$syncDisconnectButton();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void combatant$blockDisconnectOnRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        combatant$syncDisconnectButton();
    }

    @Unique
    private void combatant$syncDisconnectButton() {
        Button button = disconnectButton;
        if (button == null) {
            combatant$lastExitButton = null;
            combatant$disconnectBlocked = false;
            return;
        }

        if (button != combatant$lastExitButton) {
            combatant$lastExitButton = button;
            combatant$disconnectBlocked = false;
            combatant$previousExitActive = button.active;
        }

        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        boolean shouldBlock = cooldowns != null && cooldowns.shouldBlockDisconnect();
        if (shouldBlock) {
            if (!combatant$disconnectBlocked) {
                combatant$previousExitActive = button.active;
            }
            combatant$disconnectBlocked = true;
            button.active = false;
            button.setTooltip(Tooltip.create(cooldowns.getDisconnectBlockTooltip()));
            return;
        }

        if (combatant$disconnectBlocked) {
            button.active = combatant$previousExitActive;
            button.setTooltip(null);
            combatant$disconnectBlocked = false;
        }
    }
}
