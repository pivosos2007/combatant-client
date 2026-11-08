/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.sections;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.features.map.info.MapInfoSource;
import combatant.client.features.map.info.MapInfoChunkColors;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.RuntimeGate;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.map.MapRect;
import combatant.client.render.map.MapChunkAreaRenderer;
import combatant.client.render.map.MapScreenPoint;
import combatant.client.render.map.MapViewport;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map-owned presentation layer. ESP modules expose existing detections through MapInfoSource;
 * this layer alone decides spatial aggregation, panel sizing and hit testing.
 */
final class MapInfoOverlay {
    private static final float HEADER = 26.0f;
    private static final float ROW = 21.0f;
    private static final float BOTTOM = 5.0f;
    private static final float ICON_SIZE = 16.0f;
    private static final float PAD = 7.0f;

    private final Map<Cell, Group> groups = new HashMap<>();
    private final MapInfoChunkColors chunkColors = new MapInfoChunkColors();
    private final List<Card> cards = new ArrayList<>();
    private final Map<Cell, Integer> scrollOffsets = new HashMap<>();
    private boolean inputEnabled;

    void reset() {
        groups.clear();
        chunkColors.clear();
        cards.clear();
        scrollOffsets.clear();
    }

    void render(MapViewport viewport, float mouseX, float mouseY, boolean allowInput) {
        cards.clear();
        MapUiConfig config = MapUiConfig.get();
        inputEnabled = allowInput && config.mapInfoEnabled();
        if (!config.mapInfoEnabled()) return;
        MapRect screen = viewport.screenBounds();
        if (screen.width() < 90.0 || screen.height() < 65.0) return;

        // One cell is at least one chunk. The next power of two groups nearby chunks when
        // their projected area cannot fit a panel. Zooming in naturally splits cells again.
        int chunksPerCell = 1;
        double chunkPixels = viewport.pixelsPerBlock() * 16.0;
        int minCellPixels = Math.max(config.mapInfoClusterPixels(),
                Math.max(config.mapInfoCardWidth() + 8,
                        (int) (HEADER + Math.min(config.mapInfoRows(), 14) * ROW + BOTTOM + 10)));
        while (chunksPerCell < 2048 && chunksPerCell * chunkPixels < minCellPixels) {
            chunksPerCell <<= 1;
        }
        final int cellChunks = chunksPerCell;
        final int cellBlocks = cellChunks * 16;
        MapRect visible = viewport.visibleWorldBounds();
        groups.clear();
        chunkColors.clear();
        if (!RuntimeGate.canRunModules()) return;
        // Any enabled module implementing MapInfoSource joins automatically. No registration or
        // map-specific module wiring is needed for future integrations.
        for (Module module : ModuleManager.getModules()) {
            if (!(module instanceof MapInfoSource source) || !module.isEnabled()) continue;
            String sourceId = module.name();
            String sourceLabel = module.getDisplayName();
            source.collectMapInfo((x, z, key, label, icon, argb, count) -> {
                if (count <= 0 || key == null || label == null) return;
                int cellX = Math.floorDiv(x, cellBlocks);
                int cellZ = Math.floorDiv(z, cellBlocks);
                // Include every detection from a cell intersecting the viewport, but never
                // draw labels for off-screen cells (including the one-cell padding).
                double left = (double) cellX * cellBlocks;
                double top = (double) cellZ * cellBlocks;
                if (left > visible.maxX() || top > visible.maxY()
                        || left + cellBlocks < visible.x() || top + cellBlocks < visible.y()) return;
                // Highlight the exact occupied chunk, not the whole zoomed-out cell.
                // This is independent of how many cards can be drawn on screen.
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if ((chunkX + 1.0) * 16 >= visible.x() && chunkX * 16.0 <= visible.maxX()
                        && (chunkZ + 1.0) * 16 >= visible.y() && chunkZ * 16.0 <= visible.maxY()) {
                    chunkColors.add(x, z, sourceId, argb, count);
                }
                Cell cell = new Cell(cellX, cellZ, cellChunks);
                Group group = groups.computeIfAbsent(cell, Group::new);
                String itemKey = sourceId + ':' + key;
                RowData row = group.rows.get(itemKey);
                if (row == null) group.rows.put(itemKey,
                        new RowData(sourceLabel, label, icon, argb, count));
                else row.count += count;
                group.total += count;
            });
        }
        if (groups.isEmpty()) return;

        float panelWidth = (float) Math.min(config.mapInfoCardWidth(), Math.max(88.0, screen.width() - 12.0));
        int panelMaxRows = Math.max(1, Math.min(config.mapInfoRows(),
                (int) Math.floor((screen.height() - 16.0f - HEADER - BOTTOM) / ROW)));
        ArrayList<Group> ordered = new ArrayList<>(groups.values());
        // Stable near-center priority when the viewport has more data than visible card capacity.
        ordered.sort(Comparator.<Group>comparingDouble(group -> {
            double dx = (group.cell.x() + 0.5) * cellBlocks - viewport.centerX();
            double dz = (group.cell.z() + 0.5) * cellBlocks - viewport.centerZ();
            return dx * dx + dz * dz;
        }).thenComparingInt(group -> group.cell.x()).thenComparingInt(group -> group.cell.z()));
        int limit = Math.min(config.mapInfoMaxCards(), ordered.size());
        for (int i = 0; i < limit; i++) {
            Group group = ordered.get(i);
            List<RowData> rows = new ArrayList<>(group.rows.values());
            rows.sort(Comparator.comparingInt((RowData row) -> row.count).reversed()
                    .thenComparing(row -> row.label).thenComparing(row -> row.source));
            int visibleRows = Math.min(panelMaxRows, rows.size());
            float height = HEADER + visibleRows * ROW + BOTTOM;
            MapScreenPoint anchor = viewport.project(
                    (group.cell.x() + 0.5) * cellBlocks,
                    (group.cell.z() + 0.5) * cellBlocks);
            float x = clamp((float) anchor.x() - panelWidth * 0.5f,
                    (float) screen.x() + 4.0f,
                    (float) screen.maxX() - panelWidth - 4.0f);
            float y = clamp((float) anchor.y() - height * 0.5f,
                    (float) screen.y() + 4.0f,
                    (float) screen.maxY() - height - 4.0f);
            if (x < screen.x() || y < screen.y()) continue;
            cards.add(new Card(group.cell, rows, group.total, x, y, panelWidth, height, visibleRows));
        }

        boolean clipped = ScissorFunction.pushRaw((float) screen.x(), (float) screen.y(),
                (float) screen.width(), (float) screen.height());
        if (!clipped) return;
        try {
            // Occupancy tints are permanent and exact per chunk, even if its zoomed-out
            // aggregation panel was omitted by the maximum-panel limit. Multiple ESPs
            // in the same chunk contribute one equal-weight source color each.
            // The existing right-click selection is drawn by XaeroMapSurface AFTER
            // this whole layer, so it retains the highest visual/input priority.
            if (config.mapInfoChunkHighlights() && config.mapInfoHighlightIntensity() > 0) {
                float intensity = config.mapInfoHighlightIntensity() / 100.0f;
                int fillAlpha = Math.round(0x30 * intensity);
                int edgeAlpha = Math.round(0xC0 * intensity);
                // Chunk borders below ~3 screen pixels become a solid mass; retain
                // the occupied-cell fill only at that zoom level.
                int strokeAlpha = chunkPixels >= 3.0f ? edgeAlpha : 0;
                chunkColors.forEach((chunkX, chunkZ, color) ->
                        MapChunkAreaRenderer.draw(viewport, chunkX, chunkZ,
                                chunkX, chunkZ, color, fillAlpha, strokeAlpha, 1.0f));
            }
            // Nearer groups render last, making them legible at cell boundaries.
            for (int i = cards.size() - 1; i >= 0; i--) drawCard(cards.get(i), mouseX, mouseY, config);
        } finally {
            ScissorFunction.pop();
        }
        // Keep only offsets for currently visible cells, avoiding stale offset growth after panning.
        scrollOffsets.keySet().retainAll(groups.keySet());
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!inputEnabled || amount == 0) return false;
        // Scroll the topmost panel under the cursor. Never steal wheel input from the map when
        // there is no overflowing list at that point.
        for (Card card : cards) {
            if (!card.contains(mouseX, mouseY) || card.rows.size() <= card.visibleRows) continue;
            int max = card.rows.size() - card.visibleRows;
            int current = scrollOffsets.getOrDefault(card.cell, 0);
            int next = Math.max(0, Math.min(max, current - (int) Math.signum(amount) *
                    Math.max(1, (int) Math.round(Math.abs(amount)))));
            scrollOffsets.put(card.cell, next);
            return true;
        }
        return false;
    }

    private void drawCard(Card card, float mouseX, float mouseY, MapUiConfig config) {
        Renderer2D renderer = Renderer2D.COLOR;
        boolean hovered = inputEnabled && card.contains(mouseX, mouseY);
        // DropESP's Matte presentation: dark translucent compact label, 3.6 px radius.
        int alpha = Math.round((hovered ? 170.0f : 148.0f) * config.mapInfoBackground() / 100.0f);
        renderer.roundedRect(card.x, card.y, card.width, card.height, 3.6f, 0.0f,
                (alpha << 24) | 0x00080A0E);
        renderer.quad(card.x + PAD, card.y + HEADER - 1.0f,
                card.width - PAD * 2.0f, 1.0f, 0x38FFFFFF);
        String title = card.cell.size == 1
                ? tr("gui.combatant.map.info.chunk", "Chunk") + " " + card.cell.x + ", " + card.cell.z
                : card.cell.size + "×" + card.cell.size + " " + tr("gui.combatant.map.info.chunks", "chunks");
        String total = Integer.toString(card.total);
        float countWidth = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), total, 12.0f);
        drawText(ellipsize(title, card.width - PAD * 3.0f - countWidth, 12.0f),
                card.x + PAD, card.y + 6.0f, 12.0f, Theme.theme().textPrimary());
        drawText(total, card.x + card.width - PAD - countWidth, card.y + 6.0f, 12.0f, 0xFFD8DEE7);

        int offset = Math.min(scrollOffsets.getOrDefault(card.cell, 0), Math.max(0, card.rows.size() - card.visibleRows));
        boolean listClip = ScissorFunction.pushRaw(card.x + 2.0f, card.y + HEADER,
                card.width - 4.0f, card.visibleRows * ROW);
        if (!listClip) return;
        try {
            for (int i = 0; i < card.visibleRows; i++) {
                RowData row = card.rows.get(i + offset);
                float rowY = card.y + HEADER + i * ROW;
                renderer.roundedRect(card.x + PAD, rowY + 7.0f, 2.0f, 8.0f,
                        1.0f, 0.0f, 0xFF000000 | (row.color & 0xFFFFFF));
                float textX = card.x + PAD + 7.0f;
                if (config.mapInfoIcons() && row.icon != null) {
                    renderer.item(new ItemStack(row.icon), textX, rowY + 2.0f,
                            0.9375f, 0, Renderer2D.ITEM_OVERLAY_NONE, null);
                    textX += ICON_SIZE + 2.0f;
                }
                String countText = "×" + row.count;
                float rowCountWidth = LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), countText, 11.5f);
                float textMax = card.x + card.width - PAD - rowCountWidth - 5.0f;
                String fullLabel = config.mapInfoSources() ? row.source + " · " + row.label : row.label;
                String label = ellipsize(fullLabel, Math.max(0.0f, textMax - textX), 11.5f);
                drawText(label, textX, rowY + 3.0f, 11.5f, 0xFFE9EEF5);
                drawText(countText, card.x + card.width - PAD - rowCountWidth,
                        rowY + 3.0f, 11.5f, 0xFFCDD4DD);
            }
            if (card.rows.size() > card.visibleRows) {
                float track = card.visibleRows * ROW - 4.0f;
                float thumb = Math.max(8.0f, track * card.visibleRows / card.rows.size());
                float y = card.y + HEADER + 2.0f
                        + (track - thumb) * offset / (card.rows.size() - card.visibleRows);
                renderer.roundedRect(card.x + card.width - 3.5f, y, 2.0f, thumb,
                        1.0f, 0.0f, 0xAAFFFFFF);
            }
        } finally {
            ScissorFunction.pop();
        }
    }

    private static String ellipsize(String text, float width, float fontSize) {
        if (LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text, fontSize) <= width) return text;
        String dots = "…";
        if (LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), dots, fontSize) > width) return "";
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (LayoutRender2D.textWidth(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text.substring(0, mid) + dots, fontSize) <= width) lo = mid;
            else hi = mid - 1;
        }
        return text.substring(0, lo) + dots;
    }

    private static void drawText(String text, float x, float y, float size, int color) {
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), text, x, y, size, color, false);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String tr(String key, String fallback) {
        String translated = I18n.get(key, "");
        return translated == null || translated.isBlank() || translated.equals(key)
                || translated.startsWith("Format error:") ? fallback : translated;
    }

    private record Cell(int x, int z, int size) {}

    private static final class Group {
        final Cell cell;
        final Map<String, RowData> rows = new HashMap<>();
        int total;
        Group(Cell cell) { this.cell = cell; }
    }

    private static final class RowData {
        final String source;
        final String label;
        final Item icon;
        final int color;
        int count;
        RowData(String source, String label, Item icon, int color, int count) {
            this.source = source;
            this.label = label;
            this.icon = icon;
            this.color = color;
            this.count = count;
        }
    }

    private record Card(Cell cell, List<RowData> rows, int total, float x, float y,
                        float width, float height, int visibleRows) {
        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
}
