/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;


import combatant.client.features.theme.Theme;
import combatant.client.features.account.AccountConfig;
import combatant.client.features.account.AccountEntry;
import combatant.client.features.account.SkinManager;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import combatant.client.config.MainConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.animation.AnimatedClockText;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CombatantMainMenuScreen extends Screen {
    private static final float MENU_SCALE = 1.22f;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);

    private static final float BUTTON_SIZE = 46f * MENU_SCALE;
    private static final float BUTTON_RADIUS = BUTTON_SIZE * 0.5f;
    private static final float BUTTON_SPACING = 18f * MENU_SCALE;
    private static final float BUTTON_HOVER_SCALE = 1.09f;
    private static final float BUTTON_SHADOW = 18f * MENU_SCALE;
    private static final float BUTTON_SOFTNESS = 1.0f;
    private static final String[] BUTTON_ICONS = {"a", "b", "x", "", "s", "i"};
    private static final String[] BUTTON_SVGS = {null, null, null, "folder-pen", null, null};

    private static final long MENU_APPEAR_DURATION = 800L;
    private static final float TIME_FONT = 4.95f * MENU_SCALE;
    private static final float DATE_FONT = 0.94f * MENU_SCALE;
    private static final float ICON_FONT = 1.52f * MENU_SCALE;
    private static final float ICON_Y_OFFSET = -0.75f * MENU_SCALE;
    private static final float AUTH_WARNING_W = 250f * MENU_SCALE;
    private static final float AUTH_WARNING_H = 42f * MENU_SCALE;
    private static final float AUTH_WARNING_R = 8f * MENU_SCALE;
    private static final float AUTH_WARNING_HEAD = 26f * MENU_SCALE;
    private static final long AUTH_WARNING_PROGRESS_MS = 3200L;
    private static volatile boolean forceVanillaTitleScreen;

    private final float[] buttonScales = new float[BUTTON_ICONS.length];
    private final float[] buttonHoverProgress = new float[BUTTON_ICONS.length];
    private final AnimatedClockText clockText = new AnimatedClockText();

    private boolean initialized;
    private long openTime;
    private long lastRenderTime;
    private int hoveredButton = -1;
    private float quitHoverProgress;
    private boolean authWarningHovered;
    private float authWarningHoverProgress;
    private float fixedWidth;
    private float fixedHeight;
    private float renderScale;

    public CombatantMainMenuScreen() {
        super(Component.literal("Combatant"));
        for (int i = 0; i < buttonScales.length; i++) {
            buttonScales[i] = 1f;
            buttonHoverProgress[i] = 0f;
        }
    }

    public static boolean shouldUseVanillaTitleScreen() {
        if (forceVanillaTitleScreen) return true;
        MainConfig cfg = MainConfig.get();
        String mode = cfg != null ? cfg.getMenuBackgroundMode() : null;
        return mode != null && mode.equalsIgnoreCase("off");
    }

    private static float easeOutCubic(float x) {
        return 1f - (float) Math.pow(1f - x, 3f);
    }

    private static float easeOutQuart(float x) {
        return 1f - (float) Math.pow(1f - x, 4f);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    protected void init() {
        initialized = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (RuntimeGate.isPanic()) {
            if (minecraft != null) ClientScreen.show(minecraft, null);
            return;
        }
        if (shouldUseVanillaTitleScreen()) {
            switchToVanillaTitleScreen(null);
            return;
        }

        long now = Util.getMillis();
        if (!initialized) {
            initialized = true;
            openTime = now;
            lastRenderTime = now;
        }

        float deltaTime = Mth.clamp((now - lastRenderTime) / 1000f, 0f, 0.1f);
        lastRenderTime = now;
        updateUiMetrics();

        float menuProgress = easeOutQuart(getMenuProgress(now));
        float fixedMouseX = toFixedX(mouseX);
        float fixedMouseY = toFixedY(mouseY);

        hoveredButton = canInteract(menuProgress) ? getHoveredButton(fixedMouseX, fixedMouseY, menuProgress) : -1;
        authWarningHovered = canInteract(menuProgress) && getAuthWarningAccount() != null && authWarningBounds(menuProgress).contains(fixedMouseX, fixedMouseY);
        updateButtonAnimations(deltaTime);

        try {
            renderShaderBackground(context);
            ViewportContext.beginUnscaledLogical(context);
            Renderer2D.COLOR.begin();
            drawDimmer();
            if (menuProgress > 0.01f) {
                renderTime(menuProgress);
                renderButtons(menuProgress);
                renderAuthWarning(context, menuProgress, now);
            }
            Renderer2D.COLOR.render();
        } catch (Throwable t) {
            switchToVanillaTitleScreen(t);
        } finally {
            try {
                ViewportContext.end(context);
            } catch (Throwable t) {
                DebugLog.errorOnce("main_menu_viewport_end", "Main menu viewport end failed", t);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;

        updateUiMetrics();
        float menuProgress = easeOutQuart(getMenuProgress(Util.getMillis()));
        int index = canInteract(menuProgress)
                ? getHoveredButton(toFixedX((float) click.x()), toFixedY((float) click.y()), menuProgress)
                : -1;
        AccountEntry warningAccount = getAuthWarningAccount();
        if (warningAccount != null && minecraft != null && canInteract(menuProgress)
                && authWarningBounds(menuProgress).contains(toFixedX((float) click.x()), toFixedY((float) click.y()))) {
            ClientScreen.show(minecraft, new CombatantAltManagerScreen(this, true));
            return true;
        }
        if (index >= 0) {
            handleButton(index);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return input.key() == 256 || super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderShaderBackground(GuiGraphicsExtractor context) {
        Minecraft mc = this.minecraft;
        if (mc == null) return;

        MainConfig cfg = MainConfig.get();
        String mode = cfg != null ? cfg.getMenuBackgroundMode() : "off";
        if (mode == null || mode.equalsIgnoreCase("off")) {
            switchToVanillaTitleScreen(null);
            return;
        }
        MenuBackgroundRenderer.render(mc, mode.equalsIgnoreCase("aurora"));
    }

    private void switchToVanillaTitleScreen(Throwable t) {
        forceVanillaTitleScreen = true;
        if (t != null) {
            DebugLog.errorOnce("main_menu_vanilla_fallback", "Main menu failed, switching to vanilla title screen", t);
        } else {
            DebugLog.warnOnce("main_menu_vanilla_fallback", "Main menu switched to vanilla title screen");
        }
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc != null && !(ClientScreen.current() instanceof TitleScreen)) {
            ClientScreen.show(mc, new TitleScreen(false));
        }
    }

    private void drawDimmer() {
        Themes.Theme theme = Theme.theme();
        int base = HudRenderUtil.mixColor(theme.windowBg(), 0xFF000000, 0.46f);
        int soft = withAlpha(base, 150);
        int deep = withAlpha(HudRenderUtil.mixColor(theme.windowBg(), 0xFF000000, 0.72f), 195);
        Renderer2D.COLOR.quad(0, 0, fixedWidth, fixedHeight, soft, soft, deep, deep);
    }

    private void renderTime(float opacity) {
        float centerX = fixedWidth * 0.5f;
        float slideOffset = (1f - opacity) * 40f * MENU_SCALE;
        float centerY = fixedHeight * 0.5f - 55f * MENU_SCALE + slideOffset;

        long nowMs = Util.getMillis();
        MainConfig cfg = MainConfig.get();
        boolean showSeconds = cfg != null && cfg.isMenuClockShowSeconds();
        clockText.update(LocalTime.now(), showSeconds, nowMs);

        String dateText = LocalDate.now().format(DATE_FORMAT);

        TextRenderer timeRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer dateRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, timeRenderer);

        int timeColor = withAlpha(0xFFFFFF, Math.round(opacity * 255f));
        int dateColor = withAlpha(0xFFFFFF, Math.round(opacity * 200f));

        float timeHeight = clockText.height(timeRenderer, TIME_FONT);
        clockText.renderLiquidGlassCentered(timeRenderer, centerX, centerY - timeHeight * 0.5f, TIME_FONT, timeColor, nowMs);
        drawCenteredText(dateRenderer, dateText, centerX, centerY + timeHeight * 0.5f + 4f * MENU_SCALE, DATE_FONT, dateColor);
    }

    private void renderButtons(float opacity) {
        float totalWidth = BUTTON_SIZE * BUTTON_ICONS.length + BUTTON_SPACING * (BUTTON_ICONS.length - 1);
        float startX = (fixedWidth - totalWidth) * 0.5f;
        float slideOffset = (1f - opacity) * 60f * MENU_SCALE;
        float y = fixedHeight * 0.5f + 34f * MENU_SCALE + slideOffset;

        for (int i = 0; i < BUTTON_ICONS.length; i++) {
            float delay = i * 0.12f;
            float progress = Mth.clamp((opacity - delay) / (1f - delay * 0.5f), 0f, 1f);
            float eased = easeOutCubic(progress);
            if (eased <= 0f) continue;
            float x = startX + i * (BUTTON_SIZE + BUTTON_SPACING);
            renderCircleButton(i, x, y, eased * opacity);
        }
    }

    private void renderCircleButton(int index, float x, float y, float opacity) {
        if (opacity < 0.01f) return;

        float scale = buttonScales[index];
        float radius = BUTTON_RADIUS * scale;
        float centerX = x + BUTTON_RADIUS;
        float centerY = y + BUTTON_RADIUS;

        float hover = buttonHoverProgress[index];
        boolean quit = index == BUTTON_ICONS.length - 1;
        Themes.Theme theme = Theme.theme();

        int tint;
        if (quit) {
            float q = quitHoverProgress;
            tint = HudRenderUtil.mixColor(0xFFFF4C4C, 0xFFFF1F32, q * 0.55f + hover * 0.25f);
        } else {
            int accent = theme.accent();
            int baseTint = HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.18f);
            tint = HudRenderUtil.mixColor(baseTint, 0xFFFFFFFF, hover * 0.10f);
        }

        int tintArgb = withAlpha(tint, Math.round(opacity * (quit ? 235f : 220f)));
        int icon = quit
                ? withAlpha(HudRenderUtil.mixColor(0xFFFFFFFF, 0xFFFFD6D6, quitHoverProgress), Math.round(opacity * 255f))
                : withAlpha(0xFFFFFF, Math.round(opacity * 255f));
        int shadow = withAlpha(quit ? 0x1F0508 : 0x060810, Math.round(opacity * (quit ? 118f : 92f)));

        Renderer2D.COLOR.circleSoftShadow(centerX, centerY, radius,
                BUTTON_SHADOW, 0.052f + hover * 0.020f, shadow);
        Renderer2D.COLOR.liquidGlassCircle(centerX, centerY, radius,
                BUTTON_SOFTNESS,
                tintArgb,
                opacity,
                -20.0f,
                1.0f,
                1.0f,
                quit ? 0.66f : 0.58f,
                (0.100f + hover * 0.032f) * MENU_SCALE,
                0.0f);

        String svg = BUTTON_SVGS[index];
        if (svg != null && !svg.isBlank()) {
            float iconSize = 20.5f * MENU_SCALE * scale;
            Renderer2D.COLOR.svg(svg, centerX - iconSize * 0.5f, centerY - iconSize * 0.5f - 0.45f * MENU_SCALE,
                    iconSize, iconSize, SvgRenderOptions.overrideColor(icon));
        } else {
            TextRenderer icons = Fonts.renderer("MainMenuIcons", FontInfo.Type.Regular, TextRenderer.get());
            String glyph = BUTTON_ICONS[index];
            float iconSize = ICON_FONT * scale;
            float iconW = measureWidth(icons, glyph, iconSize);
            float iconH = measureHeight(icons, iconSize);
            drawText(icons, glyph,
                    centerX - iconW * 0.5f + 0.5f * MENU_SCALE,
                    centerY - iconH * 0.5f + ICON_Y_OFFSET,
                    iconSize,
                    icon);
        }
    }

    private void renderAuthWarning(GuiGraphicsExtractor context, float opacity, long now) {
        AccountEntry entry = getAuthWarningAccount();
        if (entry == null || opacity <= 0.01f) return;

        Bounds b = authWarningBounds(opacity);
        float hover = AnimationUtility.easeOutCubic(authWarningHoverProgress);
        float y = b.y - hover * 1.4f * MENU_SCALE;
        Themes.Theme theme = Theme.theme();

        int shadow = withAlpha(0x060810, Math.round(opacity * (96f + hover * 24f)));
        int top = HudRenderUtil.mixColor(withAlpha(0x171B22, Math.round(opacity * 226f)), withAlpha(theme.accent(), Math.round(opacity * 78f)), 0.16f + hover * 0.10f);
        int bottom = HudRenderUtil.mixColor(withAlpha(0x0D1118, Math.round(opacity * 236f)), withAlpha(theme.accent(), Math.round(opacity * 52f)), 0.08f + hover * 0.08f);
        int stroke = HudRenderUtil.mixColor(withAlpha(0x46505D, Math.round(opacity * 170f)), withAlpha(theme.accent(), Math.round(opacity * 218f)), 0.32f + hover * 0.28f);

        Renderer2D.COLOR.roundedRectSoftShadow(b.x, y + 1.2f * MENU_SCALE, b.w, b.h, AUTH_WARNING_R, 10f * MENU_SCALE, 0.026f + hover * 0.014f, shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(b.x, y, b.w, b.h, AUTH_WARNING_R, 1f, top, top, bottom, bottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(b.x + 0.65f * MENU_SCALE, y + 0.65f * MENU_SCALE, b.w - 1.3f * MENU_SCALE, b.h - 1.3f * MENU_SCALE,
                AUTH_WARNING_R - 0.65f * MENU_SCALE, 1f, 0.65f * MENU_SCALE, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.08f), stroke, 90f);

        float progress = (now % AUTH_WARNING_PROGRESS_MS) / (float) AUTH_WARNING_PROGRESS_MS;
        float lineX = b.x + 8f * MENU_SCALE;
        float lineY = y + 4f * MENU_SCALE;
        float lineW = b.w - 16f * MENU_SCALE;
        float lineH = 1.35f * MENU_SCALE;
        Renderer2D.COLOR.roundedRect(lineX, lineY, lineW, lineH, lineH * 0.5f, 1f, withAlpha(0xFFFFFF, Math.round(opacity * 42f)));
        Renderer2D.COLOR.roundedRect(lineX, lineY, lineW * (1f - progress), lineH, lineH * 0.5f, 1f, withAlpha(theme.accent(), Math.round(opacity * 170f)));

        float headX = b.x + 10f * MENU_SCALE;
        float headY = y + 10f * MENU_SCALE;
        PlayerHeadRenderer.drawRounded(context, headX, headY, AUTH_WARNING_HEAD, 4f * MENU_SCALE, SkinManager.getSkin(entry.getName()),
                new RenderColor(withAlpha(0xFFFFFF, Math.round(opacity * 255f))), true, new RenderColor(withAlpha(theme.accent(), Math.round(opacity * 120f))), 0.75f * MENU_SCALE, false);

        TextRenderer title = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer body = Fonts.renderer("InterMedium", FontInfo.Type.Regular, title);
        float textX = headX + AUTH_WARNING_HEAD + 8f * MENU_SCALE;
        drawText(title, tr("screen.combatant.main_menu.auth_warning.title"), textX, y + 12.2f * MENU_SCALE, 0.72f * MENU_SCALE, withAlpha(0xFFFFFF, Math.round(opacity * 245f)));
        String message = tr("screen.combatant.main_menu.auth_warning.body", entry.getName());
        drawText(body, ellipsize(body, message, 0.55f * MENU_SCALE, b.x + b.w - textX - 10f * MENU_SCALE), textX, y + 25.4f * MENU_SCALE, 0.55f * MENU_SCALE, withAlpha(0xC8D0DC, Math.round(opacity * 225f)));
    }

    private void updateButtonAnimations(float deltaTime) {
        for (int i = 0; i < BUTTON_ICONS.length; i++) {
            float targetHover = hoveredButton == i ? 1f : 0f;
            buttonHoverProgress[i] = Mth.lerp(Math.min(1f, deltaTime * 10f), buttonHoverProgress[i], targetHover);

            float targetScale = hoveredButton == i ? BUTTON_HOVER_SCALE : 1f;
            buttonScales[i] = Mth.lerp(Math.min(1f, deltaTime * 12f), buttonScales[i], targetScale);
        }
        float targetQuit = hoveredButton == BUTTON_ICONS.length - 1 ? 1f : 0f;
        quitHoverProgress = Mth.lerp(Math.min(1f, deltaTime * 8f), quitHoverProgress, targetQuit);
        authWarningHoverProgress = Mth.lerp(Math.min(1f, deltaTime * 10f), authWarningHoverProgress, authWarningHovered ? 1f : 0f);
    }

    private int getHoveredButton(float mouseX, float mouseY, float menuProgress) {
        if (!canInteract(menuProgress)) return -1;
        float totalWidth = BUTTON_SIZE * BUTTON_ICONS.length + BUTTON_SPACING * (BUTTON_ICONS.length - 1);
        float startX = (fixedWidth - totalWidth) * 0.5f;
        float y = fixedHeight * 0.5f + 34f * MENU_SCALE + (1f - menuProgress) * 60f * MENU_SCALE;

        for (int i = 0; i < BUTTON_ICONS.length; i++) {
            float buttonX = startX + i * (BUTTON_SIZE + BUTTON_SPACING);
            float centerX = buttonX + BUTTON_SIZE * 0.5f;
            float centerY = y + BUTTON_SIZE * 0.5f;
            float dx = mouseX - centerX;
            float dy = mouseY - centerY;
            if (dx * dx + dy * dy <= BUTTON_RADIUS * BUTTON_RADIUS) {
                return i;
            }
        }
        return -1;
    }

    private boolean canInteract(float menuProgress) {
        return menuProgress > 0.8f;
    }

    private AccountEntry getAuthWarningAccount() {
        return AccountConfig.get().getMicrosoftAuthorizationRequiredAccount();
    }

    private Bounds authWarningBounds(float menuProgress) {
        float slideOffset = (1f - menuProgress) * 60f * MENU_SCALE;
        float buttonY = fixedHeight * 0.5f + 34f * MENU_SCALE + slideOffset;
        float y = buttonY + BUTTON_SIZE + 18f * MENU_SCALE;
        y = Math.min(y, fixedHeight - AUTH_WARNING_H - 16f * MENU_SCALE);
        return new Bounds((fixedWidth - AUTH_WARNING_W) * 0.5f, y, AUTH_WARNING_W, AUTH_WARNING_H);
    }

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = width;
            fixedHeight = height;
            renderScale = 1f;
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        fixedWidth = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        fixedHeight = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
        renderScale = 1f;
    }

    private float toFixedX(float screenX) {
        if (width <= 0) return screenX;
        Minecraft mc = minecraft;
        if (mc == null) return screenX;
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float hudScale = HudScale.scale(fbw, fbh);
        if (hudScale <= 0f) return screenX;
        return screenX * mc.getWindow().getGuiScale() / hudScale;
    }

    private float toFixedY(float screenY) {
        if (height <= 0) return screenY;
        Minecraft mc = minecraft;
        if (mc == null) return screenY;
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float hudScale = HudScale.scale(fbw, fbh);
        if (hudScale <= 0f) return screenY;
        return screenY * mc.getWindow().getGuiScale() / hudScale;
    }

    private void handleButton(int index) {
        if (minecraft == null) return;
        switch (index) {
            case 0 -> ClientScreen.show(minecraft, new SelectWorldScreen(this));
            case 1 -> ClientScreen.show(minecraft, new JoinMultiplayerScreen(this));
            case 2 -> ClientScreen.show(minecraft, new CombatantAltManagerScreen(this));
            case 3 -> ClientScreen.show(minecraft, new CombatantAddonManagerScreen(this));
            case 4 ->
                    ClientScreen.show(minecraft, new OptionsScreen(this, minecraft.options, false));
            case 5 -> minecraft.stop();
            default -> {
            }
        }
    }

    private float getMenuProgress(long now) {
        if (!initialized) return 0f;
        long elapsed = now - openTime;
        return Mth.clamp(elapsed / (float) MENU_APPEAR_DURATION, 0f, 1f);
    }

    private void drawText(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCenteredText(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        float x = centerX - measureWidth(renderer, text, size) * 0.5f;
        drawText(renderer, text, x, y, size, argb);
    }

    private void drawLiquidGlassText(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, true);
        renderer.renderLiquidGlass(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCenteredLiquidGlassText(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        float x = centerX - measureWidth(renderer, text, size) * 0.5f;
        drawLiquidGlassText(renderer, text, x, y, size, argb);
    }

    private float measureWidth(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null) return 0f;
        renderer.begin(size, true, false);
        float width = (float) renderer.getWidth(text, false);
        renderer.end();
        return width;
    }

    private float measureHeight(TextRenderer renderer, float size) {
        if (renderer == null) return 0f;
        renderer.begin(size, true, false);
        float height = (float) renderer.getHeight(false);
        renderer.end();
        return height;
    }

    private String ellipsize(TextRenderer renderer, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (measureWidth(renderer, text, size) <= maxWidth) return text;
        String out = text;
        while (out.length() > 3 && measureWidth(renderer, out + "...", size) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private record Bounds(float x, float y, float w, float h) {
        private boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
