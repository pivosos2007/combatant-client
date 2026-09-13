/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations.RelationPlayerCardComponent.CardHit;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.features.relations.StaffHeuristicsConfig;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal Relations editor: tabs, one list, direct add/remove and staff heuristics.
 */
public final class RelationsComponent {
    private static final long STATUS_DURATION_MS = 2200L;
    private static final String I18N = "clickgui.settings.relations.";

    private final RelationPlayerCardComponent cardComponent = new RelationPlayerCardComponent();
    private final List<CardEntryHit> cardHits = new ArrayList<>();
    private final List<ChipHit> chipHits = new ArrayList<>();

    private RelationTab tab = RelationTab.FRIENDS;
    private ActiveField activeField = ActiveField.NONE;
    private String selectedName;
    private String playerInput = "";
    private String prefixInput = "";
    private String suffixInput = "";
    private String containsInput = "";
    private String statusMessage;
    private long statusUntilMs;

    private Rect friendsPill = Rect.ZERO;
    private Rect enemiesPill = Rect.ZERO;
    private Rect staffPill = Rect.ZERO;
    private Rect playerInputRect = Rect.ZERO;
    private Rect addTypedButton = Rect.ZERO;
    private Rect enabledToggle = Rect.ZERO;
    private Rect prefixInputRect = Rect.ZERO;
    private Rect suffixInputRect = Rect.ZERO;
    private Rect containsInputRect = Rect.ZERO;
    private Rect prefixAddButton = Rect.ZERO;
    private Rect suffixAddButton = Rect.ZERO;
    private Rect containsAddButton = Rect.ZERO;

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
    private float listX;
    private float listY;
    private float listW;
    private float listH;

    private float modeAnim;
    private float friendsHoverAnim;
    private float enemiesHoverAnim;
    private float staffHoverAnim;
    private float addHoverAnim;

