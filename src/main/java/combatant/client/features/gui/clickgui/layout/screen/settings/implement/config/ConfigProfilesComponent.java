/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import net.minecraft.client.Minecraft;

import combatant.client.util.text.SingleLineTextInput;

import combatant.client.config.profile.*;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.config.ConfigProfileCardComponent.CardHit;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.logging.DebugLog;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigProfilesComponent {
    private static final DateTimeFormatter GENERATED_NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    private static final long STATUS_DURATION_MS = 2400L;

    private final ConfigProfileCardComponent cardComponent = new ConfigProfileCardComponent();
    private final ConfigDiffPanelComponent diffPanelComponent = new ConfigDiffPanelComponent();
    private final List<CardEntryHit> hits = new ArrayList<>();

    private ConfigProfileType type = ConfigProfileType.MODULES;
    private ConfigProfileMeta selected;
    private ConfigProfileDiff selectedDiff;
    private String diffError;
    private String statusMessage;
    private long statusUntilMs;
    private ConfigProfileType editingType;
    private String editingId;
    private final SingleLineTextInput renameInput = new SingleLineTextInput(48);
    private Rect editingTitleRect = Rect.ZERO;

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

    private Rect modulesPill = Rect.ZERO;
    private Rect hudPill = Rect.ZERO;
    private Rect themesPill = Rect.ZERO;
    private Rect saveButton = Rect.ZERO;
    private float modeAnim;
    private float modulesHoverAnim;
    private float hudHoverAnim;
    private float themesHoverAnim;
    private float saveHoverAnim;
    private float savePulseAnim;

    private String hoverHint;
    private Rect importButton = Rect.ZERO;
    private Rect folderButton = Rect.ZERO;
    private final float[] toolHoverAnim = new float[2];
    private final ConfigBindingsComponent bindings = new ConfigBindingsComponent();
    private ConfigProfileMeta diffProfile;

    private static boolean inside(ConfigProfileCardComponent.ActionHit hit, float mx, float my) {
        return hit != null && ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h());
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
        diffPanelComponent.resetScroll();
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my) {
        hits.clear();
        hoverHint = null;
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        float areaX = menuX + 62f;
        float areaY = menuY + 66f;
        float areaW = menuW - 84f;
        float areaH = menuH - 78f;

        if (bindings.isActive()) {
            bindings.render(areaX, areaY, areaW, areaH, mx, my);
            return;
        }
        renderToolbar(areaX, areaY, areaW, mx, my, palette);
        List<ConfigProfileMeta> entries = filteredProfiles(ConfigProfileService.INSTANCE.list(type));
        renderCards(areaX, areaY + 54f, areaW, areaH - 54f,
                entries, mx, my, palette);

        renderStatus(areaX, areaY, areaW, palette);
    }

    public boolean isDiffActive() {
        return diffPanelComponent.isActive();
    }

    public boolean usesGlobalSearch() {
        return !bindings.isActive();
    }

    public void pointerFocus(float mx, float my) {
        diffPanelComponent.pointerFocus(mx, my);
    }

    public void renderDiff(float x, float y, float w, float h) {
        if (!diffPanelComponent.isActive()) return;
        diffPanelComponent.render(x, y, w, h, diffProfile, selectedDiff, diffError, SettingsGuiPalette.current());
        if (!diffPanelComponent.isActive()) {
            selectedDiff = null;
            diffError = null;
            diffProfile = null;
        }
    }

    public void unfocus() {
        bindings.close();
        diffPanelComponent.close();
        cancelRename();
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return false;
        if (bindings.isActive()) return false;
        if (diffPanelComponent.mousePressedScrollbar(mx, my, button)) return true;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(float mx, float my, int button) {
        bindings.mouseReleased(mx, my, button);
        diffPanelComponent.mouseReleased(button);
        if (button == combatant.client.input.InputKeys.MOUSE_LEFT) draggingScrollbar = false;
    }

    public void scroll(float mx, float my, float menuX, float menuY, float menuW, float menuH, double amount) {
        if (bindings.isActive()) {
            bindings.scroll(mx, my, amount);
            return;
        }
        if (diffPanelComponent.scroll(mx, my, amount)) return;

        float x = menuX + 62f;
        float y = menuY + 120f;
        float w = menuW - 84f;
        float h = menuH - 132f;
        if (!ClickGuiMath.insideRect(mx, my, x, y, w, h)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean click(float mx, float my, int button) {
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return false;

        if (bindings.isActive()) return bindings.click(mx, my, button);

        if (diffPanelComponent.isActive()) {
            ConfigDiffPanelComponent.HeaderAction diffAction = diffPanelComponent.headerAction(mx, my, button);
            if (diffAction == ConfigDiffPanelComponent.HeaderAction.CLOSE) {
                diffPanelComponent.close();
                return true;
            }
            if (diffAction == ConfigDiffPanelComponent.HeaderAction.SAVE) {
                overwriteSelected();
                return true;
            }
            if (diffAction == ConfigDiffPanelComponent.HeaderAction.CONSUMED) return true;
        }

        if (modulesPill.contains(mx, my)) {
            switchType(ConfigProfileType.MODULES);
            return true;
        }
        if (hudPill.contains(mx, my)) {
            switchType(ConfigProfileType.HUD);
            return true;
        }
        if (themesPill.contains(mx, my)) {
            switchType(ConfigProfileType.THEMES);
            return true;
        }
        if (saveButton.contains(mx, my)) {
            savePulseAnim = 1f;
            saveCurrent();
            return true;
        }
        if (importButton.contains(mx, my)) {
            importFromDialog();
            return true;
        }
        if (folderButton.contains(mx, my)) {
            ConfigFileDialogs.openProfilesFolder();
            return true;
        }
        if (isScrollbarHovered(mx, my)) return true;

        for (CardEntryHit entryHit : hits) {
            CardHit hit = entryHit.hit();
            if (inside(hit.title(), mx, my)) {
                if (isEditing(entryHit.meta())) {
                    renameInput.beginDrag(mx, false);
                } else {
                    select(entryHit.meta());
                    beginRename(entryHit.meta(), hit.title());
                    renameInput.layout(hit.title().x(), hit.title().w() - 10f,
                            BuiltinFontCatalog.ONEST_MEDIUM.renderer(), 17.6f);
                }
                return true;
            }
            if (inside(hit.export(), mx, my)) {
                select(entryHit.meta());
                exportProfile(entryHit.meta());
                return true;
            }
            if (inside(hit.bind(), mx, my)) {
                select(entryHit.meta());
                showBindings(entryHit.meta());
                return true;
            }
            if (inside(hit.diff(), mx, my)) {
                select(entryHit.meta());
                diffPanelComponent.resetScroll();
                loadDiff(entryHit.meta());
                return true;
            }
            if (inside(hit.apply(), mx, my)) {
                select(entryHit.meta());
                apply(entryHit.meta());
                return true;
            }
            if (inside(hit.delete(), mx, my)) {
                delete(entryHit.meta());
                return true;
            }
            if (ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                select(entryHit.meta());
                return true;
            }
        }
        return false;
    }

    private void renderToolbar(float x, float y, float w, float mx, float my, SettingsGuiPalette palette) {
        float pillW = 264f;
        float pillH = 30f;
        float targetMode = switch (type) {
            case MODULES -> 0f;
            case HUD -> 1f;
            case THEMES -> 2f;
        };
        float dt = AnimationUtility.deltaTime();
        modeAnim = AnimationUtility.approach(modeAnim, targetMode, dt, 12f);
        modeAnim = AnimationUtility.snap(modeAnim, targetMode, 0.01f);

        modulesPill = new Rect(x, y, pillW / 3f, pillH);
        hudPill = new Rect(x + pillW / 3f, y, pillW / 3f, pillH);
        themesPill = new Rect(x + pillW * 2f / 3f, y, pillW / 3f, pillH);

        modulesHoverAnim = updateHover(modulesHoverAnim, modulesPill.contains(mx, my), dt);
        hudHoverAnim = updateHover(hudHoverAnim, hudPill.contains(mx, my), dt);
        themesHoverAnim = updateHover(themesHoverAnim, themesPill.contains(mx, my), dt);

        int baseLeft = SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgLeft(), 0.34f);
        int baseRight = SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), 0.44f);
        float anyHover = Math.max(modulesHoverAnim, Math.max(hudHoverAnim, themesHoverAnim));
        baseLeft = SettingsGuiPalette.mix(baseLeft, palette.menuCategoryHoverLeft(), 0.12f * anyHover);
        baseRight = SettingsGuiPalette.mix(baseRight, palette.menuCategoryHoverRight(), 0.12f * anyHover);

        LayoutRender2D.roundedQuad(
                x,
                y,
                pillW,
                pillH,
                7f,
                baseLeft,
                baseRight,
                baseRight,
                baseLeft
        );
        LayoutRender2D.roundedStrokeQuad(
                x,
                y,
                pillW,
                pillH,
                7f,
                1f,
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerEnd(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerEnd(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.18f)
        );

        float pad = 2.4f;
        float segment = (pillW - pad * 2f) / 3f;
        float activeX = x + pad + segment * modeAnim;
        float activeHover = switch (type) {
            case MODULES -> modulesHoverAnim;
            case HUD -> hudHoverAnim;
            case THEMES -> themesHoverAnim;
        };
        int activeA = SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedLeft(), 0.22f + activeHover * 0.08f);
        int activeB = SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), 0.22f + activeHover * 0.12f);
        int activeC = SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.panelPillActive(), 0.16f), palette.menuCategorySelectedRight(), 0.18f + activeHover * 0.10f);
        LayoutRender2D.roundedQuad(
                activeX,
                y + pad,
                segment,
                pillH - pad * 2f,
                6f,
                activeA,
                activeB,
                activeC,
                activeA
        );
        LayoutRender2D.roundedStrokeQuad(
                activeX,
                y + pad,
                segment,
                pillH - pad * 2f,
                6f,
                0.7f,
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), 150),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), 178),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), 142),
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), 150)
        );

        drawToolbarSeparator(x + pad + segment, y, pillH, palette);
        drawToolbarSeparator(x + pad + segment * 2f, y, pillH, palette);

        drawSegment(modulesPill, ClickGuiI18n.tr("clickgui.settings.config.type.modules", "Modules"), type == ConfigProfileType.MODULES, modulesHoverAnim, palette);
        drawSegment(hudPill, ClickGuiI18n.tr("clickgui.settings.config.type.hud", "Hud"), type == ConfigProfileType.HUD, hudHoverAnim, palette);
        drawSegment(themesPill, ClickGuiI18n.tr("clickgui.settings.config.type.themes", "Themes"), type == ConfigProfileType.THEMES, themesHoverAnim, palette);

        float saveW = 136f;
        float saveH = pillH;
        saveButton = new Rect(x + pillW + 14f, y, saveW, saveH);
        boolean saveHover = saveButton.contains(mx, my);
        saveHoverAnim = updateHover(saveHoverAnim, saveHover, dt);
        savePulseAnim = AnimationUtility.snap(AnimationUtility.approach(savePulseAnim, 0f, dt, 7f), 0f, 0.01f);
        renderSaveButton(saveButton, saveHoverAnim, savePulseAnim, palette);
        if (saveHover) SystemCursor.set(SystemCursor.CursorType.MOVE);
        renderToolButtons(dt, mx, my, palette);
    }

    private float updateHover(float current, boolean hovered, float dt) {
        return AnimationUtility.snap(
                AnimationUtility.approach(current, hovered ? 1f : 0f, dt, 12f),
                hovered ? 1f : 0f,
                0.01f
        );
    }

    private void drawToolbarSeparator(float x, float y, float h, SettingsGuiPalette palette) {
        float backingW = Math.max(2.5f, 1.05f);
        float backingH = h - 8.8f;
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

        float lineW = Math.max(1f, 0.75f);
        float lineH = h - 12f;
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

    private void renderSaveButton(Rect rect, float hover, float pulse, SettingsGuiPalette palette) {
        float lift = hover * 1.3f + pulse * 1.7f;
        float x = rect.x();
        float y = rect.y() - lift * 0.15f;
        float w = rect.w();
        float h = rect.h();
        int bgA = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.18f + hover * 0.22f + pulse * 0.24f);
        int bgB = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), 0.16f + hover * 0.24f + pulse * 0.26f);
        int bgC = SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.panelPillBase(), 0.18f), palette.menuCategorySelectedRight(), 0.12f + hover * 0.18f + pulse * 0.24f);
        LayoutRender2D.roundedSoftShadow(
                x,
                y + 0.8f,
                w,
                h,
                7.2f,
                10f,
                0.02f + hover * 0.03f,
                SettingsGuiPalette.withAlpha(palette.menuShadow(), Math.round(65f + hover * 50f + pulse * 58f))
        );
        LayoutRender2D.roundedQuad(x, y, w, h, 7.2f, bgA, bgB, bgC, bgA);
        LayoutRender2D.roundedStrokeQuad(
                x,
                y,
                w,
                h,
                7.2f,
                0.9f + hover * 0.44f + pulse * 0.44f,
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(138f + hover * 45f + pulse * 50f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(158f + hover * 55f + pulse * 55f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(120f + hover * 45f + pulse * 50f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(138f + hover * 45f + pulse * 50f))
        );

        int iconColor = SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), 0.22f + hover * 0.34f + pulse * 0.34f);
        Renderer2D.COLOR.svg("save", x + 12.4f, y + 8f, 14f, 14f, SvgRenderOptions.overrideColor(iconColor));
        String label = ClickGuiI18n.tr("clickgui.settings.config.save", "Save");
        float textSize = 13.4f;
        int textColor = SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), hover * 0.24f + pulse * 0.24f);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), label, x + 34.8f, y + 8.4f, textSize, textColor, false);
    }

    private void renderToolButtons(float dt, float mx, float my, SettingsGuiPalette palette) {
        float size = saveButton.h();
        float gap = 9f;
        float y = saveButton.y();
        float x0 = saveButton.x() + saveButton.w() + 11f;
        importButton = new Rect(x0, y, size, size);
        folderButton = new Rect(x0 + size + gap, y, size, size);
        renderToolIcon(0, importButton, "folder-down", ClickGuiI18n.tr("clickgui.settings.config.hint.import", "Import .cbcfg file"), dt, mx, my, palette);
        renderToolIcon(1, folderButton, "folder-open", ClickGuiI18n.tr("clickgui.settings.config.hint.folder", "Open profiles folder"), dt, mx, my, palette);
    }

    private void renderToolIcon(int idx, Rect rect, String icon, String hint, float dt, float mx, float my, SettingsGuiPalette palette) {
        boolean hover = rect.contains(mx, my);
        toolHoverAnim[idx] = updateHover(toolHoverAnim[idx], hover, dt);
        float h = toolHoverAnim[idx];
        if (hover) {
            SystemCursor.set(SystemCursor.CursorType.MOVE);
            hoverHint = hint;
        }
        int bgA = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.14f + h * 0.24f);
        int bgB = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), 0.14f + h * 0.24f);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 7.2f, bgA, bgB, bgB, bgA);
        int iconColor = SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), 0.2f + h * 0.35f);
        float inset = 8f;
        Renderer2D.COLOR.svg(icon, rect.x() + inset, rect.y() + inset, rect.w() - inset * 2f, rect.h() - inset * 2f, SvgRenderOptions.overrideColor(iconColor));
    }

    private void updateCardHint(CardHit hit, List<String> contexts, float mx, float my) {
        if (inside(hit.export(), mx, my)) {
            hoverHint = ClickGuiI18n.tr("clickgui.settings.config.hint.export", "Export .cbcfg file");
        } else if (inside(hit.bind(), mx, my)) {
            hoverHint = ClickGuiI18n.tr("clickgui.settings.config.hint.profile_bindings", "View and edit this profile's bindings");
        } else if (!contexts.isEmpty() && ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
            hoverHint = String.join(", ", contexts);
        }
    }

    private void drawSegment(Rect rect, String label, boolean active, float hover, SettingsGuiPalette palette) {
        float size = 15f;
        float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), label, size);
        float th = LayoutRender2D.textHeight(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), size);
        int color = active
                ? palette.panelText()
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.20f * hover);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), label, rect.x() + (rect.w() - tw) * 0.5f, rect.y() + (rect.h() - th) * 0.5f, size, color, false);
    }

    private void renderCards(float x,
                             float y,
                             float w,
                             float h,
                             List<ConfigProfileMeta> entries,
                             float mx,
                             float my,
                             SettingsGuiPalette palette) {
        float gap = 12f;
        int columns = w > 460f ? 2 : 1;
        float cardW = (w - gap * (columns - 1)) / columns;
        float cardH = 132f;
        float stepY = cardH + gap;
        int rows = (entries.size() + columns - 1) / columns;
        float contentH = rows <= 0 ? 0f : rows * stepY - gap;
        float maxScroll = Math.max(0f, contentH - h);
        updateScrollbarMetrics(x, y, w, h, maxScroll);
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        if (entries.isEmpty()) {
            String empty = ClickGuiSearch.hasQuery()
                    ? ClickGuiI18n.tr("clickgui.settings.config.empty.no_matches", "No matching configs.")
                    : ClickGuiI18n.tr("clickgui.settings.config.empty.none_saved", "No saved configs.");
            float emptySize = 16f;
            float emptyW = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), empty, emptySize);
            LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), empty, x + (w - emptyW) * 0.5f, y + h * 0.42f, emptySize, palette.panelMuted(), false);
            if (!ClickGuiSearch.hasQuery()) {
                String hint = ClickGuiI18n.tr("clickgui.settings.config.empty.drop_hint", "Drop .cbcfg files here, or put them anywhere under config/combatant/profiles/.");
                float hintSize = 13.8f;
                String fitted = LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), hint, hintSize, w - 36f);
                float hintW = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), fitted, hintSize);
                LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), fitted, x + (w - hintW) * 0.5f, y + h * 0.42f + 26f, hintSize, palette.panelMuted(), false);
            }
            return;
        }
        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        String boundAddress = Minecraft.getInstance().level == null
                ? "" : ConfigServerBindings.currentAddress();
        String activeId = ConfigServerBindings.INSTANCE.activeProfile(type);
        Map<String, List<String>> contextsByProfile = new HashMap<>();
        ConfigServerBindings.INSTANCE.bindingsFor(type).forEach((address, id) ->
                contextsByProfile.computeIfAbsent(id, ignored -> new ArrayList<>()).add(address));
        for (int i = 0; i < entries.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float cx = x + col * (cardW + gap);
            float cy = y + row * stepY + smoothedScroll;
            if (cy + cardH < y || cy > y + h) continue;
            ConfigProfileMeta meta = entries.get(i);
            boolean selectedState = selected != null && selected.getId().equals(meta.getId()) && selected.type() == meta.type();
            List<String> contexts = contextsByProfile.getOrDefault(meta.getId(), List.of());
            boolean currentBound = !boundAddress.isEmpty() && contexts.contains(boundAddress);
            boolean activeProfile = meta.getId().equals(activeId);
            CardHit hit = cardComponent.render(meta, cx, cy, cardW, cardH, mx, my, selectedState,
                    activeProfile, currentBound, contexts, palette);
            if (isEditing(meta)) {
                editingTitleRect = new Rect(hit.title().x(), hit.title().y(), hit.title().w(), hit.title().h());
                renderRenameInput(editingTitleRect, palette);
            }
            if (inside(hit.title(), mx, my)) SystemCursor.set(SystemCursor.CursorType.MOVE);
            hits.add(new CardEntryHit(meta, hit));
            updateCardHint(hit, contexts, mx, my);
        }
        if (clipped) ScissorFunction.pop();

        renderScrollbar(x, y, w, h, maxScroll, palette);
    }

    private void renderScrollbar(float x, float y, float w, float h, float maxScroll, SettingsGuiPalette palette) {
        updateScrollbarMetrics(x, y, w, h, maxScroll);
        if (!scrollbarVisible) return;
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) SystemCursor.set(SystemCursor.CursorType.SCROLL);

        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH, 4f,
                palette.moduleScrollTrackA(), palette.moduleScrollTrackB(), palette.moduleScrollTrackB(), palette.moduleScrollTrackA());
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 4f,
                palette.moduleScrollHandleA(), palette.moduleScrollHandleB(), palette.moduleScrollHandleB(), palette.moduleScrollHandleA());
    }

    private void updateScrollbarMetrics(float x, float y, float w, float h, float maxScroll) {
        scrollbarVisible = maxScroll > 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }
        scrollbarW = 6f;
        scrollbarX = x + w - scrollbarW - 4f;
        scrollbarY = y + 6f;
        scrollbarH = Math.max(1f, h - 12f);
        scrollbarThumbH = Math.max(36f, scrollbarH * (scrollbarH / (scrollbarMaxScroll + scrollbarH)));
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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindings.isActive()) return bindings.keyPressed(keyCode, scanCode, modifiers);
        if (diffPanelComponent.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!isRenaming()) return false;
        if (keyCode == combatant.client.input.InputKeys.ESCAPE) {
            cancelRename();
            return true;
        }
        if (keyCode == combatant.client.input.InputKeys.ENTER || keyCode == combatant.client.input.InputKeys.KP_ENTER) {
            commitRename();
            return true;
        }
        renameInput.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (bindings.isActive()) return bindings.charTyped(chr);
        if (diffPanelComponent.charTyped(chr)) return true;
        if (!isRenaming()) return false;
        renameInput.type(chr);
        return true;
    }

    public boolean importDroppedFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return false;
        int imported = 0;
        int failed = 0;
        ConfigProfileMeta last = null;
        for (Path path : paths) {
            try {
                ConfigProfileStorage.ImportResult result = ConfigProfileService.INSTANCE.importProfileFile(path, false);
                imported++;
                last = result.meta();
            } catch (Exception e) {
                failed++;
                DebugLog.error("Failed to import dropped config profile %s", e, path == null ? "<null>" : path.toAbsolutePath());
            }
        }
        if (last != null) {
            switchType(last.type());
            selected = last;
        }
        if (imported > 0 && failed > 0) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.imported_partial", "Imported: %s, failed: %s", imported, failed));
        } else if (imported > 0) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.imported", "Imported: %s", imported));
        } else {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.import_failed", "Import failed"));
        }
        return true;
    }

    private void beginRename(ConfigProfileMeta meta, ConfigProfileCardComponent.ActionHit titleHit) {
        if (meta == null) return;
        editingType = meta.type();
        editingId = meta.getId();
        renameInput.setText(meta.name());
        renameInput.moveToEnd();
        editingTitleRect = new Rect(titleHit.x(), titleHit.y(), titleHit.w(), titleHit.h());
        clearStatus();
    }

    private void cancelRename() {
        editingType = null;
        editingId = null;
        renameInput.clear();
        editingTitleRect = Rect.ZERO;
    }

    private boolean isRenaming() {
        return editingType != null && editingId != null;
    }

    private boolean isEditing(ConfigProfileMeta meta) {
        return meta != null && editingType == meta.type() && editingId != null && editingId.equals(meta.getId());
    }

    private void commitRename() {
        if (!isRenaming()) return;
        String next = renameInput.text().trim();
        if (next.isBlank()) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.empty_name", "Name cannot be empty"));
            return;
        }
        try {
            ConfigProfileMeta oldSelected = selected;
            ConfigProfileMeta renamed = ConfigProfileService.INSTANCE.rename(editingType, editingId, next).meta();
            if (oldSelected != null && oldSelected.type() == editingType && oldSelected.getId().equals(editingId)) {
                selected = renamed;
            }
            cancelRename();
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.renamed", "Renamed: %s", renamed.name()));
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.rename_failed", "Rename failed"));
            DebugLog.error("Failed to rename config profile", e);
        }
    }

    private void renderRenameInput(Rect rect, SettingsGuiPalette palette) {
        float padX = 4f;
        float y = rect.y() - 2f;
        float h = rect.h() + 4f;
        LayoutRender2D.roundedQuad(
                rect.x() - padX,
                y,
                rect.w() + padX * 2f,
                h,
                6f,
                SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), 0.28f),
                SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), 0.22f),
                SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), 0.22f),
                SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), 0.28f)
        );
        renameInput.render(rect.x(), rect.y(), rect.w() - 10f, h,
                BuiltinFontCatalog.ONEST_MEDIUM.renderer(), 17.6f, palette.moduleTitleText(),
                SettingsGuiPalette.withAlpha(palette.menuCategorySelectedRight(), 145), true);
    }

    private void renderStatus(float x, float y, float w, SettingsGuiPalette palette) {
        String message = statusMessage;
        if (message == null || message.isBlank() || System.currentTimeMillis() > statusUntilMs) {
            if (message != null) clearStatus();
            message = hoverHint;
        }
        if (message == null || message.isBlank()) return;
        float size = 13.6f;
        String text = LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), message, size, w - 24f);
        float tw = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_REGULAR.renderer(), text, size);
        // The toolbar is occupied by controls; long localized statuses belong in its lower margin.
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), text, x + w - tw, y + 34f, size, palette.panelMuted(), false);
    }

    private void setStatus(String message) {
        combatant.client.features.command.CommandOutput.send(message);
        statusMessage = message;
        statusUntilMs = System.currentTimeMillis() + STATUS_DURATION_MS;
    }

    private void clearStatus() {
        statusMessage = null;
        statusUntilMs = 0L;
    }

    private List<ConfigProfileMeta> filteredProfiles(List<ConfigProfileMeta> src) {
        if (!ClickGuiSearch.hasQuery()) return src;
        List<ConfigProfileMeta> out = new ArrayList<>();
        String q = ClickGuiSearch.getText().toLowerCase(Locale.ROOT);
        for (ConfigProfileMeta meta : src) {
            if (meta.name().toLowerCase(Locale.ROOT).contains(q) || meta.author().toLowerCase(Locale.ROOT).contains(q))
                out.add(meta);
        }
        return out;
    }

    private void switchType(ConfigProfileType next) {
        if (type == next) return;
        type = next;
        selected = null;
        diffPanelComponent.close();
        clearStatus();
        cancelRename();
        resetScroll();
        GuiSound.CHANGE_MODE.feedback(0.70);
    }

    private void select(ConfigProfileMeta meta) {
        boolean same = selected != null && meta != null && selected.type() == meta.type() && selected.getId().equals(meta.getId());
        selected = meta;
        if (!same) {
            diffPanelComponent.close();
            diffPanelComponent.resetScroll();

        }
    }

    private void saveCurrent() {
        String name = type.folderName() + " " + GENERATED_NAME_TIME.format(LocalDateTime.now());
        try {
            diffPanelComponent.close();
            selected = ConfigProfileService.INSTANCE.saveCurrent(type, name).meta();
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.saved", "Saved: %s", selected.name()));
            GuiSound.BUTTON_YES.feedback(0.85);
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.save_failed", "Save failed"));
            DebugLog.error("Failed to save config profile", e);
        }
    }

    private void overwriteSelected() {
        if (selected == null) return;
        try {
            diffPanelComponent.close();
            selected = ConfigProfileService.INSTANCE.overwriteCurrent(selected).meta();
            diffPanelComponent.resetScroll();
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.overwritten", "Overwritten: %s", selected.name()));
            GuiSound.BUTTON_YES.feedback(0.85);
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.overwrite_failed", "Overwrite failed"));
            DebugLog.error("Failed to overwrite config profile", e);
        }
    }

    private void loadDiff(ConfigProfileMeta meta) {
        try {
            diffProfile = meta;
            ClickGuiSearch.unfocus();
            cancelRename();
            selectedDiff = ConfigProfileService.INSTANCE.diffWithCurrent(meta.type(), meta.getId());
            diffError = null;
            diffPanelComponent.open();
            diffPanelComponent.resetScroll();
            setStatus(selectedDiff.isEmpty()
                    ? ClickGuiI18n.tr("clickgui.settings.config.status.no_differences", "No differences")
                    : ClickGuiI18n.tr("clickgui.settings.config.status.diff_count", "Diff: %s changes", selectedDiff.valueChangeCount()));
        } catch (Exception e) {
            selectedDiff = null;
            diffError = e.getMessage();
            diffPanelComponent.open();
            diffPanelComponent.resetScroll();
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.diff_failed", "Diff failed"));
            DebugLog.error("Failed to diff config profile", e);
        }
    }

    private void apply(ConfigProfileMeta meta) {
        try {
            diffPanelComponent.close();
            ConfigProfileApplier.ApplyResult result = ConfigProfileService.INSTANCE.apply(meta.type(), meta.getId());
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.applied", "Applied: %s, missing: %s/%s", result.appliedOwners(), result.missingOwners(), result.missingValues()));
            GuiSound.BUTTON_YES.feedback(0.85);
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.apply_failed", "Apply failed"));
            DebugLog.error("Failed to apply config profile", e);
        }
    }

    private void delete(ConfigProfileMeta meta) {
        try {
            ConfigProfileService.INSTANCE.delete(meta.type(), meta.getId());
            if (selected != null && selected.getId().equals(meta.getId()) && selected.type() == meta.type()) {
                selected = null;
                diffPanelComponent.close();
            }
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.deleted", "Deleted: %s", meta.name()));
            GuiSound.BUTTON.feedback(0.65);
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.delete_failed", "Delete failed"));
            DebugLog.error("Failed to delete config profile", e);
        }
    }

    private void importFromDialog() {
        if (ConfigFileDialogs.busy()) return;
        ConfigFileDialogs.openProfile(path -> {
            try {
                ConfigProfileStorage.ImportResult res = ConfigProfileService.INSTANCE.importProfileFile(path, false);
                if (res.meta() != null) {
                    type = res.meta().type();
                    selected = res.meta();
                    setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.imported", "Imported: %s", res.meta().name()));
                    GuiSound.BUTTON_YES.feedback(0.85);
                } else {
                    setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.import_failed", "Import failed"));
                }
            } catch (Exception e) {
                setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.import_failed", "Import failed"));
                DebugLog.error("Failed to import profile file", e);
            }
        }, () -> setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.dialog_failed", "Could not open file dialog")));
    }

    private void exportProfile(ConfigProfileMeta meta) {
        if (ConfigFileDialogs.busy()) return;
        String suggested = meta.getId() + ConfigProfileStorage.EXTENSION;
        ConfigFileDialogs.saveProfile(suggested, dest -> {
            try {
                Path written = ConfigProfileService.INSTANCE.exportProfileFile(meta.type(), meta.getId(), dest);
                setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.exported", "Exported: %s", written.getFileName()));
                GuiSound.BUTTON_YES.feedback(0.85);
            } catch (Exception e) {
                setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.export_failed", "Export failed"));
                DebugLog.error("Failed to export profile file", e);
            }
        }, () -> setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.dialog_failed", "Could not open file dialog")));
    }

    private void showBindings(ConfigProfileMeta meta) {
        if (meta == null) return;
        cancelRename();
        ClickGuiSearch.unfocus();
        // A binding editor always belongs to the card that opened it.
        diffPanelComponent.close();
        bindings.open(meta.type(), meta);
    }

    private record CardEntryHit(ConfigProfileMeta meta, CardHit hit) {
    }

    private record Rect(float x, float y, float w, float h) {
        static final Rect ZERO = new Rect(0f, 0f, 0f, 0f);

        boolean contains(float mx, float my) {
            return ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }
    }
}
