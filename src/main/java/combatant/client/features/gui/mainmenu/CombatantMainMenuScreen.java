/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import combatant.client.config.MainConfig;
import combatant.client.features.account.AccountConfig;
import combatant.client.features.account.AccountEntry;
import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.renderer.ui.draw.UiStroke;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.runtime.CombatantBuild;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.logging.DebugLog;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Image-backed main menu built as one pointy-hex liquid-glass matrix. */
public final class CombatantMainMenuScreen extends Screen {
    private static final float MENU_SCALE = MainMenuBackdrop.MENU_SCALE;
    private static final float SQRT_3 = MainMenuBackdrop.SQRT_3;

    private static final long MENU_APPEAR_DURATION_MS = 520L;
    private static final float CELL_GAP = MainMenuBackdrop.CELL_GAP;
    private static final float BUTTON_ROUNDING = 1.15f * MENU_SCALE;
    private static final float BUTTON_ELEVATION = 7.5f * MENU_SCALE;
    private static final String[] BUTTON_ICONS = {"a", "b", "x", "", "s", "i"};
    private static final String[] BUTTON_SVGS = {null, null, null, "folder-pen", null, null};
    private static final String[] BUTTON_LABEL_KEYS = {
            "menu.singleplayer",
            "menu.multiplayer",
            "screen.combatant.alt_manager.title",
            "screen.combatant.addon_manager.title",
            "menu.options",
            "menu.quit"
    };

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final float TIME_FONT = 3.35f * MENU_SCALE;
    private static final float DATE_FONT = 0.84f * MENU_SCALE;
    private static final float ICON_FONT = 1.50f * MENU_SCALE;
    private static final float FOOTER_FONT = 0.68f * MENU_SCALE;
    private static final float FOOTER_ICON = 7.4f * MENU_SCALE;
    private static final float FOOTER_GAP = 3.3f * MENU_SCALE;
    private static final float FOOTER_MARGIN = 8.0f * MENU_SCALE;
    private static final float AUTH_WARNING_W = 252f * MENU_SCALE;
    private static final float AUTH_WARNING_H = 39f * MENU_SCALE;
    private static final float AUTH_WARNING_HEAD = 25f * MENU_SCALE;
    private static volatile boolean forceVanillaTitleScreen;

    private final float[] buttonHoverProgress = new float[BUTTON_ICONS.length];
    private boolean initialized;
    private long openTime;
    private long lastRenderTime;
    private int hoveredButton = -1;
    private boolean authWarningHovered;
    private float authWarningHoverProgress;
    private float fixedWidth;
    private float fixedHeight;
    private float cachedGridWidth = -1f;
    private float cachedGridHeight = -1f;
    private MainMenuBackdrop.GridLayout gridLayout;

    public CombatantMainMenuScreen() {
        super(Component.literal("Combatant"));
    }

    public static boolean shouldUseVanillaTitleScreen() {
        return forceVanillaTitleScreen;
    }

