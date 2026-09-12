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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Relations management workspace.
 *
 * The screen is deliberately split into a dense browser and a stable inspector:
 * list rows stay compact, destructive actions live in one predictable place, and
 * the inspector has room for relation metadata/rules without growing every list row.
 */
public final class RelationsComponent {
    private static final long STATUS_DURATION_MS = 2200L;
    private static final String I18N = "clickgui.settings.relations.";

    private final RelationPlayerCardComponent cardComponent = new RelationPlayerCardComponent();
    private final OnlineRelationPlayerPickerComponent onlinePicker;
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
    private Rect reservedPickerButton = Rect.ZERO;
    private Rect copyButton = Rect.ZERO;
    private Rect removeButton = Rect.ZERO;
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
    private float copyHoverAnim;
    private float removeHoverAnim;

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
        resetTransientRects();
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        movementInputBlocked = activeField != ActiveField.NONE || onlinePicker.blocksMovementInput();

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;

        List<String> entries = filteredEntries(tab.entries());
        ensureSelection(entries);

        float toolbarH = 16f * scale;
        renderToolbar(areaX, areaY, areaW, toolbarH, entries.size(), mx, my, scale, palette);

        float workspaceY = areaY + toolbarH + 7f * scale;
        float workspaceH = Math.max(1f, areaY + areaH - workspaceY);

        if (onlinePicker.isVisible()) {
            listX = areaX;
            listY = workspaceY;
            listW = areaW;
            listH = workspaceH;
            onlinePicker.render(areaX, workspaceY, areaW, workspaceH, mx, my, scale, palette);
            return;
        }

        float gap = 7f * scale;
        float browserW = Math.min(areaW * 0.58f, 196f * scale);
        browserW = Math.max(136f * scale, browserW);
        float inspectorX = areaX + browserW + gap;
        float inspectorW = Math.max(1f, areaW - browserW - gap);

        renderBrowserPanel(areaX, workspaceY, browserW, workspaceH, entries, mx, my, scale, palette);
        renderInspectorPanel(inspectorX, workspaceY, inspectorW, workspaceH, mx, my, scale, palette);
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
        scroll += (float) (amount * 20f);
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

        if (onlinePicker.isVisible()) {
            if (onlinePicker.click(mx, my, button)) {
                movementInputBlocked = onlinePicker.blocksMovementInput();
                return true;
            }
            return false;
        }

        if (copyButton.contains(mx, my) && selectedName != null) {
            ClipboardUtil.copy(selectedName);
            setStatus(tr("status.copied", "Copied: %s", selectedName));
            return true;
        }

        if (removeButton.contains(mx, my) && selectedName != null) {
            String removed = selectedName;
            if (tab.remove(removed)) {
                selectedName = null;
                setStatus(tr("status.removed", "Removed: %s", removed));
            }
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
            if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            selectedName = entryHit.name();
            activeField = ActiveField.NONE;
            movementInputBlocked = false;
            return true;
        }

        activeField = ActiveField.NONE;
        movementInputBlocked = false;
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

    private void renderToolbar(float x,
                               float y,
                               float w,
                               float h,
                               int visibleCount,
                               float mx,
                               float my,
                               float scale,
                               SettingsGuiPalette palette) {
        float dt = AnimationUtility.deltaTime();
        float segmentW = 44f * scale;
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

        int toolbarA = SettingsGuiPalette.withAlpha(palette.controlSurface(), 118);
        int toolbarB = SettingsGuiPalette.withAlpha(palette.controlSurfaceHover(), 102);
        LayoutRender2D.roundedQuad(x, y, modeW, h, 4.5f * scale, toolbarA, toolbarB, toolbarB, toolbarA);
        LayoutRender2D.roundedStroke(
                x, y, modeW, h, 4.5f * scale, 0.5f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 106)
        );

