/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.util.text.SingleLineTextInput;

import com.mojang.authlib.GameProfile;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.features.gui.clickgui.util.ClickGuiRichTextRenderer;
import combatant.client.features.module.modules.misc.DefineTarget;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Online player picker for Relations/DefineTarget.
 * Visually follows the custom tab-list shape, but writes directly to PlayerRelations.
 */
final class OnlineRelationPlayerPickerComponent {
    private static final int MAX_ROWS_PER_COLUMN = 13;
    private static final long STRUCTURE_REFRESH_MS = 350L;
    private static final long DYNAMIC_REFRESH_MS = 250L;
    private static final String I18N = "clickgui.settings.relations.online_picker.";

    private final Minecraft mc = Minecraft.getInstance();
    private final Consumer<String> statusSink;
    private final List<PlayerRow> rows = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private final Map<UUID, Identifier> skinCache = new HashMap<>();
    private final Map<String, Float> rowHoverAnim = new HashMap<>();

    private boolean openTarget;
    private boolean searchOpen;
    private boolean searchFocused;
    private String search = "";
    private final SingleLineTextInput searchInput = new SingleLineTextInput(32);
    private DefineTarget.RelationTargetMode mode = DefineTarget.RelationTargetMode.FRIEND;

    private Rect bounds = Rect.ZERO;
    private Rect searchButton = Rect.ZERO;
    private Rect searchField = Rect.ZERO;
    private Rect closeButton = Rect.ZERO;
    private Rect listRect = Rect.ZERO;

    private float openAnim;
    private float searchAnim;
    private float scroll;
    private float smoothedScroll;
    private boolean draggingScrollbar;
    private boolean scrollbarVisible;
    private float scrollbarX;
    private float scrollbarY;
    private float scrollbarW;
    private float scrollbarH;
    private float scrollbarThumbY;
    private float scrollbarThumbH;
    private float scrollbarMaxScroll;
    private float scrollbarDragOffset;
    private long lastStructureCheckMs;
    private long lastDynamicRefreshMs;
    private long lastStructureSignature;
    private int lastOnlineCount = -1;
    private int rowsVersion;
    private int filteredRowsVersion = -1;
    private String filteredSearchKey = "";
    private List<PlayerRow> filteredRowsCache = List.of();

    OnlineRelationPlayerPickerComponent(Consumer<String> statusSink) {
        this.statusSink = statusSink;
    }

    boolean isVisible() {
        return openTarget || openAnim > 0.002f;
    }

    boolean blocksMovementInput() {
        return openTarget && searchFocused;
    }

    boolean isOpen() {
        return openTarget;
    }

    SingleLineTextInput editor() { return searchInput; }

    boolean isSearchFocused() {
        return openTarget && searchFocused;
    }

    String searchText() {
        return search == null ? "" : search;
    }

    void focusSearch() {
        if (!openTarget) return;
        searchFocused = true;
        searchOpen = false;
    }

    void clearSearch() {
        if (search == null || search.isEmpty()) return;
        searchInput.clear();
        search = "";
        resetScroll();
        invalidateFilter();
    }

    void open(DefineTarget.RelationTargetMode initialMode) {
        openTarget = true;
        searchOpen = false;
        if (initialMode != null) mode = initialMode;
        resetScroll();
        refreshNow();
    }

    void close() {
        openTarget = false;
        searchFocused = false;
        draggingScrollbar = false;
        if (!search.isEmpty()) {
            searchInput.clear();
            search = "";
            invalidateFilter();
        }
    }

    void toggle(DefineTarget.RelationTargetMode initialMode) {
        if (openTarget) close();
        else open(initialMode);
    }