    private static float easeOutCubic(float value) {
        float x = Mth.clamp(value, 0f, 1f);
        float inverse = 1f - x;
        return 1f - inverse * inverse * inverse;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    private static String localizedDate(LocalDate date) {
        String weekday = tr("screen.combatant.main_menu.date.weekday."
                + date.getDayOfWeek().name().toLowerCase(Locale.ROOT));
        String month = tr("screen.combatant.main_menu.date.month."
                + date.getMonth().name().toLowerCase(Locale.ROOT));
        return tr("screen.combatant.main_menu.date.format", weekday, month, date.getDayOfMonth());
    }

    @Override
    protected void init() {
        initialized = false;
        cachedGridWidth = -1f;
        cachedGridHeight = -1f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!RuntimeGate.canRunRender()) {
            // Keep the vanilla title screen available during boot, reload and panic.
            // Never replace a failed menu with null (an empty black screen).
            Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
            if (mc != null && !(ClientScreen.current() instanceof TitleScreen))
                ClientScreen.show(mc, new TitleScreen(false));
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
        ensureGrid();

        float opacity = easeOutCubic(getMenuProgress(now));
        float fixedMouseX = toFixedX(mouseX);
        float fixedMouseY = toFixedY(mouseY);
        hoveredButton = canInteract(opacity) ? getHoveredButton(fixedMouseX, fixedMouseY, opacity) : -1;
        Bounds warningBounds = authWarningBounds();
        authWarningHovered = canInteract(opacity)
                && getAuthWarningAccount() != null
                && warningBounds.contains(fixedMouseX, fixedMouseY);
        updateAnimations(deltaTime);

        try {
            renderBackgroundTexture();
            ViewportContext.beginUnscaledLogical(context);

            Renderer2D.COLOR.begin();
            if (opacity > 0.005f) {
                renderBackgroundBlur(opacity);
                renderHoneycombMatrix(opacity, fixedMouseX, fixedMouseY);
                renderButtons(opacity);
                renderStaticClock(opacity);
                renderAuthWarning(context, opacity);
                renderFooter(opacity);
            }
            Renderer2D.COLOR.render();
        } catch (RuntimeException throwable) {
            switchToVanillaTitleScreen(throwable);
        } finally {
            try {
                ViewportContext.end(context);
            } catch (RuntimeException throwable) {
                switchToVanillaTitleScreen(throwable);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;
        updateUiMetrics();
        ensureGrid();
        float opacity = easeOutCubic(getMenuProgress(Util.getMillis()));
        float mx = toFixedX((float) click.x());
        float my = toFixedY((float) click.y());

        AccountEntry warningAccount = getAuthWarningAccount();
        if (warningAccount != null && minecraft != null && canInteract(opacity)
                && authWarningBounds().contains(mx, my)) {
            ClientScreen.show(minecraft, new CombatantAltManagerScreen(this, true));
            return true;
        }

        int index = canInteract(opacity) ? getHoveredButton(mx, my, opacity) : -1;
        if (index >= 0) handleButton(index);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Emergency escape from a custom menu whose graphics are not usable.
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F8) {
            switchToVanillaTitleScreen(null);
            return true;
        }
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

    private void renderBackgroundTexture() {
        MenuBackgroundRenderer.renderConfigured(minecraft);
    }

    private void renderBackgroundBlur(float opacity) {
        MainMenuBackdrop.renderBlur(fixedWidth, fixedHeight, opacity);
    }

    private void renderHoneycombMatrix(float opacity, float mouseX, float mouseY) {
        MainMenuBackdrop.renderHoneycomb(fixedWidth, fixedHeight, opacity, mouseX, mouseY, gridLayout, null);
    }

    private void renderButtons(float opacity) {
        for (int index = 0; index < BUTTON_ICONS.length; index++) {
            float delayed = Mth.clamp((opacity - index * 0.035f) / 0.84f, 0f, 1f);
            if (delayed <= 0.001f) continue;
            renderHexButton(index, easeOutCubic(delayed) * opacity);
        }

        if (hoveredButton >= 0) {
            float hover = easeOutCubic(buttonHoverProgress[hoveredButton]);
            TextRenderer labelFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
            String label = tr(BUTTON_LABEL_KEYS[hoveredButton]);
            float labelY = gridLayout.originY() + gridLayout.radius() + 13f * MENU_SCALE;
            drawCenteredText(labelFont, label, fixedWidth * 0.5f, labelY,
                    0.70f * MENU_SCALE, withAlpha(0xFFF3F7FC, Math.round(opacity * hover * 232f)));
        }
    }

    private void renderHexButton(int index, float opacity) {
        float hover = easeOutCubic(buttonHoverProgress[index]);
        float centerX = buttonCenterX(index);
        float planeY = gridLayout.originY();
        float elevation = BUTTON_ELEVATION * opacity * (1f - hover);
        float topY = planeY - elevation;
        float radius = gridLayout.radius() - CELL_GAP * 0.48f;
        Themes.Theme theme = Theme.theme();

        boolean quit = index == BUTTON_ICONS.length - 1;
        int accent = quit
                ? HudRenderUtil.mixColor(theme.accent(), 0xFFFF384E, hover * 0.82f)
                : theme.accent();
        int accentSoft = HudRenderUtil.mixColor(theme.accentSoft(), accent, 0.62f);

        UiPrimitive well = pointyHex(centerX, planeY, radius, 0.0f);
        Renderer2D.COLOR.primitive(well,
                UiPaint.linear(withAlpha(0xFF05080D, Math.round(opacity * 116f)),
                        withAlpha(accentSoft, Math.round(opacity * 66f)), 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(well,
                UiPaint.solid(withAlpha(accent, Math.round(opacity * (78f + hover * 46f)))),
                UiStroke.of(1.05f * MENU_SCALE));

        if (elevation > 0.08f) {
            renderButtonExtrusion(centerX, topY, radius, elevation, accent, opacity);
        }

        UiPrimitive top = pointyHex(centerX, topY, radius, BUTTON_ROUNDING);
        int denseTint = HudRenderUtil.mixColor(accentSoft, 0xFFEAF5FF, 0.14f + hover * 0.05f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                top,
                withAlpha(denseTint, 236),
                opacity * (0.92f - hover * 0.06f),
                1.0f,
                (10.6f + hover * 1.2f) * MENU_SCALE,
                -9.0f,
                0.98f,
                0.84f,
                0.48f,
                0.038f * MENU_SCALE,
                0.0f,
                0.0f,
                Renderer2D.BlurQuality.ULTRA,
                2.75f
        );

        int outerRim = withAlpha(accent, Math.round(opacity * (178f + hover * 52f)));
        int innerRim = withAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.68f),
                Math.round(opacity * (184f + hover * 40f)));
        Renderer2D.COLOR.primitiveStroke(top, UiPaint.solid(outerRim), UiStroke.of(3.15f * MENU_SCALE));
        Renderer2D.COLOR.primitiveStroke(top, UiPaint.solid(innerRim), UiStroke.of(0.82f * MENU_SCALE));

        int iconColor = withAlpha(0xFFFFFFFF, Math.round(opacity * 252f));
        int iconShadow = withAlpha(0xFF020408, Math.round(opacity * 150f));
        String svg = BUTTON_SVGS[index];
        if (svg != null && !svg.isBlank()) {
            float iconSize = 20.0f * MENU_SCALE;
            Renderer2D.COLOR.svg(svg,
                    centerX - iconSize * 0.5f + 0.7f * MENU_SCALE,
                    topY - iconSize * 0.5f + 0.7f * MENU_SCALE,
                    iconSize, iconSize, SvgRenderOptions.overrideColor(iconShadow));
            Renderer2D.COLOR.svg(svg, centerX - iconSize * 0.5f, topY - iconSize * 0.5f,
                    iconSize, iconSize, SvgRenderOptions.overrideColor(iconColor));
        } else {
            TextRenderer icons = Fonts.renderer("MainMenuIcons", FontInfo.Type.Regular, TextRenderer.get());
            String glyph = BUTTON_ICONS[index];
            float iconW = measureWidth(icons, glyph, ICON_FONT);
            float iconH = measureHeight(icons, ICON_FONT);
            float iconX = centerX - iconW * 0.5f + 0.35f * MENU_SCALE;
            float iconY = topY - iconH * 0.5f - 0.5f * MENU_SCALE;
            drawText(icons, glyph, iconX + 0.8f * MENU_SCALE, iconY + 0.8f * MENU_SCALE, ICON_FONT, iconShadow);
            drawText(icons, glyph, iconX, iconY, ICON_FONT, iconColor);
        }
    }

    private void renderButtonExtrusion(float centerX, float topY, float radius, float elevation,
                                       int accent, float opacity) {
        double[][] top = hexPoints(centerX, topY, radius);
        int right = withAlpha(HudRenderUtil.mixColor(accent, 0xFF05080D, 0.73f), Math.round(opacity * 205f));
        int lowerRight = withAlpha(HudRenderUtil.mixColor(accent, 0xFF020408, 0.59f), Math.round(opacity * 224f));
        int lowerLeft = withAlpha(HudRenderUtil.mixColor(accent, 0xFF020408, 0.78f), Math.round(opacity * 218f));
        drawExtrudedEdge(top, 1, 2, elevation, right);
        drawExtrudedEdge(top, 2, 3, elevation, lowerRight);
        drawExtrudedEdge(top, 3, 4, elevation, lowerLeft);
        drawExtrudedEdge(top, 4, 5, elevation, withAlpha(lowerLeft, Math.round(opacity * 166f)));
    }

    private void drawExtrudedEdge(double[][] points, int first, int second, float elevation, int color) {
        double[] face = {
                points[first][0], points[first][1],
                points[first][0], points[first][1] + elevation,
                points[second][0], points[second][1] + elevation,
                points[second][0], points[second][1]
        };
        Renderer2D.COLOR.polygon(face, 4, color);
    }

    private void renderStaticClock(float opacity) {
        MainConfig config = MainConfig.get();
        boolean seconds = config != null && config.isMenuClockShowSeconds();
        String time = LocalTime.now().format(seconds ? CLOCK_SECONDS_FORMAT : CLOCK_FORMAT);
        String date = localizedDate(LocalDate.now());

        TextRenderer clock = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer dateFont = Fonts.renderer("OnestBold", FontInfo.Type.Regular, clock);
        float timeHeight = measureHeight(clock, TIME_FONT);
        float centerY = gridLayout.originY() - gridLayout.radius() * 3.45f;
        drawCenteredText(clock, time, fixedWidth * 0.5f, centerY - timeHeight * 0.5f,
                TIME_FONT, withAlpha(0xFFFFFFFF, Math.round(opacity * 244f)));
        drawCenteredText(dateFont, date.toUpperCase(Locale.ROOT), fixedWidth * 0.5f,
                centerY + timeHeight * 0.5f + 4.5f * MENU_SCALE,
                DATE_FONT, withAlpha(0xFFE4EBF3, Math.round(opacity * 192f)));
    }

    private void renderAuthWarning(GuiGraphicsExtractor context, float opacity) {
        AccountEntry entry = getAuthWarningAccount();
        if (entry == null || opacity <= 0.01f) return;

        Bounds bounds = authWarningBounds();
        float hover = easeOutCubic(authWarningHoverProgress);
        Themes.Theme theme = Theme.theme();
        int accent = theme.accent();
        UiPrimitive panel = UiPrimitive.builder(bounds.x, bounds.y, bounds.w, bounds.h)
                .preset(UiPrimitive.Preset.DIRECTIONAL_RIGHT)
                .cut(10f * MENU_SCALE)
                .rounding(1.2f * MENU_SCALE)
                .build();
        Renderer2D.COLOR.primitive(panel,
                UiPaint.linear(withAlpha(0xFF080C12, Math.round(opacity * 208f)),
                        withAlpha(HudRenderUtil.mixColor(0xFF101720, accent, 0.26f + hover * 0.10f),
                                Math.round(opacity * 224f)), 0f, 0f));
        Renderer2D.COLOR.primitiveStroke(panel,
                UiPaint.solid(withAlpha(accent, Math.round(opacity * (116f + hover * 72f)))),
                UiStroke.of(1.0f * MENU_SCALE));

        float headX = bounds.x + 8f * MENU_SCALE;
        float headY = bounds.y + (bounds.h - AUTH_WARNING_HEAD) * 0.5f;
        PlayerHeadRenderer.drawRect(context, headX, headY, AUTH_WARNING_HEAD,
                SkinManager.getSkin(entry.getName()),
                new RenderColor(withAlpha(0xFFFFFFFF, Math.round(opacity * 255f))), true,
                new RenderColor(withAlpha(accent, Math.round(opacity * 150f))),
                0.8f * MENU_SCALE, false);

        TextRenderer titleFont = Fonts.renderer("OnestBold", FontInfo.Type.Regular, TextRenderer.get());
        TextRenderer bodyFont = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, titleFont);
        float textX = headX + AUTH_WARNING_HEAD + 8f * MENU_SCALE;
        drawText(titleFont, tr("screen.combatant.main_menu.auth_warning.title"), textX,
                bounds.y + 8.8f * MENU_SCALE, 0.68f * MENU_SCALE,
                withAlpha(0xFFFFFFFF, Math.round(opacity * 246f)));
        String message = tr("screen.combatant.main_menu.auth_warning.body", entry.getName());
        drawText(bodyFont,
                ellipsize(bodyFont, message, 0.52f * MENU_SCALE, bounds.x + bounds.w - textX - 15f * MENU_SCALE),
                textX, bounds.y + 22f * MENU_SCALE, 0.52f * MENU_SCALE,
                withAlpha(0xFFD5DDE7, Math.round(opacity * 216f)));
    }

    private void renderFooter(float opacity) {
        TextRenderer font = Fonts.renderer("OnestBold", FontInfo.Type.Regular, TextRenderer.get());
        String openSource = tr("screen.combatant.main_menu.open_source");
        String version = "v" + CombatantBuild.displayVersion();

        int color = withAlpha(0xFF9AA5B1, Math.round(opacity * 188f));
        float textHeight = measureHeight(font, FOOTER_FONT);
        float textY = fixedHeight - FOOTER_MARGIN - textHeight;

        float labelWidth = measureWidth(font, openSource, FOOTER_FONT);
        float groupWidth = FOOTER_ICON + FOOTER_GAP + labelWidth;
        float groupX = (fixedWidth - groupWidth) * 0.5f;
        float iconY = textY + (textHeight - FOOTER_ICON) * 0.5f;

        Renderer2D.COLOR.svg("copyleft", groupX, iconY, FOOTER_ICON, FOOTER_ICON,
                SvgRenderOptions.overrideColor(color));
        drawText(font, openSource, groupX + FOOTER_ICON + FOOTER_GAP, textY, FOOTER_FONT, color);

        float versionWidth = measureWidth(font, version, FOOTER_FONT);
        drawText(font, version, fixedWidth - FOOTER_MARGIN - versionWidth, textY, FOOTER_FONT, color);
    }

    private void ensureGrid() {
        if (Math.abs(cachedGridWidth - fixedWidth) < 0.01f
                && Math.abs(cachedGridHeight - fixedHeight) < 0.01f
                && gridLayout != null) return;

        cachedGridWidth = fixedWidth;
        cachedGridHeight = fixedHeight;
        gridLayout = MainMenuBackdrop.layout(fixedWidth, fixedHeight);
    }

    private static UiPrimitive pointyHex(float centerX, float centerY, float radius, float rounding) {
        float width = SQRT_3 * radius;
        return UiPrimitive.builder(centerX - width * 0.5f, centerY - radius, width, radius * 2f)
                .customConvex(
                        0.5, 0.0,
                        1.0, 0.25,
                        1.0, 0.75,
                        0.5, 1.0,
                        0.0, 0.75,
                        0.0, 0.25
                )
                .rounding(rounding)
                .build();
    }

    private static double[][] hexPoints(float centerX, float centerY, float radius) {
        float halfWidth = SQRT_3 * radius * 0.5f;
        return new double[][]{
                {centerX, centerY - radius},
                {centerX + halfWidth, centerY - radius * 0.5f},
                {centerX + halfWidth, centerY + radius * 0.5f},
                {centerX, centerY + radius},
                {centerX - halfWidth, centerY + radius * 0.5f},
                {centerX - halfWidth, centerY - radius * 0.5f}
        };
    }

    private float buttonCenterX(int index) {
        return gridLayout.originX() + (index - 2) * gridLayout.stepX();
    }

    private int getHoveredButton(float mouseX, float mouseY, float opacity) {
        if (!canInteract(opacity) || gridLayout == null) return -1;
        float radius = gridLayout.radius() - CELL_GAP * 0.48f;
        for (int index = 0; index < BUTTON_ICONS.length; index++) {
            float hover = easeOutCubic(buttonHoverProgress[index]);
            float elevation = BUTTON_ELEVATION * opacity * (1f - hover);
            float dx = Math.abs(mouseX - buttonCenterX(index));
            float dy = Math.abs(mouseY - (gridLayout.originY() - elevation));
            if (dx <= SQRT_3 * radius * 0.5f
                    && dx / SQRT_3 + dy <= radius) return index;
        }
        return -1;
    }

    private void updateAnimations(float deltaTime) {
        float buttonStep = Math.min(1f, deltaTime * 11f);
        for (int index = 0; index < buttonHoverProgress.length; index++) {
            float target = hoveredButton == index ? 1f : 0f;
            buttonHoverProgress[index] = Mth.lerp(buttonStep, buttonHoverProgress[index], target);
        }
        authWarningHoverProgress = Mth.lerp(Math.min(1f, deltaTime * 10f),
                authWarningHoverProgress, authWarningHovered ? 1f : 0f);
    }

    private boolean canInteract(float opacity) {
        return opacity > 0.82f;
    }

    private AccountEntry getAuthWarningAccount() {
        return AccountConfig.get().getMicrosoftAuthorizationRequiredAccount();
    }

    private Bounds authWarningBounds() {
        float y = gridLayout != null
                ? gridLayout.originY() + gridLayout.radius() + 35f * MENU_SCALE
                : fixedHeight * 0.72f;
        y = Math.min(y, fixedHeight - AUTH_WARNING_H - 13f * MENU_SCALE);
        return new Bounds((fixedWidth - AUTH_WARNING_W) * 0.5f, y, AUTH_WARNING_W, AUTH_WARNING_H);
    }

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = Math.max(1f, width);
            fixedHeight = Math.max(1f, height);
            return;
        }
        int framebufferWidth = mc.getWindow().getWidth();
        int framebufferHeight = mc.getWindow().getHeight();
        fixedWidth = Math.max(1f, HudScale.virtualWidth(framebufferWidth, framebufferHeight));
        fixedHeight = Math.max(1f, HudScale.virtualHeight(framebufferWidth, framebufferHeight));
    }