        float inset = 1.25f * scale;
        float activeX = x + inset + segmentW * modeAnim;
        float activeW = segmentW - inset * 2f;
        int activeA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.panelPillActive(), tab.color(), 0.10f), 190);
        int activeB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), tab.color(), 0.15f), 178);
        LayoutRender2D.roundedQuad(
                activeX,
                y + inset,
                activeW,
                h - inset * 2f,
                3.7f * scale,
                activeA,
                activeB,
                activeB,
                activeA
        );

        drawSegment(friendsPill, tr("tab.friends", "Friends"), tab == RelationTab.FRIENDS, friendsHoverAnim, scale, palette);
        drawSegment(enemiesPill, tr("tab.enemies", "Enemies"), tab == RelationTab.ENEMIES, enemiesHoverAnim, scale, palette);
        drawSegment(staffPill, tr("tab.staff", "Staff"), tab == RelationTab.STAFF, staffHoverAnim, scale, palette);

        String count = tr("count", "%s entries", visibleCount);
        float countSize = 5.8f * scale;
        float countX = x + modeW + 7f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                count,
                countX,
                y + (h - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), countSize)) * 0.5f,
                countSize,
                palette.panelMuted(),
                false
        );

        float action = h;
        float gap = 3f * scale;
        float inputRight = x + w - action * 2f - gap * 2f;
        float fieldX = Math.max(countX + 43f * scale, x + w - 118f * scale);
        float fieldW = Math.max(46f * scale, inputRight - fieldX);

        playerInputRect = new Rect(fieldX, y, fieldW, h);
        addTypedButton = new Rect(inputRight + gap, y, action, h);
        reservedPickerButton = new Rect(x + w - action, y, action, h);

        boolean onlineMode = onlinePicker.isOpen();
        if (onlineMode) {
            String query = onlinePicker.searchText();
            drawInput(
                    playerInputRect,
                    query,
                    tr("placeholder.search_online", "Search online"),
                    onlinePicker.isSearchFocused(),
                    mx,
                    my,
                    scale,
                    palette
            );
            addHoverAnim = updateHover(addHoverAnim, addTypedButton.contains(mx, my), dt);
            drawIconButton(addTypedButton, "rotate-ccw", addHoverAnim, query == null || query.isBlank(), scale, palette);
        } else {
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
            drawIconButton(addTypedButton, "check", addHoverAnim, false, scale, palette);
        }

        pickerHoverAnim = updateHover(pickerHoverAnim, reservedPickerButton.contains(mx, my) || onlinePicker.isVisible(), dt);
        drawIconButton(reservedPickerButton, onlineMode ? "panel-right-close" : "user-plus", pickerHoverAnim, false, scale, palette);
    }

    private void renderBrowserPanel(float x,
                                    float y,
                                    float w,
                                    float h,
                                    List<String> entries,
                                    float mx,
                                    float my,
                                    float scale,
                                    SettingsGuiPalette palette) {
        try (var transition = SettingsCardTransition.beginCard(x, y, w, h, 6f * scale, scale, palette)) {
            renderWorkspaceSurface(x, y, w, h, scale, palette);

            float pad = 6f * scale;
            float titleSize = 7.2f * scale;
            String title = tr("browser.title", "Players");
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterMedium(),
                    title,
                    x + pad,
                    y + 5.5f * scale,
                    titleSize,
                    palette.panelText(),
                    false
            );

            String summary = tr("browser.summary", "%s in %s", entries.size(), tab.label());
            float summarySize = 5.5f * scale;
            String fittedSummary = ClickGuiRenderer.fitText(
                    ClickGuiRenderer.getInterRegular(),
                    summary,
                    summarySize,
                    w - pad * 2f
            );
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    fittedSummary,
                    x + pad,
                    y + 16f * scale,
                    summarySize,
                    palette.panelMuted(),
                    false
            );

            float dividerY = y + 26f * scale;
            LayoutRender2D.rectQuad(
                    x + pad,
                    dividerY,
                    w - pad * 2f,
                    0.5f * scale,
                    LayoutRender2D.alpha(palette.menuLineLow(), 0.30f),
                    LayoutRender2D.alpha(palette.menuLineStrong(), 0.48f),
                    LayoutRender2D.alpha(palette.menuLineStrong(), 0.48f),
                    LayoutRender2D.alpha(palette.menuLineLow(), 0.30f)
            );

            listX = x + pad;
            listY = dividerY + 5f * scale;
            listW = w - pad * 2f;
            listH = Math.max(1f, y + h - pad - listY);
            renderCards(listX, listY, listW, listH, entries, mx, my, scale, palette);
        }
    }

    private void renderInspectorPanel(float x,
                                      float y,
                                      float w,
                                      float h,
                                      float mx,
                                      float my,
                                      float scale,
                                      SettingsGuiPalette palette) {
        try (var transition = SettingsCardTransition.beginCard(x, y, w, h, 6f * scale, scale, palette)) {
            renderWorkspaceSurface(x, y, w, h, scale, palette);

            float pad = 7f * scale;
            float innerX = x + pad;
            float innerW = Math.max(1f, w - pad * 2f);
            float cursorY = y + 5.5f * scale;

            String title = tr("details.title", "Inspector");
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterMedium(),
                    title,
                    innerX,
                    cursorY,
                    7.1f * scale,
                    palette.panelText(),
                    false
            );

            renderStatus(
                    innerX + 48f * scale,
                    cursorY - 0.5f * scale,
                    Math.max(1f, innerW - 48f * scale),
                    scale,
                    palette
            );

            cursorY += 15f * scale;
            float dividerY = cursorY;
            LayoutRender2D.rectQuad(
                    innerX,
                    dividerY,
                    innerW,
                    0.5f * scale,
                    LayoutRender2D.alpha(palette.menuLineLow(), 0.30f),
                    LayoutRender2D.alpha(palette.menuLineStrong(), 0.48f),
                    LayoutRender2D.alpha(palette.menuLineStrong(), 0.48f),
                    LayoutRender2D.alpha(palette.menuLineLow(), 0.30f)
            );
            cursorY += 5f * scale;

            if (selectedName == null) {
                String empty = ClickGuiSearch.hasQuery()
                        ? tr("empty.no_matches", "No matching players.")
                        : tr("details.empty", "Select a player to inspect.");
                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), empty, 6.7f * scale, innerW),
                        innerX,
                        cursorY + 3f * scale,
                        6.7f * scale,
                        palette.panelMuted(),
                        false
                );
                if (tab == RelationTab.STAFF) {
                    cursorY += 24f * scale;
                    renderHeuristicsPanel(innerX, cursorY, innerW, y + h - pad - cursorY, mx, my, scale, palette);
                }
                return;
            }

            float profileH = 37f * scale;
            float action = 15f * scale;
            float actionGap = 3f * scale;
            float actionsW = action * 2f + actionGap;
            cardComponent.renderProfile(
                    selectedName,
                    tab.label(),
                    tab.color(),
                    innerX,
                    cursorY,
                    Math.max(1f, innerW - actionsW - 6f * scale),
                    profileH,
                    scale,
                    palette
            );

            copyButton = new Rect(innerX + innerW - actionsW, cursorY + 3f * scale, action, action);
            removeButton = new Rect(copyButton.x() + action + actionGap, copyButton.y(), action, action);
            float dt = AnimationUtility.deltaTime();
            copyHoverAnim = updateHover(copyHoverAnim, copyButton.contains(mx, my), dt);
            removeHoverAnim = updateHover(removeHoverAnim, removeButton.contains(mx, my), dt);
            drawIconButton(copyButton, "copy", copyHoverAnim, false, scale, palette);
            drawDangerIconButton(removeButton, "trash-2", removeHoverAnim, scale, palette);

            cursorY += profileH + 5f * scale;
            renderInfoRow(
                    innerX,
                    cursorY,
                    innerW,
                    tr("details.relation", "Relation"),
                    tab.label(),
                    tab.color(),
                    scale,
                    palette
            );
            cursorY += 13f * scale;
            if (tab == RelationTab.STAFF) {
                // Staff rules are the useful secondary data on this tab; keep the
                // inspector dense enough that all three rule families remain visible.
                cursorY += 3f * scale;
                renderHeuristicsPanel(innerX, cursorY, innerW, y + h - pad - cursorY, mx, my, scale, palette);
            } else {
                renderInfoRow(
                        innerX,
                        cursorY,
                        innerW,
                        tr("details.storage", "Storage"),
                        tr("details.persistent", "Persistent"),
                        palette.panelMuted(),
                        scale,
                        palette
                );
                cursorY += 13f * scale;
                renderInfoRow(
                        innerX,
                        cursorY,
                        innerW,
                        tr("details.match", "Match"),
                        tr("details.exact_nick", "Exact nickname"),
                        palette.panelMuted(),
                        scale,
                        palette
                );
            }
        }
    }

    private void renderWorkspaceSurface(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float scale,
                                        SettingsGuiPalette palette) {
        float radius = 6f * scale;
        LayoutRender2D.roundedSoftShadow(
                x,
                y + 1.2f * scale,
                w,
                h,
                radius,
                7.5f * scale,
                0.0f,
                LayoutRender2D.alpha(0xFF000000, 0.13f)
        );
        ClickGuiRenderer.drawBlur(x, y, w, h, radius, palette.panelBlurTint(), 150f / 255f);
        LayoutRender2D.roundedQuad(
                x,
                y,
                w,
                h,
                radius,
                SettingsGuiPalette.withAlpha(palette.panelBgLeft(), 178),
                SettingsGuiPalette.withAlpha(palette.panelBgRight(), 166),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.07f), 172),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgLeft(), 0.05f), 182)
        );
        LayoutRender2D.roundedStrokeQuad(
                x,
                y,
                w,
                h,
                radius,
                0.55f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 102),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 84),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 72),
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 92)
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
        if (h <= 12f * scale) return;

        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        String title = tr("heuristics.title", "Staff heuristics");
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                x,
                y,
                6.7f * scale,
                palette.panelText(),
                false
        );

        enabledToggle = new Rect(x + w - 47f * scale, y - 1f * scale, 47f * scale, 12f * scale);
        drawToggle(enabledToggle, cfg.enabled(), mx, my, scale, palette);

        float rowY = y + 15f * scale;
        float remaining = Math.max(1f, h - 15f * scale);
        float rowH = Math.max(20f * scale, Math.min(25f * scale, remaining / 3f));
        renderHeuristicRow(
                ActiveField.PREFIX,
                tr("heuristics.prefixes", "Prefixes"),
                cfg.prefixes(),
                x, rowY, w, rowH, mx, my, scale, palette
        );
        rowY += rowH;
        renderHeuristicRow(
                ActiveField.SUFFIX,
                tr("heuristics.suffixes", "Suffixes"),
                cfg.suffixes(),
                x, rowY, w, rowH, mx, my, scale, palette
        );
        rowY += rowH;
        renderHeuristicRow(
                ActiveField.CONTAINS,
                tr("heuristics.contains", "Contains"),
                cfg.contains(),
                x, rowY, w, rowH, mx, my, scale, palette
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
        float labelW = 43f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), title, 5.7f * scale, labelW - 3f * scale),
                x,
                y + 4.2f * scale,
                5.7f * scale,
                palette.panelMuted(),
                false
        );

        float fieldH = 12.5f * scale;
        float buttonW = fieldH;
        float fieldX = x + labelW;
        float fieldW = Math.max(38f * scale, w - labelW - buttonW - 3f * scale);
        Rect field = new Rect(fieldX, y, fieldW, fieldH);
        Rect add = new Rect(fieldX + fieldW + 3f * scale, y, buttonW, fieldH);
        setHeuristicRects(kind, field, add);

        drawInput(field, fieldText(kind), tr("placeholder.rule", "Rule"), activeField == kind, mx, my, scale, palette);
        drawIconButton(add, "check", add.contains(mx, my) ? 1f : 0f, false, scale, palette);

        float chipY = y + fieldH + 2.5f * scale;
        if (chipY + 8f * scale > y + h) return;

        float chipX = fieldX;
        float maxX = x + w;
        for (String entry : sorted(entries)) {
            float textSize = 5.0f * scale;
            float available = Math.max(16f * scale, maxX - chipX);
            String fitted = ClickGuiRenderer.fitText(
                    ClickGuiRenderer.getInterRegular(),
                    entry,
                    textSize,
                    Math.max(8f * scale, available - 12f * scale)
            );
            float chipW = Math.min(
                    available,
                    ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, textSize) + 13f * scale
            );
            if (chipW < 15f * scale || chipX + chipW > maxX + 0.1f) break;

            Rect chip = new Rect(chipX, chipY, chipW, 8.5f * scale);
            Rect del = new Rect(chipX + chipW - 8.5f * scale, chipY, 8.5f * scale, 8.5f * scale);
            LayoutRender2D.roundedQuad(
                    chip.x(), chip.y(), chip.w(), chip.h(), 2.8f * scale,
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.90f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.80f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.80f),
                    LayoutRender2D.alpha(palette.panelPillBase(), 0.90f)
            );
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    fitted,
                    chip.x() + 3f * scale,
                    chip.y() + 1.9f * scale,
                    textSize,
                    palette.panelText(),
                    false
            );
            Renderer2D.COLOR.svg(
                    "x",
                    del.x() + 1.8f * scale,
                    del.y() + 1.8f * scale,
                    4.8f * scale,
                    4.8f * scale,
                    SvgRenderOptions.overrideColor(palette.panelMuted())
            );
            chipHits.add(new ChipHit(kind, entry, del));
            chipX += chipW + 2.5f * scale;
            if (chipX >= maxX - 15f * scale) break;
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
        float rowH = 21.5f * scale;
        float gap = 3.0f * scale;
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
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    empty,
                    x + 2f * scale,
                    y + 2f * scale,
                    6.3f * scale,
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
                        tab.label(),
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

    private void renderInfoRow(float x,
                               float y,
                               float w,
                               String label,
                               String value,
                               int valueColor,
                               float scale,
                               SettingsGuiPalette palette) {
        float h = 10.5f * scale;
        int bg = SettingsGuiPalette.withAlpha(palette.controlSurface(), 72);
        LayoutRender2D.roundedQuad(x, y, w, h, 3f * scale, bg, bg, bg, bg);

        float labelSize = 5.4f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), label, labelSize, w * 0.42f),
                x + 4f * scale,
                y + 2.6f * scale,
                labelSize,
                palette.panelMuted(),
                false
        );

        float valueX = x + w * 0.43f;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), value, labelSize, w * 0.53f),
                valueX,
                y + 2.6f * scale,
                labelSize,
                SettingsGuiPalette.mix(palette.panelText(), valueColor, 0.26f),
                false
        );
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
        float size = 6.2f * scale;
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
        float radius = 3.6f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(),
                rect.y(),
                rect.w(),
                rect.h(),
                radius,
                0.45f * scale,
                active
                        ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), tab.color(), 0.44f), 188)
                        : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), hover ? 108 : 74)
        );

        boolean empty = value == null || value.isEmpty();
        String raw = empty ? (active ? "" : placeholder) : value;
        int color = empty && !active ? palette.panelMuted() : palette.panelText();
        float size = 5.8f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), raw, size, rect.w() - 7f * scale);
        float textY = rect.y() + (rect.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), size)) * 0.5f;

        boolean clipped = ScissorFunction.pushRaw(rect.x(), rect.y(), rect.w(), rect.h());
        try {
            if (!text.isEmpty()) {
                ClickGuiRenderer.drawText(
                        ClickGuiRenderer.getInterRegular(),
                        text,
                        rect.x() + 3.5f * scale,
                        textY,
                        size,
                        color,
                        false
                );
            }
            if (active && ((System.currentTimeMillis() / 500L) & 1L) == 0L) {
                float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, size);
                LayoutRender2D.rect(
                        rect.x() + Math.min(tw + 4.5f * scale, rect.w() - 2.5f * scale),
                        rect.y() + 2.8f * scale,
                        0.55f * scale,
                        rect.h() - 5.6f * scale,
                        palette.panelText()
                );
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void drawIconButton(Rect rect,
                                String icon,
                                float hover,
                                boolean disabled,
                                float scale,
                                SettingsGuiPalette palette) {
        if (!disabled && rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }

        int bgA = disabled
                ? LayoutRender2D.alpha(palette.controlSurface(), 0.36f)
                : SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), 0.18f + hover * 0.42f),
                        118 + Math.round(28f * hover)
                );
        int bgB = disabled
                ? LayoutRender2D.alpha(palette.controlSurface(), 0.30f)
                : SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.04f + hover * 0.12f),
                        112 + Math.round(30f * hover)
                );

        float radius = 3.6f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), radius, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), disabled ? 48 : 92)
        );

        int iconColor = disabled ? LayoutRender2D.alpha(palette.panelMuted(), 0.50f) : palette.menuCategoryText();
        float iconSize = Math.min(rect.w(), rect.h()) - 6f * scale;
        Renderer2D.COLOR.svg(
                icon,
                rect.x() + (rect.w() - iconSize) * 0.5f,
                rect.y() + (rect.h() - iconSize) * 0.5f,
                iconSize,
                iconSize,
                SvgRenderOptions.overrideColor(iconColor)
        );
    }

    private void drawDangerIconButton(Rect rect,
                                      String icon,
                                      float hover,
                                      float scale,
                                      SettingsGuiPalette palette) {
        if (rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }
        int danger = 0xFFFF6B6B;
        int bgA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), danger, 0.05f + 0.13f * hover),
                112 + Math.round(24f * hover)
        );
        int bgB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), danger, 0.06f + 0.15f * hover),
                108 + Math.round(26f * hover)
        );
        float radius = 3.6f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), radius, 0.45f * scale,
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeSoft(), danger, 0.32f), 108)
        );

        float iconSize = Math.min(rect.w(), rect.h()) - 6f * scale;
        Renderer2D.COLOR.svg(
                icon,
                rect.x() + (rect.w() - iconSize) * 0.5f,
                rect.y() + (rect.h() - iconSize) * 0.5f,
                iconSize,
                iconSize,
                SvgRenderOptions.overrideColor(SettingsGuiPalette.mix(palette.menuCategoryText(), danger, 0.28f + 0.30f * hover))
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
                ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelPillActive(), tab.color(), hover ? 0.18f : 0.10f), 176)
                : SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), hover ? 0.28f : 0.08f), 118);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 3.5f * scale, bg, bg, bg, bg);

        String label = enabled ? tr("heuristics.enabled", "Enabled") : tr("heuristics.disabled", "Disabled");
        float size = 5.2f * scale;
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, size);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                label,
                rect.x() + (rect.w() - tw) * 0.5f,
                rect.y() + 3.0f * scale,
                size,
                enabled ? palette.panelText() : palette.panelMuted(),
                false
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

    private void ensureSelection(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            selectedName = null;
            return;
        }
        if (selectedName != null) {
            for (String entry : entries) {
                if (equalsIgnoreCase(selectedName, entry)) {
                    selectedName = entry;
                    return;
                }
            }
        }
        selectedName = entries.get(0);
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

    private void renderStatus(float x, float y, float w, float scale, SettingsGuiPalette palette) {
        if (statusMessage == null || statusMessage.isBlank()) return;
        if (System.currentTimeMillis() > statusUntilMs) {
            clearStatus();
            return;
        }

        float size = 5.3f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), statusMessage, size, w);
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, size);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                text,
                x + Math.max(0f, w - tw),
                y + 1.0f * scale,
                size,
                SettingsGuiPalette.mix(palette.panelMuted(), tab.color(), 0.18f),
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
                scrollbarX,
                scrollbarY,
                scrollbarW,
                scrollbarH,
                1.5f * scale,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );
        LayoutRender2D.roundedQuad(
                scrollbarX,
                scrollbarThumbY,
                scrollbarW,
                scrollbarThumbH,
                1.5f * scale,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
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

        scrollbarW = 2.2f * scale;
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
                mx,
                my,
                scrollbarX - pad,
                scrollbarY - pad,
                scrollbarW + pad * 2f,
                scrollbarH + pad * 2f
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
        copyButton = Rect.ZERO;
        removeButton = Rect.ZERO;
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

        private DefineTarget.RelationTargetMode pickerMode() {
            return switch (this) {
                case FRIENDS -> DefineTarget.RelationTargetMode.FRIEND;
                case ENEMIES -> DefineTarget.RelationTargetMode.ENEMY;
                case STAFF -> DefineTarget.RelationTargetMode.STAFF;
            };
        }

        private String label() {
            return switch (this) {
                case FRIENDS -> tr("tab.friends", "Friends");
                case ENEMIES -> tr("tab.enemies", "Enemies");
                case STAFF -> tr("tab.staff", "Staff");
            };
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
