/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import combatant.client.config.SettingDef;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.RenderMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;

import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 50)
@UiScriptAsset("combatant:api/hud/static/custom_health_bar")
public final class CustomHealthBar extends AbstractHudElement {

    public static final CustomHealthBar INSTANCE = new CustomHealthBar();
private static final float HEALTH_BAR_WIDTH = 81f;
    private static final float HEALTH_BAR_HEIGHT = 9f;
    private static final float HEALTH_BAR_RADIUS = 5.0f;
    private static final float HEALTH_TEXT_SCALE = 0.24f;
    private static final int HEALTH_TEXT_OFFSET_X = 0;
    private static final int HEALTH_TEXT_OFFSET_Y = 0;
    private static final int HEALTH_BAR_X_OFFSET = 0;
    private static final int HEALTH_BAR_Y_OFFSET = 0;
    private static final float HEALTH_ANIM_SPEED = 10.0f;
    private static final String STYLE_TARGET_HUD = "TargetHud";
    private static final String STYLE_DYNAMIC = "Dynamic";
    private static float animatedHealth = -1.0f;
    private static float animatedAbsorb = -1.0f;
    private static long lastHealthAnimMs = -1L;
    private static int lastHealthPlayerId = Integer.MIN_VALUE;
    private final NumberValue<Integer> healthAlpha =
            new NumberValue<>("health_alpha", 255, 30, 255);
    private final ModeValue hotbarHealthBarStyle =
            new ModeValue("health_bar_style", "TargetHud", STYLE_TARGET_HUD, STYLE_DYNAMIC);
    private final NumberValue<Float> customBarRadius =
            new NumberValue<>("custom_bar_radius", 5.0f, 0.0f, 20.0f);
    private final NumberValue<Float> customTextScale =
            new NumberValue<>("custom_text_scale", 0.24f, 0.12f, 0.6f);
    private final NumberValue<Integer> customTextPadding =
            new NumberValue<>("custom_text_padding", 3, 0, 12);
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(CustomHealthBar.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    private CustomHealthBar() {
        super("vanilla_health", "Health", true);
    }

    public static CustomHealthBar get() {
        return INSTANCE;
    }

    public static boolean shouldRenderHealthBar(Minecraft mc, LocalPlayer player, float maxHealth) {
        if (player == null) return false;
        if (maxHealth <= 0.0f) return false;
        if (mc == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return false;

        CustomHealthBar bm = CustomHealthBar.get();
        if (bm == null || !bm.isHudHealthEnabled()) return false;
        return true;
    }

    public static HealthTextInfo renderHealthBarBackground(Renderer2D r2d,
                                                           Minecraft mc,
                                                           LocalPlayer player,
                                                           int x,
                                                           int y,
                                                           float maxHealth) {
        if (!shouldRenderHealthBar(mc, player, maxHealth)) return null;

        CustomHealthBar bm = CustomHealthBar.get();
        if (bm == null || !bm.isHudHealthEnabled()) return null;

        float resolvedMaxHealth = Math.max(1.0f, player.getMaxHealth());
        float resolvedHealth = Mth.clamp(player.getHealth(), 0.0f, resolvedMaxHealth);
        float resolvedAbsorb = Math.max(0.0f, player.getAbsorptionAmount());

        updateHealthAnim(player, resolvedHealth, resolvedAbsorb);

        float baseRatio = Mth.clamp(animatedHealth / resolvedMaxHealth, 0.0f, 1.0f);
        float absorbRatio = Mth.clamp(animatedAbsorb / resolvedMaxHealth, 0.0f, 1.0f);

        float scale = (float) mc.getWindow().getGuiScale();

        float barX = x + HEALTH_BAR_X_OFFSET;
        float barY = y + HEALTH_BAR_Y_OFFSET;

        drawBar(r2d, barX, barY, scale, baseRatio, absorbRatio, bm);

        String text = formatHealthText(resolvedHealth + resolvedAbsorb);
        if (text.isEmpty()) return new HealthTextInfo("", 0, 0, 0, bm.getTextScale());

        float framebufferScale = Math.max(1.0f, scale);
        TextRenderer tr = getHealthTextRenderer();
        float textScale = bm.getTextScale() * framebufferScale;
        tr.begin(textScale, false, false);
        int textW = (int) Math.ceil(tr.getWidth(text, true));
        int textH = (int) Math.ceil(tr.getHeight(true));
        tr.end();

        float barW = bm.getBarWidth() * framebufferScale;
        float barH = bm.getBarHeight() * framebufferScale;
        float pad = bm.getTextPadding() * framebufferScale;
        int tx = Math.round(barX * framebufferScale + barW - textW - pad)
                + Math.round(HEALTH_TEXT_OFFSET_X * framebufferScale);
        int ty = Math.round(barY * framebufferScale + (barH - textH) * 0.5f)
                + Math.round(HEALTH_TEXT_OFFSET_Y * framebufferScale);

        int color = applyHealthAlpha(bm.getTextArgb(), bm);

        return new HealthTextInfo(text, tx, ty, color, textScale);
    }

    /**
     * Renders HP digits in the currently active projection/batch. The health
     * bar itself is submitted in framebuffer-space, so the text must stay in
     * the same unscaled ordered batch; otherwise the text can end up in a
     * different GUI stratum/projection and disappear behind later HUD work.
     */
    public static void renderHealthBarText(HealthTextInfo info) {
        if (info == null || info.text.isEmpty()) return;
        TextRenderer tr = getHealthTextRenderer();
        tr.begin(info.scale, false, false);
        tr.render(info.text, info.x, info.y, new RenderColor(info.color), true);
        tr.end();
    }

    public static void renderHealthBarText(GuiGraphicsExtractor ctx, HealthTextInfo info) {
        if (ctx == null || info == null || info.text.isEmpty()) return;
        ViewportContext.beginUnscaled(ctx);
        renderHealthBarText(info);
        Renderer2D.flushBatch(Renderer2D.FlushReason.EXPLICIT);
        ViewportContext.end(ctx);
    }

    private static TextRenderer getHealthTextRenderer() {
        return Fonts.renderer("Iosevka");
    }

    private static int applyHealthAlpha(int argb, CustomHealthBar bm) {
        if (bm == null) return argb;
        int a = (argb >>> 24) & 0xFF;
        int na = (int) (a * bm.getHudHealthAlphaFactor());
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static void renderPreview(Renderer2D r2d, float x, float y, float scale, float healthRatio, float absorbRatio) {
        CustomHealthBar bm = CustomHealthBar.get();
        if (bm == null) return;
        float baseRatio = Mth.clamp(healthRatio, 0.0f, 1.0f);
        float absorb = Mth.clamp(absorbRatio, 0.0f, 1.0f);
        drawBar(r2d, x, y, scale, baseRatio, absorb, bm);
    }

    public static HealthTextInfo buildPreviewText(float barX, float barY, float scale, float value) {
        CustomHealthBar bm = CustomHealthBar.get();
        if (bm == null) return new HealthTextInfo("", 0, 0, 0, HEALTH_TEXT_SCALE * scale);
        String text = formatHealthText(value);
        float textScale = bm.getTextScale() * scale;
        if (text.isEmpty()) return new HealthTextInfo("", 0, 0, 0, textScale);

        TextRenderer tr = getHealthTextRenderer();
        tr.begin(textScale, false, false);
        int textW = (int) Math.ceil(tr.getWidth(text, true));
        int textH = (int) Math.ceil(tr.getHeight(true));
        tr.end();

        float barW = bm.getBarWidth() * scale;
        float barH = bm.getBarHeight() * scale;
        int pad = Math.round(bm.getTextPadding() * scale);
        int tx = Math.round(barX * scale + barW - textW - pad) + Math.round(HEALTH_TEXT_OFFSET_X * scale);
        int ty = Math.round(barY * scale + (barH - textH) * 0.5f) + Math.round(HEALTH_TEXT_OFFSET_Y * scale);

        int color = applyHealthAlpha(bm.getTextArgb(), bm);
        return new HealthTextInfo(text, tx, ty, color, textScale);
    }

    private static void drawBar(Renderer2D r2d, float barX, float barY, float scale,
                                float baseRatio, float absorbRatio, CustomHealthBar bm) {
        if (r2d == null || bm == null) return;

        float barW = bm.getBarWidth();
        float barH = bm.getBarHeight();
        float radius = bm.getBarRadius();

        float xFb = barX * scale;
        float yFb = barY * scale;
        float wFb = barW * scale;
        float hFb = barH * scale;
        float roundFb = radius * scale;

        float glassScale = HEALTH_BAR_RADIUS <= 0.0f ? 1.0f : roundFb / HEALTH_BAR_RADIUS;
        Renderer2D.COLOR.liquidGlassRect(
                xFb,
                yFb,
                wFb,
                hFb,
                roundFb,
                0xFFFFFFFF,
                bm.getHudHealthAlphaFactor(),
                Renderer2D.LiquidGlassPreset.HEALTH_BAR,
                glassScale,
                0.0f
        );
        bm.renderScriptedBar(r2d, xFb, yFb, wFb, hFb, roundFb, baseRatio, absorbRatio);
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static void updateHealthAnim(LocalPlayer player, float health, float absorb) {
        int id = player.getId();
        long now = Util.getMillis();

        if (id != lastHealthPlayerId || lastHealthAnimMs < 0L) {
            animatedHealth = health;
            animatedAbsorb = absorb;
            lastHealthAnimMs = now;
            lastHealthPlayerId = id;
            return;
        }

        float dt = (now - lastHealthAnimMs) / 1000.0f;
        lastHealthAnimMs = now;

        animatedHealth = RenderMath.smoothLerp(animatedHealth, health, dt, HEALTH_ANIM_SPEED);
        animatedAbsorb = RenderMath.smoothLerp(animatedAbsorb, absorb, dt, HEALTH_ANIM_SPEED);
    }

    private static String formatHealthText(float value) {
        if (value <= 0.0f) return "0";
        float rounded = Math.round(value * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.001f) {
            return String.valueOf(Math.round(rounded));
        }
        return String.valueOf(rounded);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(healthAlpha));
        defs.add(SettingDef.mode(hotbarHealthBarStyle));
        defs.add(SettingDef.number(customBarRadius));
        defs.add(SettingDef.number(customTextScale));
        defs.add(SettingDef.number(customTextPadding));
    }

    public boolean isHudHealthEnabled() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    public float getHudHealthAlphaFactor() {
        return getHudHealthAlpha() / 255f;
    }

    public int getHudHealthAlpha() {
        int v = healthAlpha.get();
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public float getBarWidth() {
        return HEALTH_BAR_WIDTH;
    }

    public float getBarHeight() {
        return HEALTH_BAR_HEIGHT;
    }

    public float getBarRadius() {
        return customBarRadius.get();
    }

    private float getTextScale() {
        return customTextScale.get();
    }

    private int getTextPadding() {
        return customTextPadding.get();
    }

    private int getTextArgb() {
        return 0xFF000000 | (resolveTextRgb() & 0x00FFFFFF);
    }

    private void renderScriptedBar(Renderer2D r2d,
                                   float x,
                                   float y,
                                   float width,
                                   float height,
                                   float radius,
                                   float baseRatio,
                                   float absorbRatio) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || width <= 0.0f || height <= 0.0f) return;

        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            scriptRuntime.reset();
        }

        UiScriptModule loaded = ensureModule(mc);
        if (loaded == null) return;

        LinkedHashMap<String, Object> props = buildBarProps(width, height, radius, baseRatio, absorbRatio);
        long signature = CachedUiScriptRuntime.signature(props);
        UiRuntime runtime = scriptRuntime.bake(
                moduleHandle,
                loaded,
                "custom_health_bar",
                signature,
                signature,
                signature,
                width,
                height,
                TextRenderer.get(),
                x,
                y,
                width,
                height,
                () -> props,
                null
        );
        if (runtime == null) return;
        runtime.render(new UiRenderContext(r2d, TextRenderer.get(), null, 0.0f, UiProjectionMode.CURRENT));
    }

    private LinkedHashMap<String, Object> buildBarProps(float width,
                                                                   float height,
                                                                   float radius,
                                                                   float baseRatio,
                                                                   float absorbRatio) {
        float phase = (float) (Util.getMillis() / 1000.0);

        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("width", width);
        props.put("height", height);
        props.put("radius", radius);
        props.put("healthRatio", Mth.clamp(baseRatio, 0.0f, 1.0f));
        props.put("absorbRatio", Mth.clamp(absorbRatio, 0.0f, 1.0f));
        props.put("alpha", getHudHealthAlphaFactor());
        props.put("phase", phase);
        props.put("colorMode", isDynamicHealthColorMode() ? "dynamic" : "theme");
        props.put("themeAccent", hex(0xFF000000 | (theme().accent() & 0x00FFFFFF)));
        props.put("themeAccentSoft", hex(0xFF000000 | (theme().accentSoft() & 0x00FFFFFF)));
        props.put("themeTextPrimary", hex(0xFF000000 | (theme().textPrimary() & 0x00FFFFFF)));
        return props;
    }

    private UiScriptModule ensureModule(Minecraft mc) {
        if (mc == null || mc.getResourceManager() == null) return null;
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private boolean isDynamicHealthColorMode() {
        return STYLE_DYNAMIC.equals(hotbarHealthBarStyle.get());
    }

    private int resolveTextRgb() {
        return theme().textPrimary() & 0x00FFFFFF;
    }

    public static final class HealthTextInfo {
        public final String text;
        public final int x;
        public final int y;
        public final int color;
        public final float scale;

        HealthTextInfo(String text, int x, int y, int color, float scale) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.scale = scale;
        }
    }
}
