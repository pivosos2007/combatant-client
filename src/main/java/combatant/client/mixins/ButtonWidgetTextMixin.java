/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.hud.nondraggable.impl.BetterButtons;

@Mixin(Button.Plain.class)
public abstract class ButtonWidgetTextMixin {

    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
    private void combatant$drawIcon(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BetterButtons buttons = BetterButtons.get();
        if (buttons == null || !buttons.useUiButtons()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && ClientScreen.current() instanceof MerchantScreen) return;
        AbstractWidget widget = (AbstractWidget) (Object) this;
        boolean hovered = widget.isMouseOver(mouseX, mouseY);
        float hover = BetterButtons.updateHover(widget, hovered, mouseX, mouseY);
        BetterButtons.enqueueButton(widget, widget.getMessage(), hover, widget.isActive(), widget.isFocused());
        if (hovered) {
            BetterButtons.captureTooltip(widget, mouseX, mouseY);
        }
        ci.cancel();
    }
}

