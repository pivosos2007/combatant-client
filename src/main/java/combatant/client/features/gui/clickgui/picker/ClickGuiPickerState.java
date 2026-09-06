/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.picker;


import combatant.client.features.theme.Theme;
import combatant.client.render.engine.text.*;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsGlassMaterial;
import combatant.client.features.gui.clickgui.settings.PickerDetailOwner;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.*;

public final class ClickGuiPickerState {
    private static final float SCALE = 2f;

    private static final float WINDOW_W = 500f * SCALE;
    private static final float WINDOW_H = 300f * SCALE;
    private static final float PADDING = 8f * SCALE;
    private static final float HEADER_H = 30f * SCALE;

    private static final float SEARCH_H = 16f * SCALE;
    private static final float FILTER_H = SEARCH_H;
    private static final float SEARCH_W = 110f * SCALE;
    private static final float FILTER_W = 90f * SCALE;
    private static final float CLOSE_SIZE = 12f * SCALE;

    private static final float CARD_W = 48f * SCALE;
    private static final float CARD_H = 48f * SCALE;
    private static final float CARD_GAP = 5f * SCALE;
    private static final float GRID_PAD = 0f;
    private static final float SCROLL_STEP = 15f * SCALE;
    private static final float MARQUEE_SPEED = 18f * SCALE;
    private static final float MARQUEE_GAP = 10f * SCALE;
    private static final float MARQUEE_PAUSE_SEC = 0.55f;
    private static final Comparator<PickerEntryData> ENTRY_ORDER = Comparator
            .comparing(PickerEntryData::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PickerEntryData::id, String.CASE_INSENSITIVE_ORDER);
    private final TextListSetting owner;
    private final TextListSetting.PickerMode mode;
    private final PickerCatalog catalog;
    private final String title;
    private final StringBuilder search = new StringBuilder();
    private final List<PickerEntryData> allEntries = new ArrayList<>();
    private final List<PickerEntryData> visibleAll = new ArrayList<>();
    private final List<PickerEntryData> visibleSelected = new ArrayList<>();
    private final Map<String, PickerEntryData> byId = new LinkedHashMap<>();
    private final List<CardHit> cardHits = new ArrayList<>();
    private boolean showAll = true;
    private boolean listening;
    private float modeAnim;
    private float scroll;
    private float smoothScroll;
    private boolean draggingScrollbar;
    private boolean scrollbarVisible;
    private float sbTrackX;
    private float sbTrackY;
    private float sbTrackW;
    private float sbTrackH;
    private float sbThumbH;
    private float contentHeight;
    private float windowX;
    private float windowY;
    private float searchX;
    private float searchY;
    private float filterX;
    private float filterY;
    private float closeX;
    private float closeY;
    private float gridX;
    private float gridY;
    private float gridW;
    private float gridH;
    private float detailX;
    private float detailY;
    private float detailW;
    private float detailH;
    private String focusedId;
    private TextRenderer interBold;
    private String hoveredCardId;
    private long hoveredCardStartMs;
    private float allFilterHoverAnim;
    private float selectedFilterHoverAnim;
    private float openAnim;