    private static boolean movementInputBlocked;

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
        activeField = ActiveField.NONE;
        movementInputBlocked = false;
    }

    public static boolean isMovementInputBlocked() {
        return movementInputBlocked;
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        cardHits.clear();
        chipHits.clear();
        resetTransientRects();
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        movementInputBlocked = activeField != ActiveField.NONE;

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;

        List<String> entries = filteredEntries(tab.entries());
        validateSelection(entries);

        float toolbarH = 18f * scale;
        renderToolbar(areaX, areaY, areaW, toolbarH, mx, my, scale, palette);

        float workspaceY = areaY + toolbarH + 7f * scale;
        float workspaceH = Math.max(1f, areaY + areaH - workspaceY);
        renderMainPanel(areaX, workspaceY, areaW, workspaceH, entries, mx, my, scale, palette);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != 0 || !isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        if (button == 0) draggingScrollbar = false;
    }

    public void scroll(float mx, float my, double amount) {
        if (!ClickGuiMath.insideRect(mx, my, listX, listY, listW, listH)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean click(float mx, float my, int button) {
        if (button != 0) return false;

        if (friendsPill.contains(mx, my)) return switchTab(RelationTab.FRIENDS);
        if (enemiesPill.contains(mx, my)) return switchTab(RelationTab.ENEMIES);
        if (staffPill.contains(mx, my)) return switchTab(RelationTab.STAFF);

        if (playerInputRect.contains(mx, my)) return focusField(ActiveField.PLAYER);
        if (addTypedButton.contains(mx, my)) {
            commitPlayerInput();
            return true;
        }

        if (tab == RelationTab.STAFF) {
            if (enabledToggle.contains(mx, my)) {
                StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
                cfg.setEnabled(!cfg.enabled());
                return true;
            }
            if (prefixInputRect.contains(mx, my)) return focusField(ActiveField.PREFIX);
            if (suffixInputRect.contains(mx, my)) return focusField(ActiveField.SUFFIX);
            if (containsInputRect.contains(mx, my)) return focusField(ActiveField.CONTAINS);
            if (prefixAddButton.contains(mx, my)) return commitHeuristic(ActiveField.PREFIX);
            if (suffixAddButton.contains(mx, my)) return commitHeuristic(ActiveField.SUFFIX);
            if (containsAddButton.contains(mx, my)) return commitHeuristic(ActiveField.CONTAINS);

            for (ChipHit hit : chipHits) {
                if (!hit.delete().contains(mx, my)) continue;
                removeHeuristic(hit.kind(), hit.value());
                return true;
            }
        }

        for (CardEntryHit entryHit : cardHits) {
            CardHit hit = entryHit.hit();
            if (hit.containsDelete(mx, my)) {
                String removed = entryHit.name();
                if (tab.remove(removed)) {
                    if (equalsIgnoreCase(selectedName, removed)) selectedName = null;
                    setStatus(tr("status.removed", "Removed: %s", removed));
                }
                return true;
            }
            if (hit.contains(mx, my)) {
                selectedName = equalsIgnoreCase(selectedName, entryHit.name()) ? null : entryHit.name();
                activeField = ActiveField.NONE;
                movementInputBlocked = false;
                return true;
            }
        }

        activeField = ActiveField.NONE;
        movementInputBlocked = false;
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeField == ActiveField.NONE) return false;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            activeField = ActiveField.NONE;
            movementInputBlocked = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (activeField == ActiveField.PLAYER) commitPlayerInput();
            else commitHeuristic(activeField);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            setFieldText(activeField, dropLast(fieldText(activeField)));
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            appendToField(activeField, ClipboardUtil.get());
            return true;
        }
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (activeField == ActiveField.NONE) return false;
        if (chr >= 32 && chr != 127) appendToField(activeField, String.valueOf(chr));
        return true;
    }

    private void renderToolbar(float x,
                               float y,
                               float w,
                               float h,
                               float mx,
                               float my,
                               float scale,
                               SettingsGuiPalette palette) {
        float dt = AnimationUtility.deltaTime();
        float segmentW = 50f * scale;
        float modeW = segmentW * 3f;
        float targetMode = tab.ordinal();
        modeAnim = AnimationUtility.approach(modeAnim, targetMode, dt, 15f);
        modeAnim = AnimationUtility.snap(modeAnim, targetMode, 0.01f);

        friendsPill = new Rect(x, y, segmentW, h);
        enemiesPill = new Rect(x + segmentW, y, segmentW, h);
        staffPill = new Rect(x + segmentW * 2f, y, segmentW, h);
        friendsHoverAnim = updateHover(friendsHoverAnim, friendsPill.contains(mx, my), dt);
        enemiesHoverAnim = updateHover(enemiesHoverAnim, enemiesPill.contains(mx, my), dt);
        staffHoverAnim = updateHover(staffHoverAnim, staffPill.contains(mx, my), dt);

        int toolbarA = SettingsGuiPalette.withAlpha(palette.controlSurface(), 112);
        int toolbarB = SettingsGuiPalette.withAlpha(palette.controlSurfaceHover(), 96);
        LayoutRender2D.roundedQuad(x, y, modeW, h, 5f * scale, toolbarA, toolbarB, toolbarB, toolbarA);
        LayoutRender2D.roundedStroke(
                x, y, modeW, h, 5f * scale, 0.5f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 92)
        );

        float inset = 1.4f * scale;
        float activeX = x + inset + segmentW * modeAnim;
        float activeW = segmentW - inset * 2f;
        int activeA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.panelPillActive(), tab.color(), 0.10f), 188);
        int activeB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), tab.color(), 0.15f), 176);
        LayoutRender2D.roundedQuad(
                activeX, y + inset, activeW, h - inset * 2f, 4f * scale,
                activeA, activeB, activeB, activeA
        );

        drawSegment(friendsPill, tr("tab.friends", "Friends"), tab == RelationTab.FRIENDS, friendsHoverAnim, scale, palette);
        drawSegment(enemiesPill, tr("tab.enemies", "Enemies"), tab == RelationTab.ENEMIES, enemiesHoverAnim, scale, palette);
        drawSegment(staffPill, tr("tab.staff", "Staff"), tab == RelationTab.STAFF, staffHoverAnim, scale, palette);

        float action = h;
        float fieldW = Math.min(122f * scale, Math.max(72f * scale, w - modeW - action - 18f * scale));
        float fieldX = x + w - action - 4f * scale - fieldW;
        playerInputRect = new Rect(fieldX, y, fieldW, h);
        addTypedButton = new Rect(x + w - action, y, action, h);

        drawInput(
                playerInputRect,
                playerInput,
                tr("placeholder.nick", "Nick"),
                activeField == ActiveField.PLAYER,
                mx,
                my,
                scale,
                palette
        );
        addHoverAnim = updateHover(addHoverAnim, addTypedButton.contains(mx, my), dt);
        drawAddButton(addTypedButton, addHoverAnim, scale, palette);

        float statusX = x + modeW + 8f * scale;
        float statusW = Math.max(0f, fieldX - statusX - 7f * scale);
        renderStatus(statusX, y, statusW, h, scale, palette);
    }

    private void renderMainPanel(float x,
                                 float y,
                                 float w,
                                 float h,
                                 List<String> entries,
                                 float mx,
                                 float my,
                                 float scale,
                                 SettingsGuiPalette palette) {
        try (var transition = SettingsCardTransition.beginCard(x, y, w, h, 6.5f * scale, scale, palette)) {
            renderWorkspaceSurface(x, y, w, h, scale, palette);

            float pad = 8f * scale;
            float innerX = x + pad;
            float innerY = y + pad;
            float innerW = Math.max(1f, w - pad * 2f);
            float innerH = Math.max(1f, h - pad * 2f);

            if (tab != RelationTab.STAFF) {
                listX = innerX;
                listY = innerY;
                listW = innerW;
                listH = innerH;
                renderCards(listX, listY, listW, listH, entries, mx, my, scale, palette);
                return;
            }

            float gap = 8f * scale;
            float heuristicsH = Math.min(101f * scale, innerH * 0.56f);
            heuristicsH = Math.max(88f * scale, heuristicsH);
            float staffListH = innerH - heuristicsH - gap;
            if (staffListH < 52f * scale) {
                staffListH = Math.max(36f * scale, innerH * 0.38f);
                heuristicsH = Math.max(1f, innerH - staffListH - gap);
            }

            listX = innerX;
            listY = innerY;
            listW = innerW;
            listH = Math.max(1f, staffListH);
            renderCards(listX, listY, listW, listH, entries, mx, my, scale, palette);
            renderHeuristicsPanel(innerX, innerY + staffListH + gap, innerW, heuristicsH, mx, my, scale, palette);
        }
    }

    private void renderWorkspaceSurface(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float scale,
                                        SettingsGuiPalette palette) {
        float radius = 6.5f * scale;
        LayoutRender2D.roundedSoftShadow(
                x, y + 1.2f * scale, w, h, radius, 7.5f * scale, 0f,
                LayoutRender2D.alpha(0xFF000000, 0.13f)
        );
        ClickGuiRenderer.drawBlur(x, y, w, h, radius, palette.panelBlurTint(), 145f / 255f);
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                SettingsGuiPalette.withAlpha(palette.panelBgLeft(), 176),
                SettingsGuiPalette.withAlpha(palette.panelBgRight(), 164),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.07f), 170),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgLeft(), 0.05f), 180)
        );
        LayoutRender2D.roundedStrokeQuad(
                x, y, w, h, radius, 0.55f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 94),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 76),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 68),
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 86)
        );
    }

    private void renderHeuristicsPanel(float x,
                                       float y,
                                       float w,
                                       float h,
                                       float mx,
                                       float my,
                                       float scale,
                                       SettingsGuiPalette palette) {
        if (h <= 40f * scale) return;

        int bgA = SettingsGuiPalette.withAlpha(palette.controlSurface(), 70);
        int bgB = SettingsGuiPalette.withAlpha(palette.controlSurfaceHover(), 62);
        LayoutRender2D.roundedQuad(x, y, w, h, 5.5f * scale, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                x, y, w, h, 5.5f * scale, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 58)
        );

        float pad = 7f * scale;
        float titleSize = 8.0f * scale;
        String title = tr("heuristics.title", "Staff heuristics");
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                x + pad,
                y + 5.0f * scale,
                titleSize,
                palette.panelText(),
                false
        );

        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        float toggleW = 28f * scale;
        float toggleH = 14f * scale;
        enabledToggle = new Rect(x + w - pad - toggleW, y + 4f * scale, toggleW, toggleH);
        drawToggle(enabledToggle, cfg.enabled(), mx, my, scale, palette);

        float rowsY = y + 23f * scale;
        float available = Math.max(1f, y + h - pad - rowsY);
        float rowGap = 3f * scale;
        float rowH = Math.max(18f * scale, (available - rowGap * 2f) / 3f);

        renderHeuristicRow(
                ActiveField.PREFIX,
                tr("heuristics.prefixes", "Prefixes"),
                cfg.prefixes(),
                x + pad, rowsY, w - pad * 2f, rowH, mx, my, scale, palette
        );
        rowsY += rowH + rowGap;
        renderHeuristicRow(
                ActiveField.SUFFIX,
                tr("heuristics.suffixes", "Suffixes"),
                cfg.suffixes(),
                x + pad, rowsY, w - pad * 2f, rowH, mx, my, scale, palette
        );
        rowsY += rowH + rowGap;
        renderHeuristicRow(
                ActiveField.CONTAINS,
                tr("heuristics.contains", "Contains"),
                cfg.contains(),
                x + pad, rowsY, w - pad * 2f, rowH, mx, my, scale, palette
        );
    }

    private void renderHeuristicRow(ActiveField kind,
                                    String title,
                                    Set<String> entries,
                                    float x,
                                    float y,
                                    float w,
                                    float h,
                                    float mx,
                                    float my,
                                    float scale,
                                    SettingsGuiPalette palette) {
        float labelW = 51f * scale;
        float fieldH = Math.min(16f * scale, h);
        float addW = fieldH;
        float inputW = Math.min(120f * scale, Math.max(70f * scale, w * 0.34f));
        float fieldX = x + labelW;
        float addX = fieldX + inputW + 3f * scale;
        float chipsX = addX + addW + 7f * scale;
        float chipsW = Math.max(0f, x + w - chipsX);

        float labelSize = 6.5f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), title, labelSize, labelW - 5f * scale),
                x,
                y + (fieldH - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), labelSize)) * 0.5f,
                labelSize,
                palette.panelMuted(),
                false
        );

        Rect field = new Rect(fieldX, y, inputW, fieldH);
        Rect add = new Rect(addX, y, addW, fieldH);
        setHeuristicRects(kind, field, add);
        drawInput(field, fieldText(kind), tr("placeholder.rule", "Rule"), activeField == kind, mx, my, scale, palette);
        drawSmallAddButton(add, add.contains(mx, my), scale, palette);

        if (chipsW < 14f * scale) return;
        float chipX = chipsX;
        float chipH = Math.min(11.5f * scale, fieldH - 1f * scale);
        float chipY = y + (fieldH - chipH) * 0.5f;
        float maxX = chipsX + chipsW;

        for (String entry : sorted(entries)) {
            float textSize = 5.7f * scale;
            float available = maxX - chipX;
            if (available < 18f * scale) break;
            String fitted = ClickGuiRenderer.fitText(
                    ClickGuiRenderer.getInterRegular(),
                    entry,
                    textSize,
                    Math.max(6f * scale, available - 15f * scale)
            );
            float chipW = Math.min(
                    available,
                    ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, textSize) + 15f * scale
            );
            if (chipW < 18f * scale) break;

            Rect chip = new Rect(chipX, chipY, chipW, chipH);
            Rect del = new Rect(chipX + chipW - chipH, chipY, chipH, chipH);
            int chipBg = SettingsGuiPalette.withAlpha(palette.panelPillBase(), 132);
            LayoutRender2D.roundedQuad(chip.x(), chip.y(), chip.w(), chip.h(), 3.2f * scale,
                    chipBg, chipBg, chipBg, chipBg);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    fitted,
                    chip.x() + 4f * scale,
                    chip.y() + (chip.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), textSize)) * 0.5f,
                    textSize,
                    palette.panelText(),
                    false
            );
            Renderer2D.COLOR.svg(
                    "x",
                    del.x() + 2.5f * scale,
                    del.y() + 2.5f * scale,
                    del.w() - 5f * scale,
                    del.h() - 5f * scale,
                    SvgRenderOptions.overrideColor(palette.panelMuted())
            );
            chipHits.add(new ChipHit(kind, entry, del));
            chipX += chipW + 3f * scale;
        }
    }

    private void renderCards(float x,
                             float y,
                             float w,
                             float h,
                             List<String> entries,
                             float mx,
                             float my,
                             float scale,
                             SettingsGuiPalette palette) {
        float rowH = 27f * scale;
        float gap = 4f * scale;
        float reservedScrollbarW = 7f * scale;
        float rowW = Math.max(1f, w - reservedScrollbarW);
        float contentH = entries.isEmpty() ? 0f : entries.size() * (rowH + gap) - gap;
        float maxScroll = Math.max(0f, contentH - h);

        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.24f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        if (entries.isEmpty()) {
            String empty = ClickGuiSearch.hasQuery()
                    ? tr("empty.no_matches", "No matching players.")
                    : tr("empty.none_added", "No players added.");
            float size = 7.0f * scale;
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    empty,
                    x + 3f * scale,
                    y + 4f * scale,
                    size,
                    palette.panelMuted(),
                    false
            );
            return;
        }

        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        try {
            for (int i = 0; i < entries.size(); i++) {
                float rowY = y + i * (rowH + gap) + smoothedScroll;
                if (rowY + rowH < y || rowY > y + h) continue;

                String name = entries.get(i);
                CardHit hit = cardComponent.renderRow(
                        name,
                        tab.color(),
                        equalsIgnoreCase(selectedName, name),
                        x,
                        rowY,
                        rowW,
                        rowH,
                        mx,
                        my,
                        scale,
                        palette
                );
                cardHits.add(new CardEntryHit(name, hit));
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
        renderScrollbar(x, y, w, h, maxScroll, scale, palette);
    }

    private void drawSegment(Rect rect,
                             String label,
                             boolean active,
                             float hover,
                             float scale,
                             SettingsGuiPalette palette) {
        if (rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }
        float size = 6.7f * scale;
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, size);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), size);
        int color = active
                ? palette.panelText()
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.22f * hover);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                label,
                rect.x() + (rect.w() - tw) * 0.5f,
                rect.y() + (rect.h() - th) * 0.5f,
                size,
                color,
                false
        );
    }

    private void drawInput(Rect rect,
                           String value,
                           String placeholder,
                           boolean active,
                           float mx,
                           float my,
                           float scale,
                           SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        if (hover || active) SystemCursor.set(SystemCursor.CursorType.TEXT);

        int bgA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), hover || active ? 0.42f : 0.10f),
                hover || active ? 148 : 104
        );
        int bgB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), active ? 0.10f : 0.02f),
                hover || active ? 142 : 100
        );
        float radius = 4f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), radius, 0.45f * scale,
                active
                        ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), tab.color(), 0.44f), 188)
                        : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), hover ? 108 : 70)
        );

        boolean empty = value == null || value.isEmpty();
        String raw = empty ? (active ? "" : placeholder) : value;
        int color = empty && !active ? palette.panelMuted() : palette.panelText();
        float size = 6.3f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), raw, size, rect.w() - 9f * scale);
        float textY = rect.y() + (rect.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), size)) * 0.5f;

        boolean clipped = ScissorFunction.pushRaw(rect.x(), rect.y(), rect.w(), rect.h());
        try {
            if (!text.isEmpty()) {
                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        text,
                        rect.x() + 4f * scale,
                        textY,
                        size,
                        color,
                        false
                );
            }
            if (active && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
                float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, size);
                LayoutRender2D.rect(
                        rect.x() + Math.min(tw + 5f * scale, rect.w() - 2.5f * scale),
                        rect.y() + 3f * scale,
                        0.6f * scale,
                        rect.h() - 6f * scale,
                        palette.panelText()
                );
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void drawAddButton(Rect rect,
                               float hover,
                               float scale,
                               SettingsGuiPalette palette) {
        if (rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }
        int bgA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), 0.20f + hover * 0.42f),
                116 + Math.round(28f * hover)
        );
        int bgB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.05f + hover * 0.13f),
                112 + Math.round(30f * hover)
        );
        float radius = 4f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), radius, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 90)
        );

        float fontSize = 11f * scale;
        String plus = "+";
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), plus, fontSize);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), fontSize);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                plus,
                rect.x() + (rect.w() - tw) * 0.5f,
                rect.y() + (rect.h() - th) * 0.5f - 0.5f * scale,
                fontSize,
                palette.menuCategoryText(),
                false
        );
    }

    private void drawSmallAddButton(Rect rect,
                                    boolean hover,
                                    float scale,
                                    SettingsGuiPalette palette) {
        float anim = hover ? 1f : 0f;
        int bg = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.05f + anim * 0.14f),
                hover ? 142 : 110
        );
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4f * scale, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), 4f * scale, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 82)
        );
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);

        float fontSize = 9.3f * scale;
        String plus = "+";
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), plus, fontSize);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), fontSize);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                plus,
                rect.x() + (rect.w() - tw) * 0.5f,
                rect.y() + (rect.h() - th) * 0.5f - 0.3f * scale,
                fontSize,
                palette.menuCategoryText(),
                false
        );
    }

    private void drawToggle(Rect rect,
                            boolean enabled,
                            float mx,
                            float my,
                            float scale,
                            SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);

        int bg = enabled
                ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelPillActive(), tab.color(), hover ? 0.22f : 0.14f), 188)
                : SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), hover ? 0.34f : 0.10f), 132);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), rect.h() * 0.5f, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), rect.h() * 0.5f, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), enabled ? 100 : 70)
        );

        float knob = rect.h() - 4f * scale;
        float knobX = enabled ? rect.x() + rect.w() - knob - 2f * scale : rect.x() + 2f * scale;
        int knobColor = enabled ? palette.panelText() : palette.panelMuted();
        Renderer2D.COLOR.roundedRect(
                knobX,
                rect.y() + 2f * scale,
                knob,
                knob,
                knob * 0.5f,
                1f,
                SettingsGuiPalette.withAlpha(knobColor, enabled ? 232 : 190)
        );
    }

    private boolean focusField(ActiveField field) {
        activeField = field;
        movementInputBlocked = true;
        clearStatus();
        return true;
    }

    private void setHeuristicRects(ActiveField kind, Rect field, Rect add) {
        switch (kind) {
            case PREFIX -> {
                prefixInputRect = field;
                prefixAddButton = add;
            }
            case SUFFIX -> {
                suffixInputRect = field;
                suffixAddButton = add;
            }
            case CONTAINS -> {
                containsInputRect = field;
                containsAddButton = add;
            }
            default -> {
            }
        }
    }

    private boolean switchTab(RelationTab next) {
        if (tab != next) {
            tab = next;
            selectedName = null;
            activeField = ActiveField.NONE;
            scroll = 0f;
            smoothedScroll = 0f;
            draggingScrollbar = false;
            clearStatus();
        }
        movementInputBlocked = false;
        return true;
    }

    private void commitPlayerInput() {
        String cleaned = ChatNameUtil.normalizeNickCandidate(playerInput);
        if (!ChatNameUtil.isNickLike(cleaned)) {
            setStatus(tr("status.invalid_nick", "Invalid nick"));
            return;
        }

        if (tab.add(cleaned)) {
            playerInput = "";
            selectedName = cleaned;
            activeField = ActiveField.NONE;
            movementInputBlocked = false;
            setStatus(tr("status.added", "Added: %s", cleaned));
        } else {
            selectedName = cleaned;
            setStatus(tr("status.already_exists", "Already exists: %s", cleaned));
        }
    }

    private boolean commitHeuristic(ActiveField kind) {
        String value = StaffHeuristicsConfig.cleanRule(fieldText(kind));
        if (value.isBlank()) {
            setStatus(tr("status.rule_empty", "Rule is empty"));
            return true;
        }

        boolean changed = switch (kind) {
            case PREFIX -> StaffHeuristicsConfig.get().addPrefix(value);
            case SUFFIX -> StaffHeuristicsConfig.get().addSuffix(value);
            case CONTAINS -> StaffHeuristicsConfig.get().addContains(value);
            default -> false;
        };
        if (changed) {
            setFieldText(kind, "");
            activeField = ActiveField.NONE;
            movementInputBlocked = false;
            setStatus(tr("status.rule_added", "Rule added"));
        } else {
            setStatus(tr("status.rule_exists", "Rule already exists"));
        }
        return true;
    }

    private void removeHeuristic(ActiveField kind, String value) {
        boolean changed = switch (kind) {
            case PREFIX -> StaffHeuristicsConfig.get().removePrefix(value);
            case SUFFIX -> StaffHeuristicsConfig.get().removeSuffix(value);
            case CONTAINS -> StaffHeuristicsConfig.get().removeContains(value);
            default -> false;
        };
        if (changed) setStatus(tr("status.rule_removed", "Rule removed"));
    }

    private List<String> filteredEntries(List<String> entries) {
        if (!ClickGuiSearch.hasQuery()) return entries;
        String q = ClickGuiSearch.getText().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String entry : entries) {
            if (entry != null && entry.toLowerCase(Locale.ROOT).contains(q)) out.add(entry);
        }
        return out;
    }

    private static List<String> sorted(Set<String> src) {
        List<String> out = new ArrayList<>();
        if (src != null) {
            for (String value : src) {
                if (value != null && !value.isBlank()) out.add(value);
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private void validateSelection(List<String> entries) {
        if (selectedName == null) return;
        for (String entry : entries) {
            if (equalsIgnoreCase(selectedName, entry)) {
                selectedName = entry;
                return;
            }
        }
        selectedName = null;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private void appendToField(ActiveField field, String raw) {
        if (raw == null || raw.isEmpty()) return;
        String current = fieldText(field);
        int max = field == ActiveField.PLAYER ? 32 : 48;
        String next = current + raw;
        if (next.length() > max) next = next.substring(0, max);
        setFieldText(field, next);
    }

    private String fieldText(ActiveField field) {
        return switch (field) {
            case PLAYER -> playerInput;
            case PREFIX -> prefixInput;
            case SUFFIX -> suffixInput;
            case CONTAINS -> containsInput;
            default -> "";
        };
    }

    private void setFieldText(ActiveField field, String value) {
        String next = value == null ? "" : value;
        switch (field) {
            case PLAYER -> playerInput = next;
            case PREFIX -> prefixInput = next;
            case SUFFIX -> suffixInput = next;
            case CONTAINS -> containsInput = next;
            default -> {
            }
        }
    }

    private static String dropLast(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, text.length() - 1);
    }

    private float updateHover(float current, boolean hovered, float dt) {
        return AnimationUtility.snap(
                AnimationUtility.approach(current, hovered ? 1f : 0f, dt, 14f),
                hovered ? 1f : 0f,
                0.01f
        );
    }

    private void renderStatus(float x,
                              float y,
                              float w,
                              float h,
                              float scale,
                              SettingsGuiPalette palette) {
        if (w <= 8f * scale || statusMessage == null || statusMessage.isBlank()) return;
        if (System.currentTimeMillis() > statusUntilMs) {
            clearStatus();
            return;
        }

        float size = 5.8f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), statusMessage, size, w);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), size);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                text,
                x,
                y + (h - th) * 0.5f,
                size,
                SettingsGuiPalette.mix(palette.panelMuted(), tab.color(), 0.20f),
                false
        );
    }

    private void setStatus(String message) {
        statusMessage = message;
        statusUntilMs = System.currentTimeMillis() + STATUS_DURATION_MS;
    }

    private static String tr(String key, String fallback, Object... args) {
        return ClickGuiI18n.tr(I18N + key, fallback, args);
    }

    private void clearStatus() {
        statusMessage = null;
        statusUntilMs = 0L;
    }

    private void renderScrollbar(float x,
                                 float y,
                                 float w,
                                 float h,
                                 float maxScroll,
                                 float scale,
                                 SettingsGuiPalette palette) {
        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        if (!scrollbarVisible) return;

        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) SystemCursor.set(SystemCursor.CursorType.SCROLL);

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarY, scrollbarW, scrollbarH, 1.5f * scale,
                palette.moduleScrollTrackA(), palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(), palette.moduleScrollTrackA()
        );
        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 1.5f * scale,
                palette.moduleScrollHandleA(), palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(), palette.moduleScrollHandleA()
        );
    }

    private void updateScrollbarMetrics(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float maxScroll,
                                        float scale) {
        scrollbarVisible = maxScroll > 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }

        scrollbarW = 2.4f * scale;
        scrollbarX = x + w - scrollbarW - 1.2f * scale;
        scrollbarY = y + 2f * scale;
        scrollbarH = Math.max(1f, h - 4f * scale);
        scrollbarThumbH = Math.max(16f * scale, scrollbarH * (scrollbarH / (scrollbarMaxScroll + scrollbarH)));
        float scrollRatio = scrollbarMaxScroll <= 0f ? 0f : (-smoothedScroll / scrollbarMaxScroll);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(scrollRatio, 0f, 1f);
    }

    private boolean isScrollbarHovered(float mx, float my) {
        if (!scrollbarVisible) return false;
        float pad = 4f;
        return ClickGuiMath.insideRect(
                mx, my,
                scrollbarX - pad, scrollbarY - pad,
                scrollbarW + pad * 2f, scrollbarH + pad * 2f
        );
    }

    private void scrollToMouse(float my) {
        if (!scrollbarVisible || scrollbarMaxScroll <= 0f) return;
        float span = scrollbarH - scrollbarThumbH;
        if (span <= 0.5f) return;

        float thumbTop = AnimationUtility.clamp(my - scrollbarDragOffset, scrollbarY, scrollbarY + span);
        float ratio = (thumbTop - scrollbarY) / span;
        scroll = -scrollbarMaxScroll * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    private void resetTransientRects() {
        enabledToggle = Rect.ZERO;
        prefixInputRect = Rect.ZERO;
        suffixInputRect = Rect.ZERO;
        containsInputRect = Rect.ZERO;
        prefixAddButton = Rect.ZERO;
        suffixAddButton = Rect.ZERO;
        containsAddButton = Rect.ZERO;
    }

    private enum ActiveField {
        NONE,
        PLAYER,
        PREFIX,
        SUFFIX,
        CONTAINS
    }

    private enum RelationTab {
        FRIENDS,
        ENEMIES,
        STAFF;

        private List<String> entries() {
            PlayerRelations rel = PlayerRelations.get();
            return switch (this) {
                case FRIENDS -> sorted(rel.getFriends());
                case ENEMIES -> sorted(rel.getEnemies());
                case STAFF -> sorted(rel.getStaff());
            };
        }

        private boolean add(String name) {
            PlayerRelations rel = PlayerRelations.get();
            boolean changed = switch (this) {
                case FRIENDS -> rel.addFriend(name);
                case ENEMIES -> rel.addEnemy(name);
                case STAFF -> rel.addStaff(name);
            };
            if (changed) rel.save();
            return changed;
        }

        private boolean remove(String name) {
            PlayerRelations rel = PlayerRelations.get();
            boolean changed = switch (this) {
                case FRIENDS -> rel.removeFriend(name);
                case ENEMIES -> rel.removeEnemy(name);
                case STAFF -> rel.removeStaff(name);
            };
            if (changed) rel.save();
            return changed;
        }

        private int color() {
            PlayerRelations rel = PlayerRelations.get();
            return switch (this) {
                case FRIENDS -> rel.colorFriend();
                case ENEMIES -> rel.colorEnemy();
                case STAFF -> rel.colorStaff();
            };
        }
    }

    private record CardEntryHit(String name, CardHit hit) {
    }

    private record ChipHit(ActiveField kind, String value, Rect delete) {
    }

    private record Rect(float x, float y, float w, float h) {
        static final Rect ZERO = new Rect(0f, 0f, 0f, 0f);

        boolean contains(float mx, float my) {
            return w > 0f && h > 0f && ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }
    }
}
