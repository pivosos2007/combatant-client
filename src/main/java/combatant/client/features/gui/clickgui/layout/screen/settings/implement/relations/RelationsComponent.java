/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.sound.GuiSound;
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
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Relations workspace: relation browser, player inspector, online picker and staff heuristics.
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
    private Rect inspectorCopyButton = Rect.ZERO;
    private Rect inspectorDeleteButton = Rect.ZERO;
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

    private float heuristicsScroll;
    private float smoothedHeuristicsScroll;
    private float heuristicsMaxScroll;
    private Rect heuristicsViewport = Rect.ZERO;

    private float modeAnim;
    private float friendsHoverAnim;
    private float enemiesHoverAnim;
    private float staffHoverAnim;
    private float addHoverAnim;
    private float pickerHoverAnim;
    private float inspectorCopyHoverAnim;
    private float inspectorDeleteHoverAnim;
    private float enabledToggleHoverAnim;
    private float enabledToggleValueAnim;
    private final Map<ActiveField, Float> heuristicRowHoverAnim = new EnumMap<>(ActiveField.class);
    private final Map<ActiveField, Float> heuristicAddHoverAnim = new EnumMap<>(ActiveField.class);
    private final Map<String, Float> inputHoverAnim = new HashMap<>();
    private final Map<String, Float> chipHoverAnim = new HashMap<>();

    private static boolean movementInputBlocked;

    public RelationsComponent() {
        this.onlinePicker = new OnlineRelationPlayerPickerComponent(this::setStatus);
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        heuristicsScroll = 0f;
        smoothedHeuristicsScroll = 0f;
        heuristicsMaxScroll = 0f;
        heuristicsViewport = Rect.ZERO;
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
        validateSelection(entries);

        float toolbarH = 18f * scale;
        renderToolbar(areaX, areaY, areaW, toolbarH, mx, my, scale, palette);

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
        renderMainPanel(areaX, workspaceY, areaW, workspaceH, entries, mx, my, scale, palette);
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
        if (tab == RelationTab.STAFF && heuristicsViewport.contains(mx, my)) {
            heuristicsScroll += (float) (amount * 22f);
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
                movementInputBlocked = onlinePicker.blocksMovementInput();
                clearStatus();
                return true;
            }
            return focusField(ActiveField.PLAYER);
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

        if (selectedName != null && inspectorCopyButton.contains(mx, my)) {
            ClipboardUtil.copy(selectedName);
            setStatus(tr("status.copied", "Copied: %s", selectedName));
            return true;
        }
        if (selectedName != null && inspectorDeleteButton.contains(mx, my)) {
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
                GuiSound.booleanFeedback(cfg.enabled());
                return true;
            }
            if (heuristicsViewport.contains(mx, my)) {
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

        // Explicit segmented-control separators. They stay visible between all
        // three modes while the inset active pill remains visually independent.
        drawPillSeparator(x + segmentW, y, h, scale, palette);
        drawPillSeparator(x + segmentW * 2f, y, h, scale, palette);

        drawSegment(friendsPill, tr("tab.friends", "Friends"), tab == RelationTab.FRIENDS, friendsHoverAnim, scale, palette);
        drawSegment(enemiesPill, tr("tab.enemies", "Enemies"), tab == RelationTab.ENEMIES, enemiesHoverAnim, scale, palette);
        drawSegment(staffPill, tr("tab.staff", "Staff"), tab == RelationTab.STAFF, staffHoverAnim, scale, palette);

        float action = h;
        float gap = 4f * scale;
        reservedPickerButton = new Rect(x + w - action, y, action, h);
        addTypedButton = new Rect(reservedPickerButton.x() - gap - action, y, action, h);
        float fieldRight = addTypedButton.x() - gap;
        float minFieldW = 72f * scale;
        float desiredFieldW = 122f * scale;
        float maxFieldW = Math.max(40f * scale, fieldRight - (x + modeW + 12f * scale));
        float fieldW = Math.min(desiredFieldW, Math.max(minFieldW, maxFieldW));
        fieldW = Math.min(fieldW, maxFieldW);
        float fieldX = fieldRight - fieldW;
        playerInputRect = new Rect(fieldX, y, fieldW, h);

        boolean onlineMode = onlinePicker.isOpen();
        if (onlineMode) {
            String query = onlinePicker.searchText();
            drawInput(
                    "player",
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
                    "player",
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

        pickerHoverAnim = updateHover(
                pickerHoverAnim,
                reservedPickerButton.contains(mx, my) || onlinePicker.isVisible(),
                dt
        );
        drawIconButton(
                reservedPickerButton,
                onlineMode ? "panel-right-close" : "user-plus",
                pickerHoverAnim,
                false,
                scale,
                palette
        );

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

            float pad = 7f * scale;
            float gap = 7f * scale;
            float innerX = x + pad;
            float innerY = y + pad;
            float innerW = Math.max(1f, w - pad * 2f);
            float innerH = Math.max(1f, h - pad * 2f);

            float desiredSideW = Math.min(166f * scale, Math.max(136f * scale, innerW * 0.44f));
            float maxSideW = Math.max(108f * scale, innerW - gap - 150f * scale);
            float sideW = Math.min(desiredSideW, maxSideW);
            float browserW = Math.max(1f, innerW - sideW - gap);
            float sideX = innerX + browserW + gap;

            renderBrowserPanel(innerX, innerY, browserW, innerH, entries, mx, my, scale, palette);
            if (tab == RelationTab.STAFF) {
                renderHeuristicsPanel(sideX, innerY, sideW, innerH, mx, my, scale, palette);
            } else {
                renderInspectorPanel(sideX, innerY, sideW, innerH, mx, my, scale, palette);
            }
        }
    }

    private void renderWorkspaceSurface(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float scale,
                                        SettingsGuiPalette palette) {
        float radius = 7.0f * scale;
        LayoutRender2D.roundedSoftShadow(
                x, y + 1.2f * scale, w, h, radius, 8.0f * scale, 0.012f,
                LayoutRender2D.alpha(0xFF000000, 0.15f)
        );
        ClickGuiRenderer.drawBlur(x, y, w, h, radius, palette.panelBlurTint(), 150f / 255f);
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                SettingsGuiPalette.withAlpha(palette.panelBgLeft(), 174),
                SettingsGuiPalette.withAlpha(palette.panelBgRight(), 162),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.075f), 168),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgLeft(), 0.055f), 178)
        );
        LayoutRender2D.roundedStrokeQuad(
                x, y, w, h, radius, 0.55f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 96),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 76),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 64),
                SettingsGuiPalette.withAlpha(palette.glassEdgeStrong(), 88)
        );
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
        renderInnerPanel(x, y, w, h, 5.8f * scale, tab.color(), 0.055f, scale, palette);

        float pad = 7f * scale;
        float headerH = 24f * scale;
        float titleSize = 7.8f * scale;
        float countSize = 8.0f * scale;
        String title = tabLabel();
        String count = Integer.toString(entries.size());
        TextRenderer titleFont = ClickGuiRenderer.getInterMedium();
        TextRenderer countFont = relationHeaderBold();
        float titleX = x + pad;
        float titleY = y + 7.1f * scale;
        ClickGuiRenderer.drawText(titleFont, title, titleX, titleY, titleSize, palette.panelText(), false);
        float titleW = ClickGuiRenderer.textWidth(titleFont, title, titleSize);
        ClickGuiRenderer.drawText(
                countFont,
                count,
                titleX + titleW + 5f * scale,
                titleY - 0.15f * scale,
                countSize,
                SettingsGuiPalette.mix(palette.panelText(), tab.color() | 0xFF000000, 0.18f),
                false
        );

        LayoutRender2D.rectQuad(
                x + pad,
                y + headerH,
                Math.max(1f, w - pad * 2f),
                0.5f * scale,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.30f),
                LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.menuLineStrong(), tab.color(), 0.16f), 0.58f),
                LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.menuLineStrong(), tab.color(), 0.10f), 0.52f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.26f)
        );

        listX = x + pad;
        listY = y + headerH + 6f * scale;
        listW = Math.max(1f, w - pad * 2f);
        listH = Math.max(1f, y + h - pad - listY);
        renderCards(listX, listY, listW, listH, entries, mx, my, scale, palette);
    }

    private void renderInspectorPanel(float x,
                                      float y,
                                      float w,
                                      float h,
                                      float mx,
                                      float my,
                                      float scale,
                                      SettingsGuiPalette palette) {
        renderInnerPanel(x, y, w, h, 5.8f * scale, tab.color(), selectedName == null ? 0.025f : 0.075f, scale, palette);

        float pad = 7f * scale;
        String title = tr("details.title", "Inspector");
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), title,
                x + pad, y + 6f * scale,
                7.7f * scale, palette.panelText(), false
        );
        LayoutRender2D.rectQuad(
                x + pad,
                y + 20f * scale,
                Math.max(1f, w - pad * 2f),
                0.5f * scale,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.28f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.52f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.46f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.24f)
        );

        if (selectedName == null || selectedName.isBlank()) {
            float iconSize = 22f * scale;
            float centerX = x + w * 0.5f;
            float centerY = y + Math.min(h * 0.43f, 70f * scale);
            int shell = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.045f), 96);
            LayoutRender2D.roundedQuad(
                    centerX - iconSize * 0.5f,
                    centerY - iconSize * 0.5f,
                    iconSize,
                    iconSize,
                    6f * scale,
                    shell, shell, shell, shell
            );
            Renderer2D.COLOR.svg(
                    "user",
                    centerX - 5.5f * scale,
                    centerY - 5.5f * scale,
                    11f * scale,
                    11f * scale,
                    SvgRenderOptions.overrideColor(LayoutRender2D.alpha(palette.panelMuted(), 0.76f))
            );
            String empty = tr("details.empty", "Select a player to inspect.");
            float emptySize = 6.1f * scale;
            String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), empty, emptySize, w - 20f * scale);
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, emptySize);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(), fitted,
                    x + (w - tw) * 0.5f,
                    centerY + 17f * scale,
                    emptySize,
                    palette.panelMuted(),
                    false
            );
            String hint = tr("details.empty_hint", "Choose a row on the left.");
            float hintSize = 5.35f * scale;
            String fittedHint = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), hint, hintSize, w - 24f * scale);
            float hintW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fittedHint, hintSize);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(), fittedHint,
                    x + (w - hintW) * 0.5f,
                    centerY + 28f * scale,
                    hintSize,
                    LayoutRender2D.alpha(palette.panelMuted(), 0.68f),
                    false
            );
            return;
        }

        float portrait = 31f * scale;
        float portraitX = x + pad;
        float portraitY = y + 28f * scale;
        LayoutRender2D.roundedSoftShadow(
                portraitX, portraitY + 0.5f * scale, portrait, portrait,
                6f * scale, 4f * scale, 0.02f,
                SettingsGuiPalette.withAlpha(tab.color(), 64)
        );
        cardComponent.renderPortrait(selectedName, portraitX, portraitY, portrait, 6f * scale, scale, palette, 1f);
        Renderer2D.COLOR.roundedRectStroke(
                portraitX,
                portraitY,
                portrait,
                portrait,
                6f * scale,
                1f,
                0.65f * scale,
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeStrong(), tab.color(), 0.34f), 164)
        );

        float nameX = portraitX + portrait + 7f * scale;
        float availableNameW = Math.max(1f, x + w - pad - nameX);
        String fittedName = ClickGuiRenderer.fitText(
                ClickGuiRenderer.getInterMedium(), selectedName, 8.2f * scale, availableNameW
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), fittedName,
                nameX, portraitY + 2f * scale,
                8.2f * scale, palette.panelText(), false
        );
        drawRelationBadge(
                nameX,
                portraitY + 16.5f * scale,
                availableNameW,
                tabLabel(),
                tab.color(),
                scale,
                palette
        );

        float infoY = portraitY + portrait + 9f * scale;
        renderInspectorInfoRow(
                x + pad, infoY, w - pad * 2f, 22f * scale,
                tr("details.storage", "Storage"),
                tr("details.persistent", "Persistent"),
                "folder-check", scale, palette
        );
        infoY += 25f * scale;
        renderInspectorInfoRow(
                x + pad, infoY, w - pad * 2f, 22f * scale,
                tr("details.match", "Match"),
                tr("details.exact_nick", "Exact nickname"),
                "crosshair", scale, palette
        );

        float buttonGap = 4f * scale;
        float buttonH = 18f * scale;
        float buttonY = y + h - pad - buttonH;
        float buttonW = Math.max(1f, (w - pad * 2f - buttonGap) * 0.5f);
        inspectorCopyButton = new Rect(x + pad, buttonY, buttonW, buttonH);
        inspectorDeleteButton = new Rect(inspectorCopyButton.x() + buttonW + buttonGap, buttonY, buttonW, buttonH);
        float dt = AnimationUtility.deltaTime();
        inspectorCopyHoverAnim = updateHover(inspectorCopyHoverAnim, inspectorCopyButton.contains(mx, my), dt);
        inspectorDeleteHoverAnim = updateHover(inspectorDeleteHoverAnim, inspectorDeleteButton.contains(mx, my), dt);
        drawInspectorAction(inspectorCopyButton, "clipboard", tr("details.copy", "Copy"), inspectorCopyHoverAnim, false, scale, palette);
        drawInspectorAction(inspectorDeleteButton, "trash-2", tr("details.remove", "Remove"), inspectorDeleteHoverAnim, true, scale, palette);
    }

    private void renderInspectorInfoRow(float x,
                                        float y,
                                        float w,
                                        float h,
                                        String label,
                                        String value,
                                        String icon,
                                        float scale,
                                        SettingsGuiPalette palette) {
        int bgA = SettingsGuiPalette.withAlpha(palette.controlSurface(), 76);
        int bgB = SettingsGuiPalette.withAlpha(palette.controlSurfaceHover(), 68);
        LayoutRender2D.roundedQuad(x, y, w, h, 4.5f * scale, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                x, y, w, h, 4.5f * scale, 0.42f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 56)
        );
        float iconBox = 14f * scale;
        float iconX = x + 4f * scale;
        float iconY = y + (h - iconBox) * 0.5f;
        int iconBg = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurfaceHover(), tab.color(), 0.08f), 90);
        LayoutRender2D.roundedQuad(iconX, iconY, iconBox, iconBox, 3.5f * scale, iconBg, iconBg, iconBg, iconBg);
        Renderer2D.COLOR.svg(
                icon,
                iconX + 3.8f * scale,
                iconY + 3.8f * scale,
                iconBox - 7.6f * scale,
                iconBox - 7.6f * scale,
                SvgRenderOptions.overrideColor(SettingsGuiPalette.mix(palette.panelMuted(), tab.color(), 0.22f))
        );
        float textX = iconX + iconBox + 5f * scale;
        float textW = Math.max(1f, x + w - 5f * scale - textX);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), label, 5.1f * scale, textW),
                textX, y + 4f * scale, 5.1f * scale,
                palette.panelMuted(), false
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), value, 5.8f * scale, textW),
                textX, y + 11.8f * scale, 5.8f * scale,
                palette.panelText(), false
        );
    }

    private void drawRelationBadge(float x,
                                   float y,
                                   float maxW,
                                   String text,
                                   int color,
                                   float scale,
                                   SettingsGuiPalette palette) {
        float fontSize = 5.4f * scale;
        String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), text, fontSize, Math.max(1f, maxW - 12f * scale));
        float textW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), fitted, fontSize);
        float badgeW = Math.min(maxW, textW + 13f * scale);
        float badgeH = 12f * scale;
        int bgA = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), color, 0.12f), 118);
        int bgB = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurfaceHover(), color, 0.18f), 128);
        LayoutRender2D.roundedQuad(x, y, badgeW, badgeH, badgeH * 0.5f, bgA, bgB, bgB, bgA);
        float dot = 4f * scale;
        Renderer2D.COLOR.roundedRect(
                x + 4f * scale,
                y + (badgeH - dot) * 0.5f,
                dot, dot, dot * 0.5f, 1f,
                SettingsGuiPalette.withAlpha(color, 236)
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), fitted,
                x + 9.5f * scale,
                y + (badgeH - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), fontSize)) * 0.5f,
                fontSize,
                SettingsGuiPalette.mix(palette.panelText(), color | 0xFF000000, 0.16f),
                false
        );
    }

    private void drawInspectorAction(Rect rect,
                                     String icon,
                                     String label,
                                     float hover,
                                     boolean danger,
                                     float scale,
                                     SettingsGuiPalette palette) {
        if (rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }
        int accent = danger ? 0xFFFF6B6B : tab.color();
        int bgA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), accent, (danger ? 0.035f : 0.025f) + hover * 0.10f),
                Math.round(98f + 28f * hover)
        );
        int bgB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), accent, (danger ? 0.055f : 0.045f) + hover * 0.14f),
                Math.round(104f + 32f * hover)
        );
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4.5f * scale, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), 4.5f * scale, 0.45f * scale,
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeSoft(), accent, hover * 0.20f), Math.round(66f + 38f * hover))
        );

        float iconSize = 6.7f * scale;
        float fontSize = 5.6f * scale;
        String fitted = ClickGuiRenderer.fitText(
                ClickGuiRenderer.getInterMedium(), label, fontSize,
                Math.max(1f, rect.w() - iconSize - 14f * scale)
        );
        float textW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), fitted, fontSize);
        float groupW = iconSize + 4f * scale + textW;
        float startX = rect.x() + (rect.w() - groupW) * 0.5f;
        int fg = danger
                ? SettingsGuiPalette.mix(palette.panelMuted(), accent, 0.42f + hover * 0.20f)
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.56f + hover * 0.28f);
        Renderer2D.COLOR.svg(
                icon,
                startX,
                rect.y() + (rect.h() - iconSize) * 0.5f,
                iconSize,
                iconSize,
                SvgRenderOptions.overrideColor(fg)
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), fitted,
                startX + iconSize + 4f * scale,
                rect.y() + (rect.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), fontSize)) * 0.5f,
                fontSize, fg, false
        );
    }

    private void renderInnerPanel(float x,
                                  float y,
                                  float w,
                                  float h,
                                  float radius,
                                  int accent,
                                  float accentMix,
                                  float scale,
                                  SettingsGuiPalette palette) {
        int a = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), accent, accentMix), 68);
        int b = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurfaceHover(), accent, accentMix * 1.35f), 62);
        LayoutRender2D.roundedQuad(x, y, w, h, radius, a, b, b, a);
        LayoutRender2D.roundedStrokeQuad(
                x, y, w, h, radius, 0.48f * scale,
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeStrong(), accent, accentMix * 1.7f), 66),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 48),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 42),
                SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeStrong(), accent, accentMix), 58)
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
        if (h <= 58f * scale) return;
        renderInnerPanel(x, y, w, h, 5.8f * scale, tab.color(), 0.055f, scale, palette);

        float pad = 7f * scale;
        float titleSize = 7.7f * scale;
        String title = tr("heuristics.title", "Staff heuristics");
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                x + pad,
                y + 6.0f * scale,
                titleSize,
                palette.panelText(),
                false
        );

        StaffHeuristicsConfig cfg = StaffHeuristicsConfig.get();
        float toggleW = 26f * scale;
        float toggleH = 13f * scale;
        enabledToggle = new Rect(x + w - pad - toggleW, y + 5f * scale, toggleW, toggleH);
        drawToggle(enabledToggle, cfg.enabled(), mx, my, scale, palette);

        String subtitle = cfg.enabled()
                ? tr("heuristics.enabled", "Enabled")
                : tr("heuristics.disabled", "Disabled");
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                subtitle,
                x + pad,
                y + 17f * scale,
                5.25f * scale,
                SettingsGuiPalette.mix(palette.panelMuted(), cfg.enabled() ? tab.color() : palette.panelMuted(), cfg.enabled() ? 0.24f : 0f),
                false
        );

        LayoutRender2D.rectQuad(
                x + pad,
                y + 27f * scale,
                Math.max(1f, w - pad * 2f),
                0.5f * scale,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.28f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.48f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.42f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.24f)
        );

        float viewportY = y + 32f * scale;
        float viewportH = Math.max(1f, y + h - pad - viewportY);
        float scrollbarReserve = 5f * scale;
        float contentX = x + pad;
        float contentW = Math.max(1f, w - pad * 2f - scrollbarReserve);
        heuristicsViewport = new Rect(contentX, viewportY, Math.max(1f, w - pad * 2f), viewportH);

        float rowGap = 4f * scale;
        float prefixH = heuristicRowHeight(cfg.prefixes(), contentW, scale);
        float suffixH = heuristicRowHeight(cfg.suffixes(), contentW, scale);
        float containsH = heuristicRowHeight(cfg.contains(), contentW, scale);
        float contentH = prefixH + suffixH + containsH + rowGap * 2f;
        heuristicsMaxScroll = Math.max(0f, contentH - viewportH);
        heuristicsScroll = ClickGuiMath.clamp(heuristicsScroll, -heuristicsMaxScroll, 0f);
        smoothedHeuristicsScroll = AnimationUtility.approach(smoothedHeuristicsScroll, heuristicsScroll, 0.24f);
        smoothedHeuristicsScroll = AnimationUtility.snap(smoothedHeuristicsScroll, heuristicsScroll, 0.05f);

        float rowY = viewportY + smoothedHeuristicsScroll;
        boolean clipped = ScissorFunction.pushRaw(heuristicsViewport.x(), heuristicsViewport.y(), heuristicsViewport.w(), heuristicsViewport.h());
        try {
            renderHeuristicRow(
                    ActiveField.PREFIX,
                    tr("heuristics.prefixes", "Prefixes"),
                    cfg.prefixes(),
                    contentX, rowY, contentW, prefixH, mx, my, scale, palette
            );
            rowY += prefixH + rowGap;
            renderHeuristicRow(
                    ActiveField.SUFFIX,
                    tr("heuristics.suffixes", "Suffixes"),
                    cfg.suffixes(),
                    contentX, rowY, contentW, suffixH, mx, my, scale, palette
            );
            rowY += suffixH + rowGap;
            renderHeuristicRow(
                    ActiveField.CONTAINS,
                    tr("heuristics.contains", "Contains"),
                    cfg.contains(),
                    contentX, rowY, contentW, containsH, mx, my, scale, palette
            );
        } finally {
            if (clipped) ScissorFunction.pop();
        }

        renderHeuristicsScrollbar(x + w - pad - 2.5f * scale, viewportY, 2.0f * scale, viewportH, scale, palette);
    }

    private float heuristicRowHeight(Set<String> entries, float w, float scale) {
        List<String> sortedEntries = sorted(entries);
        if (sortedEntries.isEmpty()) return 34f * scale;

        float innerPad = 5.5f * scale;
        float chipGap = 3f * scale;
        float chipH = 13f * scale;
        float maxX = Math.max(1f, w - innerPad * 2f);
        float lineW = 0f;
        int lines = 1;

        for (String entry : sortedEntries) {
            float textSize = 5.25f * scale;
            float maxTextW = Math.max(12f * scale, maxX - 18f * scale);
            String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), entry, textSize, maxTextW);
            float chipW = Math.min(
                    ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, textSize) + 18f * scale,
                    maxX
            );
            if (lineW > 0f && lineW + chipGap + chipW > maxX) {
                lines++;
                lineW = chipW;
            } else {
                lineW += (lineW > 0f ? chipGap : 0f) + chipW;
            }
        }

        float lineGap = 2.7f * scale;
        float chipsH = lines * chipH + Math.max(0, lines - 1) * lineGap;
        return 34f * scale + chipsH + 4f * scale;
    }

    private void renderHeuristicsScrollbar(float x,
                                           float y,
                                           float w,
                                           float h,
                                           float scale,
                                           SettingsGuiPalette palette) {
        if (heuristicsMaxScroll <= 0.5f || h <= 1f) return;
        float thumbH = Math.max(16f * scale, h * (h / (heuristicsMaxScroll + h)));
        float ratio = heuristicsMaxScroll <= 0f ? 0f : -smoothedHeuristicsScroll / heuristicsMaxScroll;
        float thumbY = y + (h - thumbH) * AnimationUtility.clamp(ratio, 0f, 1f);
        int track = SettingsGuiPalette.withAlpha(palette.controlSurfaceHover(), 48);
        int thumb = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelMuted(), tab.color(), 0.16f), 132);
        LayoutRender2D.roundedQuad(x, y, w, h, w * 0.5f, track, track, track, track);
        LayoutRender2D.roundedQuad(x, thumbY, w, thumbH, w * 0.5f, thumb, thumb, thumb, thumb);
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
        boolean rowHovered = heuristicsViewport.contains(mx, my) && ClickGuiMath.insideRect(mx, my, x, y, w, h);
        float rowHover = animateMapValue(heuristicRowHoverAnim, kind, rowHovered, 12f);
        int rowA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.012f + rowHover * 0.035f),
                Math.round(62f + rowHover * 22f)
        );
        int rowB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), tab.color(), 0.018f + rowHover * 0.05f),
                Math.round(54f + rowHover * 26f)
        );
        LayoutRender2D.roundedQuad(x, y, w, h, 5.0f * scale, rowA, rowB, rowB, rowA);
        LayoutRender2D.roundedStroke(
                x, y, w, h, 5.0f * scale, 0.42f * scale,
                SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.glassEdgeSoft(), tab.color(), rowHover * 0.10f),
                        Math.round(46f + rowHover * 32f)
                )
        );

        float innerPad = 5.5f * scale;
        float labelSize = 5.9f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), title, labelSize, Math.max(1f, w - 46f * scale)),
                x + innerPad,
                y + 4.2f * scale,
                labelSize,
                SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.18f + rowHover * 0.16f),
                false
        );
        String count = Integer.toString(entries == null ? 0 : entries.size());
        float countSize = 5.7f * scale;
        TextRenderer countFont = relationHeaderBold();
        float countW = ClickGuiRenderer.textWidth(countFont, count, countSize);
        ClickGuiRenderer.drawText(
                countFont,
                count,
                x + w - innerPad - countW,
                y + 4.0f * scale,
                countSize,
                SettingsGuiPalette.mix(palette.panelMuted(), tab.color() | 0xFF000000, 0.20f),
                false
        );

        float fieldY = y + 14f * scale;
        float fieldH = 16f * scale;
        float addW = fieldH;
        float fieldW = Math.max(26f * scale, w - innerPad * 2f - addW - 3.5f * scale);
        Rect field = new Rect(x + innerPad, fieldY, fieldW, fieldH);
        Rect add = new Rect(field.x() + field.w() + 3.5f * scale, fieldY, addW, fieldH);
        setHeuristicRects(kind, field, add);
        boolean pointerInsideViewport = heuristicsViewport.contains(mx, my);
        float controlMx = pointerInsideViewport ? mx : -100000f;
        float controlMy = pointerInsideViewport ? my : -100000f;
        drawInput("rule:" + kind.name(), field, fieldText(kind), tr("placeholder.rule", "Rule"), activeField == kind, controlMx, controlMy, scale, palette);
        float addHover = animateMapValue(heuristicAddHoverAnim, kind, pointerInsideViewport && add.contains(mx, my), 14f);
        drawSmallAddButton(add, addHover, scale, palette);

        List<String> sortedEntries = sorted(entries);
        if (sortedEntries.isEmpty()) return;

        float chipGap = 3f * scale;
        float lineGap = 2.7f * scale;
        float chipH = 13f * scale;
        float chipsY = fieldY + fieldH + 4f * scale;
        float chipX = x + innerPad;
        float maxX = x + w - innerPad;

        for (String entry : sortedEntries) {
            float textSize = 5.25f * scale;
            float maxTextW = Math.max(12f * scale, w - innerPad * 2f - 18f * scale);
            String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), entry, textSize, maxTextW);
            float chipW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, textSize) + 18f * scale;
            chipW = Math.min(chipW, w - innerPad * 2f);

            if (chipX + chipW > maxX && chipX > x + innerPad + 0.5f * scale) {
                chipX = x + innerPad;
                chipsY += chipH + lineGap;
            }

            Rect chip = new Rect(chipX, chipsY, chipW, chipH);
            boolean chipHovered = heuristicsViewport.contains(mx, my) && chip.contains(mx, my);
            String chipKey = kind.name() + ':' + entry.toLowerCase(Locale.ROOT);
            float chipHover = animateMapValue(chipHoverAnim, chipKey, chipHovered, 15f);
            if (chipHovered) SystemCursor.set(SystemCursor.CursorType.HAND);

            int chipBgA = SettingsGuiPalette.withAlpha(
                    SettingsGuiPalette.mix(palette.panelPillBase(), tab.color(), 0.035f + chipHover * 0.075f),
                    Math.round(112f + chipHover * 34f)
            );
            int chipBgB = SettingsGuiPalette.withAlpha(
                    SettingsGuiPalette.mix(palette.controlSurfaceHover(), tab.color(), 0.055f + chipHover * 0.095f),
                    Math.round(118f + chipHover * 34f)
            );
            LayoutRender2D.roundedQuad(chip.x(), chip.y(), chip.w(), chip.h(), chip.h() * 0.38f,
                    chipBgA, chipBgB, chipBgB, chipBgA);
            LayoutRender2D.roundedStroke(
                    chip.x(), chip.y(), chip.w(), chip.h(), chip.h() * 0.38f, 0.4f * scale,
                    SettingsGuiPalette.withAlpha(
                            SettingsGuiPalette.mix(palette.glassEdgeSoft(), tab.color(), chipHover * 0.14f),
                            Math.round(48f + chipHover * 42f)
                    )
            );

            float textY = chip.y() + (chip.h() - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), textSize)) * 0.5f;
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterRegular(),
                    fitted,
                    chip.x() + 4.5f * scale,
                    textY,
                    textSize,
                    SettingsGuiPalette.mix(palette.panelText(), tab.color() | 0xFF000000, chipHover * 0.10f),
                    false
            );

            float iconSize = 6.2f * scale;
            float iconX = chip.x() + chip.w() - iconSize - 4f * scale;
            float iconY = chip.y() + (chip.h() - iconSize) * 0.5f;
            Renderer2D.COLOR.svg(
                    "x",
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    SvgRenderOptions.overrideColor(SettingsGuiPalette.mix(palette.panelMuted(), 0xFFFF6B6B, 0.18f + chipHover * 0.34f))
            );

            chipHits.add(new ChipHit(kind, entry, chip));
            chipX += chipW + chipGap;
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
        float rowH = 30f * scale;
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
            renderEmptyBrowserState(x, y, rowW, h, scale, palette);
            return;
        }

        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        try {
            String relationLabel = tabLabel();
            for (int i = 0; i < entries.size(); i++) {
                float rowY = y + i * (rowH + gap) + smoothedScroll;
                if (rowY + rowH < y || rowY > y + h) continue;

                String name = entries.get(i);
                CardHit hit = cardComponent.renderRow(
                        name,
                        relationLabel,
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

    private void renderEmptyBrowserState(float x,
                                         float y,
                                         float w,
                                         float h,
                                         float scale,
                                         SettingsGuiPalette palette) {
        String empty = ClickGuiSearch.hasQuery()
                ? tr("empty.no_matches", "No matching players.")
                : tr("empty.none_added", "No players added.");
        String hint = ClickGuiSearch.hasQuery()
                ? tr("empty.search_hint", "Try another search query.")
                : tr("empty.add_hint", "Add a nickname above or choose an online player.");

        float iconBox = 28f * scale;
        float centerX = x + w * 0.5f;
        float centerY = y + Math.min(h * 0.40f, 62f * scale);
        int bg = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.055f), 88);
        LayoutRender2D.roundedQuad(
                centerX - iconBox * 0.5f,
                centerY - iconBox * 0.5f,
                iconBox,
                iconBox,
                7f * scale,
                bg, bg, bg, bg
        );
        Renderer2D.COLOR.svg(
                ClickGuiSearch.hasQuery() ? "circle-question-mark" : "user-plus",
                centerX - 6f * scale,
                centerY - 6f * scale,
                12f * scale,
                12f * scale,
                SvgRenderOptions.overrideColor(SettingsGuiPalette.mix(palette.panelMuted(), tab.color(), 0.18f))
        );

        float titleSize = 6.6f * scale;
        String fittedTitle = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), empty, titleSize, w - 20f * scale);
        float titleW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), fittedTitle, titleSize);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), fittedTitle,
                x + (w - titleW) * 0.5f,
                centerY + 20f * scale,
                titleSize,
                palette.panelMuted(),
                false
        );

        float hintSize = 5.3f * scale;
        String fittedHint = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), hint, hintSize, w - 24f * scale);
        float hintW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fittedHint, hintSize);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(), fittedHint,
                x + (w - hintW) * 0.5f,
                centerY + 31f * scale,
                hintSize,
                LayoutRender2D.alpha(palette.panelMuted(), 0.68f),
                false
        );
    }

    private String tabLabel() {
        return switch (tab) {
            case FRIENDS -> tr("tab.friends", "Friends");
            case ENEMIES -> tr("tab.enemies", "Enemies");
            case STAFF -> tr("tab.staff", "Staff");
        };
    }

    private void drawPillSeparator(float x,
                                   float y,
                                   float h,
                                   float scale,
                                   SettingsGuiPalette palette) {
        float lineW = Math.max(0.55f * scale, 0.45f);
        float padY = 4f * scale;
        LayoutRender2D.rect(
                x - lineW * 0.5f,
                y + padY,
                lineW,
                Math.max(1f, h - padY * 2f),
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), 92)
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

    private void drawInput(String animationKey,
                           Rect rect,
                           String value,
                           String placeholder,
                           boolean active,
                           float mx,
                           float my,
                           float scale,
                           SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        float hoverAnim = animateMapValue(inputHoverAnim, animationKey, hover || active, 14f);
        if (hover || active) SystemCursor.set(SystemCursor.CursorType.TEXT);

        int bgA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), palette.controlSurfaceHover(), 0.10f + hoverAnim * 0.32f),
                Math.round(104f + hoverAnim * 44f)
        );
        int bgB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.02f + hoverAnim * (active ? 0.08f : 0.035f)),
                Math.round(100f + hoverAnim * 42f)
        );
        float radius = 4f * scale;
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), radius, bgA, bgB, bgB, bgA);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), radius, 0.45f * scale,
                active
                        ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), tab.color(), 0.44f), 188)
                        : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), Math.round(70f + 38f * hoverAnim))
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

        float radius = 4f * scale;
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
                                    float hover,
                                    float scale,
                                    SettingsGuiPalette palette) {
        int bg = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), tab.color(), 0.05f + hover * 0.14f),
                Math.round(110f + 32f * hover)
        );
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4f * scale, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), 4f * scale, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), Math.round(72f + 32f * hover))
        );
        if (hover > 0.05f && rect.contains(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY())) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }

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

        float dt = AnimationUtility.deltaTime();
        enabledToggleHoverAnim = updateHover(enabledToggleHoverAnim, hover, dt);
        enabledToggleValueAnim = AnimationUtility.snap(
                AnimationUtility.approach(enabledToggleValueAnim, enabled ? 1f : 0f, dt, 13f),
                enabled ? 1f : 0f,
                0.004f
        );

        int disabledBg = SettingsGuiPalette.mix(
                palette.controlSurface(),
                palette.controlSurfaceHover(),
                0.10f + enabledToggleHoverAnim * 0.24f
        );
        int enabledBg = SettingsGuiPalette.mix(
                palette.panelPillActive(),
                tab.color(),
                0.14f + enabledToggleHoverAnim * 0.08f
        );
        int bg = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(disabledBg, enabledBg, enabledToggleValueAnim),
                Math.round(132f + enabledToggleValueAnim * 56f + enabledToggleHoverAnim * 8f)
        );
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), rect.h() * 0.5f, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(
                rect.x(), rect.y(), rect.w(), rect.h(), rect.h() * 0.5f, 0.45f * scale,
                SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), Math.round(70f + enabledToggleValueAnim * 30f + enabledToggleHoverAnim * 12f))
        );

        float knob = rect.h() - 4f * scale;
        float knobX = AnimationUtility.lerp(
                rect.x() + 2f * scale,
                rect.x() + rect.w() - knob - 2f * scale,
                AnimationUtility.easeOutCubic(enabledToggleValueAnim)
        );
        int knobColor = SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), enabledToggleValueAnim);
        Renderer2D.COLOR.roundedRect(
                knobX,
                rect.y() + 2f * scale,
                knob,
                knob,
                knob * 0.5f,
                1f,
                SettingsGuiPalette.withAlpha(knobColor, Math.round(190f + enabledToggleValueAnim * 42f))
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
            heuristicsScroll = 0f;
            smoothedHeuristicsScroll = 0f;
            heuristicsMaxScroll = 0f;
            draggingScrollbar = false;
            if (onlinePicker.isVisible()) {
                onlinePicker.open(tab.pickerMode());
            }
            clearStatus();
            GuiSound.CHANGE_MODE.feedback(0.70);
        }
        movementInputBlocked = onlinePicker.blocksMovementInput();
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
                AnimationUtility.approach(current, hovered ? 1f : 0f, dt, 10.5f),
                hovered ? 1f : 0f,
                0.01f
        );
    }

    private <K> float animateMapValue(Map<K, Float> values, K key, boolean target, float speed) {
        float current = values.getOrDefault(key, 0f);
        float goal = target ? 1f : 0f;
        float next = AnimationUtility.snap(
                AnimationUtility.approach(current, goal, AnimationUtility.deltaTime(), speed),
                goal,
                0.006f
        );
        values.put(key, next);
        return next;
    }

    private TextRenderer relationHeaderBold() {
        return BuiltinFontCatalog.INTER_BOLD.renderer(ClickGuiRenderer.getInterMedium());
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
        inspectorCopyButton = Rect.ZERO;
        inspectorDeleteButton = Rect.ZERO;
        enabledToggle = Rect.ZERO;
        prefixInputRect = Rect.ZERO;
        suffixInputRect = Rect.ZERO;
        containsInputRect = Rect.ZERO;
        prefixAddButton = Rect.ZERO;
        suffixAddButton = Rect.ZERO;
        containsAddButton = Rect.ZERO;
        heuristicsViewport = Rect.ZERO;
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
            return w > 0f && h > 0f && ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }
    }
}
