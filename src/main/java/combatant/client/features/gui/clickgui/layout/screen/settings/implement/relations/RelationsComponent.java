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
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.features.module.modules.misc.DefineTarget;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.features.relations.StaffHeuristicsConfig;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RelationsComponent {
    private static final long STATUS_DURATION_MS = 2200L;
    private static final String I18N = "clickgui.settings.relations.";

    private final RelationPlayerCardComponent cardComponent = new RelationPlayerCardComponent();
    private final OnlineRelationPlayerPickerComponent onlinePicker;
    private final List<CardEntryHit> cardHits = new ArrayList<>();
    private final List<ChipHit> chipHits = new ArrayList<>();

    private RelationTab tab = RelationTab.FRIENDS;
    private ActiveField activeField = ActiveField.NONE;
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
    private Rect reservedPickerButton = Rect.ZERO;
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
    private float pickerHoverAnim;
    private static boolean movementInputBlocked;

    public RelationsComponent() {
        this.onlinePicker = new OnlineRelationPlayerPickerComponent(this::setStatus);
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
        activeField = ActiveField.NONE;
        onlinePicker.close();
        onlinePicker.resetScroll();
        movementInputBlocked = false;
    }

    public static boolean isMovementInputBlocked() {
        return movementInputBlocked;
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        cardHits.clear();
        chipHits.clear();
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        movementInputBlocked = activeField != ActiveField.NONE || onlinePicker.blocksMovementInput();

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;

        renderToolbar(areaX, areaY, areaW, mx, my, scale, palette);

        float y = areaY + 24f * scale;
        renderStatus(areaX, y, areaW, scale, palette);
        y += 13f * scale;

        listX = areaX;
        listY = y;
        listW = areaW;
        listH = Math.max(1f, areaY + areaH - y);
        if (onlinePicker.isVisible()) {
            onlinePicker.render(listX, listY, listW, listH, mx, my, scale, palette);
            return;
        }

        if (tab == RelationTab.STAFF) {
            float heurH = 78f * scale;
            renderHeuristicsPanel(areaX, y, areaW, heurH, mx, my, scale, palette);
            y += heurH + 8f * scale;
        }

        listX = areaX;
        listY = y;
        listW = areaW;
        listH = Math.max(1f, areaY + areaH - y);
        renderCards(listX, listY, listW, listH, filteredEntries(tab.entries()), mx, my, scale, palette);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (onlinePicker.isVisible()) return onlinePicker.mousePressedScrollbar(mx, my, button);
        if (button != 0 || !isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        onlinePicker.mouseReleased(button);
        if (button == 0) draggingScrollbar = false;
    }

    public void scroll(float mx, float my, double amount) {
        if (onlinePicker.isVisible()) {
            onlinePicker.scroll(mx, my, amount);
            return;
        }
        if (!ClickGuiMath.insideRect(mx, my, listX, listY, listW, listH)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean click(float mx, float my, int button) {
        if (button != 0) return false;

        if (friendsPill.contains(mx, my)) return switchTab(RelationTab.FRIENDS);
        if (enemiesPill.contains(mx, my)) return switchTab(RelationTab.ENEMIES);
        if (staffPill.contains(mx, my)) return switchTab(RelationTab.STAFF);

        if (playerInputRect.contains(mx, my)) {
            if (onlinePicker.isOpen()) {
                activeField = ActiveField.NONE;
                onlinePicker.focusSearch();
            } else {
                activeField = ActiveField.PLAYER;
            }
            movementInputBlocked = activeField != ActiveField.NONE || onlinePicker.blocksMovementInput();
            clearStatus();
            return true;
        }
        if (addTypedButton.contains(mx, my)) {
            if (onlinePicker.isOpen()) {
                onlinePicker.clearSearch();
                onlinePicker.focusSearch();
                movementInputBlocked = onlinePicker.blocksMovementInput();
                return true;
            }
            commitPlayerInput();
            return true;
        }
        if (reservedPickerButton.contains(mx, my)) {
            activeField = ActiveField.NONE;
            onlinePicker.toggle(tab.pickerMode());
            movementInputBlocked = onlinePicker.blocksMovementInput();
            clearStatus();
            return true;
        }
        if (onlinePicker.isVisible() && onlinePicker.click(mx, my, button)) {
            movementInputBlocked = onlinePicker.blocksMovementInput();
            return true;
        }
        if (onlinePicker.isVisible()) {
            return false;
        }
        if (tab == RelationTab.STAFF) {
            if (enabledToggle.contains(mx, my)) {
                StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
                cfg.setEnabled(!cfg.enabled());
                return true;
            }
            if (prefixInputRect.contains(mx, my)) {
                activeField = ActiveField.PREFIX;
                movementInputBlocked = true;
                clearStatus();
                return true;
            }
            if (suffixInputRect.contains(mx, my)) {
                activeField = ActiveField.SUFFIX;
                movementInputBlocked = true;
                clearStatus();
                return true;
            }
            if (containsInputRect.contains(mx, my)) {
                activeField = ActiveField.CONTAINS;
                movementInputBlocked = true;
                clearStatus();
                return true;
            }
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
            if (inside(hit.delete(), mx, my)) {
                tab.remove(entryHit.name());
                setStatus(tr("status.removed", "Removed: %s", entryHit.name()));
                return true;
            }
            if (ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                return true;
            }
        }

        activeField = ActiveField.NONE;
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onlinePicker.keyPressed(keyCode, scanCode, modifiers)) {
            movementInputBlocked = activeField != ActiveField.NONE || onlinePicker.blocksMovementInput();
            return true;
        }
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
        if (onlinePicker.charTyped(chr, modifiers)) {
            movementInputBlocked = activeField != ActiveField.NONE || onlinePicker.blocksMovementInput();
            return true;
        }
        if (activeField == ActiveField.NONE) return false;
        if (chr >= 32 && chr != 127) {
            appendToField(activeField, String.valueOf(chr));
        }
        return true;
    }

    private void renderToolbar(float x, float y, float w, float mx, float my, float scale, SettingsGuiPalette palette) {
        float pillW = 165f * scale;
        float pillH = 15f * scale;
        float targetMode = tab.ordinal();
        float dt = AnimationUtility.deltaTime();
        modeAnim = AnimationUtility.approach(modeAnim, targetMode, dt, 12f);
        modeAnim = AnimationUtility.snap(modeAnim, targetMode, 0.01f);

        friendsPill = new Rect(x, y, pillW / 3f, pillH);
        enemiesPill = new Rect(x + pillW / 3f, y, pillW / 3f, pillH);
        staffPill = new Rect(x + pillW * 2f / 3f, y, pillW / 3f, pillH);
        friendsHoverAnim = updateHover(friendsHoverAnim, friendsPill.contains(mx, my), dt);
        enemiesHoverAnim = updateHover(enemiesHoverAnim, enemiesPill.contains(mx, my), dt);
        staffHoverAnim = updateHover(staffHoverAnim, staffPill.contains(mx, my), dt);

        ClickGuiRenderer.drawBlur(x, y, pillW, pillH, 4f * scale, palette.panelBlurTint(), 145f / 255f);
        LayoutRender2D.roundedQuad(x, y, pillW, pillH, 3.5f * scale,
                SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgLeft(), 0.34f),
                SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), 0.44f),
                SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), 0.44f),
                SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgLeft(), 0.34f));
        LayoutRender2D.roundedStrokeQuad(x, y, pillW, pillH, 3.5f * scale, 0.5f * scale,
                palette.panelStroke(), palette.moduleDividerEnd(), palette.moduleDividerEnd(), palette.panelStroke());

        float pad = 1.2f * scale;
        float segment = (pillW - pad * 2f) / 3f;
        float activeX = x + pad + segment * modeAnim;
        LayoutRender2D.roundedQuad(activeX, y + pad, segment, pillH - pad * 2f, 3f * scale,
                SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedLeft(), 0.22f),
                SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), 0.22f),
                SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.panelPillActive(), 0.16f), palette.menuCategorySelectedRight(), 0.18f),
                SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedLeft(), 0.22f));

        drawToolbarSeparator(x + pad + segment, y, pillH, scale, palette);
        drawToolbarSeparator(x + pad + segment * 2f, y, pillH, scale, palette);

        drawSegment(friendsPill, tr("tab.friends", "Friends"), tab == RelationTab.FRIENDS, friendsHoverAnim, scale, palette);
        drawSegment(enemiesPill, tr("tab.enemies", "Enemies"), tab == RelationTab.ENEMIES, enemiesHoverAnim, scale, palette);
        drawSegment(staffPill, tr("tab.staff", "Staff"), tab == RelationTab.STAFF, staffHoverAnim, scale, palette);

        float fieldX = x + pillW + 8f * scale;
        float picker = 18f * scale;
        float action = 18f * scale;
        float gap = 4f * scale;
        boolean onlineMode = onlinePicker.isOpen();
        float fieldW = Math.max(80f * scale, w - (fieldX - x) - picker - action - gap * 2f);
        playerInputRect = new Rect(fieldX, y, fieldW, pillH);
        addTypedButton = new Rect(fieldX + fieldW + gap, y, action, pillH);
        reservedPickerButton = new Rect(addTypedButton.x() + action + gap, y, picker, pillH);

        if (onlineMode) {
            String query = onlinePicker.searchText();
            drawInput(playerInputRect, query, tr("placeholder.search_online", "Search online"), onlinePicker.isSearchFocused(), mx, my, scale, palette);
            addHoverAnim = updateHover(addHoverAnim, addTypedButton.contains(mx, my), dt);
            drawIconButton(addTypedButton, "x", addHoverAnim, query == null || query.isBlank(), scale, palette);
        } else {
            drawInput(playerInputRect, playerInput, tr("placeholder.nick", "Nick"), activeField == ActiveField.PLAYER, mx, my, scale, palette);
            addHoverAnim = updateHover(addHoverAnim, addTypedButton.contains(mx, my), dt);
            drawIconButton(addTypedButton, "check", addHoverAnim, false, scale, palette);
        }
        pickerHoverAnim = updateHover(pickerHoverAnim, reservedPickerButton.contains(mx, my) || onlinePicker.isVisible(), dt);
        drawIconButton(reservedPickerButton, onlineMode ? "x" : "user-plus", pickerHoverAnim, false, scale, palette);
    }

    private void renderHeuristicsPanel(float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette) {
        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 190f / 255f);
        LayoutRender2D.roundedQuad(x, y, w, h, 5f * scale,
                palette.moduleCardTop(), palette.moduleCardTopStrong(), palette.moduleCardBottom(), palette.moduleCardBottomStrong());
        LayoutRender2D.roundedStroke(x, y, w, h, 5f * scale, 0.5f * scale, palette.menuWindowStroke());

        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), tr("heuristics.title", "Staff heuristics"), x + 8f * scale, y + 6f * scale, 8f * scale, palette.moduleTitleText(), false);
        enabledToggle = new Rect(x + w - 56f * scale, y + 5f * scale, 48f * scale, 14f * scale);
        drawToggle(enabledToggle, cfg.enabled(), mx, my, scale, palette);

        float top = y + 25f * scale;
        float gap = 7f * scale;
        float colW = (w - 16f * scale - gap * 2f) / 3f;
        renderHeuristicColumn(ActiveField.PREFIX, tr("heuristics.prefixes", "Prefixes"), cfg.prefixes(), x + 8f * scale, top, colW, h - 31f * scale, mx, my, scale, palette);
        renderHeuristicColumn(ActiveField.SUFFIX, tr("heuristics.suffixes", "Suffixes"), cfg.suffixes(), x + 8f * scale + colW + gap, top, colW, h - 31f * scale, mx, my, scale, palette);
        renderHeuristicColumn(ActiveField.CONTAINS, tr("heuristics.contains", "Contains"), cfg.contains(), x + 8f * scale + (colW + gap) * 2f, top, colW, h - 31f * scale, mx, my, scale, palette);
    }

    private void renderHeuristicColumn(ActiveField kind,
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
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), title, x, y, 6.7f * scale, palette.panelMuted(), false);
        Rect field = new Rect(x, y + 10f * scale, w - 16f * scale, 14f * scale);
        Rect add = new Rect(x + w - 14f * scale, y + 10f * scale, 14f * scale, 14f * scale);
        if (kind == ActiveField.PREFIX) {
            prefixInputRect = field;
            prefixAddButton = add;
        } else if (kind == ActiveField.SUFFIX) {
            suffixInputRect = field;
            suffixAddButton = add;
        } else {
            containsInputRect = field;
            containsAddButton = add;
        }
        drawInput(field, fieldText(kind), tr("placeholder.rule", "Rule"), activeField == kind, mx, my, scale, palette);
        drawIconButton(add, "check", add.contains(mx, my) ? 1f : 0f, false, scale, palette);

        float chipX = x;
        float chipY = y + 29f * scale;
        float chipH = 11f * scale;
        List<String> sorted = sorted(entries);
        for (String entry : sorted) {
            float textSize = 5.9f * scale;
            String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), entry, textSize, w - 14f * scale);
            float chipW = Math.min(w, ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, textSize) + 16f * scale);
            if (chipX + chipW > x + w + 0.1f) {
                chipX = x;
                chipY += chipH + 3f * scale;
            }
            if (chipY + chipH > y + h) break;
            Rect chip = new Rect(chipX, chipY, chipW, chipH);
            Rect del = new Rect(chipX + chipW - 10f * scale, chipY + 1f * scale, 9f * scale, 9f * scale);
            LayoutRender2D.roundedQuad(chip.x(), chip.y(), chip.w(), chip.h(), 3f * scale,
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.95f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.88f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.88f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.95f));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), fitted, chipX + 4f * scale, chipY + 3f * scale, textSize, palette.panelText(), false);
            Renderer2D.COLOR.svg("x", del.x() + 1.8f * scale, del.y() + 1.8f * scale, 5.4f * scale, 5.4f * scale,
                    SvgRenderOptions.overrideColor(palette.panelMuted()));
            chipHits.add(new ChipHit(kind, entry, del));
            chipX += chipW + 3f * scale;
        }
    }

    private void renderCards(float x, float y, float w, float h, List<String> entries, float mx, float my, float scale, SettingsGuiPalette palette) {
        float gap = 6f * scale;
        float reservedScrollbarW = 9f * scale;
        float cardAreaW = Math.max(1f, w - reservedScrollbarW);
        int columns = cardAreaW > 230f * scale ? 2 : 1;
        float cardW = (cardAreaW - gap * (columns - 1)) / columns;
        float cardH = 36f * scale;
        float stepY = cardH + gap;
        int rows = (entries.size() + columns - 1) / columns;
        float contentH = rows <= 0 ? 0f : rows * stepY - gap;
        float maxScroll = Math.max(0f, contentH - h);
        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        if (entries.isEmpty()) {
            String empty = ClickGuiSearch.isActive() && !ClickGuiSearch.getText().isBlank()
                    ? tr("empty.no_matches", "No matching players.")
                    : tr("empty.none_added", "No players added.");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), empty, x + 4f * scale, y + 4f * scale, 7.4f * scale, palette.panelMuted(), false);
            return;
        }

        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        for (int i = 0; i < entries.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float cx = x + col * (cardW + gap);
            float cy = y + row * stepY + smoothedScroll;
            if (cy + cardH < y || cy > y + h) continue;
            String name = entries.get(i);
            CardHit hit = cardComponent.render(name, cx, cy, cardW, cardH, mx, my, scale, palette);
            cardHits.add(new CardEntryHit(name, hit));
        }
        if (clipped) ScissorFunction.pop();
        renderScrollbar(x, y, w, h, maxScroll, scale, palette);
    }

    private void drawSegment(Rect rect, String label, boolean active, float hover, float scale, SettingsGuiPalette palette) {
        float size = 7.5f * scale;
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, size);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), size);
        int color = active ? palette.panelText() : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.20f * hover);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), label, rect.x() + (rect.w() - tw) * 0.5f, rect.y() + (rect.h() - th) * 0.5f, size, color, false);
    }

    private void drawToolbarSeparator(float x, float y, float h, float scale, SettingsGuiPalette palette) {
        float backingW = Math.max(1.25f * scale, 1.05f);
        float backingH = h - 4.4f * scale;
        float backingY = y + (h - backingH) * 0.5f;
        int backingTop = SettingsGuiPalette.withAlpha(palette.panelBgLeft(), 98);
        int backingBottom = SettingsGuiPalette.withAlpha(palette.panelBgRight(), 78);
        LayoutRender2D.roundedQuad(
                x - backingW * 0.5f,
                backingY,
                backingW,
                backingH,
                backingW * 0.5f,
                backingTop,
                backingTop,
                backingBottom,
                backingBottom
        );

        float lineW = Math.max(0.5f * scale, 0.75f);
        float lineH = h - 6.0f * scale;
        float lineY = y + (h - lineH) * 0.5f;
        int top = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.moduleDividerStart(), palette.menuCategorySelectedLeft(), 0.34f), 146);
        int mid = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.moduleDividerEnd(), palette.menuCategorySelectedRight(), 0.40f), 210);
        int bottom = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.moduleDividerEnd(), palette.panelText(), 0.24f), 136);
        LayoutRender2D.roundedQuad(
                x - lineW * 0.5f,
                lineY,
                lineW,
                lineH,
                lineW * 0.5f,
                top,
                mid,
                bottom,
                top
        );
    }

    private void drawInput(Rect rect, String value, String placeholder, boolean active, float mx, float my, float scale, SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        int bgA = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), hover || active ? 0.20f : 0.06f);
        int bgB = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), hover || active ? 0.18f : 0.06f);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 3.5f * scale, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStrokeQuad(rect.x(), rect.y(), rect.w(), rect.h(), 3.5f * scale, 0.45f * scale,
                active ? palette.moduleDividerEnd() : LayoutRender2D.alpha(palette.moduleDividerStart(), 0.75f),
                active ? palette.moduleDividerEnd() : LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.75f),
                active ? palette.moduleDividerEnd() : LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.65f),
                active ? palette.moduleDividerStart() : LayoutRender2D.alpha(palette.moduleDividerStart(), 0.75f));

        boolean empty = value == null || value.isEmpty();
        String raw = empty ? (active ? "" : placeholder) : value;
        int color = empty && !active ? palette.panelMuted() : palette.panelText();
        float size = 6.8f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), raw, size, rect.w() - 8f * scale);
        float textY = rect.y() + (rect.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), size)) * 0.5f;
        if (!text.isEmpty()) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), text, rect.x() + 4f * scale, textY, size, color, false);
        }
        if (active && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, size);
            LayoutRender2D.rect(rect.x() + Math.min(tw + 5.5f * scale, rect.w() - 3f * scale), rect.y() + 3f * scale, 0.6f * scale, rect.h() - 6f * scale, palette.panelText());
        }
    }

    private void drawIconButton(Rect rect, String icon, float hover, boolean disabled, float scale, SettingsGuiPalette palette) {
        int bgA = disabled
                ? LayoutRender2D.alpha(palette.panelPillBase(), 0.42f)
                : SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.12f + hover * 0.22f);
        int bgB = disabled
                ? LayoutRender2D.alpha(palette.panelPillBase(), 0.32f)
                : SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), 0.12f + hover * 0.24f);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 3.5f * scale, bgA, bgB, bgB, bgA);
        int iconColor = disabled ? LayoutRender2D.alpha(palette.panelMuted(), 0.58f) : palette.menuCategoryText();
        float iconSize = Math.min(rect.w(), rect.h()) - 7f * scale;
        Renderer2D.COLOR.svg(icon, rect.x() + (rect.w() - iconSize) * 0.5f, rect.y() + (rect.h() - iconSize) * 0.5f,
                iconSize, iconSize, SvgRenderOptions.overrideColor(iconColor));
    }

    private void drawToggle(Rect rect, boolean enabled, float mx, float my, float scale, SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        int bg = enabled
                ? SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), hover ? 0.22f : 0.12f)
                : SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), hover ? 0.22f : 0.12f);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4f * scale, bg, bg, SettingsGuiPalette.darken(bg, 0.10f), bg);
        String label = enabled ? tr("heuristics.enabled", "Enabled") : tr("heuristics.disabled", "Disabled");
        float size = 6.2f * scale;
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, size);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), label, rect.x() + (rect.w() - tw) * 0.5f, rect.y() + 4.1f * scale, size,
                enabled ? palette.panelText() : palette.panelMuted(), false);
    }

    private boolean switchTab(RelationTab next) {
        if (tab != next) {
            tab = next;
            activeField = ActiveField.NONE;
            resetScroll();
            if (onlinePicker.isVisible()) {
                onlinePicker.open(tab.pickerMode());
            }
            clearStatus();
        }
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
            activeField = ActiveField.NONE;
            movementInputBlocked = false;
            setStatus(tr("status.added", "Added: %s", cleaned));
        } else {
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
        if (!ClickGuiSearch.isActive() || ClickGuiSearch.getText().isBlank()) return entries;
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

    private static boolean inside(RelationPlayerCardComponent.ActionHit hit, float mx, float my) {
        return hit != null && ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h());
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
        return AnimationUtility.snap(AnimationUtility.approach(current, hovered ? 1f : 0f, dt, 12f), hovered ? 1f : 0f, 0.01f);
    }

    private void renderStatus(float x, float y, float w, float scale, SettingsGuiPalette palette) {
        if (statusMessage == null || statusMessage.isBlank()) return;
        if (System.currentTimeMillis() > statusUntilMs) {
            clearStatus();
            return;
        }
        float size = 6.8f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), statusMessage, size, w - 8f * scale);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), text, x + 4f * scale, y + 2.6f * scale, size, palette.panelMuted(), false);
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

    private void renderScrollbar(float x, float y, float w, float h, float maxScroll, float scale, SettingsGuiPalette palette) {
        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        if (!scrollbarVisible) return;
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) SystemCursor.set(SystemCursor.CursorType.SCROLL);

        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH, 2f * scale,
                palette.moduleScrollTrackA(), palette.moduleScrollTrackB(), palette.moduleScrollTrackB(), palette.moduleScrollTrackA());
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 2f * scale,
                palette.moduleScrollHandleA(), palette.moduleScrollHandleB(), palette.moduleScrollHandleB(), palette.moduleScrollHandleA());
    }

    private void updateScrollbarMetrics(float x, float y, float w, float h, float maxScroll, float scale) {
        scrollbarVisible = maxScroll > 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }
        scrollbarW = 3f * scale;
        scrollbarX = x + w - scrollbarW - 2f * scale;
        scrollbarY = y + 3f * scale;
        scrollbarH = Math.max(1f, h - 6f * scale);
        scrollbarThumbH = Math.max(18f * scale, scrollbarH * (scrollbarH / (scrollbarMaxScroll + scrollbarH)));
        float scrollRatio = scrollbarMaxScroll <= 0f ? 0f : (-smoothedScroll / scrollbarMaxScroll);
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

        private DefineTarget.RelationTargetMode pickerMode() {
            return switch (this) {
                case FRIENDS -> DefineTarget.RelationTargetMode.FRIEND;
                case ENEMIES -> DefineTarget.RelationTargetMode.ENEMY;
                case STAFF -> DefineTarget.RelationTargetMode.STAFF;
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
            return ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }
    }
}
