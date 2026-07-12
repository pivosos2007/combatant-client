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

import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 95)
public final class CustomTabList extends AbstractHudElement {
    public static final CustomTabList INSTANCE = new CustomTabList();

    private static final float TOP_Y = 56f;
    private static final float HEAD_SIZE = 30f;
    private static final float ROW_PAD_X = 10.5f;
    private static final float TEXT_SIZE = 24.5f;
    private static final float PING_TEXT_SIZE = 17.5f;
    private static final float SCORE_TEXT_SIZE = 20.5f;
    private static final float HEADER_TEXT_SIZE = 24f;
    private static final float FOOTER_TEXT_SIZE = 20.5f;
    private static final float SHELL_PAD_X = 14f;

    private static final long STRUCTURE_PROBE_INTERVAL_MS = 250L;
    private static final long DYNAMIC_REFRESH_INTERVAL_MS = 500L;

    private final Minecraft mc = Minecraft.getInstance();
    private final TabListAnimator animator = new TabListAnimator();
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
    private int uiFillHotspot;
    private int uiStroke;
    private int uiStrokeSoft;
    private int uiAccent;
    private int uiAccentSoft;
    private int uiAccentGlow;
    private int uiText;
    private int uiMuted;
    private int uiRowFill;
    private int uiRowAltFill;
    private int uiRowHighlight;
    private int uiPingChip;
    private int uiDivider;

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
            refreshSnapshot(screenW, !wasShown);
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
        renderNativeChrome(renderer, drawX, drawY, width, height, panelAlpha);
        renderHeadsAndText(ctx, renderer, drawX, drawY, width, panelAlpha);
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

    private void renderNativeChrome(Renderer2D renderer, float drawX, float drawY, float width, float height, float alpha) {
        if (renderer == null || alpha <= 0.01f) return;
        renderNativeShell(renderer, drawX, drawY, width, height, alpha);
        renderNativeHeaderFooterBlocks(renderer, drawX, drawY, width, alpha);
        renderNativeRows(renderer, drawX, drawY, width, alpha);
    }

    private void renderNativeShell(Renderer2D renderer, float x, float y, float w, float h, float alpha) {
        renderer.roundedRectShadow(x, y, w, h, 14.5f, 8f, 15f,
                HudRenderUtil.scaleAlpha(0xFF000000, 0.30f * alpha));
        renderer.roundedRectSoftShadow(x + 4f, y + 4f, Math.max(0f, w - 8f), Math.max(0f, h - 2f), 12.5f, 12f, 0.20f,
                HudRenderUtil.scaleAlpha(uiAccentGlow, 0.16f * alpha));
        renderer.roundedRectGlow(x + 2f, y + 1.5f, Math.max(0f, w - 4f), Math.max(0f, h - 3f), 13f, 0f, 8f,
                HudRenderUtil.scaleAlpha(uiAccentGlow, 0.14f * alpha));
        renderer.roundedRectGradientQuad(x, y, w, h, 14.5f,
                HudRenderUtil.scaleAlpha(uiFillHotspot, 0.96f * alpha),
                HudRenderUtil.scaleAlpha(uiFillTop, 0.96f * alpha),
                HudRenderUtil.scaleAlpha(uiFillBottom, 0.97f * alpha),
                HudRenderUtil.scaleAlpha(uiFillBottom, 0.97f * alpha));
        renderer.radialGlowMasked(x, y, w, h, 14.5f, 0f, Math.max(90f, w * 0.30f),
                x + w * 0.18f, y + 9f, HudRenderUtil.scaleAlpha(uiAccentGlow, 0.26f * alpha));
        renderer.radialGlowMasked(x, y, w, h, 14.5f, 0f, Math.max(70f, w * 0.22f),
                x + w * 0.86f, y + h - 8f, HudRenderUtil.scaleAlpha(uiAccentSoft, 0.16f * alpha));
        renderer.roundedRectStrokeGradient(x, y, w, h, 14.5f, 1.25f,
                HudRenderUtil.scaleAlpha(uiStroke, 0.82f * alpha),
                HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.48f * alpha),
                115f);

