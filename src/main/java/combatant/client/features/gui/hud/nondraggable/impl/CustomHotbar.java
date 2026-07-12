/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;


import combatant.client.features.theme.Theme;
import combatant.client.config.values.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Themes;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.runtime.RuntimeGate;

import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 30)
public final class CustomHotbar extends AbstractHudElement {

    public static final CustomHotbar INSTANCE = new CustomHotbar();
    private static final int BG_ALPHA = 0xAA;
    private static final float RADIUS = 6.0f;
    private static final float HOTBAR_SOFTNESS = 0.0f;
    private static final float BLUR_QUALITY = 16.0f;
    private static final float DIVIDER_WIDTH_PX = 1.05f;
    private static final float DIVIDER_HEIGHT = 11.0f;
    private static final float SELECT_STROKE_PX = 1.45f;
    private static final float SELECT_STROKE_SOFTNESS = 0.85f;
    private static final float SELECT_FILL_ALPHA = 0.16f;
    private static final int ITEM_Y_OFFSET = 2;
    private static final int ITEM_Y_OFFSET_SELECTED = 0;
    private static final float SLOT_STEP = 20.0f;
    private static final float SELECT_SLOT_STEP = SLOT_STEP;
    private static final float SELECT_ANIMATION_MS = 145.0f;
    private static final float SELECT_ANIMATION_MIN_PROGRESS = 0.015f;
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static int animatedSelectedSlot = -1;
    private static float selectionStartSlot = 0.0f;
    private static float selectionTargetSlot = 0.0f;
    private static long selectionAnimStartMs = 0L;
    private final EnumValue<OffHandMode> offHandMode =
            new EnumValue<>("offhand_mode", OffHandMode.MERGED, OffHandMode.values());
    private final ModeValue colorMode =
            new ModeValue("hotbar_color_mode", "Custom", COLOR_THEME, COLOR_CUSTOM);
    private final RGBAColorValue bgColor =
            new RGBAColorValue("bg_color", "#7A191414");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("hotbar_bg_alpha", 167, 0, 255);
    private final RGBColorValue selectColor =
            new RGBColorValue("select_color", "#2F6277");
    private final RGBColorValue dividerColor =
            new RGBColorValue("divider_color", "#C13434");
    private final BooleanValue customSelectColor =
            new BooleanValue("custom_select_color", false);
    private final BooleanValue customDividerColor =
            new BooleanValue("custom_divider_color", false);
    private final NumberValue<Integer> accentAlpha =
            new NumberValue<>("accent_alpha", 255, 0, 255);
    private final ModeValue bgEffect =
            new ModeValue("bg_effect", "Glass", EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS);
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("blur_alpha", 255, 20, 255);
    private CustomHotbar() {
        super("vanilla_hotbar", "Hotbar", true);
    }

    public static CustomHotbar get() {
        return INSTANCE;
    }

