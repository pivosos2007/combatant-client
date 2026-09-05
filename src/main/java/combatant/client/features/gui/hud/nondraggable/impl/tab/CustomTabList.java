/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl.tab;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.module.HudPhase;
import combatant.client.features.relations.CategoryType;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.helpers.TickDelta;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 95)
public final class CustomTabList extends AbstractHudElement {
    public static final CustomTabList INSTANCE = new CustomTabList();

    private static final float TOP_Y = 10f;
    private static final float HEAD_SIZE = 20f;
    private static final float ROW_PAD_X = 7f;
    private static final float TEXT_SIZE = 20.5f;
    private static final float PING_TEXT_SIZE = 16.5f;
    private static final float SCORE_TEXT_SIZE = 18f;
    private static final float HEADER_TEXT_SIZE = 21f;
    private static final float FOOTER_TEXT_SIZE = 18f;
    private static final float SHELL_PAD_X = 5f;

    private static final long STRUCTURE_PROBE_INTERVAL_MS = 250L;
    private static final long DYNAMIC_REFRESH_INTERVAL_MS = 500L;

    private final Minecraft mc = Minecraft.getInstance();
    private final TabListAnimator animator = new TabListAnimator();
    private final ArrayList<RowRender> rowRenderCache = new ArrayList<>();
    private final RenderColor headColor = new RenderColor(255, 255, 255, 255);
    private final RenderColor transparentHeadOutline = new RenderColor(255, 255, 255, 0);
    private final NumberValue<Integer> maxWidth = num("tab_list_max_width", "max_width", 1680, 760, 2600);
    private final BooleanValue syncTheme = bool("tab_list_sync_theme", "sync_theme", true);
    private final NumberValue<Integer> themeAlpha = visibleWhen(num("tab_list_theme_alpha", "theme_alpha", 225, 0, 255), syncTheme::get);
    private final RGBAColorValue bgColor = visibleWhen(color("tab_list_bg", "background", "#E50B1018"), () -> !syncTheme.get());
    private final RGBColorValue accentColor = visibleWhen(colorNoAlpha("tab_list_accent", "accent", "#6E8DFF"), () -> !syncTheme.get());
    private final RGBColorValue textColor = visibleWhen(colorNoAlpha("tab_list_text", "text", "#F4F7FB"), () -> !syncTheme.get());
    private final RGBColorValue mutedColor = visibleWhen(colorNoAlpha("tab_list_muted", "muted", "#9AA5B5"), () -> !syncTheme.get());

    private TabListModel.Snapshot snapshot = TabListModel.Snapshot.empty();
    private long lastStructureProbeMs;
    private long lastDynamicRefreshMs;
    private long lastStructureSignature = Long.MIN_VALUE;
    private int lastSnapshotWidth = -1;
    private boolean wasShown;
    private int uiFillTop;
    private int uiFillBottom;
    private int uiStroke;
    private int uiStrokeSoft;
    private int uiAccent;
    private int uiText;
    private int uiMuted;
    private int uiRowFill;
    private int uiRowAltFill;
    private int uiRowHighlight;

    private CustomTabList() {
        super("tab_list", "Tab List", true);
    }

    public static boolean shouldReplaceVanilla() {
        return RuntimeGate.canRunHud()
                && INSTANCE != null
                && INSTANCE.isEnabled()
                && INSTANCE.canRenderReplacement();
    }

    public static void renderVanillaTabStratum(GuiGraphicsExtractor ctx, int guiWidth) {
        CustomTabList instance = INSTANCE;
        if (instance == null) return;
        instance.renderVanillaTabStratumInternal(ctx, guiWidth);
    }


