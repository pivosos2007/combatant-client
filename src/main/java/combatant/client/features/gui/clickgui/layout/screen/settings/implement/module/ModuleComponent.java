/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.module;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.StatusRender;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleComponent {
    private static final float PARALLAX_MAX_ANGLE = 4.6f;
    private static final float DESCRIPTION_TEXT_SIZE = 18f;
    private static final int MAX_DESCRIPTION_LINES = 2;
    private static final float DESCRIPTION_TEXT_LEFT = 30f;
    private static final float DESCRIPTION_TEXT_RIGHT = 20f;
    private static final float DESCRIPTION_ICON_X = 13f;
    private static final float DESCRIPTION_ICON_SIZE = 18f;
    private static final float PARALLAX_DEPTH = 4.8f;
    private static final float PARALLAX_PERSPECTIVE = 0.92f;
    private static final float PARALLAX_SCALE_BOOST = 0.012f;
    private static final float PARALLAX_CONTENT_SHIFT = 0.0f;
    private final List<CardHit> hits = new ArrayList<>();
    private final Map<String, Float> stateAnimById = new HashMap<>();
    private final Map<String, Float> hoverAnimById = new HashMap<>();
    private final Map<String, StatusRender> statusRenderById = new HashMap<>();
    private String lastHoverSoundCardId;
    private String hoverSoundCardIdThisFrame;
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

    public void scroll(float mx, float my, float menuX, float menuY, float menuW, float menuH, double amount) {
        float hitX = menuX + 70f;
        float hitY = menuY + 26f;
        float hitW = menuW - 70f + 14f;
        float hitH = menuH - 26f + 30f;
        if (!ClickGuiMath.insideRect(mx, my, hitX, hitY, hitW, hitH)) return;
        scroll += (float) (amount * 20f);
    }

    public void render(float menuX,
                       float menuY,
                       float menuW,
                       float menuH,
                       List<CardEntry> entries) {
        hits.clear();
        hoverSoundCardIdThisFrame = null;
        if (entries.isEmpty()) {
            lastHoverSoundCardId = null;
            return;
        }

        int[] offsets = calculateOffsets(entries);
        int column = 0;
        int maxScroll = 0;
        float columnWidth = 284f;
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        float scissorX = menuX + 70f - 150f;
        float scissorY = menuY + 71f;
        float scissorW = menuW - 70f + 300f;
        float scissorBottom = menuY + menuH - 7f;
        float scissorH = Math.max(1f, scissorBottom - scissorY);
        boolean clipped = ScissorFunction.pushRaw(scissorX, scissorY, scissorW, scissorH);

        for (int i = entries.size() - 1; i >= 0; i--) {
            CardEntry entry = entries.get(i);
            int componentHeight = Math.round(getComponentHeight(entry, columnWidth + 80f) + 18f);

            float x = menuX + 64f + (column * (columnWidth + 96f));
            float y = menuY + 70f + offsets[column] - componentHeight + smoothedScroll;
            float w = columnWidth + 80f;
            float h = componentHeight - 18f;

            if (y + h > scissorY + 2f && scissorBottom - 2f > y) {
                CardHit hit = drawCard(entry, x, y, w, h, palette);
                hits.add(hit);
            }

            offsets[column] -= componentHeight;
            maxScroll = Math.max(maxScroll, offsets[column]);
            column = (column + 1) % 2;
        }

        int clamped = ClickGuiMath.clamp(Math.round(maxScroll - (menuH * 0.5f + 70f)), 0, maxScroll);
        updateScrollbarMetrics(menuX, menuY, menuH, clamped);
        if (draggingScrollbar) {
            scrollToMouse(ClickGuiRenderer.getMouseY());
        }
        scroll = ClickGuiMath.clamp(scroll, -clamped, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);
        if (clipped) ScissorFunction.pop();

        renderScrollbar(menuX, menuY, menuH, clamped, palette);
        lastHoverSoundCardId = hoverSoundCardIdThisFrame;
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return false;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        if (button == combatant.client.input.InputKeys.MOUSE_LEFT) {
            draggingScrollbar = false;
        }
    }

    public CardAction click(float mx, float my, int button) {
        if (isScrollbarHovered(mx, my)) return null;
        for (CardHit hit : hits) {
            if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (button == combatant.client.input.InputKeys.MOUSE_RIGHT && !hit.hasSettings()) continue;
            return new CardAction(
                    hit.getId(),
                    button == combatant.client.input.InputKeys.MOUSE_LEFT,
                    button == combatant.client.input.InputKeys.MOUSE_RIGHT,
                    hit.statusHit(mx, my),
                    hit.bindHit(mx, my),
                    hit.toggleable()
            );
        }
        return null;
    }

    private CardHit drawCard(CardEntry entry, float x, float y, float w, float h, SettingsGuiPalette palette) {
        Module faultModule = ModuleManager.get(entry.getId());
        boolean failed = entry.failed() || SettingErrorView.hasFailure(faultModule);
        boolean quarantined = ErrorHandler.blocked(faultModule);
        float enabledAnim = stateAnimById.compute(entry.getId(), (k, v) -> AnimationUtility.approach(v == null ? 0f : v, entry.enabled() ? 1f : 0f, 0.16f));
        boolean hovered = ClickGuiMath.insideRect(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), x, y, w, h);
        if (hovered) {
            hoverSoundCardIdThisFrame = entry.getId();
            if (!entry.getId().equals(lastHoverSoundCardId)) GuiSound.MODULE_HOVER.feedback(0.60);
        }
        float hoverAnim = hoverAnimById.compute(entry.getId(), (k, v) -> AnimationUtility.approach(v == null ? 0f : v, hovered ? 1f : 0f, 0.18f));
        Parallax parallax = computeParallax(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), x, y, w, h, hoverAnim);
        int brightnessOffset = Math.round(9f * enabledAnim);
        int alphaOffset = 150 + Math.round(105f * enabledAnim);
        String description = normalizeDescription(entry.description());
        float descHeight = descriptionHeight(description, w);
        int cardTopStrong = brightenRgb(palette.moduleCardTopStrong(), brightnessOffset);
        int cardBottomStrong = brightenRgb(palette.moduleCardBottomStrong(), brightnessOffset);
        BindRect bindRect;
        float statusX = x + w - 48f;
        float statusY = y + descHeight + 62f;

        try (var ignored = pushParallax(parallax, x, y, w, h);
             var transition = SettingsCardTransition.beginCard()) {
            // Use the soft-shadow SDF here. roundedRectShadow is a bottom-edge shadow primitive
            // and visually collapses into a moving 1px strip on these tilted cards.
            LayoutRender2D.roundedSoftShadow(
                    x,
                    y + 3.2f,
                    w,
                    h,
                    10.8f,
                    10.4f,
                    0.0f,
                    LayoutRender2D.alpha(0xFF000000, 0.18f + 0.08f * hoverAnim)
            );
            LayoutRender2D.roundedQuad(
                    x, y, w, h,
                    10f,
                    palette.moduleCardTop(),
                    cardTopStrong,
                    palette.moduleCardBottom(),
                    cardBottomStrong
            );
            LayoutRender2D.rectQuad(
                    x, y + descHeight + 50f, w, 1f,
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
                    10f,
                    0f,
                    96f,
                    x + w * 0.18f,
                    y + h * 0.08f,
                    LayoutRender2D.alpha(palette.moduleCardTopStrong(), 0.12f + 0.10f * hoverAnim)
            );

            if (failed) {
                SettingErrorView.warning(x+w-36f,y+12f,20f,1f);
            }
            LayoutRender2D.drawText(
                    BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                    "• " + entry.title(),
                    x + 17.6f,
                    y + 10.8f,
                    26f,
                    SettingsGuiPalette.withAlpha(failed ? 0xFFFF7777 : palette.moduleTitleText(), alphaOffset),
                    false
            );

            drawDescription(description, x, y, w, palette);
            if (!entry.hasSettings()) {
                drawNoSettingsHint(x, y, descHeight, palette);
            }
            bindRect = drawBind(entry, x, y, w, descHeight, palette);
            if (entry.toggleable() && !quarantined) {
                StatusRender status = statusRenderById.computeIfAbsent(entry.getId(), k -> new StatusRender());
                status.render(statusX, statusY, enabledAnim);
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
                32f,
                16f,
                entry.hasSettings(),
                entry.toggleable()
        );
    }

    private void drawDescription(String description, float x, float y, float w, SettingsGuiPalette palette) {
        DescriptionLayout layout = layoutDescription(description, w);
        if (layout.lines().isEmpty()) return;

        float textSize = DESCRIPTION_TEXT_SIZE;
        float textY = y + 38f;
        float textX = x + DESCRIPTION_TEXT_LEFT;
        float lineStep = descriptionLineStep();
        TextRenderer icons = BuiltinFontCatalog.ICONS.renderer(BuiltinFontCatalog.ONEST_REGULAR.renderer());

        LayoutRender2D.drawText(
                icons,
                "J",
                x + DESCRIPTION_ICON_X,
                textY + 1.1f,
                DESCRIPTION_ICON_SIZE,
                palette.moduleDescriptionIcon(),
                false
        );

        for (int i = 0; i < layout.lines().size(); i++) {
            LayoutRender2D.drawText(
                    BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                    layout.lines().get(i),
                    textX,
                    textY + i * lineStep,
                    textSize,
                    palette.moduleDescriptionText(),
                    false
            );
        }
    }

    private BindRect drawBind(CardEntry entry, float x, float y, float w, float descHeight, SettingsGuiPalette palette) {
        String label = entry.bindLabel();
        if (label == null || label.isBlank()) {
            return new BindRect(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }
        float labelW = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), label, 24f);
        float bindX = x + w - 75f - labelW;
        float bindY = y + descHeight + 59.5f;
        float bindW = labelW + 12f;
        float bindH = 20f;
        float hitY = y + descHeight + 65f;
        float hitH = 18f;

        LayoutRender2D.roundedQuad(
                bindX + 0.5f, bindY, bindW, bindH, 6f,
                palette.moduleBindBg0(),
                palette.moduleBindBg1(),
                palette.moduleBindBg2(),
                palette.moduleBindBg3()
        );
        LayoutRender2D.roundedStroke(
                bindX + 0.5f, bindY, bindW, bindH, 6f, 1f,
                palette.moduleBindStroke()
        );
        LayoutRender2D.drawText(
                BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                label,
                x + w - 69f - labelW,
                y + descHeight + 68f,
                24f,
                palette.moduleBindText(),
                false
        );
        return new BindRect(bindX, bindY, bindW, bindH, bindX, hitY, bindW, hitH);
    }

    private void drawNoSettingsHint(float x, float y, float descHeight, SettingsGuiPalette palette) {
        LayoutRender2D.drawText(
                BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                ClickGuiI18n.tr("clickgui.settings.module.no_settings", "No settings"),
                x + 20f,
                y + descHeight + 67f,
                18f,
                palette.moduleNoSettingsText(),
                false
        );
    }

    private void renderScrollbar(float menuX, float menuY, float menuH, int clamped, SettingsGuiPalette palette) {
        updateScrollbarMetrics(menuX, menuY, menuH, clamped);
        if (!scrollbarVisible) return;

        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        boolean hover = isScrollbarHovered(mx, my);
        if (hover || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarY, scrollbarW, scrollbarH, 4f,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 4f,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
        );
    }

    private void updateScrollbarMetrics(float menuX, float menuY, float menuH, int clamped) {
        scrollbarVisible = clamped > 0;
        scrollbarMaxScroll = Math.max(0f, clamped);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }

        float offsetY = 28f;
        scrollbarW = 8f;
        scrollbarX = menuX + 800f - 70f - scrollbarW + 100f;
        scrollbarY = menuY + offsetY + 44f;
        scrollbarH = menuH - offsetY * 2f - 28f;

        float contentHeight = scrollbarMaxScroll;
        float viewHeight = menuH - offsetY * 2f;
        scrollbarThumbH = Math.max(40f, viewHeight * (viewHeight / (contentHeight + viewHeight)));
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

    private float descriptionHeight(String description, float w) {
        return layoutDescription(description, w).height();
    }

    private DescriptionLayout layoutDescription(String description, float w) {
        String normalized = normalizeDescription(description).trim();
        if (normalized.isEmpty()) return new DescriptionLayout(List.of(), 0f);

        float textSize = DESCRIPTION_TEXT_SIZE;
        float maxWidth = Math.max(48f,
                w - (DESCRIPTION_TEXT_LEFT + DESCRIPTION_TEXT_RIGHT));
        List<String> lines = new ArrayList<>(MAX_DESCRIPTION_LINES);
        String[] words = normalized.split("\\s+");
        StringBuilder current = new StringBuilder();
        boolean truncated = false;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isBlank()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), candidate, textSize) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() >= MAX_DESCRIPTION_LINES) {
                    truncated = true;
                    break;
                }
            }

            if (LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), word, textSize) > maxWidth) {
                current.append(LayoutRender2D.fitText(
                        BuiltinFontCatalog.ONEST_REGULAR.renderer(), word, textSize, maxWidth));
            } else {
                current.append(word);
            }
        }

        if (!current.isEmpty() && lines.size() < MAX_DESCRIPTION_LINES) {
            lines.add(current.toString());
        } else if (!current.isEmpty()) {
            truncated = true;
        }

        if (lines.isEmpty()) {
            lines.add(LayoutRender2D.fitText(
                    BuiltinFontCatalog.ONEST_REGULAR.renderer(), normalized, textSize, maxWidth));
        }

        if (truncated || lines.size() == MAX_DESCRIPTION_LINES && joinedLength(lines) < normalized.length()) {
            int last = lines.size() - 1;
            String withEllipsis = lines.get(last).replaceAll("[.\u2026]+$", "") + "…";
            lines.set(last, LayoutRender2D.fitText(
                    BuiltinFontCatalog.ONEST_REGULAR.renderer(), withEllipsis, textSize, maxWidth));
        }

        float lineHeight = LayoutRender2D.textHeight(BuiltinFontCatalog.ONEST_REGULAR.renderer(), textSize);
        float height = Math.max(0f,
                lineHeight - 6f + (lines.size() - 1) * descriptionLineStep());
        return new DescriptionLayout(List.copyOf(lines), height);
    }

    private static int joinedLength(List<String> lines) {
        int length = Math.max(0, lines.size() - 1);
        for (String line : lines) length += line.length();
        return length;
    }

    private float descriptionLineStep() {
        float lineHeight = LayoutRender2D.textHeight(
                BuiltinFontCatalog.ONEST_REGULAR.renderer(), DESCRIPTION_TEXT_SIZE);
        return Math.max(12f, lineHeight - 5f);
    }

    private int getComponentHeight(CardEntry entry, float w) {
        return Math.round(90f + descriptionHeight(normalizeDescription(entry.description()), w));
    }

    private int[] calculateOffsets(List<CardEntry> entries) {
        int[] offsets = new int[2];
        int column = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            int componentHeight = Math.round(getComponentHeight(entries.get(i), 364f) + 18f);
            offsets[column] += componentHeight;
            column = (column + 1) % 2;
        }
        return offsets;
    }

    private record DescriptionLayout(List<String> lines, float height) {
    }

    public record CardEntry(String id,
                            String title,
                            String description,
                            String bindLabel,
                            boolean hasSettings,
                            boolean enabled,
                            boolean toggleable,
                            boolean failed,
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
                         boolean failed,
                         boolean shownInModuleList) {
            this(id, title, description, bindLabel, hasSettings, enabled, toggleable, failed, shownInModuleList, List.of());
        }

        public CardEntry(String id,
                         String title,
                         String description,
                         String bindLabel,
                         boolean hasSettings,
                         boolean enabled,
                         boolean toggleable) {
            this(id, title, description, bindLabel, hasSettings, enabled, toggleable, false, true, List.of());
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