    private float toFixedX(float screenX) {
        Minecraft mc = minecraft;
        if (mc == null) return screenX;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        return hudScale > 0f ? screenX * mc.getWindow().getGuiScale() / hudScale : screenX;
    }

    private float toFixedY(float screenY) {
        Minecraft mc = minecraft;
        if (mc == null) return screenY;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        return hudScale > 0f ? screenY * mc.getWindow().getGuiScale() / hudScale : screenY;
    }

    private void handleButton(int index) {
        if (minecraft == null) return;
        switch (index) {
            case 0 -> ClientScreen.show(minecraft, new SelectWorldScreen(this));
            case 1 -> ClientScreen.show(minecraft, new JoinMultiplayerScreen(this));
            case 2 -> ClientScreen.show(minecraft, new CombatantAltManagerScreen(this));
            case 3 -> ClientScreen.show(minecraft, new CombatantAddonManagerScreen(this));
            case 4 -> ClientScreen.show(minecraft, new OptionsScreen(this, minecraft.options, false));
            case 5 -> minecraft.stop();
            default -> {
            }
        }
    }

    private float getMenuProgress(long now) {
        if (!initialized) return 0f;
        return Mth.clamp((now - openTime) / (float) MENU_APPEAR_DURATION_MS, 0f, 1f);
    }

    private void switchToVanillaTitleScreen(Throwable throwable) {
        forceVanillaTitleScreen = true;
        if (throwable != null) {
            DebugLog.errorOnce("main_menu_vanilla_fallback", "Main menu failed, switching to vanilla title screen", throwable);
        }
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc != null && !(ClientScreen.current() instanceof TitleScreen)) {
            ClientScreen.show(mc, new TitleScreen(false));
        }
    }

    private void drawText(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCenteredText(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        drawText(renderer, text, centerX - measureWidth(renderer, text, size) * 0.5f, y, size, argb);
    }

    private float measureWidth(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null) return 0f;
        renderer.begin(size, true, false);
        float result = (float) renderer.getWidth(text, false);
        renderer.end();
        return result;
    }

    private float measureHeight(TextRenderer renderer, float size) {
        if (renderer == null) return 0f;
        renderer.begin(size, true, false);
        float result = (float) renderer.getHeight(false);
        renderer.end();
        return result;
    }

    private String ellipsize(TextRenderer renderer, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (measureWidth(renderer, text, size) <= maxWidth) return text;
        String result = text;
        while (result.length() > 3 && measureWidth(renderer, result + "...", size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private record Bounds(float x, float y, float w, float h) {
        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