    public ClickGuiPickerState(TextListSetting owner, String title, TextListSetting.PickerMode mode) {
        this.owner = owner;
        this.mode = mode == null ? TextListSetting.PickerMode.TEXT : mode;
        this.catalog = PickerCatalogFactory.forMode(this.mode);
        this.title = title == null || title.isBlank() ? "Select" : title;
        reloadEntries();
        refreshFiltered(true);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    public boolean isListening() {
        return listening;
    }

    public void toggleListening() {
        listening = !listening;
    }

    public void stopListening() {
        listening = false;
    }

    public void insertChar(char c) {
        if (Character.isISOControl(c)) return;
        search.append(c);
        refreshFiltered(true);
    }

    public void backspace() {
        if (search.length() <= 0) return;
        search.deleteCharAt(search.length() - 1);
        refreshFiltered(true);
    }

    public void handleMouseMove(float mx, float my) {
        if (!draggingScrollbar) return;
        layout(ClickGuiRenderer.framebufferWidth(), ClickGuiRenderer.framebufferHeight());
        scrollToPosition(my);
    }

    public void handleMouseDown(float mx, float my, int button) {
        if (button != 0) return;

        layout(ClickGuiRenderer.framebufferWidth(), ClickGuiRenderer.framebufferHeight());

        if (!inside(mx, my, windowX, windowY, windowW(), WINDOW_H)) {
            ClickGuiRenderer.closePickerScreen();
            return;
        }

        if (inside(mx, my, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            ClickGuiRenderer.closePickerScreen();
            return;
        }

        if (inside(mx, my, searchX, searchY, SEARCH_W, SEARCH_H)) {
            listening = true;
            return;
        }
        listening = false;

        if (inside(mx, my, filterX, filterY, FILTER_W, FILTER_H)) {
            boolean nextShowAll = mx < filterX + FILTER_W * 0.5f;
            if (showAll != nextShowAll) {
                showAll = nextShowAll;
                scroll = 0f;
                smoothScroll = 0f;
                draggingScrollbar = false;
                clampScroll();
            }
            return;
        }

        PickerDetailOwner detailOwner = detailOwner();
        if (detailOwner != null && inside(mx, my, detailX, detailY, detailW, detailH)) {
            if (detailOwner.mouseClickedPickerDetails(mx, my, button)) return;
        }

        if (handleScrollbarMouseDown(mx, my)) return;

        for (CardHit hit : cardHits) {
            if (!inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            focusedId = hit.getId();
            if (detailOwner != null) {
                detailOwner.onPickerFocusChanged(hit.getId());
                if (!detailOwner.shouldToggleSelectionOnCardClick()) {
                    return;
                }
            }
            toggleSelection(hit.getId());
            return;
        }
    }

    public void handleMouseUp(float mx, float my, int button) {
        PickerDetailOwner detailOwner = detailOwner();
        if (detailOwner != null) {
            detailOwner.mouseReleasedPickerDetails(mx, my, button);
        }
        if (button == 0) {
            draggingScrollbar = false;
        }
    }

    public void scroll(double delta) {
        layout(ClickGuiRenderer.framebufferWidth(), ClickGuiRenderer.framebufferHeight());

        PickerDetailOwner detailOwner = detailOwner();
        if (detailOwner != null) {
            float mx = ClickGuiRenderer.getMouseX();
            float my = ClickGuiRenderer.getMouseY();
            if (inside(mx, my, detailX, detailY, detailW, detailH)
                    && detailOwner.mouseScrolledPickerDetails(mx, my, delta)) {
                return;
            }
        }

        scroll += (float) (delta * SCROLL_STEP);
        clampScroll();
    }

    public void render(int fbw, int fbh) {
        layout(fbw, fbh);
        clampScroll();

        float dt = AnimationUtility.deltaTime();
        openAnim = AnimationUtility.snap(AnimationUtility.approach(openAnim, 1f, dt, 8.5f), 1f, 0.003f);

        float targetMode = showAll ? 0f : 1f;
        modeAnim = AnimationUtility.approach(modeAnim, targetMode, 0.22f);
        modeAnim = AnimationUtility.snap(modeAnim, targetMode, 0.01f);
        smoothScroll = AnimationUtility.approach(smoothScroll, scroll, 0.2f);
        smoothScroll = AnimationUtility.snap(smoothScroll, scroll, 0.05f);

        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        Themes.Theme theme = Theme.theme();

        drawWindow(palette);
        drawHeader(palette);
        drawCloseButton(mx, my, palette, theme);
        drawFilterPill(mx, my, palette, theme);
        drawSearchField(mx, my, palette, theme);
        drawGrid(mx, my, palette, theme);
        drawPickerDetails(mx, my);
        drawScrollbar(mx, my, palette);
    }

    private void drawWindow(SettingsGuiPalette palette) {
        LayoutRender2D.roundedSoftShadow(
                windowX,
                windowY,
                windowW(),
                WINDOW_H,
                8f * SCALE,
                8f * SCALE,
                0.018f,
                palette.menuShadow()
        );
        SettingsGlassMaterial.pickerWorkspace(
                windowX,
                windowY,
                windowW(),
                WINDOW_H,
                0f,
                SCALE,
                palette,
                openAnim
        );
        LayoutRender2D.roundedStroke(
                windowX,
                windowY,
                windowW(),
                WINDOW_H,
                8f * SCALE,
                0.22f * SCALE,
                palette.menuWindowStroke()
        );
    }

    private void drawHeader(SettingsGuiPalette palette) {
        float sepY = windowY + HEADER_H;
        LayoutRender2D.rectQuad(
                windowX + PADDING,
                sepY,
                windowW() - PADDING * 2f,
                0.5f * SCALE,
                palette.menuLineStrong(),
                palette.menuLineMid(),
                palette.menuLineMid(),
                palette.menuLineStrong()
        );

        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                title,
                windowX + PADDING,
                windowY + 7f * SCALE,
                11f * SCALE,
                palette.menuHeaderText(),
                false
        );

        String count = ClickGuiI18n.tr("clickgui.picker.selected_count", "%s selected", owner.getValueSet().size());
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                count,
                windowX + PADDING,
                windowY + 15.5f * SCALE,
                8f * SCALE,
                palette.panelMuted(),
                false
        );
    }

    private void drawCloseButton(float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        boolean hover = inside(mx, my, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        int base = hover ? palette.panelPillActive() : palette.panelPillBase();
        LayoutRender2D.rounded(
                closeX,
                closeY,
                CLOSE_SIZE,
                CLOSE_SIZE,
                3.5f * SCALE,
                base
        );
        LayoutRender2D.roundedStroke(
                closeX,
                closeY,
                CLOSE_SIZE,
                CLOSE_SIZE,
                3.5f * SCALE,
                0.24f * SCALE,
                palette.panelStroke()
        );

        float pad = 3f * SCALE;
        float x1 = closeX + pad;
        float y1 = closeY + pad;
        float x2 = closeX + CLOSE_SIZE - pad;
        float y2 = closeY + CLOSE_SIZE - pad;
        int c = hover ? theme.accent() : palette.panelText();
        ClickGuiRenderer.drawLine(x1, y1, x2, y2, c);
        ClickGuiRenderer.drawLine(x2, y1, x1, y2, c);
    }

    private void drawFilterPill(float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        boolean hover = inside(mx, my, filterX, filterY, FILTER_W, FILTER_H);
        boolean allHover = hover && mx < filterX + FILTER_W * 0.5f;
        boolean selectedHover = hover && !allHover;
        float dt = AnimationUtility.deltaTime();
        allFilterHoverAnim = AnimationUtility.snap(
                AnimationUtility.approach(allFilterHoverAnim, allHover ? 1f : 0f, dt, 12f),
                allHover ? 1f : 0f,
                0.01f
        );
        selectedFilterHoverAnim = AnimationUtility.snap(
                AnimationUtility.approach(selectedFilterHoverAnim, selectedHover ? 1f : 0f, dt, 12f),
                selectedHover ? 1f : 0f,
                0.01f
        );

        int baseLeft = SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgLeft(), 0.34f);
        int baseRight = SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), 0.42f);
        LayoutRender2D.roundedQuad(filterX, filterY, FILTER_W, FILTER_H, 3.5f * SCALE,
                baseLeft, baseRight, baseRight, baseLeft);
        LayoutRender2D.roundedStroke(filterX, filterY, FILTER_W, FILTER_H, 3.5f * SCALE,
                0.22f * SCALE, palette.glassEdgeSoft());