    private void renderVanillaTabStratumInternal(GuiGraphicsExtractor ctx, int guiWidth) {
        if (!RuntimeGate.canRunHud()) return;
        if (mc == null || mc.getWindow() == null || ctx == null) return;
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        )));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        )));

        CombatantRenderSystem.ensureFrameContext();
        CombatantRenderSystem.updateFrameTiming(
                TickDelta.tickProgress(false),
                TickDelta.frameDeltaTicks(),
                TickDelta.fixedDeltaTicks()
        );
        ViewportContext.beginCurrentStratumUnscaledLogical(ctx);
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:vanilla_stratum:tab_list")) {
            Renderer2D.COLOR.begin();
            renderTab(Renderer2D.COLOR, ctx, TickDelta.tickProgress(false), screenW, screenH, true);
            Renderer2D.COLOR.render();
        } finally {
            ViewportContext.endCurrentStratum(ctx);
        }
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudRenderSpace getRenderSpace() {
        return HudRenderSpace.UNSCALED_LOGICAL;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.NONE;
    }

    @Override
    public int getRenderOrder() {
        return 1000;
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer,
                                       combatant.client.render.engine.text.TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
        renderTab(renderer, ctx, tickDelta, screenW, screenH, isEnabled() && shouldShowNow());
    }

    private void renderTab(Renderer2D renderer,
                           GuiGraphicsExtractor ctx,
                           float tickDelta,
                           int screenW,
                           int screenH,
                           boolean show) {
        if (mc == null || ctx == null || renderer == null) return;
        float panelPresence = animator.updatePanel(show);
        if (!show && panelPresence <= 0.01f && !animator.hasVisibleRows()) {
            setBounds(0f, 0f, 0f, 0f);
            return;
        }

        if (show) {
            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("tab_list:model_refresh")) {
                refreshSnapshot(screenW, !wasShown);
            }
        }
        wasShown = show;
        animator.updateRows(show ? snapshot.visibleIds() : null);

        float panelAlpha = AnimationUtility.easeOutCubic(panelPresence);
        float width = tabPanelWidth(screenW);
        float height = snapshot.height();
        float drawX = (screenW - width) * 0.5f;
        float drawY = TOP_Y - (1f - panelAlpha) * 18f;
        setBounds(drawX, drawY, width, height);

        updatePalette();
        int renderedRows;
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("tab_list:row_layout")) {
            renderedRows = collectRowRenders(drawX, drawY, width, panelAlpha);
        }
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("tab_list:chrome")) {
            renderNativeChrome(renderer, drawX, drawY, width, height, panelAlpha, renderedRows);
        }
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("tab_list:content")) {
            renderHeadsAndText(ctx, renderer, drawX, drawY, width, panelAlpha, renderedRows);
        }
    }

    private void refreshSnapshot(int screenW, boolean force) {
        if (mc == null || mc.getConnection() == null) return;
        long now = Util.getMillis();
        int listedPlayers = Math.min(mc.getConnection().getListedOnlinePlayers().size(), TabListModel.maxPlayers());
        boolean multiWide = listedPlayers > TabListModel.maxRowsPerColumn() * 2;
        int effectiveWidth = Math.max(1, Math.min(screenW, multiWide ? screenW : maxWidth.get()));
        boolean needCollect = force
                || snapshot.entries().isEmpty()
                || snapshot.entries().size() != listedPlayers
                || effectiveWidth != lastSnapshotWidth;
        long signature = lastStructureSignature;
        if (needCollect || now - lastStructureProbeMs >= STRUCTURE_PROBE_INTERVAL_MS) {
            signature = TabListModel.structureSignature(mc, effectiveWidth);
            lastStructureProbeMs = now;
            if (signature != lastStructureSignature) {
                needCollect = true;
            }
        }

        if (needCollect) {
            snapshot = TabListModel.collect(mc, effectiveWidth);
            lastSnapshotWidth = effectiveWidth;
            lastStructureSignature = signature;
            lastDynamicRefreshMs = now;
            return;
        }

        if (now - lastDynamicRefreshMs >= DYNAMIC_REFRESH_INTERVAL_MS) {
            snapshot = TabListModel.refreshDynamic(mc, snapshot);
            lastDynamicRefreshMs = now;
        }
    }

    private float tabPanelWidth(int screenW) {
        float screenCap = Math.max(360f, screenW - 24f);
        float configured = Math.max(1f, maxWidth.get());
        float wanted = snapshot.width();
        if (snapshot.columns() > 2) {
            configured = Math.max(configured, wanted);
        }
        return Math.min(screenCap, Math.min(configured, wanted));
    }

    private boolean canRenderReplacement() {
        return mc != null && mc.player != null && mc.getConnection() != null;
    }

    private boolean shouldShowNow() {
        if (mc == null || mc.player == null || mc.getConnection() == null || mc.options == null) return false;
        if (!mc.options.keyPlayerList.isDown()) return false;

        int listedPlayers = mc.getConnection().getListedOnlinePlayers().size();
        if (listedPlayers <= 0) return false;
        if (!mc.isLocalServer() || listedPlayers > 1) return true;

        Objective listObjective = null;
        if (mc.level != null) {
            Scoreboard scoreboard = mc.level.getScoreboard();
            if (scoreboard != null) {
                listObjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
            }
        }
        return listObjective != null;
    }

    private void renderNativeChrome(Renderer2D renderer,
                                    float drawX,
                                    float drawY,
                                    float width,
                                    float height,
                                    float alpha,
                                    int renderedRows) {
        if (renderer == null || alpha <= 0.01f) return;
        renderNativeShell(renderer, drawX, drawY, width, height, alpha);
        renderNativeHeaderFooterBlocks(renderer, drawX, drawY, width, alpha);
        renderNativeRows(renderer, renderedRows);
    }

    private void renderNativeShell(Renderer2D renderer, float x, float y, float w, float h, float alpha) {
        renderer.roundedRectShadow(x, y, w, h, 4f, 4f, 8f,
                HudRenderUtil.scaleAlpha(0xFF000000, 0.24f * alpha));
        renderer.roundedRectGradient(x, y, w, h, 4f, 0.8f,
                HudRenderUtil.scaleAlpha(uiFillTop, alpha),
                HudRenderUtil.scaleAlpha(uiFillBottom, alpha), 90f);
        renderer.roundedRectStroke(x, y, w, h, 4f, 0.8f, 0.75f,
                HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.52f * alpha));
    }

    private void renderNativeHeaderFooterBlocks(Renderer2D renderer, float baseX, float baseY, float width, float alpha) {
        if (!snapshot.headerLines().isEmpty() && snapshot.headerHeight() > 0f) {
            float x = baseX + SHELL_PAD_X;
            float y = baseY + snapshot.headerTop();
            float w = width - SHELL_PAD_X * 2f;
            float h = snapshot.headerHeight();
            renderer.roundedRect(x, y, w, h, 2.5f, 0.75f,
                    HudRenderUtil.scaleAlpha(uiRowHighlight, 0.80f * alpha));
            renderer.roundedRect(x, y + h - 1f, w, 1f, 0f, 0f,
                    HudRenderUtil.scaleAlpha(uiStroke, 0.48f * alpha));
        }
        if (!snapshot.footerLines().isEmpty() && snapshot.footerHeight() > 0f) {
            float x = baseX + SHELL_PAD_X;
            float y = baseY + snapshot.footerTop();
            float w = width - SHELL_PAD_X * 2f;
            float h = snapshot.footerHeight();
            renderer.roundedRect(x, y, w, h, 2f, 0.75f,
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.72f * alpha));
            renderer.roundedRect(x, y, w, 1f, 0f, 0f,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.40f * alpha));
        }
    }

    private void renderNativeRows(Renderer2D renderer, int renderedRows) {
        if (renderedRows <= 0) return;
        renderRowFills(renderer, renderedRows);
        renderRowRelationBars(renderer, renderedRows);
    }

    private int collectRowRenders(float baseX, float baseY, float drawWidth, float panelAlpha) {
        List<TabListModel.Entry> entries = snapshot.entries();
        if (entries.isEmpty() || panelAlpha <= 0.01f) return 0;

        int rowsPerColumn = Math.max(1, snapshot.rows());
        float contentX = contentX(baseX, drawWidth);
        float rowTop = snapshot.rowTop();
        float rowHeight = snapshot.rowHeight();
        float columnStride = snapshot.columnWidth() + snapshot.columnGap();
        int renderedRows = 0;
        for (int i = 0; i < entries.size(); i++) {
            TabListModel.Entry entry = entries.get(i);
            TabListAnimator.RowState state = animator.row(entry.id());
            float presence = state.presence;
            float rowAlpha = panelAlpha * AnimationUtility.easeOutCubic(presence);
            if (rowAlpha <= 0.01f) continue;

            int col = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            float x = contentX + col * columnStride;
            float y = baseY + rowTop + row * rowHeight + (1f - presence) * 10.0f;
            float w = snapshot.columnWidth();
            float h = rowHeight - 1f;
            int rowFill = (row & 1) == 0 ? uiRowFill : uiRowAltFill;
            int relationColor = entry.relationColor() == 0 ? uiAccent : entry.relationColor() | 0xFF000000;
            boolean relation = entry.relation() != CategoryType.DEFAULT;
            RowRender render;
            if (renderedRows < rowRenderCache.size()) {
                render = rowRenderCache.get(renderedRows);
            } else {
                render = new RowRender();
                rowRenderCache.add(render);
            }
            render.set(entry, x, y, w, h, rowAlpha, rowFill, relationColor, relation);
            renderedRows++;
        }
        return renderedRows;
    }

    private void renderRowFills(Renderer2D renderer, int renderedRows) {
        for (int i = 0; i < renderedRows; i++) {
            RowRender r = rowRenderCache.get(i);
            renderer.roundedRect(r.x, r.y, r.w, r.h, 2f, 0.7f,
                    HudRenderUtil.scaleAlpha(r.rowFill, r.alpha));
        }
    }

    private void renderRowRelationBars(Renderer2D renderer, int renderedRows) {
        for (int i = 0; i < renderedRows; i++) {
            RowRender r = rowRenderCache.get(i);
            if (!r.relation) continue;
            renderer.roundedRect(r.x, r.y + 3f, 2f, Math.max(2f, r.h - 6f), 0.5f, 0.5f,
                    HudRenderUtil.scaleAlpha(r.relationColor, 0.82f * r.alpha));
        }
    }

    private void renderHeadsAndText(GuiGraphicsExtractor ctx,
                                    Renderer2D renderer,
                                    float baseX,
                                    float baseY,
                                    float drawWidth,
                                    float panelAlpha,
                                    int renderedRows) {
        float textH = TabRichTextRenderer.height(TEXT_SIZE);
        renderHeaderText(renderer, baseX, baseY, drawWidth, panelAlpha);

        for (int i = 0; i < renderedRows; i++) {
            RowRender row = rowRenderCache.get(i);
            TabListModel.Entry entry = row.entry;
            float headX = row.x + ROW_PAD_X;
            float headY = row.y + (row.h - HEAD_SIZE) * 0.5f;
            int alpha = Math.max(0, Math.min(255, Math.round(255f * row.alpha)));

            var skin = PlayerHeadRenderer.resolveCachedSkin(entry.id(), entry.name(), entry.skin());
            if (skin != null) {
                headColor.a = alpha;
                PlayerHeadRenderer.drawRounded(
                        ctx,
                        headX,
                        headY,
                        HEAD_SIZE,
                        1f,
                        skin,
                        headColor,
                        true,
                        transparentHeadOutline,
                        0f,
                        false
                );
            }
        }

        renderPingTexts(renderer, renderedRows);
        renderScoreTexts(renderer, renderedRows);
        renderNameTexts(renderer, renderedRows, textH);
        renderFooterText(renderer, baseX, baseY, drawWidth, panelAlpha);
    }

    private void renderPingTexts(Renderer2D renderer, int renderedRows) {
        float pingTextH = TabRichTextRenderer.height(PING_TEXT_SIZE);
        for (int i = 0; i < renderedRows; i++) {
            RowRender row = rowRenderCache.get(i);
            TabListModel.Entry entry = row.entry;
            float right = row.x + row.w - ROW_PAD_X;
            float pingX = right - entry.pingTextWidth();
            float pingY = row.y + (row.h - pingTextH) * 0.5f - 0.25f;
            TabRichTextRenderer.drawPlain(renderer, entry.pingText(), pingX, pingY,
                    entry.pingTextWidth() + 2f, PING_TEXT_SIZE, pingBaseColor(entry.latency()), row.alpha);
        }
    }

    private void renderScoreTexts(Renderer2D renderer, int renderedRows) {
        float scoreTextH = TabRichTextRenderer.height(SCORE_TEXT_SIZE);
        for (int i = 0; i < renderedRows; i++) {
            RowRender row = rowRenderCache.get(i);
            TabListModel.Entry entry = row.entry;
            if (entry.scoreText() == null) continue;
            float right = row.x + row.w - ROW_PAD_X;
            float pingLeft = right - entry.pingColumnWidth() - 6f;
            float scoreX = pingLeft - entry.scoreWidth();
            float scoreY = row.y + (row.h - scoreTextH) * 0.5f - 0.5f;
            TabRichTextRenderer.draw(renderer, entry.scoreText(), scoreX, scoreY,
                    entry.scoreWidth() + 2f, SCORE_TEXT_SIZE, uiMuted, row.alpha);
        }
    }

    private void renderNameTexts(Renderer2D renderer, int renderedRows, float textH) {
        for (int i = 0; i < renderedRows; i++) {
            RowRender row = rowRenderCache.get(i);
            TabListModel.Entry entry = row.entry;
            float right = row.x + row.w - ROW_PAD_X;
            float occupiedLeft = right - entry.pingColumnWidth() - 6f;
            if (entry.scoreText() != null) {
                occupiedLeft = occupiedLeft - entry.scoreWidth() - 8f;
            }
            float textX = row.x + ROW_PAD_X + HEAD_SIZE + 7f;
            float textY = row.y + (row.h - textH) * 0.5f - 1.0f;
            float textW = Math.max(40f, occupiedLeft - textX);
            int baseColor = entry.spectator() ? uiMuted : uiText;
            TabRichTextRenderer.draw(renderer, entry.displayName(), textX, textY, textW, TEXT_SIZE,
                    baseColor, row.alpha);
        }
    }

    private void renderHeaderText(Renderer2D renderer, float baseX, float baseY, float drawWidth, float alpha) {
        if (snapshot.headerLines().isEmpty() || alpha <= 0.01f) return;
        float lineH = 24f;
        float y = baseY + snapshot.headerTop() + 3f + (lineH - TabRichTextRenderer.height(HEADER_TEXT_SIZE)) * 0.5f;
        for (Component line : snapshot.headerLines()) {
            drawCentered(renderer, line, baseX + drawWidth * 0.5f, y,
                    drawWidth - SHELL_PAD_X * 2f, HEADER_TEXT_SIZE, 0xFFFFFFFF, alpha);
            y += lineH;
        }
    }

    private void renderFooterText(Renderer2D renderer, float baseX, float baseY, float drawWidth, float alpha) {
        if (snapshot.footerLines().isEmpty() || alpha <= 0.01f) return;
        float lineH = 21f;
        float y = baseY + snapshot.footerTop() + 3f + (lineH - TabRichTextRenderer.height(FOOTER_TEXT_SIZE)) * 0.5f;
        for (Component line : snapshot.footerLines()) {
            drawCentered(renderer, line, baseX + drawWidth * 0.5f, y,
                    drawWidth - SHELL_PAD_X * 2f, FOOTER_TEXT_SIZE, 0xFFFFFFFF, alpha);
            y += lineH;
        }
    }

    private float contentX(float baseX, float drawWidth) {
        float contentWidth = snapshot.columns() * snapshot.columnWidth()
                + Math.max(0, snapshot.columns() - 1) * snapshot.columnGap();
        return baseX + Math.max(SHELL_PAD_X, (drawWidth - contentWidth) * 0.5f);
    }

    private void drawCentered(Renderer2D renderer,
                              Component text,
                              float centerX,
                              float y,
                              float maxWidth,
                              float size,
                              int color,
                              float alpha) {
        if (text == null || alpha <= 0.01f || maxWidth <= 0f) return;
        float width = Math.min(maxWidth, TabRichTextRenderer.width(text, size));
        TabRichTextRenderer.draw(renderer, text, centerX - width * 0.5f, y, maxWidth, size, color, alpha);
    }

    private static final class RowRender {
        private TabListModel.Entry entry;
        private float x;
        private float y;
        private float w;
        private float h;
        private float alpha;
        private int rowFill;
        private int relationColor;
        private boolean relation;

        private void set(TabListModel.Entry entry,
                         float x,
                         float y,
                         float w,
                         float h,
                         float alpha,
                         int rowFill,
                         int relationColor,
                         boolean relation) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.alpha = alpha;
            this.rowFill = rowFill;
            this.relationColor = relationColor;
            this.relation = relation;
        }
    }

    private static int pingBaseColor(int ping) {
        if (ping < 0) return 0xFF8993A3;
        if (ping < 90) return 0xFF52F2A1;
        if (ping < 180) return 0xFFFFD166;
        return 0xFFFF5A6E;
    }


    private void updatePalette() {
        if (syncTheme.get()) {
            int windowBg = HudRenderUtil.setAlpha(theme().windowBg(), themeAlpha.get());
            uiFillTop = HudRenderUtil.mixRgb(windowBg, 0xFFFFFF, 0.060f);
            uiFillBottom = HudRenderUtil.mixRgb(windowBg, 0x000000, 0.105f);
            uiStroke = theme().windowStroke();
            uiStrokeSoft = theme().strokeSoft();
            uiAccent = theme().accent();
            uiText = theme().textPrimary();
            uiMuted = theme().textMuted();
            uiRowFill = HudRenderUtil.setAlpha(theme().surface(), 122);
            uiRowAltFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(theme().surface(), 0xFFFFFF, 0.026f), 100);
            uiRowHighlight = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(theme().surface(), 0xFFFFFF, 0.070f), 130);
            return;
        }

        int baseBg = bgColor.getArgb();
        uiFillTop = HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.060f);
        uiFillBottom = HudRenderUtil.mixRgb(baseBg, 0x000000, 0.105f);
        uiAccent = accentColor.getArgb() | 0xFF000000;
        uiText = textColor.getArgb() | 0xFF000000;
        uiMuted = mutedColor.getArgb() | 0xFF000000;
        uiStroke = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiAccent, 0xFFFFFFFF, 0.18f), 0.62f);
        uiStrokeSoft = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiMuted, baseBg, 0.42f), 0.42f);
        uiRowFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.045f), 122);
        uiRowAltFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.024f), 100);
        uiRowHighlight = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.075f), 130);
    }
}
