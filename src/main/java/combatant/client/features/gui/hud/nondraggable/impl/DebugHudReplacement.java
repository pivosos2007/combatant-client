/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combatant-owned presentation for the vanilla debug text columns.
 *
 * <p>The vanilla DebugScreenOverlay remains the data source. DebugHudMixin intercepts only its
 * line presentation, so every vanilla/debug-entry line continues to be assembled by Minecraft,
 * including third-party debug entries. We then replay those lines in logical HUD space on the
 * absolute top client layer.</p>
 */
public enum DebugHudReplacement {
    ;

    private static final float FONT_SCALE = 0.90f;
    private static final float MARGIN_X = 8.0f;
    private static final float MARGIN_Y = 8.0f;
    private static final float LINE_GAP = 1.0f;
    private static final float BACKGROUND_PAD_X = 3.0f;
    private static final float BACKGROUND_PAD_Y = 1.0f;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int BACKGROUND_COLOR = 0x78000000;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    public static void beginExtractFrame() {
        snapshot = Snapshot.EMPTY;
    }

    public static void captureLines(List<String> lines, boolean leftAligned) {
        List<String> copy = immutableCopy(lines);
        Snapshot current = snapshot;
        snapshot = leftAligned
                ? new Snapshot(copy, current.right())
                : new Snapshot(current.left(), copy);
    }

    public static void renderTopLayer(GuiGraphicsExtractor ctx) {
        if (ctx == null || !RuntimeGate.canRunHud() || RuntimeGate.isPanic()) return;

        Snapshot captured = snapshot;
        if (captured.isEmpty()) return;

        CombatantRenderSystem.ensureFrameContext();
        Renderer2D.withDeferredLayer(Renderer2D.Deferred2DLayer.SCREEN_ABSOLUTE_TOP, () -> {
            ViewportContext.beginUnscaledLogical(ctx);
            try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:debug_hud")) {
                render(captured);
            } finally {
                ViewportContext.end(ctx);
            }
        });
    }

    private static void render(Snapshot captured) {
        TextRenderer fallback = TextRenderer.get();
        TextRenderer text = Fonts.renderer("montserrat", FontInfo.Type.Regular, fallback);
        if (text == null) text = fallback;

        ViewportContext viewport = ViewportContext.current();
        float screenWidth = viewport != null ? viewport.width() : 1920.0f;

        List<MeasuredLine> left;
        List<MeasuredLine> right;
        float lineHeight;

        text.begin(FONT_SCALE, false, false);
        try {
            lineHeight = (float) Math.ceil(text.getHeight(true) + LINE_GAP);
            left = measure(text, captured.left());
            right = measure(text, captured.right());
        } finally {
            text.end();
        }

        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        drawBackgrounds(renderer, left, true, screenWidth, lineHeight);
        drawBackgrounds(renderer, right, false, screenWidth, lineHeight);
        renderer.render();

        text.begin(FONT_SCALE, false, false);
        try {
            drawTextColumn(text, left, true, screenWidth, lineHeight);
            drawTextColumn(text, right, false, screenWidth, lineHeight);
        } finally {
            text.end();
        }
    }

    private static List<MeasuredLine> measure(TextRenderer text, List<String> lines) {
        if (lines.isEmpty()) return List.of();
        List<MeasuredLine> measured = new ArrayList<>(lines.size());
        for (String line : lines) {
            String safe = line != null ? line : "";
            String visible = stripLegacyFormatting(safe);
            double width = visible.isEmpty() ? 0.0 : text.getWidth(visible, true);
            measured.add(new MeasuredLine(safe, width));
        }
        return measured;
    }

    private static void drawBackgrounds(Renderer2D renderer,
                                        List<MeasuredLine> lines,
                                        boolean leftAligned,
                                        float screenWidth,
                                        float lineHeight) {
        for (int i = 0; i < lines.size(); i++) {
            MeasuredLine line = lines.get(i);
            if (line.width() <= 0.0) continue;

            double x = leftAligned
                    ? MARGIN_X
                    : screenWidth - MARGIN_X - line.width();
            double y = MARGIN_Y + i * lineHeight;
            renderer.quad(
                    x - BACKGROUND_PAD_X,
                    y - BACKGROUND_PAD_Y,
                    line.width() + BACKGROUND_PAD_X * 2.0,
                    Math.max(1.0, lineHeight),
                    BACKGROUND_COLOR
            );
        }
    }

    private static void drawTextColumn(TextRenderer text,
                                       List<MeasuredLine> lines,
                                       boolean leftAligned,
                                       float screenWidth,
                                       float lineHeight) {
        for (int i = 0; i < lines.size(); i++) {
            MeasuredLine line = lines.get(i);
            if (line.width() <= 0.0) continue;

            double x = leftAligned
                    ? MARGIN_X
                    : screenWidth - MARGIN_X - line.width();
            double y = MARGIN_Y + i * lineHeight;
            renderLegacyLine(text, line.raw(), x, y);
        }
    }

    private static void renderLegacyLine(TextRenderer text, String raw, double x, double y) {
        if (raw == null || raw.isEmpty()) return;

        int color = TEXT_COLOR;
        StringBuilder run = new StringBuilder(raw.length());
        double cursor = x;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00A7' && i + 1 < raw.length()) {
                if (!run.isEmpty()) {
                    cursor = text.render(run.toString(), cursor, y, new RenderColor(color), true);
                    run.setLength(0);
                }
                color = applyLegacyColor(raw.charAt(++i), color);
                continue;
            }
            run.append(c);
        }

        if (!run.isEmpty()) {
            text.render(run.toString(), cursor, y, new RenderColor(color), true);
        }
    }

    private static int applyLegacyColor(char code, int current) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            case 'r' -> TEXT_COLOR;
            default -> current;
        };
    }

    private static String stripLegacyFormatting(String raw) {
        if (raw == null || raw.indexOf('\u00A7') < 0) return raw != null ? raw : "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00A7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<String> copy = new ArrayList<>(source.size());
        for (String line : source) {
            copy.add(line != null ? line : "");
        }
        return Collections.unmodifiableList(copy);
    }

    private record MeasuredLine(String raw, double width) {
    }

    private record Snapshot(List<String> left, List<String> right) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of());

        private boolean isEmpty() {
            return left.isEmpty() && right.isEmpty();
        }
    }
}
