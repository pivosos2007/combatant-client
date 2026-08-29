/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import combatant.client.features.module.ModuleManager;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.clickgui.ClickGuiEditorScreen;
import combatant.client.features.gui.clickgui.ClickGuiPickerScreen;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.gui.preview.VisualPreviewRenderer;
import combatant.client.features.gui.preview.VisualPreviewRuntime;
import combatant.client.features.gui.preview.VisualPreviewScreen;
import combatant.client.features.gui.hud.nondraggable.impl.CustomBar;
import combatant.client.features.gui.hud.nondraggable.impl.DynamicIsland;
import combatant.client.features.module.modules.misc.ClickGui;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.renderer.Renderer2D;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {

    @Unique
    private static void combatant$extractCustomBarHeads(GuiGraphicsExtractor drawContext, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (ClientScreen.current() != null) return;

        CustomBar bar = CustomBar.get();
        if (bar == null || !bar.isHudBarEnabled() || !bar.isHotbarXpBarEnabled()) return;
        if (CustomBar.shouldUseJumpBar(mc) || !CustomBar.shouldUseLocator(mc, bar)) return;

        CombatantRenderSystem.ensureFrameContext();
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:hud:custom_bar_heads")) {
            Renderer2D.withDeferredLayer(Renderer2D.Deferred2DLayer.AFTER_VANILLA_GUI, () -> {
                CustomBar.renderLocatorHeads(drawContext, tickCounter, mc);
                Renderer2D.COLOR.render();
            });
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void combatant$closeClickGuiOnScreen(Screen screen, CallbackInfo ci) {
        if (!ModuleManager.isEnabled("clickgui")) {
            return;
        }

        if (ClickGuiRenderer.waitingForKey) {
            return;
        }

        if (screen instanceof ClickGuiScreen
                || screen instanceof ClickGuiPickerScreen
                || screen instanceof ClickGuiEditorScreen
                || screen instanceof VisualPreviewScreen) {
            return;
        }

        if (screen != null) {
            ClickGui.setSuppressScreenClose(true);
            try {
                ModuleManager.setEnabled("clickgui", false);
            } finally {
                ClickGui.setSuppressScreenClose(false);
            }
        }
    }

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"
            )
    )
    private void combatant$extractTopLayer(DeltaTracker tickCounter, boolean renderLevel, boolean renderScreens, CallbackInfo ci,
                                           @Local(ordinal = 0) GuiGraphicsExtractor drawContext) {
        if (drawContext == null) {
            return;
        }

        drawContext.nextStratum();
        combatant$extractCustomBarHeads(drawContext, tickCounter);
        VisualPreviewRenderer.renderTopLayer(drawContext, tickCounter.getGameTimeDeltaTicks());
        DynamicIsland.renderScreenOverlay(drawContext, tickCounter.getGameTimeDeltaTicks());
        if (!VisualPreviewRuntime.isActive()) {
            ClickGuiRenderer.renderTopLayer(drawContext, tickCounter.getGameTimeDeltaTicks());
        }
    }

}
