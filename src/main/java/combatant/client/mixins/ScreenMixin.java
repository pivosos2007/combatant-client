/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.config.MainConfig;
import combatant.client.features.gui.hud.nondraggable.impl.BetterButtons;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.NarratorBlocker;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private RenderPhaseScope combatant$screenPhaseScope;

    @Inject(method = "extractMenuBackgroundTexture", at = @At("HEAD"), cancellable = true)
    private static void combatant$replaceMenuBackground(GuiGraphicsExtractor context,
                                                        Identifier texture,
                                                        int x,
                                                        int y,
                                                        float u,
                                                        float v,
                                                        int width,
                                                        int height,
                                                        CallbackInfo ci) {
        if (texture == null || context == null) return;
        if (RuntimeGate.isPanic()) return;
        if (!Screen.MENU_BACKGROUND.equals(texture)) return;
        if (x != 0 || y != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (width != context.guiWidth() || height != context.guiHeight()) return;

        MainConfig cfg = MainConfig.get();
        if (cfg == null) return;
        String mode = cfg.getMenuBackgroundMode();
        if (mode == null || mode.equalsIgnoreCase("off")) return;

        boolean aurora = mode.equalsIgnoreCase("aurora");
        MenuBackgroundRenderer.render(mc, aurora);
        ci.cancel();
    }

    @Inject(method = "shouldRunNarration", at = @At("HEAD"), cancellable = true)
    private void combatant$disableNarratorUi(CallbackInfoReturnable<Boolean> cir) {
        if (!NarratorBlocker.isBlocked()) return;
        cir.setReturnValue(false);
    }

    @Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
    private void combatant$replaceScreenBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level != null) return;
        MainConfig cfg = MainConfig.get();
        String mode = (cfg != null) ? cfg.getMenuBackgroundMode() : "off";
        if (mode == null || mode.equalsIgnoreCase("off")) return;

        boolean aurora = mode.equalsIgnoreCase("aurora");
        MenuBackgroundRenderer.render(mc, aurora);
        ci.cancel();
    }

    @Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
    private void combatant$noRender$cancelDarkening(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        NoRender module = Modules.get(NoRender.class);
        Minecraft mc = Minecraft.getInstance();
        if (module != null && mc != null && mc.level != null && module.screenDarkeningDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTransparentBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$noRender$cancelInGameBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
        NoRender module = Modules.get(NoRender.class);
        Minecraft mc = Minecraft.getInstance();
        if (module != null && mc != null && mc.level != null && module.screenDarkeningDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true, require = 0)
    private void combatant$replaceTitlePanorama(GuiGraphicsExtractor context, float delta, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Screen self = (Screen) (Object) this;
        if (!(self instanceof TitleScreen)) return;

        MainConfig cfg = MainConfig.get();
        String mode = (cfg != null) ? cfg.getMenuBackgroundMode() : "off";
        if (mode == null || mode.equalsIgnoreCase("off")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        boolean aurora = mode.equalsIgnoreCase("aurora");
        MenuBackgroundRenderer.render(mc, aurora);
        ci.cancel();
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void combatant$beginButtonQueue(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BetterButtons.beginFrame(ctx);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void combatant$phaseScreenHead(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        String label = "2d:screen:" + self.getClass().getSimpleName();
        CombatantRenderSystem.ensureFrameContext();
        combatant$screenPhaseScope = CombatantRenderSystem.phase(RenderPhase.SCREEN, label);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"))
    private void combatant$phaseScreenTail(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        String label = "2d:screen:" + self.getClass().getSimpleName();
        if (combatant$screenPhaseScope != null) {
            combatant$screenPhaseScope.close();
            combatant$screenPhaseScope = null;
        }
    }
}


