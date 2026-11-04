/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.features.gui.hud.nondraggable.debug.DebugHudLineRegistry;
import combatant.client.features.gui.hud.nondraggable.impl.DebugHudReplacement;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void combatant$beginDebugHudReplacementFrame(GuiGraphicsExtractor context, CallbackInfo ci) {
        DebugHudReplacement.beginExtractFrame();
    }

    @Inject(method = "extractLines", at = @At("HEAD"), cancellable = true)
    private void combatant$captureDebugHudLines(GuiGraphicsExtractor context,
                                                List<String> lines,
                                                boolean leftAligned,
                                                int lineOffset,
                                                CallbackInfo ci) {
        if (!RuntimeGate.canRunHud() || RuntimeGate.isPanic()) return;
        DebugHudReplacement.captureLines(lines, leftAligned);
        ci.cancel();
    }

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
                    ordinal = 0
            ),
            index = 1
    )
    private List<String> combatant$addExtraDebugHudLines(List<String> vanilla) {
        return DebugHudLineRegistry.decorate(vanilla);
    }

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
                    ordinal = 1
            ),
            index = 1
    )
    private List<String> combatant$insertAvailableCapabilities(List<String> vanilla) {
        return DebugHudLineRegistry.decorateVanillaRight(vanilla);
    }
}
