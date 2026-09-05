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
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.gui.hud.CooldownRender;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.HudDeferredGuiElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import combatant.client.util.pvp.CooldownRegistry;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.PvpState;
import combatant.client.util.pvp.client.CooldownsState;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin implements IGuiGraphics {
    @Final
    @Shadow
    private Matrix3x2fStack pose;

    @Final
    @Shadow
    public GuiRenderState guiRenderState;

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    @Override
    public void combatant$runUnscaled(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        double uiScale = ViewportContext.getUiScale();
        float ratio = (float) (uiScale / scale);
        pose.scale(ratio, ratio);
        try {
            task.run();
        } finally {
            pose.scale((float) (scale / uiScale), (float) (scale / uiScale));
        }
    }

    @Override
    public void combatant$withTransform(float tx, float ty, float angleRad, Runnable task) {
        pose.pushMatrix();
        try {
            pose.translate(tx, ty);
            pose.rotate(angleRad);
            task.run();
        } finally {
            pose.popMatrix();
        }
    }

    @Override
    public void combatant$addDeferredHudMarker(Renderer2D.Deferred2DLayer layer) {
        if (layer == null || guiRenderState == null) return;
        int width = Math.max(1, guiWidth());
        int height = Math.max(1, guiHeight());
        guiRenderState.addGlyphToCurrentLayer(new HudDeferredGuiElement(layer, new ScreenRectangle(0, 0, width, height)));
    }

    @Override
    public void combatant$drawItemBar(ItemStack stack, int x, int y) {
        combatant$invokeDrawItemBar(stack, x, y);
    }

    @Override
    public void combatant$drawCooldownProgress(ItemStack stack, int x, int y) {
        combatant$invokeDrawCooldownProgress(stack, x, y);
    }

    @Invoker("itemBar")
    public abstract void combatant$invokeDrawItemBar(ItemStack stack, int x, int y);

    @Invoker("itemCooldown")
    public abstract void combatant$invokeDrawCooldownProgress(ItemStack stack, int x, int y);

    @Unique
    private ItemStack combatant$tooltipStackContext = ItemStack.EMPTY;

    @Inject(
            method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD")
    )
    private void combatant$beginItemTooltipContext(net.minecraft.client.gui.Font font,
                                                   ItemStack stack,
                                                   int x, int y,
                                                   CallbackInfo ci) {
        combatant$tooltipStackContext = stack != null ? stack : ItemStack.EMPTY;
    }

    @Inject(
            method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("RETURN")
    )
    private void combatant$endItemTooltipContext(net.minecraft.client.gui.Font font,
                                                 ItemStack stack,
                                                 int x, int y,
                                                 CallbackInfo ci) {
        combatant$tooltipStackContext = ItemStack.EMPTY;
    }

    @Inject(method = "setTooltipForNextFrameInternal", at = @At("HEAD"), cancellable = true)
    private void combatant$captureTooltipGlobally(net.minecraft.client.gui.Font font,
                                                  java.util.List<ClientTooltipComponent> components,
                                                  int x, int y,
                                                  ClientTooltipPositioner positioner,
                                                  net.minecraft.resources.Identifier style,
                                                  boolean replaceExisting,
                                                  CallbackInfo ci) {
        ItemStack stack = combatant$tooltipStackContext;
        if ((stack == null || stack.isEmpty())) {
            ItemStack inferred = BetterTooltips.consumeLastTooltipStack();
            if (inferred != null && !inferred.isEmpty()) stack = inferred;
        }
        if (BetterTooltips.captureTooltipComponents(
                components,
                positioner,
                x, y,
                stack != null ? stack : ItemStack.EMPTY,
                (GuiGraphicsExtractor) (Object) this,
                replaceExisting
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "itemCooldown", at = @At("HEAD"), cancellable = true)
    private void combatant$renderCustomPvpCooldown(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        if (cooldowns == null || !cooldowns.isSystemEnabled()) return;

        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.CHORUS_FRUIT)) {
            if (!PvpState.isActive()) return;
            Float secondsLeft = PvpState.getSecondsLeft();
            if (secondsLeft == null || secondsLeft <= 0f) return;
            Float maxSeconds = PvpState.getMaxSeconds();
            float total = (maxSeconds == null || maxSeconds <= 0f) ? secondsLeft : maxSeconds;
            float progress = Mth.clamp(secondsLeft / total, 0f, 1f);

            Minecraft mc = Minecraft.getInstance();
            GuiGraphicsExtractor ctx = (GuiGraphicsExtractor) (Object) this;

            float secondsLeftValue = secondsLeft;
            float displaySeconds = secondsLeftValue >= 10f
                    ? (float) ((int) secondsLeftValue)
                    : Math.round(secondsLeftValue * 10f) / 10f;

            int top = y + Mth.floor(16.0F * (1.0F - progress));
            int bottom = top + Mth.ceil(16.0F * progress);

            int color = 0x80FF5656;
            ctx.fill(RenderPipelines.GUI, x, top, x + 16, bottom, color);

            CooldownRender.renderTime(
                    ctx, progress, x, y, displaySeconds
            );

            if (ClientScreen.current() == null) {
                ci.cancel();
            }
            return;
        }

        if (!cooldowns.shouldRenderSlots()) return;
        if (!CooldownRegistry.isTracked(stack.getItem())) return;

        ItemCooldownSnapshot snapshot = CooldownsState.MANAGER.snapshot(stack.getItem());
        if (!snapshot.visible()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor ctx = (GuiGraphicsExtractor) (Object) this;

        float progress = snapshot.cooling() ? snapshot.cooldownProgress() : snapshot.usesProgress();
        if (progress <= 0f) return;

        float secondsLeft = snapshot.cooling() ? snapshot.cooldownRemainingMs() / 1000.0f : snapshot.useWindowRemainingMs() / 1000.0f;
        float displaySeconds = snapshot.cooling()
                ? (secondsLeft >= 10 ? (float) ((int) secondsLeft) : Math.round(secondsLeft * 10f) / 10f)
                : 0f;

        int top = y + Mth.floor(16.0F * (1.0F - progress));
        int bottom = top + Mth.ceil(16.0F * progress);

        int color = snapshot.cooling()
                ? (CooldownsState.MANAGER.isInPvp() ? 0x80FFA500 : 0x8000A0FF)
                : 0x706D9DFF;

        ctx.fill(RenderPipelines.GUI, x, top, x + 16, bottom, color);

        if (snapshot.cooling()) {
            CooldownRender.renderTime(ctx, progress, x, y, displaySeconds);
        }

        if (ClientScreen.current() == null) {
            ci.cancel();
        }
    }
}