    public static void renderBackground(Renderer2D r2d, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        float s = (float) mc.getWindow().getGuiScale();

        float i = mc.getWindow().getGuiScaledWidth() / 2f;
        float h = mc.getWindow().getGuiScaledHeight();

        int selected = getSelectedSlot(player);
        HotbarSelection selection = updateSelectionAnimation(selected);
        CustomHotbar bm = CustomHotbar.get();
        if (bm == null || !bm.isHudHotbarEnabled()) return;

        ItemStack offhand = player.getOffhandItem();
        boolean hasOffhand = !offhand.isEmpty();

        float baseY = h - 22f;

        boolean blur = shouldBlur(bm);
        boolean glass = shouldGlass(bm);
        int bgRgb = glass ? HudRenderUtil.glassBackgroundRgb() : bm.getHotbarBgRgb();
        int bgAlpha = bm != null ? bm.getHotbarBgAlpha() : BG_ALPHA;
        int bg = glass ? premultiply(HudRenderUtil.glassPanelBackground(1.0f)) : ColorMath.premultiplyAlpha(bgRgb, bgAlpha);
        int bgColor = bg;
        int accentAlpha = bm.getHotbarAccentAlpha();
        int dividerColor = ColorMath.premultiplyAlpha(bm.getHotbarDividerRgb(), accentAlpha);
        SelectionGradient selectionGradient = bm.getHotbarSelectionGradient(accentAlpha);

        if (glass) {
            if (!hasOffhand) {
                drawGlass((i - 90f) * s, baseY * s, 180f * s, 20f * s, RADIUS * s);
            } else if (getOffHandMode() == OffHandMode.MERGED) {
                drawGlass((i - 111f) * s, baseY * s, 201f * s, 20f * s, RADIUS * s);
            } else {
                drawGlass((i - 90f) * s, baseY * s, 180f * s, 20f * s, RADIUS * s);
                drawGlass((i - 112.5f) * s, baseY * s, 20f * s, 20f * s, RADIUS * s);
            }
        } else if (blur) {
            if (!hasOffhand) {
                drawBlur((i - 90f) * s, baseY * s, 180f * s, 20f * s, RADIUS * s, bgRgb);
            } else if (getOffHandMode() == OffHandMode.MERGED) {
                drawBlur((i - 111f) * s, baseY * s, 201f * s, 20f * s, RADIUS * s, bgRgb);
            } else {
                drawBlur((i - 90f) * s, baseY * s, 180f * s, 20f * s, RADIUS * s, bgRgb);
                drawBlur((i - 112.5f) * s, baseY * s, 20f * s, 20f * s, RADIUS * s, bgRgb);
            }
        }

        if (glass) {
            bgColor = premultiply(HudRenderUtil.glassPanelBackground(1.0f));
        }

        if (!hasOffhand) {
            drawBase(r2d, (i - 90f) * s, baseY * s, 180f * s, 20f * s, bgColor, s);
        } else {
            if (getOffHandMode() == OffHandMode.MERGED) {
                drawBase(r2d, (i - 111f) * s, baseY * s, 201f * s, 20f * s, bgColor, s);

                float divX = (i - 91f) * s;
                float divY = (baseY + (20f - DIVIDER_HEIGHT) * 0.5f) * s;
                r2d.quad(divX, divY, DIVIDER_WIDTH_PX, DIVIDER_HEIGHT * s, dividerColor);
            } else { // SEPARATELY
                drawBase(r2d, (i - 90f) * s, baseY * s, 180f * s, 20f * s, bgColor, s);
                drawBase(r2d, (i - 112.5f) * s, baseY * s, 20f * s, 20f * s, bgColor, s);
            }
        }

        float selX = (i - 88f + selection.visualSlot() * SELECT_SLOT_STEP) * s;
        float selY = (baseY + 1.5f) * s;
        drawSelectionStroke(
                r2d,
                selX,
                selY,
                17f * s,
                17f * s,
                s,
                selectionGradient.startArgb(),
                selectionGradient.endArgb(),
                selectionGradient.angleDeg()
        );
    }

    private static void drawBase(Renderer2D r2d, float x, float y, float w, float h, int argb, float scale) {
        if (RADIUS <= 0.0f) {
            r2d.quad(x, y, w, h, argb);
            return;
        }
        r2d.roundedRect(x, y, w, h, RADIUS * scale, HOTBAR_SOFTNESS * scale, argb);
    }

