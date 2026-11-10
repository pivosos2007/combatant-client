/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.preview.render.VisualPreviewItemRenderer;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec;
import combatant.client.render.engine.renderer.ui.draw.UiGlassVisual;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.item.RarityColorConfig;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BetterTooltipPresentation {
    private static final UiGlassOpticsSpec TOOLTIP_GLASS = UiGlassOpticsSpec.LIQUID
            .withRefraction(0.009f).withBevel(0.21f, 0.051f)
            .withFluid(0.37f, 0.54f).withCleanReveal(0.0f)
            .withInteractionStrength(0.0f).withChromaticAberration(0.65f)
            .withMagnification(1.006f).withShapeDeformation(false).withSpecular(false)
            .withOpticalThickness(1.08f).withSurfaceRoughness(0.21f)
            .withThinFilm(415f, 0.09f);
    static UiGlassOpticsSpec defaultGlassOptics() { return TOOLTIP_GLASS; }
    private static final UiGlassVisual TOOLTIP_VISUAL = UiGlassVisual.shader("ui_glass_tooltip.frag", 0.0f);
    private static final float EDGE = 5.0f;
    private static final float GAP = 9.0f;
    private static final float TIP = 9.0f;
    private static final float PAD_X = 9.0f;
    private static final float PAD_Y = 6.0f;
    private static final float BODY_FONT = 7.55f;
    private static final float HEADER_FONT = 9.05f;
    private static final float ROW_GAP = 1.3f;
    private static final long ENTER_NS = 135_000_000L;
    private static final long EXIT_NS = 125_000_000L;

    record Line(String text, int color) {}

    record Input(List<Line> lines,
                 ItemStack stack,
                 boolean item,
                 float anchorX,
                 float anchorY,
                 long anchorKey,
                 float footerWidth,
                 float footerHeight,
                 Screen screen) {
        Input {
            lines = List.copyOf(lines);
            stack = stack == null ? ItemStack.EMPTY : stack;
        }
    }

    record Result(float footerX, float footerY, boolean footerVisible) {
        static final Result NONE = new Result(0, 0, false);
    }

    private Input snapshot;
    private Screen previousScreen;
    private float lastAnchorX;
    private float lastAnchorY;
    private long animatingSince;
    private float revealFrom;
    private boolean opening;
    private float reveal;
    private Layout cachedLayout;
    private float cachedScale;
    private float cachedWidth;
    private boolean cachedModelEnabled;

    Result render(Minecraft mc, GuiGraphicsExtractor ctx, Input incoming,
                  float scale, float blurStrength, float darkness, float glowStrength,
                  boolean rotatingRare, float rotationSpeed, UiGlassOpticsSpec optics) {
        if (mc == null || mc.getWindow() == null || ctx == null) {
            clear();
            return Result.NONE;
        }
        long now = System.nanoTime();
        Screen currentScreen = ClientScreen.current(mc);
        if (snapshot != null && previousScreen != currentScreen) clear();

        if (incoming != null) {
            boolean changed = snapshot == null || snapshot.item() != incoming.item()
                    || snapshot.anchorKey() != incoming.anchorKey()
                    || !Objects.equals(snapshot.lines(), incoming.lines())
                    || !ItemStack.isSameItemSameComponents(snapshot.stack(), incoming.stack())
                    || snapshot.footerHeight() != incoming.footerHeight()
                    || snapshot.footerWidth() != incoming.footerWidth();
            if (changed) {
                snapshot = new Input(incoming.lines(),
                        incoming.stack().isEmpty() ? ItemStack.EMPTY : incoming.stack().copy(),
                        incoming.item(), incoming.anchorX(), incoming.anchorY(),
                        incoming.anchorKey(), incoming.footerWidth(), incoming.footerHeight(), incoming.screen());
                cachedLayout = null;
            }
            previousScreen = currentScreen;
            lastAnchorX = incoming.anchorX();
            lastAnchorY = incoming.anchorY();
            if (!opening) beginMotion(now, true);
        } else if (snapshot != null && opening) {
            beginMotion(now, false);
        }
        if (snapshot == null) return Result.NONE;

        float duration = opening ? ENTER_NS : EXIT_NS;
        reveal = mix(revealFrom, opening ? 1.0f : 0.0f, smooth((now - animatingSince) / duration));
        if (!opening && reveal <= 0.002f) {
            clear();
            return Result.NONE;
        }

        float anchorX = lastAnchorX;
        float anchorY = lastAnchorY;
        float effectiveScale = incoming == null && cachedLayout != null ? cachedScale : scale;
        float s = clamp(effectiveScale, 0.5f, 1.6f);
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float widthLimit = Math.max(72.0f, screenW - EDGE * 2.0f);
        if (cachedLayout == null || Float.compare(cachedScale, s) != 0
                || Float.compare(cachedWidth, widthLimit) != 0
                || cachedModelEnabled != rotatingRare) {
            cachedLayout = measure(snapshot, s, widthLimit, rotatingRare);
            cachedScale = s;
            cachedWidth = widthLimit;
            cachedModelEnabled = rotatingRare;
        }
        Layout layout = cachedLayout;
        if (layout == null) return Result.NONE;

        float width = layout.width * (0.65f + 0.35f * reveal);
        float height = layout.height * (0.055f + 0.945f * reveal);
        float rightRoom = screenW - anchorX - EDGE - GAP * s;
        float leftRoom = anchorX - EDGE - GAP * s;
        boolean opensRight = rightRoom >= layout.width || rightRoom >= leftRoom;
        float targetX = opensRight ? anchorX + GAP * s : anchorX - GAP * s - layout.width;
        float fullX = clamp(targetX, EDGE, Math.max(EDGE, screenW - layout.width - EDGE));
        float x = fullX + (layout.width - width) * 0.5f;
        float targetY = anchorY - Math.min(layout.height * 0.23f, 23.0f * s);
        float fullY = clamp(targetY, EDGE, Math.max(EDGE, screenH - layout.height - EDGE));
        float y = clamp(fullY + (layout.height - height) * 0.14f,
                EDGE, Math.max(EDGE, screenH - height - EDGE));
        float tip = Math.min(TIP * s, width * 0.15f);
        float tipNormY = clamp((anchorY - y) / Math.max(height, 1.0f), 0.16f, 0.84f);
        UiPrimitive shell = opensRight
                ? UiPrimitive.builder(x, y, width, height)
                    .customConvex(0.0, tipNormY,
                            tip / width, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tip / width, 1.0)
                    .rounding(5.0f * s).build()
                : UiPrimitive.builder(x, y, width, height)
                    .customConvex(0.0, 0.0,
                            1.0 - tip / width, 0.0,
                            1.0, tipNormY,
                            1.0 - tip / width, 1.0,
                            0.0, 1.0)
                    .rounding(5.0f * s).build();

        float bodyX = opensRight ? x + tip : x;
        float bodyW = width - tip;
        ViewportContext.ProjectionMode previousProjection = ViewportContext.activeMode();
        ViewportContext.beginScaled(ctx);
        try {
            renderSurface(shell, layout, bodyX, y, bodyW, height, s,
                    blurStrength, darkness, glowStrength, reveal, optics);
            if (reveal < 0.965f) return Result.NONE;
            renderText(snapshot, layout, bodyX, y, s);
            if (snapshot.item() && layout.showModel) {
                float modelCx = bodyX + bodyW - 15.5f * s;
                float modelCy = y + layout.headerHeight * 0.5f;
                float fbScale = ViewportContext.getScaleFactor();
                float rotation = (now / 1_000_000_000.0f * Math.max(0f, rotationSpeed) * 34.0f) % 360.0f;
                VisualPreviewItemRenderer.renderTooltipModel(snapshot.stack(),
                        modelCx * fbScale, modelCy * fbScale, 20.0f * s * fbScale, rotation);
            }
        } finally {
            switch (previousProjection) {
                case LOGICAL -> ViewportContext.beginUnscaledLogical(ctx);
                case UNSCALED -> ViewportContext.beginUnscaled(ctx);
                default -> ViewportContext.end(ctx);
            }
        }
        if (snapshot.footerWidth() <= 0 || snapshot.footerHeight() <= 0) return Result.NONE;
        return new Result(bodyX + (bodyW - snapshot.footerWidth()) * 0.5f,
                y + layout.footerY, true);
    }

    private void renderSurface(UiPrimitive shell, Layout layout, float bodyX, float y,
                               float bodyW, float height, float s, float blur,
                               float darkness, float glow, float progress, UiGlassOpticsSpec optics) {
        Renderer2D renderer = Renderer2D.COLOR;
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float blurAmount = clamp(blur, 0.0f, 1.0f);
        float headerFraction = layout.item ? clamp(layout.headerHeight / Math.max(height, 1.0f), 0.0f, 1.0f) : 0.0f;
        float leftFraction = clamp((bodyX - shell.bounds().x()) / shell.bounds().width(), 0.0f, 1.0f);
        float rightFraction = clamp((bodyX + bodyW - shell.bounds().x()) / shell.bounds().width(), 0.0f, 1.0f);
        float rarity = layout.item ? clamp(glow, 0.0f, 1.0f) : 0.0f;
        float red = ((layout.rarityColor >>> 16) & 255) / 255.0f;
        float green = ((layout.rarityColor >>> 8) & 255) / 255.0f;
        float blue = (layout.rarityColor & 255) / 255.0f;
        UiGlassVisual visual = TOOLTIP_VISUAL
                .withVector(0, headerFraction, leftFraction, rightFraction, clamp(darkness, 0.0f, 1.0f))
                .withVector(1, red, green, blue, rarity)
                .withVector(2, 1.0f - blurAmount, 0.0f, 0.0f, 0.0f);
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withOptics(optics)
                .withTintStrength(0.035f).withVisual(visual);
        renderer.begin();
        renderer.withLiquidGlassSceneSource(UiBackdropRequest.SceneSource.CURRENT_TARGET,
                () -> renderer.withLiquidGlassBlurProfile(Renderer2D.BlurQuality.ULTRA,
                        HudRenderUtil.HUD_GLASS_KAWASE_OFFSET_PX,
                        () -> renderer.withLiquidGlassMaterial(material,
                                () -> renderer.liquidGlassPrimitive(shell, 0xFF10151C,
                                        1.0f, 1.0f,
                                        Renderer2D.LiquidGlassPreset.HUD_SURFACE_LARGE))));
        if (layout.item && progress > 0.9f && height > layout.headerHeight) {
            renderer.roundedRectGradient(bodyX + 7.0f * s, y + layout.headerHeight,
                    Math.max(1.0f, bodyW - 14.0f * s), 0.75f * s,
                    0.35f * s,
                    alpha(layout.rarityColor, Math.round(92.0f * clamp(glow, 0.0f, 1.0f))),
                    alpha(palette.panelDivider(), 82), 0.0f);
        }
        renderer.render();
    }

    private static void renderText(Input data, Layout layout, float bodyX, float y, float s) {
        TextRenderer fallback = TextRenderer.get();
        TextRenderer body = BuiltinFontCatalog.ONEST_REGULAR.renderer(fallback);
        TextRenderer title = BuiltinFontCatalog.ONEST_MEDIUM.renderer(fallback);
        if (layout.item) {
            title.beginSize(HEADER_FONT * s, true, false);
            try {
                for (int i = 0; i < layout.headerRows.size(); i++) {
                    title.render(layout.headerRows.get(i), bodyX + PAD_X * s,
                            y + (7.0f + i * 9.7f) * s,
                            new RenderColor(layout.titleColor), false);
                }
            } finally {
                title.end();
            }
        }
        body.beginSize(BODY_FONT * s, true, false);
        try {
            for (Row row : layout.rows) {
                if (row.text.isEmpty()) continue;
                body.render(row.text, bodyX + PAD_X * s, y + row.y,
                        new RenderColor(row.color), true);
            }
        } finally {
            body.end();
        }
    }

    private static Layout measure(Input input, float s, float screenWidth, boolean modelEnabled) {
        List<Line> source = input.lines();
        if (source.isEmpty()) return null;
        boolean item = input.item();
        int titleIndex = -1;
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i) != null && !source.get(i).text().isBlank()) {
                titleIndex = i;
                break;
            }
        }
        if (titleIndex < 0) return null;
        Rarity rarity = item ? input.stack().getOrDefault(DataComponents.RARITY, Rarity.COMMON) : Rarity.COMMON;
        if (item && input.stack().isEnchanted() && rarity.ordinal() < Rarity.RARE.ordinal()) rarity = Rarity.RARE;
        int rarityColor = item ? RarityColorConfig.INSTANCE.getColor(rarity) : 0xFFC6D6EA;
        boolean showModel = modelEnabled && item && rarity.ordinal() >= Rarity.RARE.ordinal();
        String header = item ? source.get(titleIndex).text() : "";
        float rowH = 8.65f * s;
        float maxCard = Math.min(screenWidth,
                Math.max(270.0f * s, input.footerWidth() + TIP * s + PAD_X * 2.0f * s));
        float maxText = Math.max(44.0f, maxCard - (TIP + PAD_X * 2.0f) * s);
        float minCard = (item ? 112.0f : 82.0f) * s;
        float iconReserve = showModel ? 25.0f * s : 0.0f;
        TextRenderer text = BuiltinFontCatalog.ONEST_REGULAR.renderer(TextRenderer.get());
        TextRenderer headerText = BuiltinFontCatalog.ONEST_MEDIUM.renderer(TextRenderer.get());
        List<String> headerRows = List.of();
        float longest = 0.0f;
        if (item) {
            headerText.beginSize(HEADER_FONT * s, true, false);
            try {
                headerRows = wrap(headerText, header, Math.max(40.0f, maxText - iconReserve));
                for (String row : headerRows) {
                    longest = Math.max(longest, (float) headerText.getWidth(row, false) + iconReserve);
                }
            } finally {
                headerText.end();
            }
        }
        float headerHeight = item ? Math.max(28.5f * s,
                (headerRows.size() * 9.7f + 10.0f) * s) : 0.0f;
        List<Row> rows = new ArrayList<>();
        float cursorY = headerHeight + PAD_Y * s;
        text.beginSize(BODY_FONT * s, true, false);
        try {
            for (int i = 0; i < source.size(); i++) {
                if (item && i == titleIndex) continue;
                Line line = source.get(i);
                if (line == null || line.text() == null) continue;
                if (line.text().isBlank()) {
                    cursorY += 3.0f * s;
                    continue;
                }
                for (String wrapped : wrap(text, line.text().strip(), maxText)) {
                    rows.add(new Row(wrapped, cursorY, line.color() == 0 ? 0xFFE5E8F1 : line.color()));
                    longest = Math.max(longest, (float) text.getWidth(wrapped, false));
                    cursorY += rowH + ROW_GAP * s;
                }
            }
        } finally {
            text.end();
        }
        float footerY = cursorY + (input.footerHeight() > 0 ? 4.0f * s : 0.0f);
        float height = cursorY + PAD_Y * s + (input.footerHeight() > 0 ? input.footerHeight() + 4.0f * s : 0.0f);
        if (rows.isEmpty() && item) height = Math.max(height, headerHeight + 5.0f * s);
        float width = Math.min(maxCard, Math.max(minCard,
                Math.max(longest + (TIP + PAD_X * 2.0f) * s,
                        input.footerWidth() + (TIP + PAD_X * 2.0f) * s)));
        return new Layout(width, height, headerHeight, headerRows, rows,
                source.get(titleIndex).color() == 0 ? rarityColor : source.get(titleIndex).color(),
                rarityColor, item, showModel, footerY);
    }

    private static List<String> wrap(TextRenderer font, String source, float cap) {
        if (font.getWidth(source, false) <= cap) return List.of(source);
        List<String> result = new ArrayList<>();
        String[] words = source.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getWidth(candidate, false) <= cap) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                result.add(line.toString());
                line.setLength(0);
            }
            while (!word.isEmpty() && font.getWidth(word, false) > cap) {
                int fit = 0;
                for (int j = Character.charCount(word.codePointAt(0)); j <= word.length();) {
                    if (font.getWidth(word.substring(0, j), false) > cap) break;
                    fit = j;
                    if (j == word.length()) break;
                    j += Character.charCount(word.codePointAt(j));
                }
                if (fit == 0) fit = Character.charCount(word.codePointAt(0));
                result.add(word.substring(0, fit));
                word = word.substring(fit);
            }
            line.append(word);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    private void beginMotion(long now, boolean visible) {
        revealFrom = reveal;
        animatingSince = now;
        opening = visible;
    }

    void clear() {
        snapshot = null;
        previousScreen = null;
        lastAnchorX = lastAnchorY = 0.0f;
        reveal = revealFrom = 0.0f;
        opening = false;
        cachedLayout = null;
    }

    private static float smooth(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    private record Row(String text, float y, int color) {}

    private record Layout(float width, float height, float headerHeight,
                          List<String> headerRows, List<Row> rows, int titleColor,
                          int rarityColor, boolean item, boolean showModel,
                          float footerY) {}
}