    void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    void render(float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette) {
        float dt = AnimationUtility.deltaTime();
        openAnim = AnimationUtility.approach(openAnim, openTarget ? 1.0f : 0.0f, dt, openTarget ? 16.0f : 19.0f);
        openAnim = AnimationUtility.snap(openAnim, openTarget ? 1.0f : 0.0f, 0.003f);
        searchAnim = AnimationUtility.approach(searchAnim, searchFocused ? 1.0f : 0.0f, dt, 12.0f);
        searchAnim = AnimationUtility.snap(searchAnim, searchFocused ? 1.0f : 0.0f, 0.004f);
        if (!isVisible()) return;
        bounds = new Rect(x, y, w, h);
        rowHits.clear();
        refreshIfNeeded();

        float opacity = AnimationUtility.easeOutCubic(openAnim);
        float radius = 6.0f * scale;
        float panelY = y;
        float panelH = h;
        float panelBottom = panelY + panelH;

        // Embedded management surface: no bounce/height morph and no large accent glow.
        // The picker should read as a mode of Relations, not as another floating dialog.
        LayoutRender2D.roundedSoftShadow(
                x,
                panelY + 1.2f * scale,
                w,
                panelH,
                radius,
                7.5f * scale,
                0.0f,
                fade(SettingsGuiPalette.withAlpha(0xFF000000, 72), opacity)
        );
        ClickGuiRenderer.drawBlur(x, panelY, w, panelH, radius, palette.panelBlurTint(), (145f / 255f) * opacity);
        LayoutRender2D.roundedQuad(
                x,
                panelY,
                w,
                panelH,
                radius,
                fade(SettingsGuiPalette.withAlpha(palette.panelBgLeft(), 184), opacity),
                fade(SettingsGuiPalette.withAlpha(palette.panelBgRight(), 174), opacity),
                fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.07f), 178), opacity),
                fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.darken(palette.panelBgLeft(), 0.05f), 188), opacity)
        );
        LayoutRender2D.roundedStrokeQuad(
                x,
                panelY,
                w,
                panelH,
                radius,
                0.55f * scale,
                LayoutRender2D.alpha(palette.glassEdgeStrong(), 0.46f * opacity),
                LayoutRender2D.alpha(palette.glassEdgeSoft(), 0.40f * opacity),
                LayoutRender2D.alpha(palette.glassEdgeSoft(), 0.34f * opacity),
                LayoutRender2D.alpha(palette.glassEdgeStrong(), 0.42f * opacity)
        );

        float pad = 8f * scale;
        float headerH = 28f * scale;
        renderHeader(x + pad, panelY + pad, w - pad * 2f, headerH - pad, mx, my, scale, palette, opacity);

        float listX = x + pad;
        float listY = panelY + headerH + 4f * scale;
        float listW = w - pad * 2f;
        float listH = Math.max(1f, panelBottom - pad - listY);
        listRect = new Rect(listX, listY, listW, listH);
        renderRows(listX, listY, listW, listH, mx, my, scale, palette, opacity);
    }

    private void renderHeader(float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette, float opacity) {
        int accent = modeColor();
        float iconBox = 18f * scale;
        int iconA = fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), accent, 0.09f), 116), opacity);
        int iconB = fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurfaceHover(), accent, 0.15f), 126), opacity);
        LayoutRender2D.roundedQuad(x, y, iconBox, iconBox, 4.8f * scale, iconA, iconB, iconB, iconA);
        LayoutRender2D.roundedStroke(
                x, y, iconBox, iconBox, 4.8f * scale, 0.45f * scale,
                fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.glassEdgeSoft(), accent, 0.22f), 96), opacity)
        );
        Renderer2D.COLOR.svg(
                "users-round",
                x + 4.7f * scale,
                y + 4.7f * scale,
                iconBox - 9.4f * scale,
                iconBox - 9.4f * scale,
                SvgRenderOptions.overrideColor(fade(SettingsGuiPalette.mix(palette.panelText(), accent, 0.24f), opacity))
        );

        float textX = x + iconBox + 6f * scale;
        float badgeH = 12f * scale;
        String badgeText = modeDisplayName(mode) + " · " + filteredRows().size();
        float badgeSize = 5.2f * scale;
        float badgeW = Math.min(69f * scale,
                ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), badgeText, badgeSize) + 13f * scale);
        float badgeX = x + w - badgeW;
        float badgeY = y + 2.5f * scale;
        int badgeA = fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), accent, 0.10f), 110), opacity);
        int badgeB = fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurfaceHover(), accent, 0.16f), 120), opacity);
        LayoutRender2D.roundedQuad(badgeX, badgeY, badgeW, badgeH, badgeH * 0.5f, badgeA, badgeB, badgeB, badgeA);
        float dot = 3.8f * scale;
        Renderer2D.COLOR.roundedRect(
                badgeX + 4f * scale,
                badgeY + (badgeH - dot) * 0.5f,
                dot, dot, dot * 0.5f, 1f,
                fade(SettingsGuiPalette.withAlpha(accent, 238), opacity)
        );
        String fittedBadge = ClickGuiRenderer.fitText(
                ClickGuiRenderer.getInterMedium(), badgeText, badgeSize,
                Math.max(1f, badgeW - 12f * scale)
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(), fittedBadge,
                badgeX + 9.5f * scale,
                badgeY + (badgeH - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), badgeSize)) * 0.5f,
                badgeSize,
                fade(palette.panelText(), opacity),
                false
        );

        float titleMaxW = Math.max(1f, badgeX - 7f * scale - textX);
        float titleSize = 7.8f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), tr("title", "Online players"), titleSize, titleMaxW);
        String subtitle = search == null || search.isBlank()
                ? tr("subtitle", "Pick from current tab list")
                : tr("subtitle_search", "%s / %s visible", filteredRows().size(), rows.size());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), title, textX, y + 1f * scale, titleSize, fade(palette.panelText(), opacity), false);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), subtitle, 5.65f * scale, titleMaxW),
                textX,
                y + 11.6f * scale,
                5.65f * scale,
                fade(palette.panelMuted(), opacity),
                false
        );

        closeButton = Rect.ZERO;
        searchButton = Rect.ZERO;
        searchField = Rect.ZERO;
    }

    private void renderRows(float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette, float opacity) {
        List<PlayerRow> filtered = filteredRows();
        if (filtered.isEmpty()) {
            String text = search.isBlank() ? tr("empty", "No online players.") : tr("empty_search", "No matching online players.");
            float iconBox = 28f * scale;
            float centerX = x + w * 0.5f;
            float centerY = y + Math.min(h * 0.40f, 62f * scale);
            int bg = fade(SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.controlSurface(), modeColor(), 0.05f), 88), opacity);
            LayoutRender2D.roundedQuad(centerX - iconBox * 0.5f, centerY - iconBox * 0.5f, iconBox, iconBox, 7f * scale, bg, bg, bg, bg);
            Renderer2D.COLOR.svg(
                    search.isBlank() ? "users-round" : "circle-question-mark",
                    centerX - 6f * scale,
                    centerY - 6f * scale,
                    12f * scale,
                    12f * scale,
                    SvgRenderOptions.overrideColor(fade(SettingsGuiPalette.mix(palette.panelMuted(), modeColor(), 0.18f), opacity))
            );
            float size = 6.7f * scale;
            String fitted = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), text, size, w - 24f * scale);
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), fitted, size);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), fitted, x + (w - tw) * 0.5f, centerY + 20f * scale, size, fade(palette.panelMuted(), opacity), false);
            updateScrollbarMetrics(x, y, w, h, 0f, scale);
            return;
        }

        float reservedScrollbarW = 9f * scale;
        int rowsPerColumn = filtered.size();
        float gap = 3.5f * scale;
        float rowH = 27f * scale;
        float stepY = rowH + gap;
        float contentH = rowsPerColumn * stepY - gap;
        float maxScroll = Math.max(0f, contentH - h);
        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        float rowAreaW = scrollbarVisible ? Math.max(1f, w - reservedScrollbarW) : w;
        float colW = rowAreaW;
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.22f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        for (int i = 0; i < filtered.size(); i++) {
            int row = i;
            float rowX = x;
            float rowY = y + row * stepY + smoothedScroll;
            if (rowY + rowH < y || rowY > y + h) continue;
            PlayerRow player = filtered.get(i);
            renderRow(player, rowX, rowY, colW, rowH, mx, my, scale, palette, opacity);
        }
        if (clipped) ScissorFunction.pop();
        renderScrollbar(x, y, w, h, maxScroll, scale, palette, opacity);
    }

    private void renderRow(PlayerRow player, float x, float y, float w, float h, float mx, float my, float scale, SettingsGuiPalette palette, float opacity) {
        boolean hover = openTarget && ClickGuiMath.insideRect(mx, my, x, y, w, h);
        String hoverKey = player.name() == null ? "" : player.name().toLowerCase(Locale.ROOT);
        float hoverValue = rowHoverAnim.getOrDefault(hoverKey, 0f);
        float hoverTarget = hover ? 1f : 0f;
        hoverValue = AnimationUtility.snap(
                AnimationUtility.approach(hoverValue, hoverTarget, AnimationUtility.deltaTime(), 11f),
                hoverTarget,
                0.006f
        );
        rowHoverAnim.put(hoverKey, hoverValue);
        boolean selected = DefineTarget.isPlayerInRelationMode(mode, player.name());
        int relationColor = player.relationColor() != 0 ? player.relationColor() : modeColor();
        float radius = 4.5f * scale;
        if (selected) {
            LayoutRender2D.roundedSoftShadow(
                    x + 0.3f * scale, y + 0.5f * scale, w - 0.6f * scale, h,
                    radius, 4f * scale, 0.018f,
                    fade(SettingsGuiPalette.withAlpha(relationColor, 54), opacity)
            );
        }
        int rowA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), relationColor, selected ? 0.12f : hoverValue * 0.035f),
                selected ? 162 : Math.round(98f + hoverValue * 36f));
        int rowB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), relationColor, selected ? 0.18f : 0.015f + hoverValue * 0.065f),
                selected ? 170 : Math.round(102f + hoverValue * 40f));
        LayoutRender2D.roundedQuad(x, y, w, h, radius, fade(rowA, opacity), fade(rowB, opacity), fade(rowB, opacity), fade(rowA, opacity));
        LayoutRender2D.roundedStroke(
                x, y, w, h, radius, 0.5f * scale,
                fade(selected
                        ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), relationColor, 0.55f), 198)
                        : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), Math.round(68f + hoverValue * 40f)), opacity));
        if (selected) {
            float stripW = 1.8f * scale;
            float stripH = Math.max(4f * scale, h - 10f * scale);
            LayoutRender2D.roundedQuad(
                    x + 1.0f * scale, y + (h - stripH) * 0.5f, stripW, stripH, stripW * 0.5f,
                    fade(SettingsGuiPalette.withAlpha(relationColor, 198), opacity),
                    fade(SettingsGuiPalette.withAlpha(relationColor, 228), opacity),
                    fade(SettingsGuiPalette.withAlpha(relationColor, 206), opacity),
                    fade(SettingsGuiPalette.withAlpha(relationColor, 176), opacity)
            );
        }

        float head = 17.0f * scale;
        float headX = x + 6f * scale;
        float headY = y + (h - head) * 0.5f;
        renderHead(resolveSkin(player), headX, headY, head, scale, palette, opacity);

        float dot = 5.3f * scale;
        float dotX = headX + head - dot * 0.72f;
        float dotY = headY + head - dot * 0.72f;
        Renderer2D.COLOR.roundedRect(dotX, dotY, dot, dot, dot * 0.5f, 0.7f, fade(relationColor | 0xFF000000, opacity));
        Renderer2D.COLOR.roundedRectStroke(dotX, dotY, dot, dot, dot * 0.5f, 0.7f, 0.55f * scale, LayoutRender2D.alpha(0xFFFFFFFF, 0.42f * opacity));

        float textX = headX + head + 7f * scale;
        float rightPad = 12f * scale;
        float pingW = Math.max(38f * scale, ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), pingText(player.latency()), 6.2f * scale) + 26f * scale);
        float nameW = Math.max(20f * scale, w - (textX - x) - rightPad - pingW);
        int nameColor = player.spectator() ? palette.panelMuted() : palette.panelText();
        ClickGuiRichTextRenderer.draw(player.displayName(), textX, y + 4.4f * scale, nameW, 7.7f * scale, nameColor, opacity, false);

        String relation = relationLabel(player.relation());
        String relationText = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), relation, 5.3f * scale, nameW);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), relationText, textX, y + 15.8f * scale, 5.3f * scale,
                fade(SettingsGuiPalette.mix(palette.panelMuted(), relationColor | 0xFF000000, 0.45f), opacity), false);

        float pingChipH = 12f * scale;
        float pingChipX = x + w - 6f * scale - pingW;
        float pingChipY = y + (h - pingChipH) * 0.5f;
        drawPingChip(player.latency(), pingChipX, pingChipY, pingW, pingChipH, scale, palette, opacity);

        Rect hit = new Rect(x, y, w, h);
        rowHits.add(new RowHit(player.name(), hit));
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
    }

    boolean click(float mx, float my, int button) {
        if (!openTarget || button != 0) return false;
        if (closeButton.contains(mx, my)) {
            close();
            return true;
        }
        if (searchButton.contains(mx, my)) {
            focusSearch();
            return true;
        }
        if (searchField.contains(mx, my)) {
            focusSearch();
            searchInput.beginDrag(mx, false);
            return true;
        }
        for (RowHit hit : rowHits) {
            if (!hit.rect().contains(mx, my)) continue;
            addPlayer(hit.name());
            return true;
        }
        if (bounds.contains(mx, my)) {
            searchFocused = false;
            return true;
        }
        return false;
    }

    boolean mousePressedScrollbar(float mx, float my, int button) {
        if (!openTarget || button != 0 || !isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    void mouseReleased(int button) {
        if (button == 0) draggingScrollbar = false;
    }

    void scroll(float mx, float my, double amount) {
        if (!openTarget || !listRect.contains(mx, my)) return;
        scroll += (float) (amount * 24f);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!openTarget) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            focusSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            close();
            return true;
        }
        if (!searchFocused) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }
        if (searchInput.keyPressed(keyCode, modifiers)) {
            search = searchInput.text();
            resetScroll();
            invalidateFilter();
        }
        return true;
    }

    boolean charTyped(char chr, int modifiers) {
        if (!openTarget || !searchFocused) return false;
        searchInput.type(chr);
        search = searchInput.text();
        resetScroll();
        invalidateFilter();
        return true;
    }

    private void addPlayer(String rawName) {
        String name = ChatNameUtil.normalizeNickCandidate(rawName);
        if (!ChatNameUtil.isNickLike(name)) {
            setStatus(tr("invalid", "Invalid nick"));
            return;
        }
        String modeName = modeDisplayName(mode);
        boolean wasInMode = DefineTarget.isPlayerInRelationMode(mode, name);
        boolean added = DefineTarget.togglePlayerRelation(mode, name);
        refreshNow();
        if (wasInMode && !added) {
            setStatus(tr("removed", "Removed %s from %s", name, modeName));
        } else {
            setStatus(tr("added", "Added %s as %s", name, modeName));
        }
    }

    private void refreshIfNeeded() {
        if (mc == null || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        int onlineCount = onlineCount();
        boolean checkStructure = rows.isEmpty() || onlineCount != lastOnlineCount || now - lastStructureCheckMs >= STRUCTURE_REFRESH_MS;
        if (checkStructure) {
            long signature = structureSignature();
            lastStructureCheckMs = now;
            if (rows.isEmpty() || onlineCount != lastOnlineCount || signature != lastStructureSignature) {
                collectRows();
                lastOnlineCount = onlineCount;
                lastStructureSignature = signature;
                lastDynamicRefreshMs = now;
                return;
            }
        }

        if (now - lastDynamicRefreshMs >= DYNAMIC_REFRESH_MS) {
            refreshDynamicRows();
            lastDynamicRefreshMs = now;
        }
    }

    private void refreshNow() {
        collectRows();
        lastOnlineCount = onlineCount();
        lastStructureSignature = structureSignature();
        lastStructureCheckMs = System.currentTimeMillis();
        lastDynamicRefreshMs = lastStructureCheckMs;
    }

    private int onlineCount() {
        return mc != null && mc.getConnection() != null ? mc.getConnection().getListedOnlinePlayers().size() : 0;
    }

    private void collectRows() {
        rows.clear();
        if (mc == null || mc.getConnection() == null) {
            rowsVersion++;
            invalidateFilter();
            return;
        }
        Collection<PlayerInfo> online = mc.getConnection().getListedOnlinePlayers();
        List<PlayerInfo> sorted = new ArrayList<>(online);
        sorted.sort(Comparator
                .comparingInt((PlayerInfo info) -> -info.getTabListOrder())
                .thenComparing(info -> info.getGameMode() == GameType.SPECTATOR)
                .thenComparing(info -> teamName(info).toLowerCase(Locale.ROOT))
                .thenComparing(info -> profileName(info).toLowerCase(Locale.ROOT), String.CASE_INSENSITIVE_ORDER));

        Set<UUID> liveIds = new HashSet<>();
        for (PlayerInfo info : sorted) {
            if (info == null || info.getProfile() == null) continue;
            GameProfile profile = info.getProfile();
            UUID id = profile.id();
            String name = profile.name();
            if (id == null || name == null || name.isBlank()) continue;
            Component display = displayName(info, name);
            CategoryType relation = CategoryService.get(name);
            int relationColor = relation == CategoryType.DEFAULT ? PlayerRelations.get().colorDefault() : CategoryService.getColor(name);
            rows.add(new PlayerRow(id, profile, name, display, searchText(name, display), relation, relationColor, info.getLatency(), info.getGameMode() == GameType.SPECTATOR));
            liveIds.add(id);
        }
        skinCache.keySet().retainAll(liveIds);
        rowsVersion++;
        invalidateFilter();
    }

    private void refreshDynamicRows() {
        if (mc == null || mc.getConnection() == null || rows.isEmpty()) return;
        boolean changed = false;
        for (int i = 0; i < rows.size(); i++) {
            PlayerRow row = rows.get(i);
            PlayerInfo info = mc.getConnection().getPlayerInfo(row.id());
            if (info == null || info.getProfile() == null) {
                collectRows();
                return;
            }
            CategoryType relation = CategoryService.get(row.name());
            int relationColor = relation == CategoryType.DEFAULT ? PlayerRelations.get().colorDefault() : CategoryService.getColor(row.name());
            int latency = info.getLatency();
            boolean spectator = info.getGameMode() == GameType.SPECTATOR;
            if (latency == row.latency() && spectator == row.spectator() && relation == row.relation() && relationColor == row.relationColor()) {
                continue;
            }
            rows.set(i, new PlayerRow(row.id(), row.profile(), row.name(), row.displayName(), row.searchText(), relation, relationColor, latency, spectator));
            changed = true;
        }
        if (changed) {
            rowsVersion++;
            invalidateFilter();
        }
    }

    private long structureSignature() {
        if (mc == null || mc.getConnection() == null) return 0L;
        long h = 0x6A09E667F3BCC909L;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null) continue;
            GameProfile profile = info.getProfile();
            h = mix(h, profile.id());
            h = mix(h, profile.name());
            h = mix(h, info.getTabListOrder());
            h = mix(h, info.getGameMode() == GameType.SPECTATOR ? 1 : 0);
            h = mix(h, teamName(info));
            Component rawDisplay = info.getTabListDisplayName();
            h = mix(h, rawDisplay != null ? rawDisplay.getString() : "");
            String name = profile.name() == null ? "" : profile.name();
            CategoryType relation = CategoryService.get(name);
            h = mix(h, relation.ordinal());
            h = mix(h, relation == CategoryType.DEFAULT ? PlayerRelations.get().colorDefault() : CategoryService.getColor(name));
        }
        return h;
    }

    private Component displayName(PlayerInfo info, String fallback) {
        Component display = null;
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getTabList() != null) {
            display = mc.gui.hud.getTabList().getNameForDisplay(info);
        }
        if (display == null) display = info.getTabListDisplayName();
        if (display == null || display.getString().isBlank()) display = Component.literal(fallback);
        return LegacyTextUtil.convertLegacyCodes(display);
    }

    private List<PlayerRow> filteredRows() {
        String q = normalizedSearch();
        if (filteredRowsVersion == rowsVersion && filteredSearchKey.equals(q)) return filteredRowsCache;
        if (q.isBlank()) {
            filteredRowsCache = rows;
        } else {
            List<PlayerRow> out = new ArrayList<>();
            for (PlayerRow row : rows) {
                if (row.searchText().contains(q)) out.add(row);
            }
            filteredRowsCache = out;
        }
        filteredRowsVersion = rowsVersion;
        filteredSearchKey = q;
        return filteredRowsCache;
    }

    private String normalizedSearch() {
        return search == null ? "" : LegacyTextUtil.stripLegacy(search).trim().toLowerCase(Locale.ROOT);
    }

    private void invalidateFilter() {
        filteredRowsVersion = -1;
    }

    private Identifier resolveSkin(PlayerRow player) {
        if (player == null || player.id() == null) return null;
        Identifier cached = skinCache.get(player.id());
        if (cached != null) return cached;
        Identifier skin = PlayerSkinResolver.resolveProfileSkin(player.profile());
        if (skin != null) skinCache.put(player.id(), skin);
        return skin;
    }

    private static String searchText(String name, Component displayName) {
        String raw = (name == null ? "" : name) + " " + (displayName == null ? "" : displayName.getString());
        return LegacyTextUtil.stripLegacy(raw).trim().toLowerCase(Locale.ROOT);
    }

    private static long mix(long h, int value) {
        return (h ^ value) * 0x100000001B3L;
    }

    private static long mix(long h, UUID value) {
        if (value == null) return mix(h, 0);
        h = (h ^ value.getMostSignificantBits()) * 0x100000001B3L;
        return (h ^ value.getLeastSignificantBits()) * 0x100000001B3L;
    }

    private static long mix(long h, String value) {
        return (h ^ (value != null ? value.hashCode() : 0)) * 0x100000001B3L;
    }

    private void drawPingChip(int latency, float x, float y, float w, float h, float scale, SettingsGuiPalette palette, float opacity) {
        String text = pingText(latency);
        int base = pingBaseColor(latency);
        int chipA = LayoutRender2D.alpha(palette.panelPillBase(), 0.84f * opacity);
        int chipB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillBase(), palette.moduleCardBottomStrong(), 0.38f), 0.52f * opacity);
        LayoutRender2D.roundedQuad(x, y, w, h, 5.4f * scale, chipA, chipA, chipB, chipB);
        LayoutRender2D.roundedStrokeQuad(x, y, w, h, 5.4f * scale, 0.55f * scale,
                LayoutRender2D.alpha(palette.panelStroke(), 0.40f * opacity),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.26f * opacity),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.18f * opacity),
                LayoutRender2D.alpha(palette.panelStroke(), 0.30f * opacity));

        float cx = x + 7.3f * scale;
        float cy = y + h * 0.5f;
        float r = 3.1f * scale;
        Renderer2D.COLOR.circleSoftShadow(cx, cy, r + 1.5f * scale, 5.0f * scale, 0.34f, LayoutRender2D.alpha(base, 0.48f * opacity));
        Renderer2D.COLOR.circle(cx, cy, r + 0.95f * scale, 0.8f * scale, LayoutRender2D.alpha(base, 0.30f * opacity));
        Renderer2D.COLOR.circle(cx, cy, r, 0.75f * scale, LayoutRender2D.alpha(base, opacity));
        Renderer2D.COLOR.circleStroke(cx, cy, r + 0.72f * scale, 0.55f * scale, 0.8f * scale, LayoutRender2D.alpha(0xFFFFFFFF, 0.36f * opacity));
        Renderer2D.COLOR.arcStrokeGradient(cx, cy, r + 1.55f * scale, 0.9f * scale, -140f, 70f, 0.45f * scale,
                LayoutRender2D.alpha(base, 0.68f * opacity),
                LayoutRender2D.alpha(0xFFFFFFFF, 0.16f * opacity),
                35f, 0f);

        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), text, x + 14.5f * scale, y + 3.3f * scale, 6.2f * scale, fade(palette.panelText(), opacity), false);
    }

    private void drawSmallIconButton(Rect rect, String icon, boolean active, boolean hover, float scale, SettingsGuiPalette palette, float opacity) {
        int color = active ? modeColor() : palette.panelMuted();
        int bgA = active || hover ? SettingsGuiPalette.mix(palette.panelPillBase(), color, active ? 0.30f : 0.14f) : LayoutRender2D.alpha(palette.panelPillBase(), 0.82f);
        int bgB = active || hover ? SettingsGuiPalette.mix(palette.moduleCardTopStrong(), color, active ? 0.18f : 0.08f) : LayoutRender2D.alpha(palette.panelPillBase(), 0.70f);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4.3f * scale, fade(bgA, opacity), fade(bgB, opacity), fade(bgB, opacity), fade(bgA, opacity));
        LayoutRender2D.roundedStroke(rect.x(), rect.y(), rect.w(), rect.h(), 4.3f * scale, 0.45f * scale, LayoutRender2D.alpha(color, (active || hover ? 0.56f : 0.24f) * opacity));
        Renderer2D.COLOR.svg(icon, rect.x() + 4.1f * scale, rect.y() + 4.1f * scale, rect.w() - 8.2f * scale, rect.h() - 8.2f * scale,
                SvgRenderOptions.overrideColor(fade(active ? palette.panelText() : SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), hover ? 0.25f : 0.0f), opacity)));
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);
    }

    private void drawSearchField(Rect rect, float mx, float my, float scale, SettingsGuiPalette palette, float opacity) {
        boolean hover = rect.contains(mx, my);
        int stroke = searchFocused ? modeColor() : palette.panelStroke();
        ClickGuiRenderer.drawBlur(rect.x(), rect.y(), rect.w(), rect.h(), 4.8f * scale, palette.panelBlurTint(), (100f / 255f) * opacity);
        LayoutRender2D.roundedQuad(rect.x(), rect.y(), rect.w(), rect.h(), 4.8f * scale,
                LayoutRender2D.alpha(palette.panelPillBase(), (hover ? 1.0f : 0.84f) * opacity),
                LayoutRender2D.alpha(palette.panelPillBase(), (hover ? 0.92f : 0.76f) * opacity),
                LayoutRender2D.alpha(palette.panelPillBase(), (hover ? 0.82f : 0.68f) * opacity),
                LayoutRender2D.alpha(palette.panelPillBase(), (hover ? 0.96f : 0.76f) * opacity));
        LayoutRender2D.roundedStroke(rect.x(), rect.y(), rect.w(), rect.h(), 4.8f * scale, 0.55f * scale, LayoutRender2D.alpha(stroke, (searchFocused ? 0.74f : 0.42f) * opacity));
        boolean clipped = ScissorFunction.pushRaw(rect.x(), rect.y(), rect.w(), rect.h());
        boolean empty = searchInput.text().isEmpty();
        float tx = rect.x() + 7f * scale;
        float ty = rect.y() + 5f * scale;
        if (empty && !searchFocused) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), tr("search_placeholder", "Search online player"),
                    tx, ty, 6.8f * scale, fade(palette.panelMuted(), opacity), false);
        } else {
            searchInput.render(tx, ty, rect.w() - 14f * scale, rect.h(), ClickGuiRenderer.getInterRegular(),
                    6.8f * scale, fade(palette.panelText(), opacity), fade(LayoutRender2D.alpha(modeColor(), 0.48f), opacity), searchFocused);
        }
        if (clipped) ScissorFunction.pop();
        if (hover || searchFocused) SystemCursor.set(SystemCursor.CursorType.TEXT);
    }

    private void renderHead(Identifier skin, float x, float y, float size, float scale, SettingsGuiPalette palette, float opacity) {
        float headOpacity = AnimationUtility.clamp01(opacity * ClickGuiRenderer.getRenderAlphaMultiplier());
        if (headOpacity <= 0.01f) return;
        GuiGraphicsExtractor ctx = ViewportContext.getCurrentContext();
        if (skin != null && ctx != null) {
            int alpha = Math.round(255f * headOpacity);
            PlayerHeadRenderer.drawRounded(ctx, x, y, size, 4.2f * scale, skin, new RenderColor(255, 255, 255, alpha), true,
                    new RenderColor(255, 255, 255, Math.round(48f * headOpacity)), 0.95f * scale, false);
            return;
        }
        Renderer2D.COLOR.roundedRect(x, y, size, size, 4.2f * scale, 1.0f, LayoutRender2D.alpha(palette.panelMuted(), 0.22f * headOpacity));
        Renderer2D.COLOR.roundedRectStroke(x, y, size, size, 4.2f * scale, 1.0f, 0.55f * scale, LayoutRender2D.alpha(palette.panelMuted(), 0.62f * headOpacity));
    }

    private void renderScrollbar(float x, float y, float w, float h, float maxScroll, float scale, SettingsGuiPalette palette, float opacity) {
        updateScrollbarMetrics(x, y, w, h, maxScroll, scale);
        if (!scrollbarVisible) return;
        if (isScrollbarHovered(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY()) || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH, 2f * scale,
                fade(palette.panelScrollTrackA(), opacity), fade(palette.panelScrollTrackB(), opacity), fade(palette.panelScrollTrackB(), opacity), fade(palette.panelScrollTrackA(), opacity));
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 2f * scale,
                fade(palette.panelScrollHandleA(), opacity), fade(palette.panelScrollHandleB(), opacity), fade(palette.panelScrollHandleB(), opacity), fade(palette.panelScrollHandleA(), opacity));
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

    private static int fade(int color, float opacity) {
        return LayoutRender2D.alpha(color, AnimationUtility.clamp01(opacity));
    }

    private int modeColor() {
        return mode.color(PlayerRelations.get()) | 0xFF000000;
    }


    private static int pingBaseColor(int latency) {
        if (latency < 0) return 0xFF8993A3;
        if (latency < 90) return 0xFF52F2A1;
        if (latency < 180) return 0xFFFFD166;
        return 0xFFFF5A6E;
    }

    private static int pingColor(int latency) {
        if (latency < 0) return 0xFF9AA5B5;
        if (latency < 75) return 0xFF66E086;
        if (latency < 150) return 0xFFE8D36E;
        if (latency < 300) return 0xFFFFA756;
        return 0xFFFF5E6A;
    }

    private static String pingText(int latency) {
        return latency < 0 ? "?" : String.valueOf(latency);
    }

    private static String modeDisplayName(DefineTarget.RelationTargetMode mode) {
        if (mode == null) return "";
        return tr("mode_name." + mode.id(), mode.fallbackLabel());
    }

    private static String relationLabel(CategoryType type) {
        if (type == null) return tr("relation.default", "Default");
        return switch (type) {
            case FRIEND -> tr("relation.friend", "Friend");
            case ENEMY -> tr("relation.enemy", "Enemy");
            case STAFF -> tr("relation.staff", "Staff");
            case BEDWARS_SELF -> tr("relation.bedwars_self", "Bedwars Self");
            case BEDWARS_ENEMY -> tr("relation.bedwars_enemy", "Bedwars Enemy");
            default -> tr("relation.default", "Default");
        };
    }

    private static String teamName(PlayerInfo info) {
        return info != null && info.getTeam() != null && info.getTeam().getName() != null ? info.getTeam().getName() : "";
    }

    private static String profileName(PlayerInfo info) {
        GameProfile profile = info != null ? info.getProfile() : null;
        return profile != null && profile.name() != null ? profile.name() : "";
    }

    private void setStatus(String text) {
        if (statusSink != null) statusSink.accept(text);
    }

    private static String tr(String key, String fallback, Object... args) {
        return ClickGuiI18n.tr(I18N + key, fallback, args);
    }

    private record PlayerRow(UUID id,
                             GameProfile profile,
                             String name,
                             Component displayName,
                             String searchText,
                             CategoryType relation,
                             int relationColor,
                             int latency,
                             boolean spectator) {
    }

    private record RowHit(String name, Rect rect) {
    }

    private record Rect(float x, float y, float w, float h) {
        static final Rect ZERO = new Rect(0f, 0f, 0f, 0f);

        boolean contains(float mx, float my) {
            return ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }
    }
}
