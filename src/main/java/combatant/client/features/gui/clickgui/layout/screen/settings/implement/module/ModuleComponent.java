/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.module;

import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.StatusRender;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleComponent {
    private static final float PARALLAX_MAX_ANGLE = 4.6f;
    private static final float PARALLAX_DEPTH = 4.8f;
    private static final float PARALLAX_PERSPECTIVE = 0.92f;
    private static final float PARALLAX_SCALE_BOOST = 0.012f;
    private static final float PARALLAX_CONTENT_SHIFT = 0.0f;
    private final List<CardHit> hits = new ArrayList<>();
    private final Map<String, Float> stateAnimById = new HashMap<>();
    private final Map<String, Float> hoverAnimById = new HashMap<>();
    private final Map<String, StatusRender> statusRenderById = new HashMap<>();
    private float scroll;
    private float smoothedScroll;
    private boolean scrollbarVisible;
    private boolean draggingScrollbar;
    private float scrollbarX;
    private float scrollbarY;
    private float scrollbarW;
    private float scrollbarH;
    private float scrollbarThumbY;
    private float scrollbarThumbH;
    private float scrollbarMaxScroll;
    private float scrollbarDragOffset;

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return ClickGuiI18n.tr("clickgui.settings.module.description_missing", "Module description is missing.");
        }
        return description;
    }

    private static int brightenRgb(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + delta);
        int b = Math.min(255, (color & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static Parallax computeParallax(float mx, float my, float x, float y, float w, float h, float hover) {
        float ease = smooth(Math.max(0f, Math.min(1f, hover)));
        if (ease <= 0.001f || w <= 0f || h <= 0f) return Parallax.NONE;
        float nx = clamp((mx - x) / w * 2f - 1f, -1f, 1f);
        float ny = clamp((my - y) / h * 2f - 1f, -1f, 1f);
        return new Parallax(
                -nx * PARALLAX_MAX_ANGLE * ease,
                ny * PARALLAX_MAX_ANGLE * ease,
                1f + PARALLAX_SCALE_BOOST * ease,
                nx * PARALLAX_CONTENT_SHIFT * ease,
                ny * PARALLAX_CONTENT_SHIFT * ease,
                true
        );
    }

    private static RenderWarpStack.Scope pushParallax(Parallax p, float x, float y, float w, float h) {
        if (p == null || !p.active()) return Renderer2D.pushWarp(null);
        return Renderer2D.pushPerspectiveWarp(x, y, w, h, p.yawDeg(), p.pitchDeg(), 0f, PARALLAX_DEPTH, PARALLAX_PERSPECTIVE, p.scale());
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    public void scroll(float mx, float my, float menuX, float menuY, float menuW, float menuH, double amount, float scale) {
        float hitX = menuX + 35f * scale;
        float hitY = menuY + 13f * scale;
        float hitW = menuW - 35f * scale + 7f * scale;
        float hitH = menuH - 13f * scale + 15f * scale;
        if (!ClickGuiMath.insideRect(mx, my, hitX, hitY, hitW, hitH)) return;
        scroll += (float) (amount * 20f);
    }

    public void render(float menuX,
                       float menuY,
                       float menuW,
                       float menuH,
                       List<CardEntry> entries,
                       float scale) {
        hits.clear();
        if (entries.isEmpty()) return;

        int[] offsets = calculateOffsets(entries, scale);
        int column = 0;
        int maxScroll = 0;
        float columnWidth = 142f * scale;
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        float scissorX = menuX + 35f * scale - 75f * scale;
        float scissorY = menuY + 35.5f * scale;
        float scissorW = menuW - 35f * scale + 150f * scale;
        float scissorBottom = menuY + menuH - 3.5f * scale;
        float scissorH = Math.max(1f, scissorBottom - scissorY);
        boolean clipped = ScissorFunction.pushRaw(scissorX, scissorY, scissorW, scissorH);

        for (int i = entries.size() - 1; i >= 0; i--) {
            CardEntry entry = entries.get(i);
            int componentHeight = Math.round(getComponentHeight(entry, columnWidth + 40f * scale, scale) + 9f * scale);

            float x = menuX + 32f * scale + (column * (columnWidth + 48f * scale));
            float y = menuY + 35f * scale + offsets[column] - componentHeight + smoothedScroll;
            float w = columnWidth + 40f * scale;
            float h = componentHeight - 9f * scale;

            if (y + h > scissorY + 1.0f * scale && scissorBottom - 1.0f * scale > y) {
                CardHit hit = drawCard(entry, x, y, w, h, scale, palette);
                hits.add(hit);
            }

            offsets[column] -= componentHeight;
            maxScroll = Math.max(maxScroll, offsets[column]);
            column = (column + 1) % 2;
        }

        int clamped = ClickGuiMath.clamp(Math.round(maxScroll - (menuH * 0.5f + 35f * scale)), 0, maxScroll);
        updateScrollbarMetrics(menuX, menuY, menuH, clamped, scale);
        if (draggingScrollbar) {
            scrollToMouse(ClickGuiRenderer.getMouseY());
        }
        scroll = ClickGuiMath.clamp(scroll, -clamped, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);
        if (clipped) ScissorFunction.pop();

        renderScrollbar(menuX, menuY, menuH, clamped, scale, palette);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != 0) return false;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
    }

    public CardAction click(float mx, float my, int button) {
        if (isScrollbarHovered(mx, my)) return null;
        for (CardHit hit : hits) {
            if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (button == 1 && !hit.hasSettings()) continue;
            return new CardAction(
                    hit.getId(),
                    button == 0,
                    button == 1,
                    hit.statusHit(mx, my),
                    hit.bindHit(mx, my),
                    hit.toggleable()
            );
        }
        return null;
    }

    private CardHit drawCard(CardEntry entry, float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
        float enabledAnim = stateAnimById.compute(entry.getId(), (k, v) -> AnimationUtility.approach(v == null ? 0f : v, entry.enabled() ? 1f : 0f, 0.16f));
        boolean hovered = ClickGuiMath.insideRect(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), x, y, w, h);
        float hoverAnim = hoverAnimById.compute(entry.getId(), (k, v) -> AnimationUtility.approach(v == null ? 0f : v, hovered ? 1f : 0f, 0.18f));
        Parallax parallax = computeParallax(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), x, y, w, h, hoverAnim);
        int brightnessOffset = Math.round(9f * enabledAnim);
        int alphaOffset = 150 + Math.round(105f * enabledAnim);
        String description = normalizeDescription(entry.description());
        float descHeight = descriptionHeight(description, w, scale);
        int cardTopStrong = brightenRgb(palette.moduleCardTopStrong(), brightnessOffset);
        int cardBottomStrong = brightenRgb(palette.moduleCardBottomStrong(), brightnessOffset);
        BindRect bindRect;
        float statusX = x + w - 24f * scale;
        float statusY = y + descHeight + 31f * scale;

        ClickGuiRenderer.drawRoundedRectShadow(
                x,
                y + 1.5f * scale,
                w,
                h,
                5.4f * scale,
                9.0f * scale,
                1.0f * scale,
                LayoutRender2D.alpha(0xFF000000, 0.22f + 0.10f * hoverAnim)
        );
        try (var ignored = pushParallax(parallax, x, y, w, h)) {
            ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
            LayoutRender2D.roundedQuad(
                    x, y, w, h,
                    5f * scale,
                    palette.moduleCardTop(),
                    cardTopStrong,
                    palette.moduleCardBottom(),
                    cardBottomStrong
            );
            LayoutRender2D.rectQuad(
                    x, y + descHeight + 25f * scale, w, 0.5f * scale,
                    palette.moduleDividerStart(),
                    palette.moduleDividerEnd(),
                    palette.moduleDividerEnd(),
                    palette.moduleDividerStart()
            );
            Renderer2D.COLOR.radialGlowMasked(
                    x,
                    y,
                    w,
                    h,
                    5f * scale,
                    0f,
                    48f * scale,
                    x + w * 0.18f,
                    y + h * 0.08f,
                    LayoutRender2D.alpha(palette.moduleCardTopStrong(), 0.12f + 0.10f * hoverAnim)
            );

                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        "• " + entry.title(),
                        x + 8.8f * scale,
                        y + 5.4f * scale,
                        13f * scale,
                        SettingsGuiPalette.withAlpha(palette.moduleTitleText(), alphaOffset),
                        false
                );

                drawDescription(description, x, y, w, scale, palette);
                if (!entry.hasSettings()) {
                    drawNoSettingsHint(x, y, descHeight, scale, palette);
                }
                bindRect = drawBind(entry, x, y, w, descHeight, scale, palette);
                if (entry.toggleable()) {
                    StatusRender status = statusRenderById.computeIfAbsent(entry.getId(), k -> new StatusRender());
                    status.render(statusX, statusY, enabledAnim, scale);
                }
        }

        return new CardHit(
                entry.getId(),
                x,
                y,
                w,
                h,
                bindRect.hitX(),
                bindRect.hitY(),
                bindRect.hitW(),
                bindRect.hitH(),
                statusX,
                statusY,
                16f * scale,
                8f * scale,
                entry.hasSettings(),
                entry.toggleable()
        );
    }

    private void drawDescription(String description, float x, float y, float w, float scale, SettingsGuiPalette palette) {
        float maxWidth = w - 25f * scale;
        float currentX = x + 10f * scale;
        float currentY = y + 19f * scale;
        String[] words = description.split(" ");
        StringBuilder line = new StringBuilder();
        int currentLine = 1;
        float textSize = 10f * scale;
        TextRenderer icons = Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        int iconColor = palette.moduleDescriptionIcon();
        int textColor = palette.moduleDescriptionText();

        for (String word : words) {
            float wordWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), word + " ", textSize);
            if (currentX + wordWidth > x + maxWidth) {
                if (currentLine == 1) {
                    ClickGuiRenderer.drawText(
                            icons,
                            "J",
                            x + 6.5f * scale,
                            currentY + 0.75f * scale,
                            9f * scale,
                            iconColor,
                            false
                    );
                    ClickGuiRenderer.drawText(
                            ClickGuiRenderer.getInterRegular(),
                            line.toString(),
                            x + 15f * scale,
                            currentY,
                            textSize,
                            textColor,
                            false
                    );
                } else {
                    ClickGuiRenderer.drawText(
                            ClickGuiRenderer.getInterRegular(),
                            line.toString(),
                            x + 5f * scale,
                            currentY,
                            textSize,
                            textColor,
                            false
                    );
                }
                line = new StringBuilder();
                currentY += ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), textSize) - 5.75f * scale;
                currentX = x + 10f * scale;
                currentLine++;
            }
            line.append(word).append(" ");
            currentX += wordWidth;
        }

        if (!line.isEmpty()) {
            if (currentLine == 1) {
                ClickGuiRenderer.drawText(
                        icons,
                        "J",
                        x + 6.5f * scale,
                        currentY + 0.75f * scale,
                        9f * scale,
                        iconColor,
                        false
                );
                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        line.toString(),
                        x + 15f * scale,
                        currentY,
                        textSize,
                        textColor,
                        false
                );
            } else {
                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        line.toString(),
                        x + 7f * scale,
                        currentY,
                        textSize,
                        textColor,
                        false
                );
            }
        }
    }

    private BindRect drawBind(CardEntry entry, float x, float y, float w, float descHeight, float scale, SettingsGuiPalette palette) {
        String label = entry.bindLabel();
        if (label == null || label.isBlank()) {
            return new BindRect(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }
        float labelW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), label, 12f * scale);
        float bindX = x + w - 37.5f * scale - labelW;
        float bindY = y + descHeight + 29.75f * scale;
        float bindW = labelW + 6f * scale;
        float bindH = 10f * scale;
        float hitY = y + descHeight + 32.5f * scale;
        float hitH = 9f * scale;

        LayoutRender2D.roundedQuad(
                bindX + 0.25f * scale, bindY, bindW, bindH, 3f * scale,
                palette.moduleBindBg0(),
                palette.moduleBindBg1(),
                palette.moduleBindBg2(),
                palette.moduleBindBg3()
        );
        LayoutRender2D.roundedStroke(
                bindX + 0.25f * scale, bindY, bindW, bindH, 3f * scale, 0.5f * scale,
                palette.moduleBindStroke()
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                label,
                x + w - 34.5f * scale - labelW,
                y + descHeight + 34f * scale,
                12f * scale,
                palette.moduleBindText(),
                false
        );
        return new BindRect(bindX, bindY, bindW, bindH, bindX, hitY, bindW, hitH);
    }

    private void drawNoSettingsHint(float x, float y, float descHeight, float scale, SettingsGuiPalette palette) {
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiI18n.tr("clickgui.settings.module.no_settings", "No settings"),
                x + 10f * scale,
                y + descHeight + 33.5f * scale,
                9f * scale,
                palette.moduleNoSettingsText(),
                false
        );
    }

    private void renderScrollbar(float menuX, float menuY, float menuH, int clamped, float scale, SettingsGuiPalette palette) {
        updateScrollbarMetrics(menuX, menuY, menuH, clamped, scale);
        if (!scrollbarVisible) return;

        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        boolean hover = isScrollbarHovered(mx, my);
        if (hover || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarY, scrollbarW, scrollbarH, 2f * scale,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 2f * scale,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
        );
    }

    private void updateScrollbarMetrics(float menuX, float menuY, float menuH, int clamped, float scale) {
        scrollbarVisible = clamped > 0;
        scrollbarMaxScroll = Math.max(0f, clamped);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }

        float offsetY = 14f * scale;
        scrollbarW = 4f * scale;
        scrollbarX = menuX + 400f * scale - 35f * scale - scrollbarW + 50f * scale;
        scrollbarY = menuY + offsetY + 22f * scale;
        scrollbarH = menuH - offsetY * 2f - 14f * scale;

        float contentHeight = scrollbarMaxScroll;
        float viewHeight = menuH - offsetY * 2f;
        scrollbarThumbH = Math.max(20f * scale, viewHeight * (viewHeight / (contentHeight + viewHeight)));
        float scrollRatio = contentHeight <= 0f ? 0f : (-smoothedScroll / contentHeight);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(scrollRatio, 0f, 1f);
    }

    private boolean isScrollbarHovered(float mx, float my) {
        if (!scrollbarVisible) return false;
        float pad = 4f;
        return ClickGuiMath.insideRect(mx, my, scrollbarX - pad, scrollbarY - pad, scrollbarW + pad * 2f, scrollbarH + pad * 2f);
    }

    private void scrollToMouse(float my) {
        if (!scrollbarVisible || scrollbarMaxScroll <= 0f) return;
        float span = scrollbarH - scrollbarThumbH;
        if (span <= 0.5f) return;
        float thumbTop = AnimationUtility.clamp(my - scrollbarDragOffset, scrollbarY, scrollbarY + span);
        float ratio = (thumbTop - scrollbarY) / span;
        scroll = -scrollbarMaxScroll * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    private float descriptionHeight(String description, float w, float scale) {
        float maxWidth = w - 25f * scale;
        float currentX = 0f;
        int lineCount = 1;
        for (String word : description.split(" ")) {
            float wordW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), word + " ", 10f * scale);
            if (currentX + wordW > maxWidth) {
                lineCount++;
                currentX = 0f;
            }
            currentX += wordW;
        }
        float lineH = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), 10f * scale);
        float compactStep = Math.max(scale, lineH - 5.75f * scale);
        return Math.max(0f, lineH - 4f * scale + (lineCount - 1) * compactStep);
    }

    private int getComponentHeight(CardEntry entry, float w, float scale) {
        return Math.round(45f * scale + descriptionHeight(normalizeDescription(entry.description()), w, scale));
    }

    private int[] calculateOffsets(List<CardEntry> entries, float scale) {
        int[] offsets = new int[2];
        int column = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            int componentHeight = Math.round(getComponentHeight(entries.get(i), 182f * scale, scale) + 9f * scale);
            offsets[column] += componentHeight;
            column = (column + 1) % 2;
        }
        return offsets;
    }

    public record CardEntry(String id,
                            String title,
                            String description,
                            String bindLabel,
                            boolean hasSettings,
                            boolean enabled,
                            boolean toggleable,
                            boolean shownInModuleList,
                            List<String> searchAliases) {
        public CardEntry {
            searchAliases = searchAliases == null ? List.of() : List.copyOf(searchAliases);
        }

        public CardEntry(String id,
                         String title,
                         String description,
                         String bindLabel,
                         boolean hasSettings,
                         boolean enabled,
                         boolean toggleable,
                         boolean shownInModuleList) {
            this(id, title, description, bindLabel, hasSettings, enabled, toggleable, shownInModuleList, List.of());
        }

        public CardEntry(String id,
                         String title,
                         String description,
                         String bindLabel,
                         boolean hasSettings,
                         boolean enabled,
                         boolean toggleable) {
            this(id, title, description, bindLabel, hasSettings, enabled, toggleable, true, List.of());
        }

        public String getId() {
            return id;
        }
    }

    public record CardAction(String id,
                             boolean leftClick,
                             boolean rightClick,
                             boolean statusClick,
                             boolean bindClick,
                             boolean toggleable) {
        public String getId() {
            return id;
        }
    }

    private record Parallax(float yawDeg, float pitchDeg, float scale, float shiftX, float shiftY, boolean active) {
        private static final Parallax NONE = new Parallax(0f, 0f, 1f, 0f, 0f, false);
    }

    private record BindRect(float x, float y, float w, float h, float hitX, float hitY, float hitW, float hitH) {
    }

    private record CardHit(String id,
                           float x,
                           float y,
                           float w,
                           float h,
                           float bindX,
                           float bindY,
                           float bindW,
                           float bindH,
                           float statusX,
                           float statusY,
                           float statusW,
                           float statusH,
                           boolean hasSettings,
                           boolean toggleable) {
        public String getId() {
            return id;
        }

        boolean statusHit(float mx, float my) {
            if (!toggleable) return false;
            return ClickGuiMath.insideRect(mx, my, statusX, statusY, statusW, statusH);
        }

        boolean bindHit(float mx, float my) {
            return ClickGuiMath.insideRect(mx, my, bindX, bindY, bindW, bindH);
        }
    }
}
