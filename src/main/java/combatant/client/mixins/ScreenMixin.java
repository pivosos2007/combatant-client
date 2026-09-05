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
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.command.CommandManager;
import combatant.client.features.gui.hud.nondraggable.impl.BetterButtons;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.util.NarratorBlocker;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private RenderPhaseScope combatant$screenPhaseScope;

    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void combatant$runClientChatCommand(ClickEvent event,
                                                       Minecraft client,
                                                       Screen screen,
                                                       CallbackInfo ci) {
        if (!(event instanceof ClickEvent.RunCommand run)) return;
        String command = run.command();
        if (command == null || !command.startsWith("@")) return;
        if (CommandManager.handle(command)) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRunNarration", at = @At("HEAD"), cancellable = true)
    private void combatant$disableNarratorUi(CallbackInfoReturnable<Boolean> cir) {
        if (!NarratorBlocker.isBlocked()) return;
        cir.setReturnValue(false);
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

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void combatant$beginButtonQueue(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BetterTooltips.beginTooltipFrame();
        BetterTooltips.setDrawContext(ctx);
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

