/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.PvpOverlayEvent;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.draggable.impl.Potions;
import combatant.client.features.gui.hud.draggable.impl.Scoreboard;
import combatant.client.features.gui.hud.nondraggable.impl.SwapTooltip;
import combatant.client.features.gui.hud.nondraggable.impl.CustomBar;
import combatant.client.features.gui.hud.nondraggable.impl.CustomHealthBar;
import combatant.client.features.gui.hud.nondraggable.impl.CustomHotbar;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.MessageFilter;
import combatant.client.features.module.modules.visuals.Crosshair;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
// import combatant.client.render.engine.debug.RenderThread2DDebugRenderer; // disabled: debug probes are manual-only
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorUtil;
import combatant.client.util.item.TopEnchantUtil;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.TextJsonUtil;

@Mixin(Hud.class)
public abstract class GuiMixin {


    @Shadow
    private int toolHighlightTimer;
    @Shadow
    private net.minecraft.world.item.ItemStack lastToolHighlight;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void combatant$debug2dGuiStateProbe(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        // RenderThread2DDebugRenderer.extractGuiStateProbe(ctx);
    }

    @Unique
    private static void combatant$renderCustomBarOnly(GuiGraphicsExtractor ctx,
                                                      DeltaTracker tickCounter,
                                                      Minecraft mc,
                                                      CustomBar bar) {
        boolean useJump = CustomBar.shouldUseJumpBar(mc);
        boolean useLocator = !useJump && CustomBar.shouldUseLocator(mc, bar);
        boolean drawXp = !useJump && !useLocator;

        CombatantRenderSystem.ensureFrameContext();
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.HUD_MAIN, "2d:hud:custom_bar")) {
            Renderer2D.withDeferredLayer(COMBATANT$VANILLA_HUD_REPLACEMENT_LAYER, () -> {
                ViewportContext.beginUnscaled(ctx);
                Renderer2D.COLOR.begin();
                if (drawXp) {
                    CustomBar.renderXpBarBackground(Renderer2D.COLOR, mc);
                }
                if (useJump) {
                    CustomBar.renderJumpBarBackground(Renderer2D.COLOR, mc);
                }
                if (useLocator) {
                    CustomBar.renderLocatorBackground(Renderer2D.COLOR, mc);
                }
                Renderer2D.COLOR.render();
                ViewportContext.end(ctx);

                if (drawXp) {
                    CustomBar.renderXpBarText(ctx, mc);
                }
            });
            combatant$appendVanillaHudReplacementMarker(ctx);
        }
    }

    @Unique
    private static final Renderer2D.Deferred2DLayer COMBATANT$VANILLA_HUD_REPLACEMENT_LAYER =
            Renderer2D.Deferred2DLayer.HUD_AFTER_MISC_OVERLAYS;

    @Unique
    private static void combatant$appendVanillaHudReplacementMarker(GuiGraphicsExtractor ctx) {
        if (!(ctx instanceof IGuiGraphics graphics)) return;
        ctx.nextStratum();
        graphics.combatant$addDeferredHudMarker(COMBATANT$VANILLA_HUD_REPLACEMENT_LAYER);
        ctx.nextStratum();
    }

    @Unique
    private static int argbFrom(float[] rgba) {
        if (rgba == null || rgba.length < 3) return 0xFFFFFFFF;
        float r = rgba[0], g = rgba[1], b = rgba[2];
        float a = rgba.length > 3 ? rgba[3] : 1f;
        int ai = (int) (a * 255) & 0xFF;
        int ri = (int) (r * 255) & 0xFF;
        int gi = (int) (g * 255) & 0xFF;
        int bi = (int) (b * 255) & 0xFF;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    @Inject(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$hideScoreboardSidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        NoRender noRender = Modules.get(NoRender.class);
        if ((noRender != null && noRender.hideScoreboard()) || Scoreboard.shouldReplaceVanilla()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$filterOverlayMessage(net.minecraft.network.chat.Component message, boolean tinted, CallbackInfo ci) {
        if (DebugLog.serverOnly()) {
            if (message == null) {
                // DebugLog.server("HUD overlay message: tinted=%s raw=null", tinted);
            } else {
                String raw = message.getString();
                String json = TextJsonUtil.toJson(message);
                // DebugLog.server("HUD overlay message: tinted=%s raw=\"%s\" json=%s", tinted, raw, json);
            }
        }
        if (Events.BUS.hasListeners(PvpOverlayEvent.class)) {
            Events.BUS.post(new PvpOverlayEvent(message, tinted));
        }
        if (RuntimeGate.isPanic()) return;
        MessageFilter messageFilter = Modules.get(MessageFilter.class);
        if (messageFilter != null && messageFilter.shouldHideHud(message)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$hideVanillaPotions(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (!DraggableHudElementRegistry.isEnabled(Potions.class)) return;
        if (mc.player.getActiveEffects().isEmpty()) return;
        ci.cancel();
    }


    @Inject(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$replaceCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Crosshair crosshair = Modules.get(Crosshair.class);
        if (crosshair != null && crosshair.shouldHideVanilla()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "extractVignette(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$killVignette(GuiGraphicsExtractor context, Entity entity, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        // Completely remove vanilla vignette post-processing
        ci.cancel();
    }

    @Inject(
            method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$customHotbar(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        CustomHotbar hotbar = CustomHotbar.get();
        CustomBar bar = CustomBar.get();
        if (hotbar == null && bar == null) return;

        boolean hotbarEnabled = hotbar != null && hotbar.isHudHotbarEnabled();
        boolean barEnabled = bar != null && bar.isHudBarEnabled() && bar.isHotbarXpBarEnabled();
        if (!hotbarEnabled && !barEnabled) return;
        if (!hotbarEnabled) return;

        boolean useJump = barEnabled && CustomBar.shouldUseJumpBar(mc);
        boolean useLocator = barEnabled && !useJump && CustomBar.shouldUseLocator(mc, bar);
        boolean drawXp = barEnabled && !useJump && !useLocator;

        CombatantRenderSystem.ensureFrameContext();
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.HUD_MAIN, "2d:hud:custom_hotbar")) {
            Renderer2D.withDeferredLayer(COMBATANT$VANILLA_HUD_REPLACEMENT_LAYER, () -> {
                ViewportContext.beginUnscaled(ctx);
                Renderer2D.COLOR.begin();
                if (hotbarEnabled) {
                    CustomHotbar.renderBackground(Renderer2D.COLOR, mc);
                }
                if (drawXp) {
                    CustomBar.renderXpBarBackground(Renderer2D.COLOR, mc);
                }
                if (barEnabled && useJump) {
                    CustomBar.renderJumpBarBackground(Renderer2D.COLOR, mc);
                }
                if (barEnabled && useLocator) {
                    CustomBar.renderLocatorBackground(Renderer2D.COLOR, mc);
                }
                Renderer2D.COLOR.render();
                ViewportContext.end(ctx);

                if (hotbarEnabled) {
                    CustomHotbar.renderItems(ctx, tickCounter, mc);
                }
            });
            combatant$appendVanillaHudReplacementMarker(ctx);

            if (drawXp) {
                CustomBar.renderXpBarText(ctx, mc);
            }
            if (hotbarEnabled) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void combatant$customBarAfterVanillaHotbar(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        CustomHotbar hotbar = CustomHotbar.get();
        if (hotbar != null && hotbar.isHudHotbarEnabled()) return;

        CustomBar bar = CustomBar.get();
        if (bar == null || !bar.isHudBarEnabled() || !bar.isHotbarXpBarEnabled()) return;

        combatant$renderCustomBarOnly(ctx, tickCounter, mc, bar);
    }

    @Inject(
            method = "extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$disableVanillaPortalOverlay(GuiGraphicsExtractor context, float nauseaStrength, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        ci.cancel();
    }

    @Inject(
            method = "extractSleepOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$disableVanillaSleepOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        ci.cancel();
    }

    @Inject(
            method = "extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$renderHealthBar(GuiGraphicsExtractor context,
                                           Player player,
                                           int x, int y, int lines,
                                           int regeneratingHeartIndex, float maxHealth,
                                           int lastHealth, int health,
                                           int absorption, boolean blinking,
                                           CallbackInfo ci) {
        if (!RuntimeGate.canRunHud()) return;
        if (!(player instanceof LocalPlayer clientPlayer)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        if (!CustomHealthBar.shouldRenderHealthBar(mc, clientPlayer, maxHealth)) return;

        CombatantRenderSystem.ensureFrameContext();
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.HUD_MAIN, "2d:hud:custom_health")) {
            Renderer2D.withDeferredLayer(COMBATANT$VANILLA_HUD_REPLACEMENT_LAYER, () -> {
                ViewportContext.beginUnscaled(context);
                Renderer2D.COLOR.begin();
                CustomHealthBar.HealthTextInfo textInfo =
                        CustomHealthBar.renderHealthBarBackground(
                                Renderer2D.COLOR,
                                mc,
                                clientPlayer,
                                x,
                                y,
                                maxHealth
                        );
                CustomHealthBar.renderHealthBarText(textInfo);
                Renderer2D.COLOR.render();
                ViewportContext.end(context);
            });
            combatant$appendVanillaHudReplacementMarker(context);
            ci.cancel();
        }
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void combatant$colorizeHeldItemTooltip(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (!RuntimeGate.canRunHud()) return;
        SwapTooltip swapTooltip = SwapTooltip.get();
        if (swapTooltip == null || !swapTooltip.isEnabled()) {
            return;
        }
        if (this.toolHighlightTimer <= 0 || this.lastToolHighlight == null || this.lastToolHighlight.isEmpty()) {
            SwapTooltip.clear();
            return;
        }

        MutableComponent name = Component.empty().append(this.lastToolHighlight.getHoverName());
        if (this.lastToolHighlight.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            name.withStyle(net.minecraft.ChatFormatting.ITALIC);
        }

        int illegalColor = IllegalItemUtil.illegalColor();
        int topColor = TopEnchantUtil.topColor();
        int rarityColor = argbFrom(RarityColorUtil.INSTANCE.getRarityColor(this.lastToolHighlight));
        boolean hasIllegal = IllegalItemUtil.isIllegal(this.lastToolHighlight);
        boolean hasTop = !hasIllegal && TopEnchantUtil.hasTopEnchant(this.lastToolHighlight);
        int nameColor = hasIllegal ? illegalColor : (hasTop ? topColor : rarityColor);
        int alpha = (int) ((float) this.toolHighlightTimer * 256.0F / 10.0F);
        if (alpha > 255) alpha = 255;
        if (alpha <= 0) {
            SwapTooltip.clear();
            return;
        }
        float fade = alpha / 255f;
        int argb = 0xFF000000 | (nameColor & 0x00FFFFFF);
        SwapTooltip.capture(
                name.getString(),
                argb,
                fade,
                this.minecraft.gameMode.canHurtPlayer()
        );
        ci.cancel();
    }

    @Redirect(
            method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"
            )
    )
    private float combatant$ignoreAbsorptionForLayout(Player player) {
        if (!RuntimeGate.canRunHud()) {
            return player.getAbsorptionAmount();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && player instanceof LocalPlayer clientPlayer) {
            float actualMaxHealth = (float) player.getAttributeValue(Attributes.MAX_HEALTH);
            if (CustomHealthBar.shouldRenderHealthBar(mc, clientPlayer, actualMaxHealth)) {
                return 0.0f;
            }
        }
        return player.getAbsorptionAmount();
    }

    @Redirect(
            method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"
            )
    )
    private double combatant$clampMaxHealthForLayout(Player player, Holder<Attribute> attribute) {
        if (!RuntimeGate.canRunHud()) {
            return player.getAttributes().getValue(attribute);
        }
        if (attribute == Attributes.MAX_HEALTH) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && player instanceof LocalPlayer clientPlayer) {
                float actualMaxHealth = (float) player.getAttributes().getValue(attribute);
                if (CustomHealthBar.shouldRenderHealthBar(mc, clientPlayer, actualMaxHealth)) {
                    return 20.0;
                }
            }
        }
        return player.getAttributes().getValue(attribute);
    }
}
