/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.hud.nondraggable.impl.BetterButtons;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {

    @Inject(method = "enableScissor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"))
    private void combatant$pushScissor(GuiGraphicsExtractor context, CallbackInfo ci) {
        AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
        BetterButtons.pushScissorContext(self.getX(), self.getY(), self.getRight(), self.getBottom());
    }

    @Inject(
            method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;disableScissor()V")
    )
    private void combatant$popScissor(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        BetterButtons.popScissorContext();
    }
}




