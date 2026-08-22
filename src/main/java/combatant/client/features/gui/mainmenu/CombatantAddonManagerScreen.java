/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;


import combatant.client.features.theme.Theme;
import combatant.client.addon.AddonIssue;
import combatant.client.addon.AddonManager;
import combatant.client.addon.AddonSnapshot;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
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
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.screen.ClientScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CombatantAddonManagerScreen extends Screen {
    private static final float SCALE = 2.28f;
    private static final float TEXT_SCALE = 1.30f;
    private static final float LEFT_W = 112f * SCALE;
    private static final float LEFT_TOP_H = 137f * SCALE;
    private static final float LEFT_BOTTOM_H = 58f * SCALE;
    private static final float RIGHT_W = 300f * SCALE;
    private static final float RIGHT_H = 165f * SCALE;
    private static final float GAP = 5f * SCALE;
    private static final float PANEL_R = 6f * SCALE;
    private static final float PANEL_SHADOW = 12f * SCALE;
    private static final float HEADER_H = 22f * SCALE;
    private static final float LEFT_CARD_R = 4f * SCALE;
    private static final float CARD_MARKER_W = 2.2f * SCALE;
    private static final float MARQUEE_SPEED = 24f * SCALE;
    private static final float MARQUEE_GAP = 18f * SCALE;
    private static final float MARQUEE_PAUSE = 0.55f;
    private static final float TITLE_FONT = 0.84f * TEXT_SCALE;
    private static final float BODY_FONT = 0.60f * TEXT_SCALE;
    private static final float SMALL_FONT = 0.49f * TEXT_SCALE;
    private static final float METRIC_FONT = 0.56f * TEXT_SCALE;
    private static final float DESCRIPTION_FONT = 0.53f * TEXT_SCALE;
    private static final float SELECTED_DESCRIPTION_FONT = 0.54f * TEXT_SCALE;
    private static final float ADDON_NAME_FONT = 0.66f * TEXT_SCALE;
    private static final float SELECTED_NAME_FONT = 0.70f * TEXT_SCALE;
    private static final float BUTTON_FONT = 0.52f * TEXT_SCALE;
    private static final float BUTTON_H = 13f * SCALE;
    private static final float METRIC_H = 13f * SCALE;
    private static final float METRIC_GAP = 2.5f * SCALE;
    private static final float CARD_H = 40f * SCALE;
    private static final float CARD_GAP = 5f * SCALE;
    private static final float CARD_CUT = 4.5f * SCALE;
    private static final float CARD_ROUNDING = 0.82f * SCALE;
    private static final float ACTION_CUT = 4.0f * SCALE;
    private static final float ACTION_ROUNDING = 0.82f * SCALE;
    private static final float ACTION_ELEVATION = 1.85f * SCALE;

    private final Screen parent;
    private final Map<String, Float> cardHover = new HashMap<>();
    private final Map<String, Float> cardActive = new HashMap<>();
    private final Map<String, Float> cardPress = new HashMap<>();
    private String selectedId = "";
    private String status = "";
    private long statusUntilMillis;
    private List<AddonIssue> scanIssues = List.of();
    private float fixedWidth;
    private float fixedHeight;
    private float openAnim;
    private float scrollOffset;
    private float targetScrollOffset;
    private float toggleHover;
    private float togglePress;
    private float scanHover;
    private float scanPress;
    private float modsHover;
    private float modsPress;
    private float configHover;
    private float configPress;
    private float backHover;
    private float backPress;
    private TextRenderer titleRenderer;
    private TextRenderer bodyRenderer;

    public CombatantAddonManagerScreen(Screen parent) {
        super(Component.translatable("screen.combatant.addon_manager.title"));
        this.parent = parent;
    }

    private static float animate(float current, float target, float dt, float speed) {
        float next = AnimationUtility.approach(current, target, dt, speed);
        return AnimationUtility.snap(next, target, 0.001f);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    protected void init() {
        titleRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        bodyRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, titleRenderer);
        ensureSelection();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateUiMetrics();
        ensureSelection();
        clampScroll();
        renderBackgroundTexture();
        float fx = toFixedX(mouseX);
        float fy = toFixedY(mouseY);
        updateAnimations(fx, fy);

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            MainMenuBackdrop.GridLayout backdropGrid = MainMenuBackdrop.layout(fixedWidth, fixedHeight);
            MainMenuBackdrop.render(fixedWidth, fixedHeight, 1.0f, fx, fy, backdropGrid, null);
            renderUi(fx, fy);
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;
        return handleClick(toFixedX((float) click.x()), toFixedY((float) click.y()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float fx = toFixedX((float) mouseX);
        float fy = toFixedY((float) mouseY);
        Layout l = layout();
        float listX = l.rightX + 5f * SCALE;
        float listY = bodyY(l);
        float listW = RIGHT_W - 10f * SCALE;
        float listH = listHeight(l);
        if (!inside(fx, fy, listX, listY, listW, listH)) return false;
        targetScrollOffset -= (float) verticalAmount * (25f * SCALE);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        if (input.key() == 256) {
            closeToParent();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void renderUi(float mouseX, float mouseY) {
        Layout l = layout();
        float open = AnimationUtility.easeOutCubic(openAnim);
        PanelColors c = colors(open);

        renderPanel(l.leftX, l.topY, LEFT_W, LEFT_TOP_H, c, open, true);
        renderPanel(l.leftX, l.bottomY, LEFT_W, LEFT_BOTTOM_H, c, open, false);
        renderPanel(l.rightX, l.topY, RIGHT_W, RIGHT_H, c, open, true);

        renderDetails(l, c, mouseX, mouseY);
        renderActionsPanel(l, c, mouseX, mouseY);
        renderList(l, c, mouseX, mouseY);
    }

    private void renderDetails(Layout l, PanelColors c, float mouseX, float mouseY) {
        List<AddonSnapshot> addons = AddonManager.snapshots();
        AddonSnapshot selected = selected(addons);
        int active = 0;
        int warnings = 0;
        int errors = 0;
        for (AddonSnapshot addon : addons) {
            if (addon.enabled()) active++;
            for (AddonIssue issue : addon.issues()) {
                if (issue.severity() == AddonIssue.Severity.ERROR) errors++;
                else if (issue.severity() == AddonIssue.Severity.WARNING) warnings++;
            }
        }
        for (AddonIssue issue : scanIssues) {
            if (issue.severity() == AddonIssue.Severity.ERROR) errors++;
            else if (issue.severity() == AddonIssue.Severity.WARNING) warnings++;
        }

        float x = l.leftX + 5f * SCALE;
        float y = l.topY;
        float w = LEFT_W - 10f * SCALE;
        draw(titleRenderer, tr("screen.combatant.addon_manager.header"), l.leftX + 8f * SCALE, y + 7f * SCALE, TITLE_FONT, c.title);
        renderSelectedDetails(x, y + 27f * SCALE, w, c, selected, mouseX, mouseY);

        float metricsY = y + 75f * SCALE;
        float metricStep = METRIC_H + METRIC_GAP;
        renderMetricLine(x, metricsY, w, tr("screen.combatant.addon_manager.installed"), String.valueOf(addons.size()), "package", c, inside(mouseX, mouseY, x, metricsY, w, METRIC_H));
        renderMetricLine(x, metricsY + metricStep, w, tr("screen.combatant.addon_manager.active"), String.valueOf(active), "check", c, inside(mouseX, mouseY, x, metricsY + metricStep, w, METRIC_H));
        renderMetricLine(x, metricsY + metricStep * 2f, w, tr("screen.combatant.addon_manager.issues"), errors + "/" + warnings, errors > 0 ? "folder-x" : "folder-clock", c, inside(mouseX, mouseY, x, metricsY + metricStep * 2f, w, METRIC_H));

        if (statusVisible()) {
            float statusY = y + LEFT_TOP_H - 15f * SCALE;
            renderStatusLine(x, statusY, w, status, c, inside(mouseX, mouseY, x, statusY, w, 9f * SCALE));
        }
    }

    private void renderActionsPanel(Layout l, PanelColors c, float mouseX, float mouseY) {
        List<AddonSnapshot> addons = AddonManager.snapshots();
        AddonSnapshot selected = selected(addons);
        float x = l.leftX + 5f * SCALE;
        float y = l.bottomY + 6f * SCALE;
        float w = LEFT_W - 10f * SCALE;
        float half = (w - GAP) * 0.5f;

        renderAction(x, y, half, BUTTON_H, selected != null && selected.enabled()
                ? tr("screen.combatant.addon_manager.disable")
                : tr("screen.combatant.addon_manager.enable"), selected != null && selected.enabled(), selected != null && selected.enabled() ? "toggle-left" : "toggle-right", toggleHover, togglePress, c);
        renderAction(x + half + GAP, y, half, BUTTON_H, tr("screen.combatant.addon_manager.scan"), false, "folder-sync", scanHover, scanPress, c);
        renderAction(x, y + 17f * SCALE, half, BUTTON_H, tr("screen.combatant.addon_manager.mods"), false, "folder-open", modsHover, modsPress, c);
        renderAction(x + half + GAP, y + 17f * SCALE, half, BUTTON_H, tr("screen.combatant.addon_manager.config"), false, "folder-cog", configHover, configPress, c);
        renderAction(x, y + 34f * SCALE, w, BUTTON_H, tr("screen.combatant.addon_manager.back"), false, "x", backHover, backPress, c);
    }

    private void renderList(Layout l, PanelColors c, float mouseX, float mouseY) {
        renderListHeader(l.rightX + 8f * SCALE, l.topY, RIGHT_W - 16f * SCALE, c, mouseX, mouseY);
        List<AddonSnapshot> addons = AddonManager.snapshots();
        float listX = l.rightX + 5f * SCALE;
        float listY = bodyY(l);
        float listW = RIGHT_W - 10f * SCALE;
        float listH = listHeight(l);
        if (addons.isEmpty()) {
            renderEmptyState(listX, listY, listW, listH, c);
            return;
        }

        int columns = listColumns(addons.size(), listW);
        float cardW = cardWidth(listW, columns);
        boolean clipped = ScissorFunction.pushRaw(listX, listY - 3f * SCALE, listW, listH + 6f * SCALE);
        try {
            for (int i = 0; i < addons.size(); i++) {
                int col = i % columns;
                int row = i / columns;
                float cardX = listX + col * (cardW + CARD_GAP);
                float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
                if (cardY + CARD_H < listY - 10f || cardY > listY + listH + 10f) continue;
                renderAddonCard(addons.get(i), cardX, cardY, cardW, CARD_H, c, inside(mouseX, mouseY, cardX, cardY, cardW, CARD_H));
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }

        int rows = rowsFor(addons.size(), columns);
        float contentH = Math.max(0f, rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP);
        if (contentH > listH + 1f) {
            float trackX = listX + listW + 2.5f * SCALE;
            float thumbH = Math.max(18f * SCALE, listH * listH / contentH);
            float thumbY = listY + (listH - thumbH) * (scrollOffset / Math.max(1f, contentH - listH));
            Renderer2D.COLOR.roundedRect(trackX, listY, 1.4f * SCALE, listH, 0.7f * SCALE, 1f, c.scrollTrack);
            Renderer2D.COLOR.roundedRect(trackX - 0.2f * SCALE, thumbY, 1.8f * SCALE, thumbH, 0.9f * SCALE, 1f, c.accent);
        }
    }

    private void renderAddonCard(AddonSnapshot addon, float x, float y, float w, float h, PanelColors c, boolean hovered) {
        float hover = AnimationUtility.easeOutCubic(cardHover.getOrDefault(addon.id(), 0f));
        float active = AnimationUtility.easeOutCubic(cardActive.getOrDefault(addon.id(), 0f));
        float press = AnimationUtility.easeOutCubic(cardPress.getOrDefault(addon.id(), 0f));
        Themes.Theme theme = Theme.theme();

        UiPrimitive card = addonCard(x, y, w, h);
        renderAddonCardMaterial(card, c, hover, active, press);
        renderActiveCardMarker(x, y, h, active, theme);

        float iconBox = 24f * SCALE;
        float iconX = x + 7f * SCALE;
        float iconY = y + 8f * SCALE;
        Renderer2D.COLOR.roundedRectGradientQuad(iconX, iconY, iconBox, iconBox, 3f * SCALE, 1f, withAlpha(c.surface, 170), withAlpha(c.surfaceHover, 180), withAlpha(0xFF0D1118, 184), withAlpha(0xFF0D1118, 184));
        Renderer2D.COLOR.roundedRectStrokeGradient(iconX, iconY, iconBox, iconBox, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(c.stroke, c.strokeSoft, 0.35f), c.stroke, 90f);
        drawSvgGradient(addonIcon(addon), iconX + 6.2f * SCALE, iconY + 6.2f * SCALE, 11.6f * SCALE, 11.6f * SCALE, c);

        String state = statusText(addon);
        float stateW = miniStatusWidth(state);
        float stateX = x + w - stateW - 6f * SCALE;
        renderMiniStatusPill(stateX, y + 7f * SCALE, state, statusColor(addon, c), c);

        float textX = x + 37f * SCALE;
        float titleW = Math.max(1f, stateX - textX - 5f * SCALE);
        drawFitOrMarquee(bodyRenderer, addon.name(), textX, y + 8.3f * SCALE, ADDON_NAME_FONT, titleW, c.title, hovered);

        String description = addonInfo(addon);
        if (!description.isBlank()) {
            float descW = Math.max(1f, x + w - 7f * SCALE - textX);
            drawFitOrMarquee(bodyRenderer, description, textX, y + 20.3f * SCALE, DESCRIPTION_FONT, descW, addon.restartRequired() ? c.label : c.muted, hovered);
        }
        drawFitOrMarquee(bodyRenderer, countsText(addon), textX, y + 29.6f * SCALE, SMALL_FONT, Math.max(1f, x + w - 7f * SCALE - textX), c.mutedLabel, hovered);
    }

    private static UiPrimitive addonCard(float x, float y, float w, float h) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.CHAMFERED)
                .cut(CARD_CUT)
                .rounding(CARD_ROUNDING)
                .build();
    }

    private static UiPrimitive directional(float x, float y, float w, float h) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.DIRECTIONAL_RIGHT)
                .cut(ACTION_CUT)
                .rounding(ACTION_ROUNDING)
                .build();
    }

    private static UiPrimitive parallelogram(float x, float y, float w, float h, float cut, float rounding) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.PARALLELOGRAM_RIGHT)
                .cut(cut)
                .rounding(rounding)
                .build();
    }

    private void renderAddonCardMaterial(UiPrimitive card, PanelColors c, float hover, float active, float press) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float h = AnimationUtility.clamp01(hover);
        float a = AnimationUtility.clamp01(active);
        float p = AnimationUtility.clamp01(press);
        var b = card.bounds();

        UiPrimitive shadow = addonCard((float) b.x(), (float) b.y() + 1.15f * SCALE,
                (float) b.width(), (float) b.height());
        Renderer2D.COLOR.primitive(shadow,
                UiPaint.solid(withAlpha(0xFF010307, Math.round(48f + h * 24f + a * 16f))));

        int glassTint = HudRenderUtil.mixColor(palette.workspaceGlassTint(), c.surfaceHover, 0.12f + h * 0.05f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                card, withAlpha(glassTint, 208), 0.78f, 0.92f,
                5.8f * SCALE, -13.0f, 0.70f, 0.76f, 0.28f,
                0.011f * SCALE, 0.0f, 0.0f,
                Renderer2D.BlurQuality.ULTRA, 2.08f
        );

        int tl = HudRenderUtil.mixColor(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), a * 0.15f);
        int tr = HudRenderUtil.mixColor(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), a * 0.18f + h * 0.04f);
        int br = HudRenderUtil.mixColor(palette.moduleCardBottomStrong(), palette.menuCategoryHoverRight(), h * 0.09f + a * 0.08f);
        int bl = HudRenderUtil.mixColor(palette.moduleCardBottom(), palette.menuCategoryHoverLeft(), h * 0.08f + a * 0.06f);
        Renderer2D.COLOR.primitive(card, UiPaint.corners(
                withAlpha(tl, Math.round(146f + a * 26f)),
                withAlpha(tr, Math.round(174f + h * 18f + a * 28f)),
                withAlpha(br, Math.round(170f + a * 24f)),
                withAlpha(bl, Math.round(144f + a * 18f))
        ));

        int edge = HudRenderUtil.mixColor(c.strokeSoft, c.accent, a * 0.58f);
        edge = HudRenderUtil.mixColor(edge, 0xFFFFFFFF, h * 0.07f);
        Renderer2D.COLOR.primitiveStroke(card,
                UiPaint.solid(withAlpha(edge, Math.round(76f + h * 48f + a * 88f - p * 16f))),
                UiStroke.of((0.42f + a * 0.17f) * SCALE));
    }

    private void renderMetricLine(float x, float y, float w, String label, String value, String svg, PanelColors c, boolean hovered) {
        float r = 3.5f * SCALE;
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 144), withAlpha(c.surfaceHover, 166), hovered ? 0.34f : 0.12f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(0xFF0D1118, 158), withAlpha(c.surface, 150), hovered ? 0.18f : 0.05f);
        int stroke = HudRenderUtil.mixColor(c.stroke, c.strokeSoft, hovered ? 0.42f : 0.20f);
        Renderer2D.COLOR.roundedRectCornersQuad(x, y, w, METRIC_H, r, r, r, r, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeCorners(x, y, w, METRIC_H, r, r, r, r, 0.55f, stroke);
        drawSvgGradient(svg, x + 4.0f * SCALE, y + 3.0f * SCALE, 7.0f * SCALE, 7.0f * SCALE, c);
        float valueW = width(bodyRenderer, value, METRIC_FONT);
        float textY = y + 3.15f * SCALE;
        drawFitOrMarquee(bodyRenderer, label, x + 14.0f * SCALE, textY, METRIC_FONT, Math.max(1f, w - valueW - 23f * SCALE), c.muted, hovered);
        draw(bodyRenderer, value, x + w - valueW - 5f * SCALE, textY, METRIC_FONT, c.title);
    }

    private void renderSelectedDetails(float x, float y, float w, PanelColors c, AddonSnapshot selected, float mouseX, float mouseY) {
        float h = 42f * SCALE;
        Renderer2D.COLOR.roundedRectCornersQuad(x, y, w, h, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, 1f,
                withAlpha(c.surface, 160), withAlpha(c.surfaceHover, 170), withAlpha(0xFF0D1118, 178), withAlpha(0xFF0D1118, 178));
        Renderer2D.COLOR.roundedRectStrokeCorners(x, y, w, h, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, 0.55f, HudRenderUtil.mixColor(c.stroke, c.strokeSoft, 0.36f));
        if (selected == null) {
            drawCentered(bodyRenderer, tr("screen.combatant.addon_manager.empty"), x + w * 0.5f, y + 17f * SCALE, BODY_FONT, c.mutedLabel);
            return;
        }
        float iconBox = 18f * SCALE;
        float iconX = x + 6f * SCALE;
        float iconY = y + 7f * SCALE;
        Renderer2D.COLOR.roundedRectGradientQuad(iconX, iconY, iconBox, iconBox, 3f * SCALE, 1f, withAlpha(c.surface, 176), withAlpha(c.surfaceHover, 186), withAlpha(0xFF0D1118, 190), withAlpha(0xFF0D1118, 190));
        Renderer2D.COLOR.roundedRectStrokeGradient(iconX, iconY, iconBox, iconBox, 3f * SCALE, 1f, 0.5f, c.stroke, c.strokeSoft, 90f);
        drawSvgGradient(addonIcon(selected), iconX + 4.3f * SCALE, iconY + 4.3f * SCALE, 9.4f * SCALE, 9.4f * SCALE, c);

        String state = statusText(selected);
        float stateW = miniStatusWidth(state);
        float titleX = iconX + iconBox + 5f * SCALE;
        float stateX = x + w - stateW - 6f * SCALE;
        boolean drawState = stateX > titleX + 30f * SCALE;
        if (drawState) {
            renderMiniStatusPill(stateX, y + 6f * SCALE, state, statusColor(selected, c), c);
        }

        float titleW = Math.max(1f, (drawState ? stateX - titleX - 5f * SCALE : x + w - titleX - 7f * SCALE));
        drawFitOrMarquee(bodyRenderer, selected.name(), titleX, y + 7.4f * SCALE, SELECTED_NAME_FONT, titleW, c.title, inside(mouseX, mouseY, titleX, y + 6f * SCALE, titleW, 10f * SCALE));

        String info = addonInfo(selected);
        if (!info.isBlank()) {
            drawFitOrMarquee(bodyRenderer, info, titleX, y + 19.4f * SCALE, SELECTED_DESCRIPTION_FONT, Math.max(1f, x + w - titleX - 7f * SCALE), selected.restartRequired() ? c.label : c.muted, inside(mouseX, mouseY, titleX, y + 18f * SCALE, w - 35f * SCALE, 10f * SCALE));
        }
        drawFitOrMarquee(bodyRenderer, countsText(selected), x + 7f * SCALE, y + 31.0f * SCALE, SMALL_FONT, w - 14f * SCALE, c.mutedLabel, inside(mouseX, mouseY, x + 7f * SCALE, y + 30f * SCALE, w - 14f * SCALE, 10f * SCALE));
    }

    private void renderStatusLine(float x, float y, float w, String text, PanelColors c, boolean hovered) {
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, 9f * SCALE, 4.5f * SCALE, 1f, c.statusTop, c.statusTop, c.statusBottom, c.statusBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, 9f * SCALE, 4.5f * SCALE, 1.2f, 0.4f, c.strokeSoft, c.stroke, 90f);
        drawFitOrMarquee(bodyRenderer, text, x + 5f * SCALE, y + 1.8f * SCALE, SMALL_FONT, w - 10f * SCALE, c.muted, hovered);
    }

    private void renderListHeader(float x, float headerTop, float w, PanelColors c, float mouseX, float mouseY) {
        float centerY = headerTop + HEADER_H * 0.5f;
        float iconSize = 13.0f * SCALE;
        float iconX = x + 7.0f * SCALE;
        float iconY = centerY - iconSize * 0.5f;
        drawSvgGradient("folder-tree", iconX, iconY, iconSize, iconSize, c);
        String title = tr("screen.combatant.addon_manager.list");
        float titleX = x + 25.0f * SCALE;
        float titleW = Math.max(1f, w - 33f * SCALE);
        float titleY = centerY - 4.8f * SCALE;
        drawFitOrMarquee(titleRenderer, title, titleX, titleY, TITLE_FONT, titleW, c.title, inside(mouseX, mouseY, titleX, titleY - 1.5f * SCALE, titleW, 12f * SCALE));
        if (!scanIssues.isEmpty()) {
            String label = tr("screen.combatant.addon_manager.status.scanned", scanIssues.size());
            float labelW = Math.min(w * 0.34f, Math.max(42f * SCALE, width(bodyRenderer, label, SMALL_FONT)));
            float labelX = x + w - labelW - 8f * SCALE;
            drawFitOrMarquee(bodyRenderer, label, labelX, centerY - 3.6f * SCALE, SMALL_FONT, labelW, c.muted, inside(mouseX, mouseY, labelX, centerY - 5f * SCALE, labelW, 10f * SCALE));
        }
    }

    private void renderEmptyState(float x, float y, float w, float h, PanelColors c) {
        float boxW = Math.min(w - 18f * SCALE, 110f * SCALE);
        float boxH = 44f * SCALE;
        float boxX = x + w * 0.5f - boxW * 0.5f;
        float boxY = y + h * 0.5f - boxH * 0.5f;
        Renderer2D.COLOR.roundedRectGradientQuad(boxX, boxY, boxW, boxH, 7f * SCALE, 1f, c.detailTop, c.detailTop, c.detailBottom, c.detailBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(boxX, boxY, boxW, boxH, 7f * SCALE, 1.2f, 0.5f, c.strokeSoft, c.stroke, 90f);
        drawSvgGradient("folder-open", boxX + boxW * 0.5f - 8f * SCALE, boxY + 7f * SCALE, 16f * SCALE, 16f * SCALE, c);
        drawCentered(bodyRenderer, tr("screen.combatant.addon_manager.empty"), boxX + boxW * 0.5f, boxY + 27f * SCALE, SMALL_FONT, c.muted);
    }

    private float miniStatusWidth(String label) {
        return Math.max(28f * SCALE, width(bodyRenderer, label, SMALL_FONT) + 11f * SCALE);
    }

    private void renderActiveCardMarker(float x, float y, float h, float active, Themes.Theme theme) {
        float reveal = AnimationUtility.clamp01(active);
        if (reveal <= 0.001f) return;

        int accentBase = theme != null ? theme.accent() : 0xFFFFFFFF;
        int accent = withAlpha(accentBase, Math.round(220f * reveal));
        float markerH = Math.max(8f * SCALE, h - 10f * SCALE);
        UiPrimitive marker = parallelogram(
                x + 2.1f * SCALE,
                y + (h - markerH) * 0.5f,
                Math.max(1.2f * SCALE, CARD_MARKER_W * reveal),
                markerH,
                0.75f * SCALE,
                0.34f * SCALE
        );
        Renderer2D.COLOR.primitive(marker, UiPaint.linear(
                HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.18f), accent, 90f, 0f));
    }

    private void renderMiniStatusPill(float x, float y, String label, int color, PanelColors c) {
        float w = miniStatusWidth(label);
        UiPrimitive pill = parallelogram(x, y, w, 9f * SCALE, 2.6f * SCALE, 0.72f * SCALE);
        Renderer2D.COLOR.primitive(pill, UiPaint.linear(
                HudRenderUtil.mixColor(withAlpha(c.surface, 170), color, 0.09f),
                withAlpha(0xFF0D1118, 178), 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(pill,
                UiPaint.solid(withAlpha(HudRenderUtil.mixColor(c.stroke, color, 0.42f), 118)),
                UiStroke.of(0.42f * SCALE));
        Renderer2D.COLOR.roundedRect(x + 4.0f * SCALE, y + 3.2f * SCALE, 2.4f * SCALE, 2.4f * SCALE, 1.2f * SCALE, 1f, color);
        draw(bodyRenderer, label, x + 8.4f * SCALE, y + 1.75f * SCALE, SMALL_FONT, c.label);
    }

    private void renderAction(float x, float y, float w, float h, String label, boolean destructive, String svgName, float hover, float press, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(hover);
        float pressAnim = AnimationUtility.easeOutCubic(press);

        int accent = destructive
                ? HudRenderUtil.mixColor(withAlpha(0x8C5054, 255), withAlpha(0xFF9A9A, 255), hoverAnim * 0.32f)
                : HudRenderUtil.mixColor(c.strokeSoft, c.accentSoft, 0.22f + hoverAnim * 0.38f);
        int tint = destructive
                ? HudRenderUtil.mixColor(withAlpha(0x241518, 255), withAlpha(0x59282C, 255), 0.20f + hoverAnim * 0.30f)
                : HudRenderUtil.mixColor(c.surfaceHover, withAlpha(0x203543, 255), 0.22f + hoverAnim * 0.24f);
        int text = destructive
                ? HudRenderUtil.mixColor(withAlpha(0xD7A7A7, 255), withAlpha(0xFFD1D1, 255), 0.20f + hoverAnim * 0.58f)
                : HudRenderUtil.mixColor(withAlpha(0xD6DEE8, 255), withAlpha(0xF4FBFF, 255), 0.18f + hoverAnim * 0.48f);

        // A real two-part button: dark socket at the nominal bounds and a raised glass cap.
        // Hover lifts it slightly; press collapses the cap back into the socket.
        float elevationFactor = AnimationUtility.clamp01(0.74f + hoverAnim * 0.26f - pressAnim * 0.68f);
        float elevation = ACTION_ELEVATION * elevationFactor;
        float topY = y - elevation;

        UiPrimitive well = directional(x, y, w, h);
        int wellTop = withAlpha(HudRenderUtil.mixColor(0xFF06090E, accent, 0.08f), Math.round(184f + hoverAnim * 18f));
        int wellBottom = withAlpha(HudRenderUtil.mixColor(0xFF010307, accent, 0.22f), Math.round(218f + pressAnim * 18f));
        Renderer2D.COLOR.primitive(well, UiPaint.linear(wellTop, wellBottom, 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(well,
                UiPaint.solid(withAlpha(accent, Math.round(72f + hoverAnim * 62f))),
                UiStroke.of(0.62f * SCALE));

        UiPrimitive top = directional(x, topY, w, h);
        if (elevation > 0.05f) {
            renderActionExtrusion(top, elevation, accent);
        }

        int glassTint = HudRenderUtil.mixColor(tint, 0xFFEAF7FF, 0.11f + hoverAnim * 0.07f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                top,
                withAlpha(glassTint, Math.round(230f + hoverAnim * 16f - pressAnim * 12f)),
                0.97f,
                1.0f,
                (8.8f + hoverAnim * 1.6f) * SCALE,
                -10.0f,
                0.98f,
                0.86f,
                0.46f,
                0.032f * SCALE,
                0.0f,
                0.0f,
                Renderer2D.BlurQuality.ULTRA,
                2.60f
        );

        int faceTop = HudRenderUtil.mixColor(withAlpha(tint, 132), withAlpha(0xFFFFFFFF, 118), 0.18f + hoverAnim * 0.10f);
        int faceBottom = HudRenderUtil.mixColor(withAlpha(tint, 126), withAlpha(0xFF02050A, 188), 0.46f + pressAnim * 0.12f);
        Renderer2D.COLOR.primitive(top, UiPaint.linear(faceTop, faceBottom, 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(accent, Math.round(150f + hoverAnim * 72f - pressAnim * 22f))),
                UiStroke.of(1.08f * SCALE));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.72f), Math.round(124f + hoverAnim * 46f))),
                UiStroke.of(0.30f * SCALE));

        float contentY = topY + 3.7f * SCALE;
        drawFitOrMarquee(bodyRenderer, label, x + 5f * SCALE, contentY, BUTTON_FONT, w - 18f * SCALE, text, hoverAnim > 0.55f);
        float iconSize = 6.7f * SCALE;
        Renderer2D.COLOR.svg(svgName, x + w - 11.1f * SCALE, topY + (h - iconSize) * 0.5f, iconSize, iconSize, SvgRenderOptions.overrideColor(text));
    }

    private void renderActionExtrusion(UiPrimitive top, float elevation, int accent) {
        double[] points = top.points();
        int count = top.pointCount();
        if (points.length < 6 || count < 3) return;

        double centerX = top.bounds().x() + top.bounds().width() * 0.5;
        double centerY = top.bounds().y() + top.bounds().height() * 0.5;
        double halfW = Math.max(1.0, top.bounds().width() * 0.5);
        double halfH = Math.max(1.0, top.bounds().height() * 0.5);
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            double x1 = points[i * 2];
            double y1 = points[i * 2 + 1];
            double x2 = points[j * 2];
            double y2 = points[j * 2 + 1];
            double midX = (x1 + x2) * 0.5;
            double midY = (y1 + y2) * 0.5;
            if (midY < centerY - top.bounds().height() * 0.04) continue;

            float nx = (float) ((midX - centerX) / halfW);
            float ny = AnimationUtility.clamp01((float) ((midY - centerY) / halfH));
            float darkMix = ny > 0.52f
                    ? (nx >= 0f ? 0.56f : 0.75f)
                    : (nx > 0.05f ? 0.70f : 0.82f);
            int face = withAlpha(HudRenderUtil.mixColor(accent, 0xFF020408, darkMix), ny > 0.52f ? 226 : 194);
            Renderer2D.COLOR.polygon(new double[]{
                    x1, y1,
                    x1, y1 + elevation,
                    x2, y2 + elevation,
                    x2, y2
            }, 4, face);
        }
    }

    private void drawFitOrMarquee(TextRenderer renderer, String text, float x, float y, float size, float maxWidth, int color, boolean marquee) {
        if (text == null || text.isEmpty() || maxWidth <= 1f) return;
        float textW = width(renderer, text, size);
        if (textW <= maxWidth) {
            draw(renderer, text, x, y, size, color);
            return;
        }
        if (!marquee) {
            draw(renderer, ellipsize(renderer, text, size, maxWidth), x, y, size, color);
            return;
        }
        drawScrollingText(renderer, text, x, y, textW, maxWidth, size, color);
    }

    private void drawScrollingText(TextRenderer renderer, String text, float x, float y, float fullWidth, float viewWidth, float size, int color) {
        if (renderer == null || text == null || text.isEmpty() || viewWidth <= 1f) return;
        float cycleDistance = fullWidth + MARQUEE_GAP;
        float cycleTime = MARQUEE_PAUSE + cycleDistance / Math.max(1f, MARQUEE_SPEED);
        float time = (Util.getMillis() / 1000.0f) % cycleTime;
        float offset = time <= MARQUEE_PAUSE ? 0f : -(time - MARQUEE_PAUSE) * MARQUEE_SPEED;
        float fade = Math.min(viewWidth * 0.25f, 8f * SCALE);
        RenderColor renderColor = new RenderColor(color);
        renderer.begin(size, false, false);
        try {
            renderer.renderHorizontalFadeClipped(text, x + offset, y, renderColor, x, x + viewWidth, fade, fade, false);
            if (cycleDistance > viewWidth * 0.5f) {
                renderer.renderHorizontalFadeClipped(text, x + offset + cycleDistance, y, renderColor, x, x + viewWidth, fade, fade, false);
            }
        } finally {
            renderer.end();
        }
    }

    private int listColumns(int count, float listW) {
        if (count <= 1) return 1;
        return listW >= 250f * SCALE ? 2 : 1;
    }

    private int rowsFor(int count, int columns) {
        if (count <= 0) return 0;
        return (count + Math.max(1, columns) - 1) / Math.max(1, columns);
    }

    private float cardWidth(float listW, int columns) {
        int safeColumns = Math.max(1, columns);
        return safeColumns == 1 ? listW : (listW - CARD_GAP * (safeColumns - 1)) / safeColumns;
    }

    private boolean handleClick(float mouseX, float mouseY) {
        Layout l = layout();
        List<AddonSnapshot> addons = AddonManager.snapshots();
        AddonSnapshot selected = selected(addons);
        float x = l.leftX + 5f * SCALE;
        float y = l.bottomY + 6f * SCALE;
        float w = LEFT_W - 10f * SCALE;
        float half = (w - GAP) * 0.5f;

        if (selected != null && inside(mouseX, mouseY, x, y, half, BUTTON_H)) {
            togglePress = 1f;
            boolean next = !selected.enabled();
            if (AddonManager.setEnabled(selected.id(), next)) {
                setStatus("");
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + half + GAP, y, half, BUTTON_H)) {
            scanPress = 1f;
            scanIssues = AddonManager.scan();
            setStatus(scanIssues.isEmpty() ? "" : tr("screen.combatant.addon_manager.status.scanned", scanIssues.size()));
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 17f * SCALE, half, BUTTON_H)) {
            modsPress = 1f;
            if (openPath(FabricLoader.getInstance().getGameDir().resolve("mods"))) {
                setStatus(tr("screen.combatant.addon_manager.status.opened_mods"));
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + half + GAP, y + 17f * SCALE, half, BUTTON_H)) {
            configPress = 1f;
            if (openPath(FabricLoader.getInstance().getConfigDir().resolve("combatant").resolve("addons"))) {
                setStatus(tr("screen.combatant.addon_manager.status.opened_config"));
            }
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 34f * SCALE, w, BUTTON_H)) {
            backPress = 1f;
            closeToParent();
            return true;
        }

        float listX = l.rightX + 5f * SCALE;
        float listY = bodyY(l);
        float listW = RIGHT_W - 10f * SCALE;
        float listH = listHeight(l);
        int columns = listColumns(addons.size(), listW);
        float cardW = cardWidth(listW, columns);
        if (!inside(mouseX, mouseY, listX, listY, listW, listH)) return true;
        for (int i = 0; i < addons.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float cardX = listX + col * (cardW + CARD_GAP);
            float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
            if (inside(mouseX, mouseY, cardX, cardY, cardW, CARD_H)) {
                selectedId = addons.get(i).id();
                cardPress.put(selectedId, 1f);
                setStatus("");
                return true;
            }
        }
        return true;
    }

    private void updateAnimations(float mouseX, float mouseY) {
        float dt = AnimationUtility.deltaTime();
        openAnim = animate(openAnim, 1f, dt, 7f);
        Layout l = layout();
        float x = l.leftX + 5f * SCALE;
        float y = l.bottomY + 6f * SCALE;
        float w = LEFT_W - 10f * SCALE;
        float half = (w - GAP) * 0.5f;
        toggleHover = animate(toggleHover, inside(mouseX, mouseY, x, y, half, BUTTON_H) ? 1f : 0f, dt, 11f);
        scanHover = animate(scanHover, inside(mouseX, mouseY, x + half + GAP, y, half, BUTTON_H) ? 1f : 0f, dt, 11f);
        modsHover = animate(modsHover, inside(mouseX, mouseY, x, y + 17f * SCALE, half, BUTTON_H) ? 1f : 0f, dt, 11f);
        configHover = animate(configHover, inside(mouseX, mouseY, x + half + GAP, y + 17f * SCALE, half, BUTTON_H) ? 1f : 0f, dt, 11f);
        backHover = animate(backHover, inside(mouseX, mouseY, x, y + 34f * SCALE, w, BUTTON_H) ? 1f : 0f, dt, 11f);
        togglePress = animate(togglePress, 0f, dt, 8f);
        scanPress = animate(scanPress, 0f, dt, 8f);
        modsPress = animate(modsPress, 0f, dt, 8f);
        configPress = animate(configPress, 0f, dt, 8f);
        backPress = animate(backPress, 0f, dt, 8f);
        if (!status.isBlank() && statusUntilMillis > 0L && Util.getMillis() > statusUntilMillis) {
            status = "";
            statusUntilMillis = 0L;
        }

        List<AddonSnapshot> addons = AddonManager.snapshots();
        Set<String> keys = new HashSet<>();
        float listX = l.rightX + 5f * SCALE;
        float listY = bodyY(l);
        float listW = RIGHT_W - 10f * SCALE;
        int columns = listColumns(addons.size(), listW);
        float cardW = cardWidth(listW, columns);
        for (int i = 0; i < addons.size(); i++) {
            AddonSnapshot addon = addons.get(i);
            keys.add(addon.id());
            int col = i % columns;
            int row = i / columns;
            float cardX = listX + col * (cardW + CARD_GAP);
            float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
            boolean hover = inside(mouseX, mouseY, cardX, cardY, cardW, CARD_H);
            cardHover.put(addon.id(), animate(cardHover.getOrDefault(addon.id(), 0f), hover ? 1f : 0f, dt, 10f));
            cardActive.put(addon.id(), animate(cardActive.getOrDefault(addon.id(), 0f), addon.id().equals(selectedId) ? 1f : 0f, dt, 8f));
            cardPress.put(addon.id(), animate(cardPress.getOrDefault(addon.id(), 0f), 0f, dt, 7f));
        }
        prune(cardHover, keys);
        prune(cardActive, keys);
        prune(cardPress, keys);
        scrollOffset = animate(scrollOffset, targetScrollOffset, dt, 12f);
    }

    private void ensureSelection() {
        List<AddonSnapshot> addons = AddonManager.snapshots();
        if (addons.isEmpty()) {
            selectedId = "";
            return;
        }
        if (selected(addons) == null) {
            selectedId = addons.get(0).id();
        }
    }

    private AddonSnapshot selected(List<AddonSnapshot> addons) {
        for (AddonSnapshot addon : addons) {
            if (addon.id().equals(selectedId)) return addon;
        }
        return null;
    }

    private void clampScroll() {
        List<AddonSnapshot> addons = AddonManager.snapshots();
        float listW = RIGHT_W - 10f * SCALE;
        int columns = listColumns(addons.size(), listW);
        int rows = rowsFor(addons.size(), columns);
        float listH = listHeight(layout());
        float contentH = Math.max(0f, rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP);
        float max = Math.max(0f, contentH - listH);
        targetScrollOffset = Mth.clamp(targetScrollOffset, 0f, max);
        scrollOffset = Mth.clamp(scrollOffset, 0f, max);
    }

    private boolean openPath(Path path) {
        try {
            Files.createDirectories(path);
            Util.getPlatform().openUri(path.toUri());
            return true;
        } catch (Throwable ignored) {
            setStatus(tr("screen.combatant.addon_manager.status.open_failed"));
            return false;
        }
    }

    private String addonIcon(AddonSnapshot addon) {
        return switch (addon.status()) {
            case ACTIVE -> addon.enabled() ? "folder-check" : "folder";
            case ERROR -> "folder-x";
            case DISABLED -> "folder";
            case SUSPENDED -> "folder-clock";
            case SHUTDOWN -> "folder-output";
            default -> "package";
        };
    }

    private String statusText(AddonSnapshot addon) {
        if (!addon.enabled()) return tr("screen.combatant.addon_manager.status.disabled_short");
        return switch (addon.status()) {
            case ACTIVE -> tr("screen.combatant.addon_manager.state.active");
            case ERROR -> tr("screen.combatant.addon_manager.state.error");
            case DISABLED -> tr("screen.combatant.addon_manager.state.disabled");
            case SUSPENDED -> tr("screen.combatant.addon_manager.state.suspended");
            case SHUTDOWN -> tr("screen.combatant.addon_manager.state.shutdown");
            default -> addon.status().name().toLowerCase(Locale.ROOT);
        };
    }

    private String countsText(AddonSnapshot addon) {
        int hud = addon.draggableHudElements() + addon.staticHudElements();
        return tr("screen.combatant.addon_manager.metric.modules") + " " + addon.modules()
                + " · " + tr("screen.combatant.addon_manager.metric.hud") + " " + hud
                + " · " + tr("screen.combatant.addon_manager.metric.commands") + " " + addon.commands()
                + " · " + tr("screen.combatant.addon_manager.metric.gui") + " " + addon.clickGuiSections();
    }

    private int statusColor(AddonSnapshot addon, PanelColors c) {
        return switch (addon.status()) {
            case ACTIVE -> c.accent;
            case ERROR -> withAlpha(0xFF8888, 255);
            case DISABLED -> c.muted;
            default -> c.label;
        };
    }

    private void renderPanel(float x, float y, float w, float h, PanelColors c, float open, boolean header) {
        Renderer2D.COLOR.blurRect(x, y, w, h, PANEL_R, 15f, 1.0f, 0.30f, 0xFFFFFF);
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, PANEL_R, PANEL_SHADOW, 0.022f + open * 0.012f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, PANEL_R, 1f, c.bgTopLeft, c.bgTopRight, c.bgBottomRight, c.bgBottomLeft);
        if (header) {
            Renderer2D.COLOR.roundedRectMaskedQuad(x, y, w, HEADER_H, x, y, w, h, PANEL_R, 1f,
                    c.headerTopLeft, c.headerTopRight, c.headerBottomRight, c.headerBottomLeft);
        }
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, PANEL_R, 1f, 1f, HudRenderUtil.mixColor(c.stroke, 0xFFFFFFFF, 0.04f), c.stroke, 90f);
    }

    private PanelColors colors(float alpha) {
        int bgAlpha = Math.round(alpha * 120f);
        int headerAlpha = Math.round(alpha * 150f);
        int strokeAlpha = Math.round(alpha * 100f);
        int shadowAlpha = Math.round(alpha * 80f);
        int titleAlpha = Math.round(alpha * 255f);
        int labelAlpha = Math.round(alpha * 155f);

        Themes.Theme theme = Theme.theme();
        int themeAccent = theme != null ? theme.accent() : 0xFFFFFFFF;
        int themeAccentSoft = theme != null ? theme.accentSoft() : themeAccent;

        int bgTopLeft = withAlpha(0x0D0F14, bgAlpha);
        int bgTopRight = withAlpha(0x101218, bgAlpha);
        int bgBottomLeft = withAlpha(0x08090C, bgAlpha);
        int bgBottomRight = withAlpha(0x0D0F14, bgAlpha);
        int headerTopLeft = withAlpha(0x14171F, headerAlpha);
        int headerTopRight = withAlpha(0x181B24, headerAlpha);
        int headerBottomLeft = withAlpha(0x10131A, headerAlpha);
        int headerBottomRight = withAlpha(0x14171F, headerAlpha);
        int stroke = withAlpha(0x252A36, strokeAlpha);
        int strokeSoft = withAlpha(0x3A4A5A, Math.round(alpha * 150f));
        int surface = withAlpha(0x1A1D24, Math.round(alpha * 160f));
        int surfaceHover = withAlpha(0x1A1F28, Math.round(alpha * 200f));
        int accent = withAlpha(themeAccent, titleAlpha);
        int accentSoft = withAlpha(themeAccentSoft, Math.round(alpha * 180f));
        int detailTop = withAlpha(0x1A1D24, Math.round(alpha * 160f));
        int detailBottom = withAlpha(0x0D1118, Math.round(alpha * 185f));
        int statusTop = withAlpha(0x1A1D24, Math.round(alpha * 150f));
        int statusBottom = withAlpha(0x0D1118, Math.round(alpha * 175f));
        int iconTop = withAlpha(themeAccent, titleAlpha);
        int iconBottom = withAlpha(HudRenderUtil.mixColor(themeAccent, themeAccentSoft, 0.58f), titleAlpha);
        int iconShadow = withAlpha(0x060810, Math.round(alpha * 150f));

        return new PanelColors(
                bgTopLeft, bgTopRight, bgBottomRight, bgBottomLeft,
                headerTopLeft, headerTopRight, headerBottomRight, headerBottomLeft,
                stroke, strokeSoft,
                withAlpha(0x060810, shadowAlpha),
                withAlpha(0xFFFFFF, titleAlpha),
                withAlpha(0xFFFFFF, labelAlpha),
                withAlpha(0x808890, titleAlpha),
                withAlpha(0x606878, labelAlpha),
                surface, surfaceHover,
                accent, accentSoft,
                detailTop, detailBottom,
                statusTop, statusBottom,
                withAlpha(0x3A4A5A, Math.round(alpha * 132f)),
                iconTop, iconBottom, iconShadow
        );
    }

    private void renderBackgroundTexture() {
        Minecraft mc = this.minecraft;
        if (mc != null) MenuBackgroundRenderer.renderConfigured(mc);
    }

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = width;
            fixedHeight = height;
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        fixedWidth = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        fixedHeight = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
    }

    private float toFixedX(float screenX) {
        Minecraft mc = minecraft;
        if (mc == null) return screenX;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenX;
        return screenX * mc.getWindow().getGuiScale() / hudScale;
    }

    private float toFixedY(float screenY) {
        Minecraft mc = minecraft;
        if (mc == null) return screenY;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenY;
        return screenY * mc.getWindow().getGuiScale() / hudScale;
    }

    private Layout layout() {
        float totalW = LEFT_W + GAP + RIGHT_W;
        float totalH = LEFT_TOP_H + GAP + LEFT_BOTTOM_H;
        float topY = fixedHeight * 0.5f - totalH * 0.5f;
        float leftX = fixedWidth * 0.5f - totalW * 0.5f;
        return new Layout(leftX, topY, leftX + LEFT_W + GAP, topY + LEFT_TOP_H + GAP);
    }

    private float bodyY(Layout l) {
        return l.topY + 28f * SCALE;
    }

    private float listHeight(Layout l) {
        return Math.max(1f, RIGHT_H - 31f * SCALE);
    }

    private void closeToParent() {
        if (minecraft != null) ClientScreen.show(minecraft, parent);
    }

    private void draw(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCentered(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        draw(renderer, text, centerX - width(renderer, text, size) * 0.5f, y, size, argb);
    }
    private SvgRenderOptions svgGradient(PanelColors c) {
        return SvgRenderOptions.linearGradient(c.iconTop, c.iconBottom, 92f)
                .withTextureCache(false)
                .withCurveFlatness(0.18f);
    }

    private void drawSvgGradient(String svg, float x, float y, float w, float h, PanelColors c) {
        Renderer2D.COLOR.svg(svg, x + 0.38f * SCALE, y + 0.42f * SCALE, w, h,
                SvgRenderOptions.overrideColor(c.iconShadow).withTextureCache(false).withCurveFlatness(0.18f));
        Renderer2D.COLOR.svg(svg, x, y, w, h, svgGradient(c));
    }


    private float width(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
    }

    private String ellipsize(String text, float size, float maxWidth) {
        return ellipsize(bodyRenderer, text, size, maxWidth);
    }

    private String ellipsize(TextRenderer renderer, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (width(renderer, text, size) <= maxWidth) return text;
        String out = text;
        while (out.length() > 3 && width(renderer, out + "...", size) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private boolean statusVisible() {
        return !status.isBlank() && (statusUntilMillis <= 0L || Util.getMillis() <= statusUntilMillis);
    }

    private void setStatus(String text) {
        if (text == null || text.isBlank()) {
            status = "";
            statusUntilMillis = 0L;
            return;
        }
        status = text;
        statusUntilMillis = Util.getMillis() + 1600L;
    }

    private String addonInfo(AddonSnapshot addon) {
        if (addon == null) return "";
        AddonIssue issue = primaryIssue(addon);
        if (issue != null) return issueText(issue);
        if (addon.restartRequired()) return tr("screen.combatant.addon_manager.restart_required");
        return addonDescription(addon);
    }

    private AddonIssue primaryIssue(AddonSnapshot addon) {
        AddonIssue fallback = null;
        for (AddonIssue issue : addon.issues()) {
            if (issue.severity() == AddonIssue.Severity.ERROR) return issue;
            if (fallback == null && issue.severity() == AddonIssue.Severity.WARNING) fallback = issue;
        }
        return fallback;
    }

    private String issueText(AddonIssue issue) {
        if (issue == null) return "";
        String message = issue.message() == null ? "" : issue.message().trim();
        String detail = issue.detail() == null ? "" : issue.detail().trim();
        String body = !message.isEmpty() ? message : detail;
        if (!message.isEmpty() && !detail.isEmpty()) body = message + " · " + detail;
        if (body.isEmpty()) body = issue.severity().name().toLowerCase(Locale.ROOT);
        return body;
    }

    private String addonDescription(AddonSnapshot addon) {
        if (addon == null || addon.description() == null) return "";
        return addon.description().trim();
    }

    private static void prune(Map<String, Float> map, Set<String> keys) {
        map.keySet().removeIf(key -> !keys.contains(key));
    }

    private record Layout(float leftX, float topY, float rightX, float bottomY) {
    }

    private record PanelColors(
            int bgTopLeft,
            int bgTopRight,
            int bgBottomRight,
            int bgBottomLeft,
            int headerTopLeft,
            int headerTopRight,
            int headerBottomRight,
            int headerBottomLeft,
            int stroke,
            int strokeSoft,
            int shadow,
            int title,
            int label,
            int muted,
            int mutedLabel,
            int surface,
            int surfaceHover,
            int accent,
            int accentSoft,
            int detailTop,
            int detailBottom,
            int statusTop,
            int statusBottom,
            int scrollTrack,
            int iconTop,
            int iconBottom,
            int iconShadow
    ) {
    }
}