        float pad = 1.2f * SCALE;
        float half = (FILTER_W - pad * 2f) * 0.5f;
        float activeX = filterX + pad + half * modeAnim;
        float activeHover = showAll ? allFilterHoverAnim : selectedFilterHoverAnim;
        int activeA = SettingsGuiPalette.mix(palette.panelPillActive(), theme.accentSoft(), 0.13f + activeHover * 0.08f);
        int activeB = SettingsGuiPalette.mix(palette.panelPillActive(), theme.accent(), 0.08f + activeHover * 0.09f);
        LayoutRender2D.roundedQuad(
                activeX,
                filterY + pad,
                half,
                FILTER_H - pad * 2f,
                3f * SCALE,
                activeA,
                activeB,
                activeB,
                activeA
        );
        LayoutRender2D.roundedStroke(activeX, filterY + pad, half, FILTER_H - pad * 2f,
                3f * SCALE, 0.20f * SCALE,
                SettingsGuiPalette.withAlpha(theme.accentSoft(), 135));

        TextRenderer font = ClickGuiRenderer.getInterRegular();
        float textSize = 7.2f * SCALE;
        String allText = ClickGuiI18n.tr("clickgui.picker.all_short", "All");
        String selectedText = ClickGuiI18n.tr("clickgui.picker.selected", "Selected");
        float allW = ClickGuiRenderer.textWidth(font, allText, textSize);
        float selectedW = ClickGuiRenderer.textWidth(font, selectedText, textSize);
        float textY = filterY + (FILTER_H - ClickGuiRenderer.textHeight(font, textSize)) * 0.5f;

