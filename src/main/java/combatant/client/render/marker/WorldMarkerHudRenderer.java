/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.marker;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.TextSizing;
import combatant.client.util.text.TextRenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral compact world marker HUD renderer.
 *
 * <p>The renderer owns only presentation/layout. Callers provide projected screen anchors, styled
 * title parts, optional secondary text and an accent. This keeps Xaero, MapLink and triangulation
 * sources on the same visual contract without coupling the renderer to any of those backends.</p>
 */
public enum WorldMarkerHudRenderer {
    ;

    private static final float PAD_X = 4.25f;
    private static final float PAD_Y = 2.75f;
    private static final float ICON_TEXT_GAP = 3.25f;
    private static final float META_GAP = 5.0f;
    private static final float ANCHOR_Y_BIAS = -1.5f;
    private static final float MIN_HEIGHT = 20.0f;
    private static final float MATTE_RADIUS = 3.6f;
    private static final int MATTE_BACKDROP_ALPHA = 148;

    public static List<Layout> layout(List<Marker> markers, TextRenderer fallback) {
        if (markers == null || markers.isEmpty() || fallback == null) return List.of();

        TextRenderer titleRegular = BuiltinFontCatalog.ONEST_BOLD.renderer(fallback);
        TextRenderer titleBold = BuiltinFontCatalog.ONEST_BOLD.renderer(titleRegular);
        TextRenderer metaFont = BuiltinFontCatalog.ONEST_BOLD.renderer(titleRegular);
        List<Layout> layouts = new ArrayList<>(markers.size());

        for (Marker marker : markers) {
            if (marker == null || marker.opacity() <= 0.001f) continue;
            float iconSize = Math.max(8.0f, marker.iconSize());
            float titleSize = Math.max(7.0f, marker.titleSize());
            float metaSize = Math.max(6.5f, marker.metaSize());
            float titleWidth = marker.titleParts().isEmpty()
                    ? 0.0f
                    : measureStyledWidth(titleRegular, titleBold, marker.titleParts(), titleSize);
            float metaWidth = marker.meta().isBlank()
                    ? 0.0f
                    : measureWidth(metaFont, marker.meta(), metaSize);
            float titleHeight = marker.titleParts().isEmpty() ? 0.0f : measureHeight(titleRegular, titleSize);
            float metaHeight = marker.meta().isBlank() ? 0.0f : measureHeight(metaFont, metaSize);

            boolean hasTitle = titleWidth > 0.0f;
            boolean hasMeta = metaWidth > 0.0f;
            float textWidth = titleWidth + (hasTitle && hasMeta ? META_GAP : 0.0f) + metaWidth;
            float contentWidth = iconSize + (textWidth > 0.0f ? ICON_TEXT_GAP + textWidth : 0.0f);
            float contentHeight = Math.max(iconSize, Math.max(titleHeight, metaHeight));
            float plateHeight = Math.max(MIN_HEIGHT, contentHeight + PAD_Y * 2.0f);
            float plateWidth = PAD_X * 2.0f + contentWidth;
            float plateX = marker.anchorX() - plateWidth * 0.5f;
            float plateY = marker.anchorY() - plateHeight * 0.5f + ANCHOR_Y_BIAS;
            float iconX = plateX + PAD_X;
            float iconY = plateY + (plateHeight - iconSize) * 0.5f;
            float textX = iconX + iconSize + ICON_TEXT_GAP;
            float titleY = plateY + (plateHeight - titleHeight) * 0.5f;
            float metaX = textX + titleWidth + (hasTitle && hasMeta ? META_GAP : 0.0f);
            float metaY = plateY + (plateHeight - metaHeight) * 0.5f;

            layouts.add(new Layout(
                    marker,
                    plateX,
                    plateY,
                    plateWidth,
                    plateHeight,
                    iconX,
                    iconY,
                    iconSize,
                    textX,
                    titleY,
                    metaX,
                    metaY
            ));
        }
        return List.copyOf(layouts);
    }

    public static void renderBackground(Renderer2D renderer, List<Layout> layouts) {
        if (renderer == null || layouts == null || layouts.isEmpty()) return;
        for (Layout layout : layouts) {
            Marker marker = layout.marker();
            float opacity = clamp01(marker.opacity());

            // Match DropESP's matte labels: one calm translucent backdrop, no optical rim or
            // second overlay. The accent remains on the icon where it cannot fight the text.
            renderer.roundedRect(
                    layout.plateX(),
                    layout.plateY(),
                    layout.plateWidth(),
                    layout.plateHeight(),
                    MATTE_RADIUS,
                    0.0f,
                    withAlpha(0x000000, Math.round(MATTE_BACKDROP_ALPHA * opacity))
            );

            renderer.svg(
                    marker.iconId(),
                    layout.iconX(),
                    layout.iconY(),
                    layout.iconSize(),
                    layout.iconSize(),
                    SvgRenderOptions.overrideColor(withAlpha(marker.accent(), Math.round(246.0f * opacity)))
            );
        }
    }

