/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.chat.BetterChatRenderer;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;

import java.util.Map;

//todo Description
@HudElementInfo(
        id = "better_chat",
        displayName = "Better Chat",
        enabledByDefault = true,
        order = 40
)
public final class BetterChat extends DraggableHudElement {

    {
        defaultLayout(16.0f, 485.12f);
    }


    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Double> widthRatio = num("width_ratio", 0.54, 0.2, 0.8);
    private final NumberValue<Double> fontScale = num("font_scale", 1.36, 0.6, 1.4);
    private final NumberValue<Integer> maxLines = num("lines", 12, 4, 20);
    private final NumberValue<Double> fadeSeconds = num("fade_seconds", 14.98, 3.0, 30.0);
    private final NumberValue<Integer> liquidGlassAlpha = num("liquid_glass_alpha", 230, 0, 255);
    private final BooleanValue hideVanilla = bool("hide_vanilla", true);
    private final BooleanValue stackDuplicates = bool("stack_duplicates", true);
    private final BooleanValue antiSpam = bool("anti_spam", true);
    private final BooleanValue passwordPrivacy = bool("password_privacy", true);
    private final KeyBindValue passwordRevealBindValue =
            visibleWhen(bind("password_reveal_bind", "LEFT_CTRL+LEFT_SHIFT+H", BindMode.PRESS), passwordPrivacy::get);
    private final FunctionBindSetting passwordRevealAction =
            new FunctionBindSetting("password_reveal_bind", passwordRevealBindValue, BindMode.PRESS);
    private final BooleanValue historyEnabled = bool("history_enabled", true);
    private final NumberValue<Integer> historyLimit = num("history_limit", 32000, 1000, 32000);
    private final BooleanValue timestampsEnabled = bool("timestamps_enabled", true);
    private final RGBColorValue timestampColor =
            visibleWhen(colorNoAlpha("timestamp_color", "#C6D2CD"), timestampsEnabled::get);
    private final BooleanMapValue timestampToggles =
            visibleWhen(group("timestamp_toggles", "timestamp_toggles", Map.of(
                    "hover_date", true,
                    "hover_unix", false,
                    "active_only", false,
                    "with_seconds", false,
                    "with_brackets", true
            )), timestampsEnabled::get);

    // Internal defaults. These remain enabled but are no longer exposed as noisy HUD settings.
    private final BooleanValue cacheItemHovers =
            new BooleanValue("cache_item_hovers", true);
    private final BooleanValue cacheEntityHovers =
            new BooleanValue("cache_entity_hovers", true);
    private final NumberValue<Integer> hoverCacheSize =
            new NumberValue<>("hover_cache_size", 512, 64, 4096);

    public static BetterChat get() {
        return DraggableHudElementRegistry.get(BetterChat.class);
    }

    public static boolean isActive() {
        return RuntimeGate.canRunHud() && DraggableHudElementRegistry.isEnabled(BetterChat.class);
    }

    @Override
    protected void onLoaded() {
        // Register the runtime action after config load so the loaded combo is authoritative.
        passwordRevealAction.setParent(this);
    }

    public boolean consumePasswordRevealToggle() {
        return passwordRevealAction.isPressedAllowScreen();
    }

    public boolean isPasswordRevealBindHeld() {
        return passwordRevealAction.isHeldForHud();
    }

    public float widthRatio() {
        return widthRatio.get().floatValue();
    }

    public float fontScale() {
        return fontScale.get().floatValue();
    }

    public int maxLines() {
        return maxLines.get();
    }

    public float fadeSeconds() {
        return fadeSeconds.get().floatValue();
    }

    public int liquidGlassAlpha() {
        return liquidGlassAlpha.get();
    }

    public float liquidGlassAlphaFactor() {
        return Math.max(0f, Math.min(1f, liquidGlassAlpha.get() / 255f));
    }

    public boolean hideVanilla() {
        return hideVanilla.get();
    }

    public boolean stackDuplicates() {
        return stackDuplicates.get();
    }

    public boolean antiSpam() {
        return antiSpam.get();
    }

    public boolean passwordPrivacy() {
        return passwordPrivacy.get();
    }

    public boolean historyEnabled() {
        return historyEnabled.get();
    }

    public int historyLimit() {
        return historyLimit.get();
    }

    public boolean timestampEnabled() {
        return timestampsEnabled.get();
    }

    public int timestampColor() {
        return timestampColor.getArgb();
    }

    public BooleanMapValue timestampToggles() {
        return timestampToggles;
    }

    public boolean timestampSeconds() {
        return timestampToggles.get("with_seconds");
    }

    public boolean timestampBrackets() {
        return timestampToggles.get("with_brackets");
    }

    public boolean cacheItems() {
        return cacheItemHovers.get();
    }

    public boolean cacheEntities() {
        return cacheEntityHovers.get();
    }

    public int cacheLimit() {
        return hoverCacheSize.get();
    }

    public boolean gradientEnabled() {
        return false;
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float baseScale = HudScale.scale(screenW, screenH);
        float height = Math.max(140f * baseScale, BetterChatRenderer.getLastHeight());
        this.x = 16f * baseScale;
        this.y = Math.max(16f * baseScale, screenH - height - 16f * baseScale);
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        BetterChatRenderer.renderEngine(renderer, textRenderer, ctx, screenW, x, y);
        float w = BetterChatRenderer.getLastWidth();
        float h = BetterChatRenderer.getLastHeight();
        this.width = Math.max(0f, w);
        this.height = Math.max(0f, h);
    }

    @Override
    public boolean isMouseOverInteractive(float mx, float my) {
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;
        return BetterChatRenderer.isInteractive(mx, my);
    }

    @Override
    public boolean contains(float mx, float my) {
        return BetterChatRenderer.contains(mx, my);
    }

    @Override
    public boolean onMouseClicked(float mx, float my, int button) {
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;
        return isMouseOverInteractive(mx, my);
    }
}