        int allColor = showAll
                ? palette.panelText()
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.22f * allFilterHoverAnim);
        int selColor = !showAll
                ? palette.panelText()
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.22f * selectedFilterHoverAnim);
        float allCenter = filterX + pad + half * 0.5f;
        float selectedCenter = filterX + pad + half + half * 0.5f;
        ClickGuiRenderer.drawText(font, allText, allCenter - allW * 0.5f, textY, textSize, allColor, false);
        ClickGuiRenderer.drawText(font, selectedText, selectedCenter - selectedW * 0.5f, textY, textSize, selColor, false);
    }

    private void drawSearchField(float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        boolean hover = inside(mx, my, searchX, searchY, SEARCH_W, SEARCH_H);
        boolean active = listening;

        int bgL = palette.panelBgLeft();
        int bgR = palette.panelBgRight();
        if (hover) {
            bgL = ClickGuiRenderer.mixColor(bgL, palette.panelPillActive(), 0.18f);
            bgR = ClickGuiRenderer.mixColor(bgR, palette.panelPillActive(), 0.18f);
        }
        if (active) {
            bgL = ClickGuiRenderer.mixColor(bgL, theme.accentSoft(), 0.16f);
            bgR = ClickGuiRenderer.mixColor(bgR, theme.accentSoft(), 0.16f);
        }

        ClickGuiRenderer.drawBlur(searchX, searchY, SEARCH_W, SEARCH_H, 3f * SCALE, palette.panelBlurTint(), 105f / 255f);
        LayoutRender2D.roundedQuad(
                searchX,
                searchY,
                SEARCH_W,
                SEARCH_H,
                3f * SCALE,
                bgL,
                bgR,
                bgR,
                bgL
        );
        LayoutRender2D.roundedStroke(
                searchX,
                searchY,
                SEARCH_W,
                SEARCH_H,
                3f * SCALE,
                0.22f * SCALE,
                palette.panelStroke()
        );

        float dividerX = searchX + SEARCH_W - 13f * SCALE;
        LayoutRender2D.rect(
                dividerX,
                searchY + 3f * SCALE,
                0.5f * SCALE,
                SEARCH_H - 6f * SCALE,
                SettingsGuiPalette.withAlpha(palette.panelMuted(), 95)
        );

        TextRenderer font = ClickGuiRenderer.getInterRegular();
        float textSize = 7.5f * SCALE;
        boolean empty = search.length() == 0;
        boolean caret = active && AnimationUtility.blink(500L);
        String shown = empty && !active ? ClickGuiI18n.tr("clickgui.picker.search", "Search") : search.toString();
        if (active && caret) shown += "|";
        int color = empty && !active ? palette.panelMuted() : palette.panelText();
        float textY = searchY + (SEARCH_H - ClickGuiRenderer.textHeight(font, textSize)) * 0.5f;
        float textX = searchX + 4f * SCALE;
        boolean clipped = ScissorFunction.pushRaw(textX, searchY + SCALE, Math.max(1f, dividerX - textX - 1.5f * SCALE), SEARCH_H - 2f * SCALE);
        ClickGuiRenderer.drawText(font, shown, textX, textY, textSize, color, false);
        if (clipped) ScissorFunction.pop();

        TextRenderer icons = Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        String icon = "s";
        float iconSize = 8f * SCALE;
        float iconW = ClickGuiRenderer.textWidth(icons, icon, iconSize);
        float iconH = ClickGuiRenderer.textHeight(icons, iconSize);
        float iconX = dividerX + ((searchX + SEARCH_W) - dividerX - iconW) * 0.5f;
        float iconY = searchY + (SEARCH_H - iconH) * 0.5f - 0.35f * SCALE;
        int iconColor = active ? palette.panelText() : palette.panelMuted();
        ClickGuiRenderer.drawText(icons, icon, iconX, iconY, iconSize, iconColor, false);
    }

    private void drawGrid(float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        List<PickerEntryData> visible = visibleEntries();
        Set<String> selected = owner.getValueSet();
        int columns = columns();

        cardHits.clear();
        float clipX = gridX + GRID_PAD;
        float clipY = gridY + GRID_PAD;
        float clipW = Math.max(1f, gridW - GRID_PAD * 2f);
        float clipH = Math.max(1f, gridH - GRID_PAD * 2f);
        ClickGuiRenderer.beginPickerIconScissor(clipX, clipY, clipW, clipH);
        boolean clipped = ScissorFunction.pushRaw(clipX, clipY, clipW, clipH);

        float stepX = CARD_W + CARD_GAP;
        float stepY = CARD_H + CARD_GAP;
        float iconScale = 1.65f * SCALE;
        float iconPx = 16f * iconScale;
        TextRenderer font = ClickGuiRenderer.getInterRegular();
        float nameSize = 6.15f * SCALE;
        boolean hasHoveredCard = false;

        for (int i = 0; i < visible.size(); i++) {
            int col = i % columns;
            int row = i / columns;

            float cardX = clipX + col * stepX;
            float cardY = clipY + row * stepY + smoothScroll;
            if (cardY + CARD_H < clipY - 0.5f * SCALE) continue;
            if (cardY > clipY + clipH + 0.5f * SCALE) continue;

            PickerEntryData entry = visible.get(i);
            boolean isSelected = selected.contains(entry.getId());
            boolean hover = inside(mx, my, cardX, cardY, CARD_W, CARD_H);
            if (hover) {
                hasHoveredCard = true;
                if (!entry.getId().equals(hoveredCardId)) {
                    hoveredCardId = entry.getId();
                    hoveredCardStartMs = Util.getMillis();
                }
            }

            int left = SettingsGuiPalette.withAlpha(
                    SettingsGuiPalette.mix(theme.windowBg(), theme.surface(), 0.42f),
                    205
            );
            int right = SettingsGuiPalette.withAlpha(
                    SettingsGuiPalette.darken(SettingsGuiPalette.mix(theme.windowHeader(), theme.windowBg(), 0.55f), 0.26f),
                    205
            );
            if (isSelected) {
                left = ClickGuiRenderer.mixColor(left, theme.accentSoft(), 0.14f);
                right = ClickGuiRenderer.mixColor(right, theme.accentSoft(), 0.26f);
            }
            if (hover) {
                left = ClickGuiRenderer.mixColor(left, palette.moduleCardTopStrong(), 0.40f);
                right = ClickGuiRenderer.mixColor(right, palette.moduleCardBottomStrong(), 0.42f);
            }

            LayoutRender2D.roundedQuad(
                    cardX,
                    cardY,
                    CARD_W,
                    CARD_H,
                    5f * SCALE,
                    left,
                    right,
                    right,
                    left
            );

            int stroke = isSelected ? theme.accentSoft() : palette.moduleDividerStart();
            float strokeW = isSelected ? 0.42f * SCALE : 0.22f * SCALE;
            LayoutRender2D.roundedStroke(cardX, cardY, CARD_W, CARD_H, 5f * SCALE, strokeW, stroke);

            float lineY = cardY + CARD_H - 12f * SCALE;
            LayoutRender2D.horizontalHairline(
                    cardX + 5f * SCALE,
                    lineY,
                    CARD_W - 10f * SCALE,
                    0.28f * SCALE,
                    palette.moduleDividerStart(),
                    palette.moduleDividerEnd()
            );

            ItemStack stack = entry.stack();
            if (stack != null && !stack.isEmpty()) {
                float iconX = cardX + (CARD_W - iconPx) * 0.5f;
                float iconY = cardY + 4.2f * SCALE;
                float iconClipX = cardX + SCALE;
                float iconClipY = cardY + SCALE;
                float iconClipW = CARD_W - 2f * SCALE;
                float iconClipH = Math.max(1f, lineY - iconClipY - 0.75f * SCALE);
                ClickGuiRenderer.queuePickerIcon(
                        stack,
                        iconX,
                        iconY,
                        iconScale,
                        iconClipX,
                        iconClipY,
                        iconClipW,
                        iconClipH
                );
            } else {
                TextRenderer bold = interBold();
                float qSize = 13f * SCALE;
                String q = "?";
                float qW = ClickGuiRenderer.textWidth(bold, q, qSize);
                float qH = ClickGuiRenderer.textHeight(bold, qSize);
                float iconAreaTop = cardY + 3.5f * SCALE;
                float iconAreaBottom = lineY - SCALE;
                float qX = cardX + (CARD_W - qW) * 0.5f;
                float qY = iconAreaTop + ((iconAreaBottom - iconAreaTop) - qH) * 0.5f;
                int qColor = isSelected
                        ? SettingsGuiPalette.withAlpha(palette.moduleTitleText(), 210)
                        : SettingsGuiPalette.withAlpha(palette.panelMuted(), 190);
                ClickGuiRenderer.drawText(bold, q, qX, qY, qSize, qColor, false);
            }

            float textMaxW = CARD_W - 10f * SCALE;
            float textY = lineY + 2f * SCALE;
            int textColor = isSelected ? palette.moduleTitleText() : palette.moduleDescriptionText();
            drawCardLabel(font, entry.label(), cardX, textY, textMaxW, nameSize, textColor, hover);

            if (isSelected) {
                float dot = 3.2f * SCALE;
                LayoutRender2D.rounded(cardX + CARD_W - 6f * SCALE, cardY + 3.5f * SCALE,
                        dot, dot, dot * 0.5f, SettingsGuiPalette.withAlpha(theme.accent(), 235));
            }
            cardHits.add(new CardHit(entry.getId(), cardX, cardY, CARD_W, CARD_H));
        }
        if (!hasHoveredCard) {
            hoveredCardId = null;
            hoveredCardStartMs = 0L;
        }

        if (clipped) {
            ScissorFunction.pop();
        }
        ClickGuiRenderer.endPickerIconScissor();

        if (visible.isEmpty()) {
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    showAll
                            ? ClickGuiI18n.tr("clickgui.picker.empty", "No entries")
                            : ClickGuiI18n.tr("clickgui.picker.empty_selected", "Nothing selected"),
                    clipX + 2f * SCALE,
                    clipY + 2f * SCALE,
                    7.2f * SCALE,
                    palette.panelMuted(),
                    false
            );
        }
    }

    private void drawScrollbar(float mx, float my, SettingsGuiPalette palette) {
        float viewportH = Math.max(1f, gridH - GRID_PAD * 2f);
        if (!scrollbarVisible || contentHeight <= viewportH + 0.5f) {
            scrollbarVisible = false;
            return;
        }

        float baseW = 2.25f * SCALE;
        boolean hover = isScrollbarHovered(mx, my) || draggingScrollbar;
        if (hover) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }
        float trackW = hover ? 3.2f * SCALE : baseW;
        float trackX = gridX + gridW - trackW - SCALE;
        float trackY = gridY + SCALE;
        float trackH = Math.max(12f * SCALE, gridH - 2f * SCALE);

        sbTrackX = trackX;
        sbTrackY = trackY;
        sbTrackW = trackW;
        sbTrackH = trackH;

        LayoutRender2D.roundedQuad(
                trackX,
                trackY,
                trackW,
                trackH,
                2f * SCALE,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );

        float thumbH = Math.max(16f * SCALE, trackH * (viewportH / contentHeight));
        sbThumbH = thumbH;
        float ratio = contentHeight <= viewportH ? 0f : (-smoothScroll / (contentHeight - viewportH));
        float thumbY = trackY + (trackH - thumbH) * clamp(ratio, 0f, 1f);

        LayoutRender2D.roundedQuad(
                trackX,
                thumbY,
                trackW,
                thumbH,
                2f * SCALE,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
        );
    }

    private void drawCardLabel(TextRenderer font, String label, float cardX, float textY, float maxW, float size, int color, boolean hover) {
        String text = label == null ? "" : label;
        float fullW = ClickGuiRenderer.textWidth(font, text, size);
        if (!hover || fullW <= maxW + 0.5f * SCALE) {
            String shown = ClickGuiRenderer.fitText(font, text, size, maxW);
            float shownW = ClickGuiRenderer.textWidth(font, shown, size);
            ClickGuiRenderer.drawText(font, shown, cardX + 5f * SCALE, textY, size, color, false);
            return;
        }

        float clipX = cardX + 5f * SCALE;
        float textH = Math.max(1f, ClickGuiRenderer.textHeight(font, size));
        boolean clipped = ScissorFunction.pushRaw(clipX, textY - SCALE, maxW, textH + 2f * SCALE);
        float elapsed = Math.max(0f, (Util.getMillis() - hoveredCardStartMs) / 1000f);
        float cycleDistance = fullW + MARQUEE_GAP;
        float cycleTime = MARQUEE_PAUSE_SEC + cycleDistance / Math.max(1f, MARQUEE_SPEED);
        float t = elapsed % cycleTime;
        float offset = t <= MARQUEE_PAUSE_SEC ? 0f : -(t - MARQUEE_PAUSE_SEC) * MARQUEE_SPEED;

        ClickGuiRenderer.drawText(font, text, clipX + offset, textY, size, color, false);
        if (cycleDistance > maxW * 0.5f) {
            ClickGuiRenderer.drawText(font, text, clipX + offset + cycleDistance, textY, size, color, false);
        }
        if (clipped) ScissorFunction.pop();
    }

    private float windowW() {
        PickerDetailOwner detailOwner = detailOwner();
        return WINDOW_W + (detailOwner != null ? detailOwner.pickerDetailWidth() : 0f);
    }

    private PickerDetailOwner detailOwner() {
        return owner instanceof PickerDetailOwner detailOwner ? detailOwner : null;
    }

    private void drawPickerDetails(float mx, float my) {
        PickerDetailOwner detailOwner = detailOwner();
        if (detailOwner == null) return;
        detailOwner.renderPickerDetails(detailX, detailY, detailW, detailH, focusedId, mx, my);
    }

    private void layout(int fbw, int fbh) {
        windowX = (fbw - windowW()) * 0.5f;
        windowY = (fbh - WINDOW_H) * 0.5f;

        closeX = windowX + windowW() - PADDING - CLOSE_SIZE;
        closeY = windowY + 7f * SCALE;

        searchX = closeX - 3f * SCALE - SEARCH_W;
        searchY = windowY + 6f * SCALE;
        filterX = searchX - 3f * SCALE - FILTER_W;
        filterY = searchY;

        gridX = windowX + PADDING;
        gridY = windowY + HEADER_H + 6f * SCALE;
        PickerDetailOwner detailOwner = detailOwner();
        detailW = detailOwner != null ? detailOwner.pickerDetailWidth() : 0f;
        detailY = gridY;
        detailH = WINDOW_H - (gridY - windowY) - PADDING;
        detailX = windowX + windowW() - PADDING - detailW;
        gridW = windowW() - PADDING * 2f - (detailOwner != null ? detailW + CARD_GAP : 0f);
        gridH = detailH;
    }

    private void reloadEntries() {
        allEntries.clear();
        allEntries.addAll(catalog.entries(owner));
    }

    private void refreshFiltered(boolean resetScroll) {
        String needle = search.toString().trim().toLowerCase(Locale.ROOT);

        byId.clear();
        for (PickerEntryData entry : allEntries) {
            byId.put(entry.getId(), entry);
        }

        visibleAll.clear();
        for (PickerEntryData entry : allEntries) {
            if (!matches(entry, needle)) continue;
            visibleAll.add(entry);
        }
        visibleAll.sort(ENTRY_ORDER);

        visibleSelected.clear();
        for (String id : owner.getValueSet()) {
            PickerEntryData entry = byId.get(id);
            if (entry == null) {
                entry = new PickerEntryData(id, id, ItemStack.EMPTY);
            }
            if (!matches(entry, needle)) continue;
            visibleSelected.add(entry);
        }
        visibleSelected.sort(ENTRY_ORDER);

        if (resetScroll) {
            scroll = 0f;
            smoothScroll = 0f;
            draggingScrollbar = false;
        } else {
            clampScroll();
        }
    }

    private List<PickerEntryData> visibleEntries() {
        return showAll ? visibleAll : visibleSelected;
    }

    private void toggleSelection(String id) {
        Set<String> next = owner.getValueSet();
        if (next.contains(id)) {
            next.remove(id);
        } else {
            next.add(owner.normalizeEntry(id));
        }
        owner.setValueSet(next);
        if (!next.contains(focusedId)) {
            focusedId = next.isEmpty() ? null : next.iterator().next();
            PickerDetailOwner detailOwner = detailOwner();
            if (detailOwner != null && focusedId != null) detailOwner.onPickerFocusChanged(focusedId);
        }
        refreshFiltered(false);
    }

    private boolean matches(PickerEntryData entry, String needle) {
        if (needle == null || needle.isBlank()) return true;
        String id = entry.getId() == null ? "" : entry.getId().toLowerCase(Locale.ROOT);
        String label = entry.label() == null ? "" : entry.label().toLowerCase(Locale.ROOT);
        return id.contains(needle) || label.contains(needle);
    }

    private int columns() {
        float available = Math.max(1f, gridW - GRID_PAD * 2f);
        return Math.max(1, (int) Math.floor((available + CARD_GAP) / (CARD_W + CARD_GAP)));
    }

    private float computeContentHeight(int entriesCount) {
        if (entriesCount <= 0) return 0f;
        int cols = columns();
        int rows = (entriesCount + cols - 1) / cols;
        return rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP;
    }

    private void clampScroll() {
        contentHeight = computeContentHeight(visibleEntries().size());
        float viewportH = Math.max(1f, gridH - GRID_PAD * 2f);
        float minScroll = Math.min(0f, viewportH - contentHeight);
        scroll = clamp(scroll, minScroll, 0f);
        smoothScroll = clamp(smoothScroll, minScroll, 0f);
        scrollbarVisible = contentHeight > viewportH + 0.5f;
    }

    private boolean handleScrollbarMouseDown(float mx, float my) {
        if (!scrollbarVisible) return false;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollToPosition(my);
        return true;
    }

    private boolean isScrollbarHovered(float mx, float my) {
        if (!scrollbarVisible) return false;
        float pad = 3f * SCALE;
        return mx >= sbTrackX - pad &&
                mx <= sbTrackX + sbTrackW + pad &&
                my >= sbTrackY - pad &&
                my <= sbTrackY + sbTrackH + pad;
    }

    private void scrollToPosition(float mouseY) {
        float viewportH = Math.max(1f, gridH - GRID_PAD * 2f);
        if (contentHeight <= viewportH + 0.5f) {
            scroll = 0f;
            smoothScroll = 0f;
            return;
        }
        float trackSpan = sbTrackH - sbThumbH;
        if (trackSpan <= 0f) return;
        float rel = mouseY - sbTrackY - sbThumbH * 0.5f;
        float t = clamp(rel / trackSpan, 0f, 1f);
        float scrollRange = contentHeight - viewportH;
        scroll = -scrollRange * t;
        smoothScroll = scroll;
    }

    private TextRenderer interBold() {
        if (interBold != null) return interBold;
        FontFamily family = Fonts.getFamily("Inter");
        if (family == null) {
            interBold = ClickGuiRenderer.getInterRegular();
            return interBold;
        }
        FontFace face = family.get(FontInfo.Type.Bold);
        if (face == null) {
            interBold = ClickGuiRenderer.getInterRegular();
            return interBold;
        }
        interBold = Fonts.renderer(face, ClickGuiRenderer.getInterRegular());
        return interBold;
    }

    private record CardHit(String id, float x, float y, float w, float h) {
        public String getId() {
            return id;
        }
    }
}