    public static void renderForeground(TextRenderer fallback, List<Layout> layouts) {
        if (fallback == null || layouts == null || layouts.isEmpty()) return;
        TextRenderer titleRegular = BuiltinFontCatalog.ONEST_BOLD.renderer(fallback);
        TextRenderer titleBold = BuiltinFontCatalog.ONEST_BOLD.renderer(titleRegular);
        TextRenderer metaFont = BuiltinFontCatalog.ONEST_BOLD.renderer(titleRegular);

        boolean ownDecorationBatch = false;
        boolean hasDecorations = layouts.stream()
                .flatMap(layout -> layout.marker().titleParts().stream())
                .anyMatch(part -> part.underline() || part.strikethrough());
        if (hasDecorations && !Renderer2D.isBatching()) {
            Renderer2D.COLOR.begin();
            ownDecorationBatch = true;
        }
        try {
            for (Layout layout : layouts) {
                Marker marker = layout.marker();
                float opacity = clamp01(marker.opacity());
                if (!marker.titleParts().isEmpty()) {
                    renderStyledTitle(
                            titleRegular,
                            titleBold,
                            marker.titleParts(),
                            layout.titleX(),
                            layout.titleY(),
                            marker.titleSize(),
                            opacity
                    );
                }
                if (!marker.meta().isBlank()) {
                    metaFont.begin(TextSizing.scaleForSize(marker.metaSize()), false, false);
                    try {
                        metaFont.render(
                                marker.meta(),
                                layout.metaX(),
                                layout.metaY(),
                                new RenderColor(withAlpha(0xFFC7D0DB, Math.round(205.0f * opacity))),
                                false
                        );
                    } finally {
                        metaFont.end();
                    }
                }
            }
        } finally {
            if (ownDecorationBatch) Renderer2D.COLOR.render();
        }
    }

    private static float measureStyledWidth(TextRenderer regular,
                                            TextRenderer bold,
                                            List<TextRenderUtil.Part> parts,
                                            float size) {
        float width = 0.0f;
        for (TextRenderUtil.Part part : parts) {
            width += measureWidth(part.bold() ? bold : regular, part.text(), size);
        }
        return width;
    }

    private static void renderStyledTitle(TextRenderer regular,
                                          TextRenderer bold,
                                          List<TextRenderUtil.Part> parts,
                                          float x,
                                          float y,
                                          float size,
                                          float opacity) {
        float cursor = x;
        for (TextRenderUtil.Part part : parts) {
            if (part.text() == null || part.text().isEmpty()) continue;
            TextRenderer font = part.bold() ? bold : regular;
            font.begin(TextSizing.scaleForSize(size), false, false);
            float width;
            try {
                font.render(
                        part.text(),
                        cursor,
                        y,
                        new RenderColor(withAlpha(part.color(), Math.round(255.0f * opacity))),
                        false
                );
                width = (float) font.getWidth(part.text(), false);
            } finally {
                font.end();
            }
            if (part.underline()) {
                Renderer2D.COLOR.quad(
                        cursor,
                        y + size + 0.3f,
                        width,
                        0.75f,
                        withAlpha(part.color(), Math.round(255.0f * opacity))
                );
            }
            if (part.strikethrough()) {
                Renderer2D.COLOR.quad(
                        cursor,
                        y + size * 0.55f,
                        width,
                        0.75f,
                        withAlpha(part.color(), Math.round(255.0f * opacity))
                );
            }
            cursor += width;
        }
    }

    private static float measureWidth(TextRenderer renderer, String text, float size) {
        if (text == null || text.isEmpty()) return 0.0f;
        renderer.begin(TextSizing.scaleForSize(size), true, false);
        try {
            return (float) renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    private static float measureHeight(TextRenderer renderer, float size) {
        renderer.begin(TextSizing.scaleForSize(size), true, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public record Marker(
            float anchorX,
            float anchorY,
            String iconId,
            float iconSize,
            List<TextRenderUtil.Part> titleParts,
            float titleSize,
            String meta,
            float metaSize,
            int accent,
            float opacity
    ) {
        public Marker {
            iconId = iconId == null || iconId.isBlank() ? "map-pin" : iconId;
            titleParts = titleParts == null ? List.of() : List.copyOf(titleParts);
            meta = meta == null ? "" : meta;
        }
    }

    public record Layout(
            Marker marker,
            float plateX,
            float plateY,
            float plateWidth,
            float plateHeight,
            float iconX,
            float iconY,
            float iconSize,
            float titleX,
            float titleY,
            float metaX,
            float metaY
    ) {
    }
}
