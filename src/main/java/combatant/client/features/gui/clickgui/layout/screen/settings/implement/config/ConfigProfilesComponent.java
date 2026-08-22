/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.config.profile.*;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.config.ConfigProfileCardComponent.CardHit;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
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
import java.util.List;
import java.util.Locale;

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
    private String editingText = "";
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

    private static boolean inside(ConfigProfileCardComponent.ActionHit hit, float mx, float my) {
        return hit != null && ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h());
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
        diffPanelComponent.resetScroll();
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        hits.clear();
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;

        renderToolbar(areaX, areaY, areaW, mx, my, scale, palette);

        float listX = areaX;
        float listY = areaY + 24f * scale;
        float listW = areaW;
        float listH = areaH - 24f * scale;

        List<ConfigProfileMeta> entries = filteredProfiles(ConfigProfileService.INSTANCE.list(type));
        renderCards(listX, listY, listW, listH, entries, mx, my, scale, palette);

        if (selectedDiff != null || diffError != null || diffPanelComponent.isActive()) {
            diffPanelComponent.render(
                    menuX + menuW + 24f * scale,
                    menuY + 4f * scale,
                    160f * scale,
                    Math.min(200f * scale, menuH - 8f * scale),
                    selected,
                    selectedDiff,
                    diffError,
                    scale,
                    palette
            );
            if (!diffPanelComponent.isOpen() && !diffPanelComponent.isActive()) {
                selectedDiff = null;
                diffError = null;
            }
        }

        renderStatus(areaX, areaY, areaW, scale, palette);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != 0) return false;
        if (diffPanelComponent.mousePressedScrollbar(mx, my, button)) return true;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        diffPanelComponent.mouseReleased(button);
        if (button == 0) draggingScrollbar = false;
    }

    public void scroll(float mx, float my, float menuX, float menuY, float menuW, float menuH, double amount, float scale) {
        if (diffPanelComponent.scroll(mx, my, amount)) return;

        float x = menuX + 31f * scale;
        float y = menuY + 55f * scale;
        float w = menuW - 42f * scale;
        float h = menuH - 61f * scale;
        if (!ClickGuiMath.insideRect(mx, my, x, y, w, h)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean click(float mx, float my, int button) {
        if (button != 0) return false;

        if (selectedDiff != null || diffError != null) {
            ConfigDiffPanelComponent.HeaderAction diffAction = diffPanelComponent.headerAction(mx, my, button);
            if (diffAction == ConfigDiffPanelComponent.HeaderAction.CLOSE) {
                diffPanelComponent.close();
                return true;
            }
            if (diffAction == ConfigDiffPanelComponent.HeaderAction.SAVE) {
                overwriteSelected();
                return true;
            }
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
        if (isScrollbarHovered(mx, my)) return true;

        for (CardEntryHit entryHit : hits) {
            CardHit hit = entryHit.hit();
            if (inside(hit.title(), mx, my)) {
                select(entryHit.meta());
                beginRename(entryHit.meta(), hit.title());
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

    private void renderToolbar(float x, float y, float w, float mx, float my, float scale, SettingsGuiPalette palette) {
        float pillW = 132f * scale;
        float pillH = 15f * scale;
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

        ClickGuiRenderer.drawBlur(x, y, pillW, pillH, 4f * scale, palette.panelBlurTint(), 145f / 255f);
        LayoutRender2D.roundedQuad(
                x,
                y,
                pillW,
                pillH,
                3.5f * scale,
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
                3.5f * scale,
                0.5f * scale,
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerEnd(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerEnd(), 0.18f),
                SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.18f)
        );

        float pad = 1.2f * scale;
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
                3f * scale,
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
                3f * scale,
                0.35f * scale,
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), 150),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), 178),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), 142),
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), 150)
        );

        drawToolbarSeparator(x + pad + segment, y, pillH, scale, palette);
        drawToolbarSeparator(x + pad + segment * 2f, y, pillH, scale, palette);

        drawSegment(modulesPill, ClickGuiI18n.tr("clickgui.settings.config.type.modules", "Modules"), type == ConfigProfileType.MODULES, modulesHoverAnim, scale, palette);
        drawSegment(hudPill, ClickGuiI18n.tr("clickgui.settings.config.type.hud", "Hud"), type == ConfigProfileType.HUD, hudHoverAnim, scale, palette);
        drawSegment(themesPill, ClickGuiI18n.tr("clickgui.settings.config.type.themes", "Themes"), type == ConfigProfileType.THEMES, themesHoverAnim, scale, palette);

        float saveW = 68f * scale;
        float saveH = pillH;
        saveButton = new Rect(x + pillW + 7f * scale, y, saveW, saveH);
        boolean saveHover = saveButton.contains(mx, my);
        saveHoverAnim = updateHover(saveHoverAnim, saveHover, dt);
        savePulseAnim = AnimationUtility.snap(AnimationUtility.approach(savePulseAnim, 0f, dt, 7f), 0f, 0.01f);
        renderSaveButton(saveButton, saveHoverAnim, savePulseAnim, scale, palette);
        if (saveHover) SystemCursor.set(SystemCursor.CursorType.MOVE);
    }

    private float updateHover(float current, boolean hovered, float dt) {
        return AnimationUtility.snap(
                AnimationUtility.approach(current, hovered ? 1f : 0f, dt, 12f),
                hovered ? 1f : 0f,
                0.01f
        );
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

    private void renderSaveButton(Rect rect, float hover, float pulse, float scale, SettingsGuiPalette palette) {
        float lift = (hover * 0.65f + pulse * 0.85f) * scale;
        float x = rect.x();
        float y = rect.y() - lift * 0.15f;
        float w = rect.w();
        float h = rect.h();
        int bgA = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.18f + hover * 0.22f + pulse * 0.24f);
        int bgB = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), 0.16f + hover * 0.24f + pulse * 0.26f);
        int bgC = SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.panelPillBase(), 0.18f), palette.menuCategorySelectedRight(), 0.12f + hover * 0.18f + pulse * 0.24f);
        LayoutRender2D.roundedSoftShadow(
                x,
                y + 0.4f * scale,
                w,
                h,
                3.6f * scale,
                5f * scale,
                0.02f + hover * 0.03f,
                SettingsGuiPalette.withAlpha(palette.menuShadow(), Math.round(65f + hover * 50f + pulse * 58f))
        );
        LayoutRender2D.roundedQuad(x, y, w, h, 3.6f * scale, bgA, bgB, bgC, bgA);
        LayoutRender2D.roundedStrokeQuad(
                x,
                y,
                w,
                h,
                3.6f * scale,
                (0.45f + hover * 0.22f + pulse * 0.22f) * scale,
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(138f + hover * 45f + pulse * 50f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(158f + hover * 55f + pulse * 55f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), Math.round(120f + hover * 45f + pulse * 50f)),
                SettingsGuiPalette.withAlpha(palette.moduleDividerStart(), Math.round(138f + hover * 45f + pulse * 50f))
        );

        int iconColor = SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), 0.22f + hover * 0.34f + pulse * 0.34f);
        Renderer2D.COLOR.svg("save", x + 6.2f * scale, y + 4f * scale, 7f * scale, 7f * scale, SvgRenderOptions.overrideColor(iconColor));
        String label = ClickGuiI18n.tr("clickgui.settings.config.save", "Save");
        float textSize = 6.7f * scale;
        int textColor = SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), hover * 0.24f + pulse * 0.24f);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), label, x + 17.4f * scale, y + 4.2f * scale, textSize, textColor, false);
    }

    private void drawSegment(Rect rect, String label, boolean active, float hover, float scale, SettingsGuiPalette palette) {
        float size = 7.5f * scale;
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), label, size);
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), size);
        int color = active
                ? palette.panelText()
                : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.20f * hover);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), label, rect.x() + (rect.w() - tw) * 0.5f, rect.y() + (rect.h() - th) * 0.5f, size, color, false);
    }

    private void renderCards(float x,
                             float y,
                             float w,
                             float h,
                             List<ConfigProfileMeta> entries,
                             float mx,
                             float my,
                             float scale,
                             SettingsGuiPalette palette) {
        float gap = 6f * scale;
        int columns = w > 230f * scale ? 2 : 1;
        float cardW = (w - gap * (columns - 1)) / columns;
        float cardH = 58f * scale;
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
            String empty = ClickGuiSearch.hasQuery()
                    ? ClickGuiI18n.tr("clickgui.settings.config.empty.no_matches", "No matching configs.")
                    : ClickGuiI18n.tr("clickgui.settings.config.empty.none_saved", "No saved configs.");
            float emptySize = 8.0f * scale;
            float emptyW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), empty, emptySize);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), empty, x + (w - emptyW) * 0.5f, y + h * 0.42f, emptySize, palette.panelMuted(), false);
            if (!ClickGuiSearch.hasQuery()) {
                String hint = ClickGuiI18n.tr("clickgui.settings.config.empty.drop_hint", "Drop .cbcfg files here, or put them anywhere under config/combatant/profiles/.");
                float hintSize = 6.9f * scale;
                String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), hint, hintSize, w - 18f * scale);
                float hintW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), fitted, hintSize);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), fitted, x + (w - hintW) * 0.5f, y + h * 0.42f + 13f * scale, hintSize, palette.panelMuted(), false);
            }
            return;
        }
        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        for (int i = 0; i < entries.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            float cx = x + col * (cardW + gap);
            float cy = y + row * stepY + smoothedScroll;
            if (cy + cardH < y || cy > y + h) continue;
            ConfigProfileMeta meta = entries.get(i);
            boolean selectedState = selected != null && selected.getId().equals(meta.getId()) && selected.type() == meta.type();
            CardHit hit = cardComponent.render(meta, cx, cy, cardW, cardH, mx, my, selectedState, scale, palette);
            if (isEditing(meta)) {
                editingTitleRect = new Rect(hit.title().x(), hit.title().y(), hit.title().w(), hit.title().h());
                renderRenameInput(editingTitleRect, scale, palette);
            }
            if (inside(hit.title(), mx, my)) SystemCursor.set(SystemCursor.CursorType.MOVE);
            hits.add(new CardEntryHit(meta, hit));
        }
        if (clipped) ScissorFunction.pop();

        renderScrollbar(x, y, w, h, maxScroll, scale, palette);
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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isRenaming()) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelRename();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitRename();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!editingText.isEmpty()) editingText = editingText.substring(0, editingText.length() - 1);
            return true;
        }
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!isRenaming()) return false;
        if (chr >= 32 && chr != 127 && editingText.length() < 48) {
            editingText += chr;
        }
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
        editingText = meta.name();
        editingTitleRect = new Rect(titleHit.x(), titleHit.y(), titleHit.w(), titleHit.h());
        clearStatus();
    }

    private void cancelRename() {
        editingType = null;
        editingId = null;
        editingText = "";
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
        String next = editingText == null ? "" : editingText.trim();
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

    private void renderRenameInput(Rect rect, float scale, SettingsGuiPalette palette) {
        float padX = 2f * scale;
        float y = rect.y() - scale;
        float h = rect.h() + 2f * scale;
        LayoutRender2D.roundedQuad(
                rect.x() - padX,
                y,
                rect.w() + padX * 2f,
                h,
                3f * scale,
                SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), 0.28f),
                SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), 0.22f),
                SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), 0.22f),
                SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), 0.28f)
        );
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), editingText, 8.8f * scale, rect.w() - 5f * scale);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), text, rect.x(), rect.y(), 8.8f * scale, palette.moduleTitleText(), false);
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), text, 8.8f * scale);
            LayoutRender2D.rect(rect.x() + Math.min(tw + 1.5f * scale, rect.w() - 2f * scale), rect.y() + 1.2f * scale, 0.6f * scale, 8f * scale, palette.moduleTitleText());
        }
    }

    private void renderStatus(float x, float y, float w, float scale, SettingsGuiPalette palette) {
        if (statusMessage == null || statusMessage.isBlank()) return;
        if (System.currentTimeMillis() > statusUntilMs) {
            clearStatus();
            return;
        }
        float size = 6.8f * scale;
        String text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), statusMessage, size, w - 176f * scale);
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, size);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), text, x + w - tw, y + 7.2f * scale, size, palette.panelMuted(), false);
    }

    private void setStatus(String message) {
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
        selectedDiff = null;
        diffError = null;
        diffPanelComponent.close();
        clearStatus();
        cancelRename();
        resetScroll();
    }

    private void select(ConfigProfileMeta meta) {
        boolean same = selected != null && meta != null && selected.type() == meta.type() && selected.getId().equals(meta.getId());
        selected = meta;
        if (!same) {
            selectedDiff = null;
            diffError = null;
            diffPanelComponent.close();
            diffPanelComponent.resetScroll();
        }
    }

    private void saveCurrent() {
        String name = type.folderName() + " " + GENERATED_NAME_TIME.format(LocalDateTime.now());
        try {
            diffPanelComponent.close();
            selected = ConfigProfileService.INSTANCE.saveCurrent(type, name).meta();
            selectedDiff = null;
            diffError = null;
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.saved", "Saved: %s", selected.name()));
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
            selectedDiff = null;
            diffError = null;
            diffPanelComponent.resetScroll();
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.overwritten", "Overwritten: %s", selected.name()));
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.overwrite_failed", "Overwrite failed"));
            DebugLog.error("Failed to overwrite config profile", e);
        }
    }

    private void loadDiff(ConfigProfileMeta meta) {
        try {
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
            selectedDiff = null;
            diffError = null;
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.applied", "Applied: %s, missing: %s/%s", result.appliedOwners(), result.missingOwners(), result.missingValues()));
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
                selectedDiff = null;
                diffError = null;
                diffPanelComponent.close();
            }
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.deleted", "Deleted: %s", meta.name()));
        } catch (Exception e) {
            setStatus(ClickGuiI18n.tr("clickgui.settings.config.status.delete_failed", "Delete failed"));
            DebugLog.error("Failed to delete config profile", e);
        }
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