    private static void drawSelectionStroke(Renderer2D r2d,
                                            float x,
                                            float y,
                                            float w,
                                            float h,
                                            float scale,
                                            int startArgb,
                                            int endArgb,
                                            float gradientAngleDeg) {
        if (r2d == null) return;
        float radius = Math.max(3.0f * scale, Math.min(w, h) * 0.22f);
        float thickness = Math.max(1.15f, SELECT_STROKE_PX * Math.min(scale, 1.35f));
        float softness = SELECT_STROKE_SOFTNESS * Math.min(scale, 1.25f);
        int fillStart = HudRenderUtil.scaleAlpha(startArgb, SELECT_FILL_ALPHA);
        int fillEnd = HudRenderUtil.scaleAlpha(endArgb, SELECT_FILL_ALPHA * 0.82f);
        r2d.roundedRectGradient(x, y, w, h, radius, softness, fillStart, fillEnd, gradientAngleDeg);

        float t = (Util.getMillis() % 2200L) / 2200.0f;
        r2d.roundedRectStrokeAngularGradient(
                x,
                y,
                w,
                h,
                radius,
                softness,
                thickness,
                startArgb,
                endArgb,
                t
        );
        int halo = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(startArgb, endArgb, 0.5f), 0.22f);
        r2d.roundedRectStroke(x, y, w, h, radius, softness + 0.4f, thickness + 0.55f, halo);
    }

    public static void renderItems(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        CustomHotbar bm = CustomHotbar.get();
        if (bm == null || !bm.isHudHotbarEnabled()) return;

        int i = mc.getWindow().getGuiScaledWidth() / 2;
        int baseY = mc.getWindow().getGuiScaledHeight() - 22;

        int selected = getSelectedSlot(player);
        HotbarSelection selection = updateSelectionAnimation(selected);

        int seed = 1;

        // Offhand item with compact side positioning.
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int x = (getOffHandMode() == OffHandMode.MERGED) ? (i - 109) : (i - 111);
            renderHotbarItem(ctx, tickCounter, player, offhand, x, baseY + ITEM_Y_OFFSET, seed++);
        }

        // 9 slots
        for (int slot = 0; slot < 9; slot++) {
            double x = i - 90.0 + slot * SLOT_STEP + 2.0;
            double y = baseY + animatedItemYOffset(slot, selection);

            ItemStack stack = player.getInventory().getItem(slot);
            renderHotbarItem(ctx, tickCounter, player, stack, x, y, seed++);
        }
    }

    private static HotbarSelection updateSelectionAnimation(int selectedSlot) {
        long now = Util.getMillis();
        selectedSlot = clampSlot(selectedSlot);

        if (animatedSelectedSlot < 0) {
            animatedSelectedSlot = selectedSlot;
            selectionStartSlot = selectedSlot;
            selectionTargetSlot = selectedSlot;
            selectionAnimStartMs = now;
            return new HotbarSelection(selectedSlot, selectedSlot, selectedSlot, 1.0f);
        }

        float visualSlot = currentVisualSelectedSlot(now);
        if (selectedSlot != animatedSelectedSlot) {
            selectionStartSlot = visualSlot;
            selectionTargetSlot = selectedSlot;
            selectionAnimStartMs = now;
            animatedSelectedSlot = selectedSlot;
        }

        float progress = selectionProgress(now);
        float eased = AnimationUtility.easeOutCubic(progress);
        visualSlot = AnimationUtility.lerp(selectionStartSlot, selectionTargetSlot, eased);

        return new HotbarSelection(visualSlot, selectionStartSlot, selectionTargetSlot, eased);
    }

    private static float animatedItemYOffset(int slot, HotbarSelection selection) {
        float lift = selection.liftFor(slot);
        return AnimationUtility.lerp(ITEM_Y_OFFSET, ITEM_Y_OFFSET_SELECTED, lift);
    }

    private static float currentVisualSelectedSlot(long now) {
        float progress = selectionProgress(now);
        float eased = AnimationUtility.easeOutCubic(progress);
        return AnimationUtility.lerp(selectionStartSlot, selectionTargetSlot, eased);
    }

    private static float selectionProgress(long now) {
        float elapsed = Math.max(0.0f, (float) (now - selectionAnimStartMs));
        if (SELECT_ANIMATION_MS <= 0.0f) return 1.0f;
        return AnimationUtility.clamp01(elapsed / SELECT_ANIMATION_MS);
    }

    private static int clampSlot(int slot) {
        if (slot < 0) return 0;
        if (slot > 8) return 8;
        return slot;
    }

    private static void renderHotbarItem(GuiGraphicsExtractor ctx,
                                         DeltaTracker tickCounter,
                                         LocalPlayer player,
                                         ItemStack stack,
                                         double x, double y,
                                         int seed) {
        if (stack.isEmpty()) return;

        // vanilla bobbing animation (InGameHud#renderHotbarItem)
        float f = (float) stack.getPopTime() - tickCounter.getGameTimeDeltaPartialTick(false);
        if (f > 0.0F) {
            float g = 1.0F + f / 5.0F;
            Renderer2D.COLOR.itemPivot(
                    stack,
                    x,
                    y,
                    1.0f / g,
                    (g + 1.0f) / 2.0f,
                    8.0f,
                    12.0f,
                    seed,
                    Renderer2D.ITEM_OVERLAY_NONE,
                    null
            );
            Renderer2D.COLOR.itemOverlay(stack, x, y, 1.0f, Renderer2D.ITEM_OVERLAY_ALL, null);
        } else {
            Renderer2D.COLOR.item(stack, x, y, 1.0f, seed, Renderer2D.ITEM_OVERLAY_ALL, null);
        }
    }

    private static int getSelectedSlot(LocalPlayer player) {
        return ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
    }

    private static OffHandMode getOffHandMode() {
        CustomHotbar bm = CustomHotbar.get();
        if (bm == null) return OffHandMode.MERGED;
        return bm.getHotbarOffHandMode();
    }

    private static boolean shouldBlur(CustomHotbar bm) {
        return bm != null && bm.isHudHotbarEnabled() && bm.isHotbarBlurEnabled();
    }

    private static boolean shouldGlass(CustomHotbar bm) {
        return bm != null && bm.isHudHotbarEnabled() && bm.isHotbarGlassEnabled();
    }

    private static void drawBlur(float x, float y, float w, float h, float radius, int tintRgb) {
        CustomHotbar bm = CustomHotbar.get();
        float quality = BLUR_QUALITY;
        float brightness = 1.0f;
        float alpha = bm != null ? bm.getHotbarBlurAlpha() / 255f : 0.5f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private static void drawGlass(float x, float y, float w, float h, float radius) {
        CustomHotbar bm = CustomHotbar.get();
        float blurStrength = bm != null ? bm.getHotbarBlurAlpha() / 255f : 0.5f;
        float glassScale = RADIUS <= 0.0f ? 1.0f : radius / RADIUS;
        HudRenderUtil.drawLiquidGlass(x, y, w, h, radius, glassScale, true, blurStrength, 1.0f);
    }

    private static int withSelectorAlpha(int argb, int alpha) {
        int sourceAlpha = (argb >>> 24) & 0xFF;
        int outAlpha = Math.round(sourceAlpha * (clampAlpha(alpha) / 255.0f));
        return ColorMath.premultiplyAlpha(argb & 0x00FFFFFF, outAlpha);
    }

    private static int clampAlpha(int alpha) {
        if (alpha < 0) return 0;
        if (alpha > 255) return 255;
        return alpha;
    }

    private static int premultiply(int argb) {
        return ColorMath.premultiplyAlpha(argb & 0x00FFFFFF, (argb >>> 24) & 0xFF);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.mode(offHandMode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.color(bgColor).visibleWhen(() -> isCustomMode() && !isGlassEffect()));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(() -> isThemeMode() && !isGlassEffect()));
        defs.add(SettingDef.bool(customSelectColor));
        defs.add(SettingDef.colorNoAlpha(selectColor).visibleWhen(customSelectColor::get));
        defs.add(SettingDef.bool(customDividerColor));
        defs.add(SettingDef.colorNoAlpha(dividerColor).visibleWhen(customDividerColor::get));
        defs.add(SettingDef.number(accentAlpha));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
    }

    public boolean isHudHotbarEnabled() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    public OffHandMode getHotbarOffHandMode() {
        return offHandMode.get();
    }

    public int getHotbarBgArgb() {
        return ColorMath.premultiplyAlpha(getHotbarBgRgb(), getHotbarBgAlpha());
    }

    public int getHotbarBgRgb() {
        if (isThemeMode()) {
            return theme().windowBg() & 0x00FFFFFF;
        }
        return bgColor.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarBgAlpha() {
        if (isThemeMode()) {
            return bgAlpha.get();
        }
        return (bgColor.getArgb() >>> 24) & 0xFF;
    }

    public int getHotbarSelectRgb() {
        if (!customSelectColor.get()) {
            return theme().accent() & 0x00FFFFFF;
        }
        return selectColor.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarSelectEndRgb() {
        if (!customSelectColor.get()) {
            return HudRenderUtil.mixColor(theme().accentSoft(), theme().textPrimary(), 0.18f) & 0x00FFFFFF;
        }
        return HudRenderUtil.mixColor(selectColor.getArgb(), 0xFFFFFFFF, 0.24f) & 0x00FFFFFF;
    }

    public int getHotbarDividerRgb() {
        if (!customDividerColor.get()) {
            return HudRenderUtil.mixColor(theme().windowStroke(), theme().textMuted(), 0.32f) & 0x00FFFFFF;
        }
        return dividerColor.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarAccentAlpha() {
        int v = accentAlpha.get();
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public float getHotbarAccentAlphaFactor() {
        return getHotbarAccentAlpha() / 255f;
    }

    public boolean isHotbarBlurEnabled() {
        return !RuntimeGate.isPanic() && isBlurEffect();
    }

    public boolean isHotbarGlassEnabled() {
        return !RuntimeGate.isPanic() && isGlassEffect();
    }

    public int getHotbarBlurAlpha() {
        return blurAlpha.get();
    }

    private SelectionGradient getHotbarSelectionGradient(int alpha) {
        int a = clampAlpha(alpha);
        if (customSelectColor.get()) {
            int start = ColorMath.premultiplyAlpha(getHotbarSelectRgb(), a);
            int end = ColorMath.premultiplyAlpha(getHotbarSelectEndRgb(), a);
            return new SelectionGradient(start, end, 90.0f);
        }

        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec gradient = entry != null ? entry.cardGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new SelectionGradient(
                    withSelectorAlpha(gradient.start(), a),
                    withSelectorAlpha(gradient.end(), a),
                    gradient.angleDeg()
            );
        }

        int start = ColorMath.premultiplyAlpha(getHotbarSelectRgb(), a);
        int end = ColorMath.premultiplyAlpha(getHotbarSelectEndRgb(), a);
        return new SelectionGradient(start, end, 90.0f);
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isBlurEffect() {
        return EFFECT_BLUR.equals(bgEffect.get());
    }

    private boolean isGlassEffect() {
        return EFFECT_GLASS.equals(bgEffect.get());
    }

    public enum OffHandMode implements EnumValue.IdProvider {
        MERGED("merged"),
        SEPARATELY("separately");

        private final String id;

        OffHandMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private record HotbarSelection(float visualSlot, int startSlot, int targetSlot, float progress) {
        private HotbarSelection(float visualSlot, float startSlot, float targetSlot, float progress) {
            this(
                    visualSlot,
                    clampSlot(Math.round(startSlot)),
                    clampSlot(Math.round(targetSlot)),
                    AnimationUtility.clamp01(progress)
            );
        }

        private float liftFor(int slot) {
            if (startSlot == targetSlot) {
                return slot == targetSlot ? 1.0f : 0.0f;
            }
            if (slot == startSlot) {
                return 1.0f - progress;
            }
            if (slot == targetSlot) {
                return Math.max(progress, SELECT_ANIMATION_MIN_PROGRESS);
            }
            return 0.0f;
        }
    }

    private record SelectionGradient(int startArgb, int endArgb, float angleDeg) {
    }

}