        float accentW = Math.max(62f, w * 0.34f);
        renderer.roundedRectGradient(x + 21f, y + 11f, accentW, 1.45f, 0.8f,
                HudRenderUtil.scaleAlpha(uiAccent, 0.58f * alpha),
                HudRenderUtil.scaleAlpha(uiAccentSoft, 0.10f * alpha),
                0f);
        renderer.roundedRectGradient(x + w - 14f - accentW * 0.72f, y + h - 15.5f, accentW * 0.62f, 2.5f, 1.25f,
                HudRenderUtil.scaleAlpha(uiAccentSoft, 0.06f * alpha),
                HudRenderUtil.scaleAlpha(uiAccent, 0.25f * alpha),
                0f);
    }

    private void renderNativeHeaderFooterBlocks(Renderer2D renderer, float baseX, float baseY, float width, float alpha) {
        if (!snapshot.headerLines().isEmpty() && snapshot.headerHeight() > 0f) {
            float x = baseX + 14f;
            float y = baseY + snapshot.headerTop();
            float w = width - 28f;
            float h = snapshot.headerHeight();
            renderer.roundedRectGradientQuad(x, y, w, h, 10.5f,
                    HudRenderUtil.scaleAlpha(uiRowHighlight, 0.78f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowHighlight, 0.52f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.48f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.55f * alpha));
            renderer.roundedRectStrokeGradient(x, y, w, h, 10.5f, 0.95f,
                    HudRenderUtil.scaleAlpha(uiStroke, 0.44f * alpha),
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.18f * alpha),
                    96f);
            renderer.roundedRectGradient(x + 12f, y + h - 5.5f, Math.max(30f, w * 0.20f), 1.25f, 0.65f,
                    HudRenderUtil.scaleAlpha(uiAccent, 0.44f * alpha),
                    HudRenderUtil.scaleAlpha(uiAccentSoft, 0.06f * alpha),
                    0f);
        }
        if (!snapshot.footerLines().isEmpty() && snapshot.footerHeight() > 0f) {
            float x = baseX + 14f;
            float y = baseY + snapshot.footerTop();
            float w = width - 28f;
            float h = snapshot.footerHeight();
            renderer.roundedRectGradientQuad(x, y, w, h, 10f,
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.34f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.46f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.46f * alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.34f * alpha));
            renderer.roundedRectStroke(x, y, w, h, 10f, 0f, 0.8f,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.30f * alpha));
            float accentW = Math.max(42f, w * 0.24f);
            renderer.roundedRectGradient(x + w - accentW - 12f, y + h - 5.5f, accentW, 1.15f, 0.6f,
                    HudRenderUtil.scaleAlpha(uiAccentSoft, 0.04f * alpha),
                    HudRenderUtil.scaleAlpha(uiAccent, 0.32f * alpha),
                    0f);
        }
    }

    private void renderNativeRows(Renderer2D renderer, float baseX, float baseY, float drawWidth, float panelAlpha) {
        List<RowRender> rows = collectRowRenders(baseX, baseY, drawWidth, panelAlpha);
        if (rows.isEmpty()) return;

        // OrderedUiBatcher merges only adjacent compatible backend entries. Rendering a whole row at a time
        // alternates shadow/fill/stroke/chip/circle/arc for every player and explodes the draw list. Keep
        // the same visual stack, but render the row layer as grouped passes so equal primitive types stay
        // adjacent and become real batches.
        renderRowShadows(renderer, rows);
        renderRowFills(renderer, rows);
        renderRowRelationFills(renderer, rows);
        renderRowRelationBars(renderer, rows);
        renderRowStrokes(renderer, rows);
        renderRowHeadPlates(renderer, rows);
        renderRowHeadPlateStrokes(renderer, rows);
        renderRowDividers(renderer, rows);
        renderRowSpectatorBadges(renderer, rows);
        renderScoreChipFills(renderer, rows);
        renderScoreChipStrokes(renderer, rows);
        renderPingChipFills(renderer, rows);
        renderPingChipStrokes(renderer, rows);
        renderPingChipGlows(renderer, rows);
        renderPingChipOuterDots(renderer, rows);
        renderPingChipInnerDots(renderer, rows);
        renderPingChipDotStrokes(renderer, rows);
        renderPingChipArcs(renderer, rows);
    }

    private List<RowRender> collectRowRenders(float baseX, float baseY, float drawWidth, float panelAlpha) {
        List<TabListModel.Entry> entries = snapshot.entries();
        if (entries.isEmpty() || panelAlpha <= 0.01f) return List.of();

        int rowsPerColumn = Math.max(1, snapshot.rows());
        float contentX = contentX(baseX, drawWidth);
        float rowTop = snapshot.rowTop();
        float rowHeight = snapshot.rowHeight();
        float columnStride = snapshot.columnWidth() + snapshot.columnGap();
        List<RowRender> out = new java.util.ArrayList<>(entries.size());
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
            float h = rowHeight - 5f;
            int rowFill = (row & 1) == 0 ? uiRowFill : uiRowAltFill;
            int relationColor = entry.relationColor() == 0 ? uiAccent : entry.relationColor() | 0xFF000000;
            boolean relation = entry.relation() != CategoryType.DEFAULT;
            out.add(new RowRender(entry, x, y, w, h, rowAlpha, rowFill, relationColor, relation));
        }
        return out;
    }

    private void renderRowShadows(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            renderer.roundedRectSoftShadow(r.x + 1.5f, r.y + 2f, r.w - 3f, r.h, 9.5f, 6f, 0.18f,
                    HudRenderUtil.scaleAlpha(0xFF000000, 0.14f * r.alpha));
        }
    }

    private void renderRowFills(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            renderer.roundedRectGradientQuad(r.x, r.y, r.w, r.h, 9.5f,
                    HudRenderUtil.scaleAlpha(uiRowHighlight, r.alpha),
                    HudRenderUtil.scaleAlpha(r.rowFill, r.alpha),
                    HudRenderUtil.scaleAlpha(r.rowFill, r.alpha),
                    HudRenderUtil.scaleAlpha(uiRowHighlight, r.alpha));
        }
    }

    private void renderRowRelationFills(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            if (!r.relation) continue;
            int relationFill = HudRenderUtil.scaleAlpha(r.relationColor, 0.16f * r.alpha);
            renderer.roundedRectGradientQuad(r.x + 1f, r.y + 1f, r.w - 2f, r.h - 2f, 8.5f,
                    relationFill,
                    HudRenderUtil.scaleAlpha(relationFill, 0.42f),
                    HudRenderUtil.scaleAlpha(relationFill, 0.28f),
                    HudRenderUtil.scaleAlpha(relationFill, 0.68f));
        }
    }

    private void renderRowRelationBars(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            if (!r.relation) continue;
            renderer.roundedRectGradient(r.x + 1.8f, r.y + 6f, 2.25f, r.h - 12f, 1.15f,
                    HudRenderUtil.scaleAlpha(r.relationColor, 0.62f * r.alpha),
                    HudRenderUtil.scaleAlpha(r.relationColor, 0.18f * r.alpha),
                    90f);
        }
    }

    private void renderRowStrokes(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            int strokeA = r.relation
                    ? HudRenderUtil.scaleAlpha(r.relationColor, 0.52f * r.alpha)
                    : HudRenderUtil.scaleAlpha(uiStroke, 0.40f * r.alpha);
            renderer.roundedRectStrokeGradient(r.x, r.y, r.w, r.h, 9.5f, r.relation ? 1.25f : 0.85f,
                    strokeA,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.24f * r.alpha),
                    92f);
        }
    }

    private void renderRowHeadPlates(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            float plateX = r.x + ROW_PAD_X - 3.5f;
            float plateY = r.y + (r.h - HEAD_SIZE) * 0.5f - 3.5f;
            renderer.roundedRectGradient(plateX, plateY, 37f, 37f, 7.75f,
                    HudRenderUtil.scaleAlpha(uiRowHighlight, 0.72f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiRowFill, 0.56f * r.alpha),
                    92f);
        }
    }

    private void renderRowHeadPlateStrokes(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            float plateX = r.x + ROW_PAD_X - 3.5f;
            float plateY = r.y + (r.h - HEAD_SIZE) * 0.5f - 3.5f;
            renderer.roundedRectStroke(plateX, plateY, 37f, 37f, 7.75f, 0f, 0.75f,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.30f * r.alpha));
        }
    }

    private void renderRowDividers(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            renderer.roundedRectGradient(r.x + ROW_PAD_X + HEAD_SIZE + 10.5f, r.y + 8.5f, 1.15f, Math.max(6f, r.h - 17f), 0.6f,
                    HudRenderUtil.scaleAlpha(uiAccentSoft, 0.20f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiDivider, r.alpha),
                    90f);
        }
    }

    private void renderRowSpectatorBadges(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            if (!r.entry.spectator()) continue;
            renderer.roundedRectGradient(r.x + r.w - 57f, r.y + 8.5f, 19f, 4.5f, 2.25f,
                    HudRenderUtil.scaleAlpha(uiMuted, 0.40f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiMuted, 0.08f * r.alpha),
                    0f);
        }
    }

    private void renderScoreChipFills(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            if (r.entry.scoreText() == null) continue;
            ScoreChip chip = scoreChip(r);
            renderer.roundedRectGradient(chip.x, chip.y, chip.w, chip.h, 7.25f,
                    HudRenderUtil.scaleAlpha(uiPingChip, 0.82f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiPingChip, 0.50f * r.alpha),
                    90f);
        }
    }

    private void renderScoreChipStrokes(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            if (r.entry.scoreText() == null) continue;
            ScoreChip chip = scoreChip(r);
            renderer.roundedRectStroke(chip.x, chip.y, chip.w, chip.h, 7.25f, 0f, 0.75f,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.30f * r.alpha));
        }
    }

    private void renderPingChipFills(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.roundedRectGradient(chip.x, chip.y, chip.w, chip.h, 7.5f,
                    HudRenderUtil.scaleAlpha(uiPingChip, 0.84f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiPingChip, 0.52f * r.alpha),
                    90f);
        }
    }

    private void renderPingChipStrokes(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.roundedRectStrokeGradient(chip.x, chip.y, chip.w, chip.h, 7.5f, 0.8f,
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.40f * r.alpha),
                    HudRenderUtil.scaleAlpha(uiStrokeSoft, 0.18f * r.alpha),
                    0f);
        }
    }

    private void renderPingChipGlows(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.circleSoftShadow(chip.cx, chip.cy, 5.4f, 7f, 0.34f,
                    HudRenderUtil.scaleAlpha(pingBaseColor(r.entry.latency()), 0.48f * r.alpha));
        }
    }

    private void renderPingChipOuterDots(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.circle(chip.cx, chip.cy, 4.4f, 0.95f, HudRenderUtil.scaleAlpha(pingBaseColor(r.entry.latency()), 0.30f * r.alpha));
        }
    }

    private void renderPingChipInnerDots(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.circle(chip.cx, chip.cy, 3.35f, 0.9f, HudRenderUtil.scaleAlpha(pingBaseColor(r.entry.latency()), r.alpha));
        }
    }

    private void renderPingChipDotStrokes(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            renderer.circleStroke(chip.cx, chip.cy, 4.15f, 0.7f, 0.8f,
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.36f * r.alpha));
        }
    }

    private void renderPingChipArcs(Renderer2D renderer, List<RowRender> rows) {
        for (RowRender r : rows) {
            PingChip chip = pingChip(r);
            int base = pingBaseColor(r.entry.latency());
            renderer.arcStrokeGradient(chip.cx, chip.cy, 5.6f, 1.25f, -140f, 70f, 0.45f,
                    HudRenderUtil.scaleAlpha(base, 0.68f * r.alpha),
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.16f * r.alpha),
                    35f);
        }
    }

    private ScoreChip scoreChip(RowRender r) {
        float pingChipW = r.entry.pingChipWidth();
        float occupiedLeft = r.x + r.w - 10.5f - pingChipW - 10f;
        float scoreBoxW = r.entry.scoreBoxWidth();
        float scoreBoxH = 23f;
        float scoreX = occupiedLeft - scoreBoxW - 8f;
        float scoreY = r.y + (r.h - scoreBoxH) * 0.5f;
        return new ScoreChip(scoreX, scoreY, scoreBoxW, scoreBoxH);
    }

    private PingChip pingChip(RowRender r) {
        float chipW = r.entry.pingChipWidth();
        float chipH = 22.5f;
        float chipX = r.x + r.w - 10.5f - chipW;
        float chipY = r.y + (r.h - chipH) * 0.5f;
        float cx = chipX + 10.2f;
        float cy = chipY + chipH * 0.5f;
        return new PingChip(chipX, chipY, chipW, chipH, cx, cy);
    }

    private void renderHeadsAndText(GuiGraphicsExtractor ctx, Renderer2D renderer, float baseX, float baseY, float drawWidth, float panelAlpha) {
        float rowTop = snapshot.rowTop();
        float textH = TabRichTextRenderer.height(TEXT_SIZE);
        float contentX = contentX(baseX, drawWidth);
        renderHeaderText(renderer, baseX, baseY, drawWidth, panelAlpha);

        for (int i = 0; i < snapshot.entries().size(); i++) {
            TabListModel.Entry entry = snapshot.entries().get(i);
            TabListAnimator.RowState state = animator.row(entry.id());
            float rowAlpha = panelAlpha * AnimationUtility.easeOutCubic(state.presence);
            if (rowAlpha <= 0.01f) continue;

            int col = i / Math.max(1, snapshot.rows());
            int row = i % Math.max(1, snapshot.rows());
            float rowX = contentX + col * (snapshot.columnWidth() + snapshot.columnGap());
            float rowY = baseY + rowTop + row * snapshot.rowHeight() + (1f - state.presence) * 10.0f;
            float rowH = snapshot.rowHeight() - 5f;
            float headX = rowX + ROW_PAD_X;
            float headY = rowY + (rowH - HEAD_SIZE) * 0.5f;
            int alpha = Math.max(0, Math.min(255, Math.round(255f * rowAlpha)));

            if (entry.skin() != null) {
                PlayerHeadRenderer.drawRounded(
                        ctx,
                        headX,
                        headY,
                        HEAD_SIZE,
                        5.25f,
                        entry.skin(),
                        new RenderColor(255, 255, 255, alpha),
                        true,
                        new RenderColor(255, 255, 255, Math.round(42f * rowAlpha)),
                        1.2f,
                        false
                );
            }
        }

        renderPingTexts(renderer, baseX, baseY, drawWidth, panelAlpha);
        renderScoreTexts(renderer, baseX, baseY, drawWidth, panelAlpha);
        renderNameTexts(renderer, baseX, baseY, drawWidth, panelAlpha, textH);
        renderFooterText(renderer, baseX, baseY, drawWidth, panelAlpha);
    }

    private void renderPingTexts(Renderer2D renderer, float baseX, float baseY, float drawWidth, float panelAlpha) {
        float rowTop = snapshot.rowTop();
        float contentX = contentX(baseX, drawWidth);
        float pingTextH = TabRichTextRenderer.height(PING_TEXT_SIZE);
        for (int i = 0; i < snapshot.entries().size(); i++) {
            TabListModel.Entry entry = snapshot.entries().get(i);
            TabListAnimator.RowState state = animator.row(entry.id());
            float rowAlpha = panelAlpha * AnimationUtility.easeOutCubic(state.presence);
            if (rowAlpha <= 0.01f) continue;
            int col = i / Math.max(1, snapshot.rows());
            int row = i % Math.max(1, snapshot.rows());
            float rowX = contentX + col * (snapshot.columnWidth() + snapshot.columnGap());
            float rowY = baseY + rowTop + row * snapshot.rowHeight() + (1f - state.presence) * 10.0f;
            float rowH = snapshot.rowHeight() - 5f;
            float right = rowX + snapshot.columnWidth() - 10.5f;
            float pingChipW = entry.pingChipWidth();
            float pingChipH = 22.5f;
            float pingChipX = right - pingChipW;
            float pingChipY = rowY + (rowH - pingChipH) * 0.5f;
            TabRichTextRenderer.drawPlain(renderer, entry.pingText(), pingChipX + 19.5f,
                    pingChipY + (pingChipH - pingTextH) * 0.5f - 0.25f,
                    pingChipW - 22f, PING_TEXT_SIZE, uiText, rowAlpha);
        }
    }

    private void renderScoreTexts(Renderer2D renderer, float baseX, float baseY, float drawWidth, float panelAlpha) {
        float rowTop = snapshot.rowTop();
        float contentX = contentX(baseX, drawWidth);
        float scoreTextH = TabRichTextRenderer.height(SCORE_TEXT_SIZE);
        for (int i = 0; i < snapshot.entries().size(); i++) {
            TabListModel.Entry entry = snapshot.entries().get(i);
            if (entry.scoreText() == null) continue;
            TabListAnimator.RowState state = animator.row(entry.id());
            float rowAlpha = panelAlpha * AnimationUtility.easeOutCubic(state.presence);
            if (rowAlpha <= 0.01f) continue;
            int col = i / Math.max(1, snapshot.rows());
            int row = i % Math.max(1, snapshot.rows());
            float rowX = contentX + col * (snapshot.columnWidth() + snapshot.columnGap());
            float rowY = baseY + rowTop + row * snapshot.rowHeight() + (1f - state.presence) * 10.0f;
            float rowH = snapshot.rowHeight() - 5f;
            float right = rowX + snapshot.columnWidth() - 10.5f;
            float occupiedLeft = right - entry.pingChipWidth() - 10f;
            float scoreBoxW = entry.scoreBoxWidth();
            float scoreBoxH = 23f;
            float scoreX = occupiedLeft - scoreBoxW - 8f;
            float scoreY = rowY + (rowH - scoreBoxH) * 0.5f;
            TabRichTextRenderer.draw(renderer, entry.scoreText(), scoreX + 8f,
                    scoreY + (scoreBoxH - scoreTextH) * 0.5f - 0.5f,
                    entry.scoreWidth() + 2f, SCORE_TEXT_SIZE, uiText, rowAlpha);
        }
    }

    private void renderNameTexts(Renderer2D renderer, float baseX, float baseY, float drawWidth, float panelAlpha, float textH) {
        float rowTop = snapshot.rowTop();
        float contentX = contentX(baseX, drawWidth);
        for (int i = 0; i < snapshot.entries().size(); i++) {
            TabListModel.Entry entry = snapshot.entries().get(i);
            TabListAnimator.RowState state = animator.row(entry.id());
            float rowAlpha = panelAlpha * AnimationUtility.easeOutCubic(state.presence);
            if (rowAlpha <= 0.01f) continue;
            int col = i / Math.max(1, snapshot.rows());
            int row = i % Math.max(1, snapshot.rows());
            float rowX = contentX + col * (snapshot.columnWidth() + snapshot.columnGap());
            float rowY = baseY + rowTop + row * snapshot.rowHeight() + (1f - state.presence) * 10.0f;
            float rowH = snapshot.rowHeight() - 5f;
            float right = rowX + snapshot.columnWidth() - 10.5f;
            float pingChipX = right - entry.pingChipWidth();
            float occupiedLeft = pingChipX - 10f;
            if (entry.scoreText() != null) {
                occupiedLeft = occupiedLeft - entry.scoreBoxWidth() - 18f;
            }
            float textX = rowX + ROW_PAD_X + HEAD_SIZE + 22f;
            float textY = rowY + (rowH - textH) * 0.5f - 1.0f;
            float textW = Math.max(40f, occupiedLeft - textX);
            int baseColor = entry.spectator() ? uiMuted : uiText;
            TabRichTextRenderer.draw(renderer, entry.displayName(), textX, textY, textW, TEXT_SIZE,
                    baseColor, rowAlpha);
        }
    }

    private void renderHeaderText(Renderer2D renderer, float baseX, float baseY, float drawWidth, float alpha) {
        if (snapshot.headerLines().isEmpty() || alpha <= 0.01f) return;
        float lineH = 29f;
        float y = baseY + snapshot.headerTop() + 8f + (lineH - TabRichTextRenderer.height(HEADER_TEXT_SIZE)) * 0.5f;
        for (Component line : snapshot.headerLines()) {
            drawCentered(renderer, line, baseX + drawWidth * 0.5f, y,
                    drawWidth - 56f, HEADER_TEXT_SIZE, 0xFFFFFFFF, alpha);
            y += lineH;
        }
    }

    private void renderFooterText(Renderer2D renderer, float baseX, float baseY, float drawWidth, float alpha) {
        if (snapshot.footerLines().isEmpty() || alpha <= 0.01f) return;
        float lineH = 25f;
        float y = baseY + snapshot.footerTop() + 7f + (lineH - TabRichTextRenderer.height(FOOTER_TEXT_SIZE)) * 0.5f;
        for (Component line : snapshot.footerLines()) {
            drawCentered(renderer, line, baseX + drawWidth * 0.5f, y, drawWidth - 56f, FOOTER_TEXT_SIZE, 0xFFFFFFFF, alpha);
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

    private void renderFallbackShell(Renderer2D renderer, float drawX, float drawY, float width, float height, float alpha) {
        if (alpha <= 0.01f) return;
        renderer.roundedRect(drawX, drawY, width, height, 14.5f, 0.95f,
                HudRenderUtil.scaleAlpha(uiFillTop, 0.96f * alpha));
        renderer.roundedRectStroke(drawX, drawY, width, height, 14.5f, 0.95f, 1.25f,
                HudRenderUtil.scaleAlpha(uiStroke, 0.82f * alpha));
    }


    private record RowRender(TabListModel.Entry entry,
                             float x,
                             float y,
                             float w,
                             float h,
                             float alpha,
                             int rowFill,
                             int relationColor,
                             boolean relation) {
    }

    private record ScoreChip(float x, float y, float w, float h) {
    }

    private record PingChip(float x, float y, float w, float h, float cx, float cy) {
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
            uiFillHotspot = HudRenderUtil.mixColor(uiFillTop, theme().accentSoft(), 0.12f);
            uiStroke = theme().windowStroke();
            uiStrokeSoft = theme().strokeSoft();
            uiAccent = theme().accent();
            uiAccentSoft = theme().accentSoft();
            uiAccentGlow = HudRenderUtil.setAlpha(theme().accent(), 255);
            uiText = theme().textPrimary();
            uiMuted = theme().textMuted();
            uiRowFill = HudRenderUtil.setAlpha(theme().surface(), 122);
            uiRowAltFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(theme().surface(), 0xFFFFFF, 0.026f), 100);
            uiRowHighlight = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(theme().surface(), 0xFFFFFF, 0.070f), 130);
            uiPingChip = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(theme().surface(), 0x000000, 0.05f), 128);
            uiDivider = HudRenderUtil.setAlpha(theme().strokeSoft(), 96);
            return;
        }

        int baseBg = bgColor.getArgb();
        uiFillTop = HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.060f);
        uiFillBottom = HudRenderUtil.mixRgb(baseBg, 0x000000, 0.105f);
        uiAccent = accentColor.getArgb() | 0xFF000000;
        uiAccentSoft = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiAccent, baseBg, 0.35f), 0.55f);
        uiAccentGlow = HudRenderUtil.setAlpha(uiAccent, 255);
        uiFillHotspot = HudRenderUtil.mixColor(uiFillTop, uiAccentSoft, 0.12f);
        uiText = textColor.getArgb() | 0xFF000000;
        uiMuted = mutedColor.getArgb() | 0xFF000000;
        uiStroke = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiAccent, 0xFFFFFFFF, 0.18f), 0.62f);
        uiStrokeSoft = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiMuted, baseBg, 0.42f), 0.42f);
        uiRowFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.045f), 122);
        uiRowAltFill = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.024f), 100);
        uiRowHighlight = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFFFFFF, 0.075f), 130);
        uiPingChip = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0x000000, 0.06f), 128);
        uiDivider = HudRenderUtil.setAlpha(uiMuted, 72);
    }
}
