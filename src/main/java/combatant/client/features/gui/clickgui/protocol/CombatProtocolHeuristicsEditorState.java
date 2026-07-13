/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.protocol;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.settings.ProtocolHeuristicsSetting;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.combat.protocol.CombatProtocolHeuristicSource;
import combatant.client.util.combat.protocol.CombatProtocolHeuristicsConfig;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.TextSelection;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CombatProtocolHeuristicsEditorState {
    private static final float SCALE = 2f;
    private static final float WINDOW_W = 420f * SCALE;
    private static final float WINDOW_H = 280f * SCALE;
    private static final float PADDING = 9f * SCALE;
    private static final float HEADER_H = 31f * SCALE;
    private static final float SOURCE_W = 108f * SCALE;
    private static final float GAP = 7f * SCALE;
    private static final float CLOSE_SIZE = 12f * SCALE;

    private final ProtocolHeuristicsSetting owner;
    private final String title;
    private final List<SourceHit> sourceHits = new ArrayList<>();
    private final EnumMap<CombatProtocolHeuristicSource, Float> sourceHover =
            new EnumMap<>(CombatProtocolHeuristicSource.class);
    private final EnumMap<CombatProtocolHeuristicSource, Float> sourceSelect =
            new EnumMap<>(CombatProtocolHeuristicSource.class);
    private final EnumMap<CombatProtocolHeuristicSource, EnumMap<CombatProtocolHeuristicsConfig.ProtocolFamily, InlineEditor>> editors =
            new EnumMap<>(CombatProtocolHeuristicSource.class);

    private CombatProtocolHeuristicSource selected = CombatProtocolHeuristicSource.BOSSBAR;
    private InlineEditor focusedEditor;
    private float windowX;
    private float windowY;
    private float openAnim;
    private Rect close = Rect.EMPTY;
    private Rect reset = Rect.EMPTY;
    private float closeHover;
    private float resetHover;

    public CombatProtocolHeuristicsEditorState(ProtocolHeuristicsSetting owner, String title) {
        this.owner = owner;
        this.title = title == null || title.isBlank() ? tr("title", "Protocol heuristics") : title;

        CombatProtocolHeuristicsConfig config = CombatProtocolHeuristicsConfig.get();
        for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
            EnumMap<CombatProtocolHeuristicsConfig.ProtocolFamily, InlineEditor> byFamily =
                    new EnumMap<>(CombatProtocolHeuristicsConfig.ProtocolFamily.class);
            for (CombatProtocolHeuristicsConfig.ProtocolFamily family : CombatProtocolHeuristicsConfig.ProtocolFamily.values()) {
                byFamily.put(family, new InlineEditor(source, family, config.patterns(source, family)));
            }
            editors.put(source, byFamily);
            sourceHover.put(source, 0f);
            sourceSelect.put(source, source == selected ? 1f : 0f);
        }
    }

    public void handleMouseMove(float mx, float my) {
        if (focusedEditor != null) focusedEditor.mouseMoved(mx, my);
    }

    public void handleMouseDown(float mx, float my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (!inside(mx, my, windowX, windowY, WINDOW_W, WINDOW_H) || close.contains(mx, my)) {
            ClickGuiRenderer.closeProtocolHeuristicsEditor();
            return;
        }

        for (SourceHit hit : sourceHits) {
            if (!hit.card().contains(mx, my)) continue;
            blurEditor();
            selected = hit.source();
            boolean enabled = sourceEnabled(hit.source());
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                owner.setSourceEnabled(hit.source().key(), !enabled);
            }
            return;
        }

        InlineEditor legacy = editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.LEGACY);
        InlineEditor modern = editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.MODERN);
        if (legacy.mousePressed(mx, my, button)) {
            focus(legacy);
            return;
        }
        if (modern.mousePressed(mx, my, button)) {
            focus(modern);
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && reset.contains(mx, my)) {
            blurEditor();
            CombatProtocolHeuristicsConfig.get().reset(selected);
            reloadEditors(selected);
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) blurEditor();
    }

    public void handleMouseUp(float mx, float my, int button) {
        if (focusedEditor != null) focusedEditor.mouseReleased(button);
    }

    public void scroll(double delta) {
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        InlineEditor legacy = editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.LEGACY);
        InlineEditor modern = editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.MODERN);
        if (legacy.scroll(mx, my, delta)) return;
        modern.scroll(mx, my, delta);
    }

    public boolean charTyped(char chr) {
        return focusedEditor != null && focusedEditor.charTyped(chr);
    }

    public boolean keyPressed(int key, int scancode, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return focusedEditor != null;
        if (focusedEditor != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                blurEditor();
                return true;
            }
            return focusedEditor.keyPressed(key, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            ClickGuiRenderer.closeProtocolHeuristicsEditor();
            return true;
        }
        return true;
    }

    public void render(int width, int height) {
        float dt = AnimationUtility.deltaTime();
        openAnim = AnimationUtility.snap(AnimationUtility.approach(openAnim, 1f, dt, 8.5f), 1f, 0.008f);
        float eased = easeOutCubic(openAnim);
        windowX = (width - WINDOW_W) * 0.5f;
        windowY = (height - WINDOW_H) * 0.5f + (1f - eased) * 10f * SCALE;

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        Themes.Theme theme = Theme.theme();
        TextRenderer regular = ClickGuiRenderer.getInterRegular();
        TextRenderer medium = ClickGuiRenderer.getInterMedium();
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();

        float previousAlpha = ClickGuiRenderer.setRenderAlphaMultiplier(eased);
        try {
            int shadow = SettingsGuiPalette.withAlpha(palette.menuShadow(), Math.round(255f * eased));
            ClickGuiRenderer.drawBlur(windowX, windowY, WINDOW_W, WINDOW_H, 9f * SCALE, 0xFF000000, 210f / 255f);
            ClickGuiRenderer.drawRoundedRectShadow(windowX, windowY, WINDOW_W, WINDOW_H, 9f * SCALE, 13f * SCALE, 1.5f * SCALE, shadow);
            ClickGuiRenderer.drawRoundedRectGradient(
                    windowX, windowY, WINDOW_W, WINDOW_H, 9f * SCALE,
                    palette.menuWindowBgLeft(), palette.menuWindowBgRight(), 0f
            );
            ClickGuiRenderer.drawRoundedRectStrokeGradient(
                    windowX, windowY, WINDOW_W, WINDOW_H, 9f * SCALE, 0.55f * SCALE,
                    SettingsGuiPalette.mix(palette.menuWindowStroke(), theme.accentSoft(), 0.18f),
                    SettingsGuiPalette.mix(palette.menuWindowStroke(), theme.accent(), 0.10f),
                    0f
            );

            renderHeader(regular, medium, mx, my, palette, theme);

            float contentY = windowY + HEADER_H + PADDING;
            float contentH = WINDOW_H - HEADER_H - PADDING * 2f;
            renderSources(windowX + PADDING, contentY, SOURCE_W, contentH, regular, medium, mx, my, palette, theme);

            float detailsX = windowX + PADDING + SOURCE_W + GAP;
            float detailsW = WINDOW_W - PADDING * 2f - SOURCE_W - GAP;
            renderDetails(detailsX, contentY, detailsW, contentH, regular, medium, mx, my, palette, theme);
        } finally {
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousAlpha);
        }
    }

    private void renderHeader(TextRenderer regular, TextRenderer medium, float mx, float my,
                              SettingsGuiPalette palette, Themes.Theme theme) {
        float accentX = windowX + PADDING;
        float accentY = windowY + 7f * SCALE;
        ClickGuiRenderer.drawRoundedRect(accentX, accentY, 2f * SCALE, 17f * SCALE, SCALE, theme.accent());
        ClickGuiRenderer.drawRoundedRectGlow(accentX, accentY, 2f * SCALE, 17f * SCALE, SCALE, 7f * SCALE,
                SettingsGuiPalette.withAlpha(theme.accent(), 90));

        ClickGuiRenderer.drawText(medium, title, accentX + 7f * SCALE, windowY + 5.5f * SCALE,
                10.5f * SCALE, palette.menuHeaderText(), false);
        String subtitle = tr("subtitle", "Configure protocol signals independently for each source");
        ClickGuiRenderer.drawText(regular, subtitle, accentX + 7f * SCALE, windowY + 17f * SCALE,
                6.3f * SCALE, palette.panelMuted(), false);

        float separatorY = windowY + HEADER_H;
        ClickGuiRenderer.drawGradientRect(windowX + PADDING, separatorY, WINDOW_W - PADDING * 2f,
                0.5f * SCALE, palette.menuLineStrong(), palette.menuLineMid(),
                palette.menuLineMid(), palette.menuLineStrong());

        close = new Rect(windowX + WINDOW_W - PADDING - CLOSE_SIZE, windowY + 9f * SCALE, CLOSE_SIZE, CLOSE_SIZE);
        closeHover = animate(closeHover, close.contains(mx, my), 14f);
        int bg = SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelPillActive(), 0.22f + closeHover * 0.58f);
        ClickGuiRenderer.drawRoundedRect(close.x, close.y, close.w, close.h, 3.5f * SCALE, bg);
        ClickGuiRenderer.drawRoundedRectStroke(close.x, close.y, close.w, close.h, 3.5f * SCALE,
                0.45f * SCALE, SettingsGuiPalette.mix(palette.panelStroke(), theme.accentSoft(), closeHover * 0.45f));
        float pad = 3.2f * SCALE;
        int color = SettingsGuiPalette.mix(palette.panelMuted(), theme.accent(), closeHover);
        ClickGuiRenderer.drawLine(close.x + pad, close.y + pad, close.x + close.w - pad, close.y + close.h - pad, color);
        ClickGuiRenderer.drawLine(close.x + close.w - pad, close.y + pad, close.x + pad, close.y + close.h - pad, color);
        if (close.contains(mx, my)) SystemCursor.set(SystemCursor.CursorType.HAND);
    }

    private void renderSources(float x, float y, float w, float h, TextRenderer regular, TextRenderer medium,
                               float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        sourceHits.clear();
        panel(x, y, w, h, palette);
        ClickGuiRenderer.drawText(medium, tr("sources", "Sources"), x + 8f * SCALE, y + 7f * SCALE,
                7.2f * SCALE, palette.panelText(), false);
        ClickGuiRenderer.drawText(regular, tr("source_controls", "LMB toggle · RMB configure"),
                x + 8f * SCALE, y + 16f * SCALE, 5.3f * SCALE, palette.panelMuted(), false);

        float rowY = y + 28f * SCALE;
        float rowH = 39f * SCALE;
        float rowGap = 5f * SCALE;
        for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
            Rect card = new Rect(x + 5f * SCALE, rowY, w - 10f * SCALE, rowH);
            boolean hovered = card.contains(mx, my);
            boolean active = source == selected;
            boolean enabled = sourceEnabled(source);
            float hover = animate(sourceHover.get(source), hovered, 13f);
            float select = animate(sourceSelect.get(source), active, 11f);
            sourceHover.put(source, hover);
            sourceSelect.put(source, select);

            int baseLeft = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), hover * 0.30f);
            int baseRight = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), hover * 0.30f);
            float accentMix = Math.max(enabled ? 0.42f : 0f, select * 0.32f);
            int left = SettingsGuiPalette.mix(baseLeft, theme.accentSoft(), accentMix);
            int right = SettingsGuiPalette.mix(baseRight, theme.accent(), accentMix * 0.72f);
            ClickGuiRenderer.drawRoundedRectGradient(card.x, card.y, card.w, card.h, 5.5f * SCALE, left, right, 0f);
            ClickGuiRenderer.drawRoundedRectStrokeGradient(
                    card.x, card.y, card.w, card.h, 5.5f * SCALE, 0.5f * SCALE,
                    SettingsGuiPalette.mix(palette.panelStroke(), theme.accentSoft(), Math.max(select, enabled ? 0.55f : 0f)),
                    SettingsGuiPalette.mix(palette.panelStroke(), theme.accent(), Math.max(select * 0.7f, enabled ? 0.32f : 0f)),
                    0f
            );

            float markerW = (1.5f + 1.5f * select) * SCALE;
            int marker = enabled ? theme.accent() : SettingsGuiPalette.withAlpha(palette.panelMuted(), 100);
            ClickGuiRenderer.drawRoundedRect(card.x + 3f * SCALE, card.y + 7f * SCALE, markerW,
                    card.h - 14f * SCALE, markerW * 0.5f, marker);
            if (enabled) {
                ClickGuiRenderer.drawRoundedRectGlow(card.x + 3f * SCALE, card.y + 7f * SCALE, markerW,
                        card.h - 14f * SCALE, markerW * 0.5f, 5f * SCALE,
                        SettingsGuiPalette.withAlpha(theme.accent(), Math.round(55 + 55 * hover)));
            }

            String name = sourceName(source);
            ClickGuiRenderer.drawText(medium, name, card.x + 9f * SCALE, card.y + 7f * SCALE,
                    7f * SCALE, palette.panelText(), false);
            String state = enabled ? tr("enabled", "Enabled") : tr("disabled", "Disabled");
            int stateColor = enabled ? theme.accent() : palette.panelMuted();
            ClickGuiRenderer.drawText(regular, state, card.x + 9f * SCALE, card.y + 20f * SCALE,
                    5.8f * SCALE, stateColor, false);

            int count = editor(source, CombatProtocolHeuristicsConfig.ProtocolFamily.LEGACY).patternCount()
                    + editor(source, CombatProtocolHeuristicsConfig.ProtocolFamily.MODERN).patternCount();
            String countLabel = tr("rules_short", "%s rules", count);
            float countSize = 5.3f * SCALE;
            float countW = ClickGuiRenderer.textWidth(regular, countLabel, countSize);
            ClickGuiRenderer.drawText(regular, countLabel, card.x + card.w - 7f * SCALE - countW,
                    card.y + 20f * SCALE, countSize, palette.panelMuted(), false);

            sourceHits.add(new SourceHit(source, card));
            if (hovered) SystemCursor.set(SystemCursor.CursorType.HAND);
            rowY += rowH + rowGap;
        }
    }

    private void renderDetails(float x, float y, float w, float h, TextRenderer regular, TextRenderer medium,
                               float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
        panel(x, y, w, h, palette);
        boolean enabled = sourceEnabled(selected);
        ClickGuiRenderer.drawText(medium, sourceName(selected), x + 8f * SCALE, y + 6f * SCALE,
                8.2f * SCALE, palette.panelText(), false);
        String sourceHint = tr("source_hint." + selected.key(), defaultSourceHint(selected));
        ClickGuiRenderer.drawText(regular, sourceHint, x + 8f * SCALE, y + 17f * SCALE,
                5.8f * SCALE, palette.panelMuted(), false);

        String status = enabled ? tr("enabled", "Enabled") : tr("disabled", "Disabled");
        float statusSize = 5.7f * SCALE;
        float statusW = ClickGuiRenderer.textWidth(medium, status, statusSize) + 12f * SCALE;
        float statusX = x + w - 8f * SCALE - statusW;
        int statusBg = enabled
                ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(theme.accentSoft(), palette.panelPillActive(), 0.32f), 205)
                : palette.panelPillBase();
        ClickGuiRenderer.drawRoundedRect(statusX, y + 7f * SCALE, statusW, 11f * SCALE, 5.5f * SCALE, statusBg);
        ClickGuiRenderer.drawText(medium, status, statusX + 6f * SCALE, y + 9.6f * SCALE,
                statusSize, enabled ? theme.accent() : palette.panelMuted(), false);

        float headingH = 29f * SCALE;
        float footerH = 19f * SCALE;
        float editorGap = 6f * SCALE;
        float editorH = (h - headingH - footerH - editorGap * 2f) * 0.5f;
        float editorY = y + headingH;
        editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.LEGACY)
                .render(x + 6f * SCALE, editorY, w - 12f * SCALE, editorH, regular, medium, mx, my, palette, theme);
        editorY += editorH + editorGap;
        editor(selected, CombatProtocolHeuristicsConfig.ProtocolFamily.MODERN)
                .render(x + 6f * SCALE, editorY, w - 12f * SCALE, editorH, regular, medium, mx, my, palette, theme);

        String resetLabel = tr("reset_source", "Reset source");
        float resetW = Math.max(58f * SCALE, ClickGuiRenderer.textWidth(regular, resetLabel, 5.8f * SCALE) + 14f * SCALE);
        reset = new Rect(x + w - 6f * SCALE - resetW, y + h - 15f * SCALE, resetW, 11f * SCALE);
        resetHover = animate(resetHover, reset.contains(mx, my), 13f);
        button(reset, resetLabel, resetHover, regular, palette, theme);
        if (reset.contains(mx, my)) SystemCursor.set(SystemCursor.CursorType.HAND);

        String hint = focusedEditor == null
                ? tr("edit_hint", "Click a pattern area to edit")
                : tr("keyboard_hint", "Ctrl+A/C/X/V · Enter new line · Esc unfocus");
        ClickGuiRenderer.drawText(regular, hint, x + 8f * SCALE, y + h - 12.3f * SCALE,
                5.2f * SCALE, palette.panelMuted(), false);
    }

    private void focus(InlineEditor editor) {
        if (focusedEditor == editor) return;
        if (focusedEditor != null) focusedEditor.setFocused(false);
        focusedEditor = editor;
        if (focusedEditor != null) focusedEditor.setFocused(true);
    }

    private void blurEditor() {
        if (focusedEditor != null) focusedEditor.setFocused(false);
        focusedEditor = null;
    }

    private void reloadEditors(CombatProtocolHeuristicSource source) {
        CombatProtocolHeuristicsConfig config = CombatProtocolHeuristicsConfig.get();
        for (CombatProtocolHeuristicsConfig.ProtocolFamily family : CombatProtocolHeuristicsConfig.ProtocolFamily.values()) {
            editor(source, family).replace(config.patterns(source, family));
        }
    }

    private InlineEditor editor(CombatProtocolHeuristicSource source,
                                CombatProtocolHeuristicsConfig.ProtocolFamily family) {
        return editors.get(source).get(family);
    }

    private boolean sourceEnabled(CombatProtocolHeuristicSource source) {
        return owner.sources() != null && owner.sources().get(source.key());
    }

    private final class InlineEditor {
        private static final float LINE_H = 8f * SCALE;
        private static final float FONT_SIZE = 5.9f * SCALE;
        private static final float GUTTER_W = 19f * SCALE;
        private static final float TEXT_PAD = 5f * SCALE;

        private final CombatProtocolHeuristicSource source;
        private final CombatProtocolHeuristicsConfig.ProtocolFamily family;
        private final StringBuilder buffer = new StringBuilder();
        private final TextSelection selection = new TextSelection();
        private Rect bounds = Rect.EMPTY;
        private Rect textArea = Rect.EMPTY;
        private int caret;
        private int preferredColumn = -1;
        private float scrollY;
        private float targetScrollY;
        private float scrollX;
        private float targetScrollX;
        private float focusAnim;
        private float hoverAnim;
        private float blink;
        private boolean focused;
        private boolean dragging;

        InlineEditor(CombatProtocolHeuristicSource source,
                     CombatProtocolHeuristicsConfig.ProtocolFamily family,
                     Set<String> patterns) {
            this.source = source;
            this.family = family;
            replace(patterns);
        }

        void replace(Set<String> patterns) {
            buffer.setLength(0);
            if (patterns != null && !patterns.isEmpty()) buffer.append(String.join("\n", patterns));
            caret = Math.min(caret, buffer.length());
            selection.clear();
            targetScrollX = targetScrollY = scrollX = scrollY = 0f;
        }

        void setFocused(boolean value) {
            focused = value;
            blink = 0f;
            if (!value) {
                dragging = false;
                selection.clear();
            }
        }

        int patternCount() {
            return parsedPatterns().size();
        }

        void render(float x, float y, float w, float h, TextRenderer regular, TextRenderer medium,
                    float mx, float my, SettingsGuiPalette palette, Themes.Theme theme) {
            bounds = new Rect(x, y, w, h);
            boolean hovered = bounds.contains(mx, my);
            focusAnim = animate(focusAnim, focused, 13f);
            hoverAnim = animate(hoverAnim, hovered, 13f);
            blink += AnimationUtility.deltaTime();
            scrollY = AnimationUtility.approach(scrollY, targetScrollY, 0.28f);
            scrollX = AnimationUtility.approach(scrollX, targetScrollX, 0.28f);

            int baseA = SettingsGuiPalette.mix(palette.moduleCardTop(), palette.panelBgLeft(), 0.30f);
            int baseB = SettingsGuiPalette.mix(palette.moduleCardBottom(), palette.panelBgRight(), 0.28f);
            int bgA = SettingsGuiPalette.mix(baseA, theme.accentSoft(), focusAnim * 0.14f + hoverAnim * 0.04f);
            int bgB = SettingsGuiPalette.mix(baseB, theme.accent(), focusAnim * 0.08f + hoverAnim * 0.03f);
            ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, 5.5f * SCALE, bgA, bgB, 85f);
            ClickGuiRenderer.drawRoundedRectStrokeGradient(
                    x, y, w, h, 5.5f * SCALE, (0.45f + focusAnim * 0.25f) * SCALE,
                    SettingsGuiPalette.mix(palette.panelStroke(), theme.accentSoft(), 0.18f + focusAnim * 0.66f),
                    SettingsGuiPalette.mix(palette.panelStroke(), theme.accent(), 0.10f + focusAnim * 0.54f),
                    0f
            );
            if (focused) {
                ClickGuiRenderer.drawRoundedRectGlow(x, y, w, h, 5.5f * SCALE, 6f * SCALE,
                        SettingsGuiPalette.withAlpha(theme.accentSoft(), Math.round(25f + 25f * focusAnim)));
            }

            boolean legacy = family == CombatProtocolHeuristicsConfig.ProtocolFamily.LEGACY;
            String name = legacy ? tr("legacy", "Legacy (1.8)") : tr("modern", "Modern (1.9+)");
            int invalid = invalidCount(List.of(lines()));
            String meta = tr("pattern_count", "%s patterns", patternCount());
            if (invalid > 0) meta += " · " + tr("invalid_count", "%s invalid", invalid);
            ClickGuiRenderer.drawText(medium, name, x + 7f * SCALE, y + 5.5f * SCALE,
                    6.7f * SCALE, palette.moduleTitleText(), false);
            float metaSize = 5.3f * SCALE;
            float metaW = ClickGuiRenderer.textWidth(regular, meta, metaSize);
            ClickGuiRenderer.drawText(regular, meta, x + w - 7f * SCALE - metaW, y + 7f * SCALE,
                    metaSize, invalid > 0 ? 0xFFFF8A80 : palette.panelMuted(), false);

            float areaY = y + 19f * SCALE;
            textArea = new Rect(x + 5f * SCALE, areaY, w - 10f * SCALE, h - 24f * SCALE);
            int areaBg = SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.18f), 190);
            ClickGuiRenderer.drawRoundedRect(textArea.x, textArea.y, textArea.w, textArea.h, 3.5f * SCALE, areaBg);
            ClickGuiRenderer.drawRoundedRectStroke(textArea.x, textArea.y, textArea.w, textArea.h, 3.5f * SCALE,
                    0.35f * SCALE, SettingsGuiPalette.mix(palette.panelStroke(), theme.accentSoft(), focusAnim * 0.42f));
            renderText(regular, palette, theme);
            if (hovered) SystemCursor.set(SystemCursor.CursorType.TEXT);
        }

        private void renderText(TextRenderer font, SettingsGuiPalette palette, Themes.Theme theme) {
            String[] lines = lines();
            float viewX = textArea.x + GUTTER_W + TEXT_PAD - scrollX;
            float viewY = textArea.y + 3f * SCALE - scrollY;
            boolean clipped = ScissorFunction.pushRaw(textArea.x, textArea.y, textArea.w, textArea.h);
            int globalOffset = 0;
            int caretLine = 0;
            int caretCol = 0;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                float lineY = viewY + i * LINE_H;
                int lineStart = globalOffset;
                int lineEnd = lineStart + line.length();
                if (caret >= lineStart && caret <= lineEnd) {
                    caretLine = i;
                    caretCol = caret - lineStart;
                }

                if (lineY + LINE_H >= textArea.y && lineY <= textArea.y + textArea.h) {
                    if (focused && selection.hasRange() && selection.appliesToLine(0)) {
                        int selStart = Math.min(selection.anchor(), selection.caret());
                        int selEnd = Math.max(selection.anchor(), selection.caret());
                        int a = Math.max(lineStart, selStart);
                        int b = Math.min(lineEnd, selEnd);
                        if (b > a) {
                            float x0 = viewX + ClickGuiRenderer.textWidth(font, line.substring(0, a - lineStart), FONT_SIZE);
                            float x1 = viewX + ClickGuiRenderer.textWidth(font, line.substring(0, b - lineStart), FONT_SIZE);
                            ClickGuiRenderer.drawRoundedRect(x0, lineY, Math.max(1f, x1 - x0), LINE_H,
                                    2f * SCALE, SettingsGuiPalette.withAlpha(theme.accentSoft(), 155));
                        }
                    }

                    String lineNumber = Integer.toString(i + 1);
                    float numberW = ClickGuiRenderer.textWidth(font, lineNumber, 5.1f * SCALE);
                    ClickGuiRenderer.drawText(font, lineNumber, textArea.x + GUTTER_W - 4f * SCALE - numberW,
                            lineY + 0.5f * SCALE, 5.1f * SCALE, palette.panelMuted(), false);
                    int textColor = line.isBlank() || validPattern(line) ? palette.moduleDescriptionText() : 0xFFFF8A80;
                    ClickGuiRenderer.drawText(font, line, viewX, lineY + 0.2f * SCALE, FONT_SIZE, textColor, false);
                }
                globalOffset = lineEnd + 1;
            }

            ClickGuiRenderer.drawRect(textArea.x + GUTTER_W, textArea.y + 2f * SCALE,
                    0.4f * SCALE, textArea.h - 4f * SCALE, palette.panelDivider());

            if (focused && ((int) (blink * 2f) & 1) == 0) {
                String line = lines[Math.max(0, Math.min(caretLine, lines.length - 1))];
                float caretX = viewX + ClickGuiRenderer.textWidth(font, line.substring(0, Math.min(caretCol, line.length())), FONT_SIZE);
                float caretY = viewY + caretLine * LINE_H;
                ClickGuiRenderer.drawRoundedRect(caretX, caretY, 0.65f * SCALE, LINE_H,
                        0.3f * SCALE, theme.accent());
            }
            if (clipped) ScissorFunction.pop();
        }

        boolean mousePressed(float mx, float my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !textArea.contains(mx, my)) return false;
            int target = caretFromPoint(mx, my);
            boolean shift = shiftDown();
            if (shift) {
                if (!selection.hasCaret()) selection.begin(0, caret);
                selection.updateCaret(0, target);
            } else {
                selection.clear();
                selection.begin(0, target);
            }
            caret = target;
            preferredColumn = -1;
            dragging = true;
            blink = 0f;
            ensureCaretVisible();
            return true;
        }

        void mouseMoved(float mx, float my) {
            if (!focused || !dragging) return;
            int target = caretFromPoint(mx, my);
            if (!selection.hasCaret()) selection.begin(0, caret);
            selection.updateCaret(0, target);
            caret = target;
            preferredColumn = -1;
            blink = 0f;
            ensureCaretVisible();
        }

        void mouseReleased(int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        }

        boolean scroll(float mx, float my, double delta) {
            if (!bounds.contains(mx, my)) return false;
            targetScrollY -= (float) delta * LINE_H * 2f;
            clampScroll();
            return true;
        }

        boolean charTyped(char chr) {
            if (!focused || Character.isISOControl(chr)) return false;
            insertText(String.valueOf(chr));
            return true;
        }

        boolean keyPressed(int key, int modifiers) {
            if (!focused) return false;
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (ctrl) {
                switch (key) {
                    case GLFW.GLFW_KEY_A -> selectAll();
                    case GLFW.GLFW_KEY_C -> copy();
                    case GLFW.GLFW_KEY_X -> cut();
                    case GLFW.GLFW_KEY_V -> insertText(ClipboardUtil.get());
                    default -> {
                        return true;
                    }
                }
                return true;
            }
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> insertText("\n");
                case GLFW.GLFW_KEY_BACKSPACE -> backspace();
                case GLFW.GLFW_KEY_DELETE -> deleteForward();
                case GLFW.GLFW_KEY_LEFT -> moveHorizontal(-1, shift);
                case GLFW.GLFW_KEY_RIGHT -> moveHorizontal(1, shift);
                case GLFW.GLFW_KEY_UP -> moveVertical(-1, shift);
                case GLFW.GLFW_KEY_DOWN -> moveVertical(1, shift);
                case GLFW.GLFW_KEY_HOME -> moveLineBoundary(false, shift);
                case GLFW.GLFW_KEY_END -> moveLineBoundary(true, shift);
                default -> {
                    return true;
                }
            }
            return true;
        }

        private void insertText(String text) {
            if (text == null || text.isEmpty()) return;
            deleteSelection();
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            buffer.insert(caret, normalized);
            caret += normalized.length();
            selection.clear();
            preferredColumn = -1;
            blink = 0f;
            changed();
        }

        private void backspace() {
            if (deleteSelection()) {
                changed();
                return;
            }
            if (caret <= 0) return;
            buffer.deleteCharAt(caret - 1);
            caret--;
            selection.clear();
            preferredColumn = -1;
            changed();
        }

        private void deleteForward() {
            if (deleteSelection()) {
                changed();
                return;
            }
            if (caret >= buffer.length()) return;
            buffer.deleteCharAt(caret);
            selection.clear();
            preferredColumn = -1;
            changed();
        }

        private boolean deleteSelection() {
            if (!selection.hasRange()) return false;
            int start = clamp(Math.min(selection.anchor(), selection.caret()), 0, buffer.length());
            int end = clamp(Math.max(selection.anchor(), selection.caret()), 0, buffer.length());
            if (end <= start) return false;
            buffer.delete(start, end);
            caret = start;
            selection.clear();
            return true;
        }

        private void moveHorizontal(int delta, boolean extend) {
            int target;
            if (!extend && selection.hasRange()) {
                target = delta < 0
                        ? Math.min(selection.anchor(), selection.caret())
                        : Math.max(selection.anchor(), selection.caret());
            } else {
                target = clamp(caret + delta, 0, buffer.length());
            }
            updateSelection(target, extend);
            caret = target;
            preferredColumn = -1;
            blink = 0f;
            ensureCaretVisible();
        }

        private void moveVertical(int delta, boolean extend) {
            String[] lines = lines();
            LineColumn current = lineColumn(caret, lines);
            if (preferredColumn < 0) preferredColumn = current.column;
            int targetLine = clamp(current.line + delta, 0, lines.length - 1);
            int target = indexForLineColumn(lines, targetLine, Math.min(preferredColumn, lines[targetLine].length()));
            updateSelection(target, extend);
            caret = target;
            blink = 0f;
            ensureCaretVisible();
        }

        private void moveLineBoundary(boolean end, boolean extend) {
            String[] lines = lines();
            LineColumn current = lineColumn(caret, lines);
            int target = indexForLineColumn(lines, current.line, end ? lines[current.line].length() : 0);
            updateSelection(target, extend);
            caret = target;
            preferredColumn = -1;
            blink = 0f;
            ensureCaretVisible();
        }

        private void updateSelection(int target, boolean extend) {
            if (extend) {
                if (!selection.hasCaret()) selection.begin(0, caret);
                selection.updateCaret(0, target);
            } else {
                selection.clear();
            }
        }

        private void selectAll() {
            selection.begin(0, 0);
            selection.updateCaret(0, buffer.length());
            caret = buffer.length();
            preferredColumn = -1;
            blink = 0f;
            ensureCaretVisible();
        }

        private void copy() {
            if (selection.hasRange()) {
                int start = clamp(Math.min(selection.anchor(), selection.caret()), 0, buffer.length());
                int end = clamp(Math.max(selection.anchor(), selection.caret()), 0, buffer.length());
                if (end > start) ClipboardUtil.copy(buffer.substring(start, end));
            } else if (!buffer.isEmpty()) {
                ClipboardUtil.copy(buffer.toString());
            }
        }

        private void cut() {
            if (!selection.hasRange()) return;
            copy();
            if (deleteSelection()) changed();
        }

        private void changed() {
            CombatProtocolHeuristicsConfig.get().setPatterns(source, family, parsedPatterns());
            clampScroll();
            ensureCaretVisible();
        }

        private Set<String> parsedPatterns() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String line : lines()) {
                String clean = line.trim();
                if (!clean.isEmpty()) out.add(clean);
            }
            return out;
        }

        private int caretFromPoint(float mx, float my) {
            String[] lines = lines();
            int line = clamp((int) Math.floor((my - textArea.y - 3f * SCALE + scrollY) / LINE_H), 0, lines.length - 1);
            String text = lines[line];
            float localX = mx - (textArea.x + GUTTER_W + TEXT_PAD) + scrollX;
            int column = 0;
            TextRenderer font = ClickGuiRenderer.getInterRegular();
            for (int i = 1; i <= text.length(); i++) {
                float prev = ClickGuiRenderer.textWidth(font, text.substring(0, i - 1), FONT_SIZE);
                float next = ClickGuiRenderer.textWidth(font, text.substring(0, i), FONT_SIZE);
                if (localX < (prev + next) * 0.5f) break;
                column = i;
            }
            return indexForLineColumn(lines, line, column);
        }

        private void ensureCaretVisible() {
            String[] lines = lines();
            LineColumn pos = lineColumn(caret, lines);
            float caretY = pos.line * LINE_H;
            float visibleH = Math.max(LINE_H, textArea.h - 6f * SCALE);
            if (caretY < targetScrollY) targetScrollY = caretY;
            if (caretY + LINE_H > targetScrollY + visibleH) targetScrollY = caretY + LINE_H - visibleH;

            TextRenderer font = ClickGuiRenderer.getInterRegular();
            String line = lines[pos.line];
            float caretX = ClickGuiRenderer.textWidth(font, line.substring(0, Math.min(pos.column, line.length())), FONT_SIZE);
            float visibleW = Math.max(20f * SCALE, textArea.w - GUTTER_W - TEXT_PAD * 2f);
            if (caretX < targetScrollX) targetScrollX = caretX;
            if (caretX + 2f * SCALE > targetScrollX + visibleW) targetScrollX = caretX + 2f * SCALE - visibleW;
            clampScroll();
        }

        private void clampScroll() {
            String[] lines = lines();
            float maxY = Math.max(0f, lines.length * LINE_H - Math.max(LINE_H, textArea.h - 6f * SCALE));
            float maxLineW = 0f;
            TextRenderer font = ClickGuiRenderer.getInterRegular();
            for (String line : lines) maxLineW = Math.max(maxLineW, ClickGuiRenderer.textWidth(font, line, FONT_SIZE));
            float maxX = Math.max(0f, maxLineW - Math.max(20f * SCALE, textArea.w - GUTTER_W - TEXT_PAD * 2f));
            targetScrollY = clamp(targetScrollY, 0f, maxY);
            targetScrollX = clamp(targetScrollX, 0f, maxX);
        }

        private String[] lines() {
            return buffer.toString().split("\n", -1);
        }
    }

    private static void panel(float x, float y, float w, float h, SettingsGuiPalette palette) {
        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * SCALE, palette.panelBlurTint(), 175f / 255f);
        ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, 6f * SCALE,
                palette.panelBgLeft(), palette.panelBgRight(), 0f);
        ClickGuiRenderer.drawRoundedRectStroke(x, y, w, h, 6f * SCALE,
                0.5f * SCALE, palette.panelStroke());
    }

    private static void button(Rect rect, String label, float hover, TextRenderer font,
                               SettingsGuiPalette palette, Themes.Theme theme) {
        int bg = SettingsGuiPalette.mix(palette.panelPillBase(), theme.accentSoft(), 0.08f + hover * 0.24f);
        ClickGuiRenderer.drawRoundedRect(rect.x, rect.y, rect.w, rect.h, 3.5f * SCALE, bg);
        ClickGuiRenderer.drawRoundedRectStroke(rect.x, rect.y, rect.w, rect.h, 3.5f * SCALE,
                0.4f * SCALE, SettingsGuiPalette.mix(palette.panelStroke(), theme.accent(), hover * 0.42f));
        float size = 5.8f * SCALE;
        float tw = ClickGuiRenderer.textWidth(font, label, size);
        float th = ClickGuiRenderer.textHeight(font, size);
        ClickGuiRenderer.drawText(font, label, rect.x + (rect.w - tw) * 0.5f,
                rect.y + (rect.h - th) * 0.5f, size,
                SettingsGuiPalette.mix(palette.panelText(), theme.accent(), hover * 0.55f), false);
    }

    private static float animate(float value, boolean active, float speed) {
        float target = active ? 1f : 0f;
        return AnimationUtility.snap(
                AnimationUtility.approach(value, target, AnimationUtility.deltaTime(), speed),
                target,
                0.01f
        );
    }

    private static float easeOutCubic(float value) {
        float t = clamp(value, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static int invalidCount(Iterable<String> patterns) {
        int invalid = 0;
        if (patterns == null) return invalid;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && !validPattern(pattern)) invalid++;
        }
        return invalid;
    }

    private static boolean validPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return true;
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static String sourceName(CombatProtocolHeuristicSource source) {
        return ClickGuiI18n.tr(
                "setting.common.combat.protocol_heuristics.option." + source.key(),
                switch (source) {
                    case MESSAGES -> "Messages";
                    case OVERLAY -> "Overlay";
                    case BOSSBAR -> "Boss bar";
                }
        );
    }

    private static String defaultSourceHint(CombatProtocolHeuristicSource source) {
        return switch (source) {
            case MESSAGES -> "Chat and system messages received from the server";
            case OVERLAY -> "Action bar and overlay text";
            case BOSSBAR -> "Visible boss bar titles";
        };
    }

    private static String tr(String suffix, String fallback, Object... args) {
        return ClickGuiI18n.tr("clickgui.protocol_heuristics." + suffix, fallback, args);
    }

    private static boolean shiftDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static LineColumn lineColumn(int index, String[] lines) {
        int remaining = clamp(index, 0, totalLength(lines));
        for (int line = 0; line < lines.length; line++) {
            int length = lines[line].length();
            if (remaining <= length) return new LineColumn(line, remaining);
            remaining -= length + 1;
        }
        int last = Math.max(0, lines.length - 1);
        return new LineColumn(last, lines[last].length());
    }

    private static int indexForLineColumn(String[] lines, int line, int column) {
        int index = 0;
        int resolvedLine = clamp(line, 0, lines.length - 1);
        for (int i = 0; i < resolvedLine; i++) index += lines[i].length() + 1;
        return index + clamp(column, 0, lines[resolvedLine].length());
    }

    private static int totalLength(String[] lines) {
        int length = 0;
        for (int i = 0; i < lines.length; i++) {
            length += lines[i].length();
            if (i < lines.length - 1) length++;
        }
        return length;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SourceHit(CombatProtocolHeuristicSource source, Rect card) {
    }

    private record LineColumn(int line, int column) {
    }

    private record Rect(float x, float y, float w, float h) {
        static final Rect EMPTY = new Rect(0f, 0f, 0f, 0f);

        boolean contains(float mx, float my) {
            return inside(mx, my, x, y, w, h);
        }
    }
}
