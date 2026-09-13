/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBoxShape;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiStroke;
import combatant.client.render.engine.svg.SvgRenderOptions;

import java.util.List;

/**
 * Visual ownership boundary for the map screen chrome.
 *
 * <p>The terrain/viewport pipeline stays in {@link XaeroMapSurface}. Buttons, info cards,
 * browser/drawer presentation and context-menu presentation live here so UI work can be done
 * without touching map rendering code.</p>
 */
final class XaeroMapUiRenderer {
    private static final float MAP_INFO_FONT_SIZE = 18.0f;
    private static final float MAP_INFO_PAD_X = 14.0f;
    private static final float MAP_INFO_PAD_Y = 8.0f;
    private static final float MAP_INFO_RADIUS = 10.0f;
    private static final float MAP_INFO_EDGE_INSET = 18.0f;
    private static final float MAP_INFO_TOP_GAP = 10.0f;

    private XaeroMapUiRenderer() {
    }


    static void layoutButtons(List<XaeroMapSurface.UiButton> out,
                              float areaX, float areaY, float areaWidth, float areaHeight,
                              ChromeState state) {
        out.clear();
        float size = 42.0f;
        float gap = 9.0f;
        float inset = 18.0f;

        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.SETTINGS, "settings-2",
                areaX + inset, areaY + inset, size, state.settingsTooltip(), state.settingsOpen()));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RECENTER, "locate-fixed",
                areaX + inset + size + gap, areaY + inset, size, state.recenterTooltip(), false));

        float leftBottom = areaY + areaHeight - inset - size;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CAVE, "layers",
                areaX + inset, leftBottom, size, state.caveTooltip(), state.caveActive()));
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.DIMENSION, "route",
                areaX + inset, leftBottom - size - gap, size,
                state.dimensionTooltip(), state.customDimension()));

        float right = areaX + areaWidth - inset - size;
        float cursor = areaY + areaHeight - inset - size;
        if (state.showWaypoints()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.WAYPOINTS, "map-pinned",
                    right, cursor, size, state.waypointsTooltip(),
                    state.drawer() == XaeroMapSurface.Drawer.WAYPOINTS));
            cursor -= size + gap;
        }
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.PLAYERS, "users-round",
                right, cursor, size, state.playersTooltip(),
                state.drawer() == XaeroMapSurface.Drawer.PLAYERS));
        cursor -= size + gap;
        if (state.showRadar()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.RADAR, "radar",
                    right, cursor, size, state.radarTooltip(), state.radarActive()));
            cursor -= size + gap;
        }
        if (state.showClaims()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CLAIMS, "land-plot",
                    right, cursor, size, state.claimsTooltip(), state.claimsActive()));
            cursor -= size + gap;
        }
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.EXPORT, "map",
                right, cursor, size, state.exportTooltip(), false));
        cursor -= size + gap;
        out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.CONTROLS, "circle-question-mark",
                right, cursor, size, state.controlsTooltip(), false));
        cursor -= size + gap;
        if (state.showZoomButtons()) {
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_OUT, "zoom-out",
                    right, cursor, size, state.zoomOutTooltip(), false));
            cursor -= size + gap;
            out.add(new XaeroMapSurface.UiButton(XaeroMapSurface.Action.ZOOM_IN, "zoom-in",
                    right, cursor, size, state.zoomInTooltip(), false));
        }
    }

    static void drawZoom(float areaX, float areaY, float areaWidth, float areaHeight,
                         double destinationScale, SettingsGuiPalette palette) {
        String zoom = Math.round(destinationScale * 1000.0) / 1000.0 + "x";
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), zoom, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = areaY + areaHeight - MAP_INFO_EDGE_INSET - cardHeight;
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getOnestMedium(), zoom,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    static void drawUiButton(XaeroMapSurface.UiButton button,
                             float mouseX, float mouseY,
                             SettingsGuiPalette palette) {
        boolean hover = button.contains(mouseX, mouseY);
        int background = button.active()
                ? palette.panelPillActive()
                : hover ? palette.controlSurfaceHover() : palette.controlSurface();
        Renderer2D renderer = Renderer2D.COLOR;
        UiBoxShape shape = UiBoxShape.squircle(
                button.x(), button.y(), button.size(), button.size(), 4.0f);
        renderer.box(shape, UiPaint.solid(SettingsGuiPalette.withAlpha(background, 255)));
        Themes.GradientSpec stroke = Themes.hudAccentGradient();
        renderer.boxStroke(shape,
                UiPaint.linear(stroke.start(), stroke.end(), stroke.angleDeg(), 0.0f),
                UiStroke.of(1.25f));
        float iconSize = button.size() * 0.52f;
        renderer.svg(button.icon(),
                button.x() + (button.size() - iconSize) * 0.5f,
                button.y() + (button.size() - iconSize) * 0.5f,
                iconSize, iconSize,
                SvgRenderOptions.overrideColor(button.active() || hover
                        ? palette.panelText() : palette.panelMuted()));
    }

    static void drawTooltip(float areaX, float areaY, float areaWidth, float areaHeight,
                            float mouseX, float mouseY,
                            List<XaeroMapSurface.UiButton> buttons,
                            boolean blocked,
                            SettingsGuiPalette palette) {
        if (blocked) return;
        XaeroMapSurface.UiButton hovered = null;
        for (XaeroMapSurface.UiButton button : buttons) {
            if (button.contains(mouseX, mouseY)) {
                hovered = button;
                break;
            }
        }
        if (hovered == null) return;
        float fontSize = 16.0f;
        float width = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), hovered.tooltip(), fontSize);
        float x = clamp(mouseX + 16.0f, areaX + 6.0f,
                areaX + areaWidth - width - 26.0f);
        float y = clamp(mouseY + 19.0f, areaY + 6.0f,
                areaY + areaHeight - 42.0f);
        Renderer2D.COLOR.roundedRect(x, y, width + 20.0f, 32.0f,
                8.0f, palette.panelBgRight());
        Renderer2D.COLOR.roundedRectStroke(x, y, width + 20.0f, 32.0f,
                8.0f, 1.1f, palette.panelStroke());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), hovered.tooltip(),
                x + 10.0f, y + 8.0f, fontSize, palette.panelText(), false);
    }

    static void drawCompass(float areaX, float areaY, float areaWidth, float areaHeight,
                            String north, String east, String south, String west,
                            SettingsGuiPalette palette) {
        float centerX = areaX + areaWidth * 0.5f;
        float centerY = areaY + areaHeight * 0.5f;
        float edgeInset = 102.0f;
        drawCardinal(north, centerX, areaY + edgeInset, palette);
        drawCardinal(south, centerX, areaY + areaHeight - edgeInset, palette);
        drawCardinal(west, areaX + edgeInset, centerY, palette);
        drawCardinal(east, areaX + areaWidth - edgeInset, centerY, palette);
    }

    private static void drawCardinal(String text, float centerX, float centerY,
                                     SettingsGuiPalette palette) {
        float size = 16.0f;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), text, size);
        Renderer2D.COLOR.roundedRect(centerX - width * 0.5f - 8.0f,
                centerY - 5.0f, width + 16.0f, size + 10.0f,
                8.0f, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), text,
                centerX - width * 0.5f, centerY, size, palette.panelText(), false);
    }

    static void drawCoordinates(float areaX, float areaY, float areaWidth,
                                String coordinates, float chromeBottom,
                                SettingsGuiPalette palette) {
        float textWidth = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), coordinates, MAP_INFO_FONT_SIZE);
        float cardWidth = textWidth + MAP_INFO_PAD_X * 2.0f;
        float cardHeight = MAP_INFO_FONT_SIZE + MAP_INFO_PAD_Y * 2.0f;
        float cardX = areaX + (areaWidth - cardWidth) * 0.5f;
        float cardY = Math.max(areaY + MAP_INFO_EDGE_INSET, chromeBottom + MAP_INFO_TOP_GAP);
        Renderer2D.COLOR.roundedRect(cardX, cardY, cardWidth, cardHeight,
                MAP_INFO_RADIUS, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), coordinates,
                cardX + MAP_INFO_PAD_X, cardY + MAP_INFO_PAD_Y,
                MAP_INFO_FONT_SIZE, palette.panelText(), false);
    }

    static void drawDrawer(float areaX, float areaY, float areaWidth,
                           float mouseX, float mouseY,
                           XaeroMapSurface.Drawer drawer,
                           XaeroMapElements.Snapshot snapshot,
                           List<XaeroMapSurface.ElementHit> hits,
                           String waypointsTitle,
                           SettingsGuiPalette palette) {
        hits.clear();
        if (drawer == XaeroMapSurface.Drawer.NONE) return;
        float width = 360.0f;
        float x = areaX + areaWidth - width - 82.0f;
        float y = areaY + 72.0f;
        float rowHeight = 46.0f;
        List<XaeroMapElements.Element> rows = snapshot.elements().stream()
                .filter(element -> drawer == XaeroMapSurface.Drawer.WAYPOINTS
                        ? element.kind() == XaeroMapElements.Kind.WAYPOINT
                        : element.kind() != XaeroMapElements.Kind.WAYPOINT)
                .limit(14)
                .toList();
        float height = 56.0f + Math.max(1, rows.size()) * rowHeight + 12.0f;
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 14.0f, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 14.0f,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 14.0f,
                1.2f, palette.panelStroke());
        String title = drawer == XaeroMapSurface.Drawer.WAYPOINTS ? waypointsTitle : "Players & Radar";
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                x + 18.0f, y + 18.0f, 17.0f, palette.panelText(), false);
        renderer.quad(x + 16.0f, y + 52.0f, width - 32.0f,
                1.0f, palette.panelDivider());
        float rowY = y + 58.0f;
        if (rows.isEmpty()) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Nothing to display",
                    x + 18.0f, rowY + 10.0f, 15.0f, palette.panelMuted(), false);
        }
        for (XaeroMapElements.Element element : rows) {
            boolean hover = inside(mouseX, mouseY, x + 10.0f, rowY, width - 20.0f, 42.0f);
            if (hover) {
                renderer.roundedRect(x + 10.0f, rowY, width - 20.0f, 42.0f,
                        8.0f, palette.controlSurfaceHover());
            }
            String icon = switch (element.kind()) {
                case WAYPOINT -> "map-pin";
                case PLAYER -> "users-round";
                case ENTITY -> "radar";
            };
            renderer.svg(icon, x + 18.0f, rowY + 11.0f,
                    20.0f, 20.0f, SvgRenderOptions.overrideColor(element.color()));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), element.plainName(),
                    x + 48.0f, rowY + 9.0f, 15.0f,
                    element.disabled() ? palette.panelMuted() : palette.panelText(), false);
            String location = (int) element.worldX() + ", " + (int) element.worldZ();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), location,
                    x + 48.0f, rowY + 27.0f, 12.0f, palette.panelMuted(), false);
            hits.add(new XaeroMapSurface.ElementHit(
                    element, x + 10.0f, rowY, width - 20.0f, 42.0f));
            rowY += rowHeight;
        }
    }

    static ContextBounds drawContext(float areaX, float areaY, float areaWidth, float areaHeight,
                                     float contextX, float contextY,
                                     float mouseX, float mouseY,
                                     List<XaeroMapSurface.MenuEntry> entries,
                                     SettingsGuiPalette palette) {
        float width = 340.0f;
        float rowHeight = 42.0f;
        float height = 16.0f + entries.size() * rowHeight;
        float x = clamp(contextX, areaX + 8.0f, areaX + areaWidth - width - 8.0f);
        float y = clamp(contextY, areaY + 8.0f, areaY + areaHeight - height - 8.0f);
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 13.0f, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 13.0f,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 13.0f,
                1.2f, palette.panelStroke());
        float rowY = y + 8.0f;
        for (XaeroMapSurface.MenuEntry entry : entries) {
            boolean hover = entry.enabled() && inside(mouseX, mouseY,
                    x + 8.0f, rowY, width - 16.0f, rowHeight - 3.0f);
            if (hover) {
                renderer.roundedRect(x + 8.0f, rowY,
                        width - 16.0f, rowHeight - 3.0f,
                        8.0f, palette.controlSurfaceHover());
            }
            if (entry.icon() != null) {
                renderer.svg(entry.icon(), x + 16.0f, rowY + 11.0f, 20.0f, 20.0f,
                        SvgRenderOptions.overrideColor(
                                entry.enabled() ? palette.panelText() : palette.panelMuted()));
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), entry.label(),
                    x + 46.0f, rowY + 11.0f, 15.0f,
                    entry.enabled() ? palette.panelText() : palette.panelMuted(), false);
            rowY += rowHeight;
        }
        return new ContextBounds(x, y, width, height);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    record ChromeState(
            boolean settingsOpen,
            boolean caveActive,
            boolean customDimension,
            boolean showWaypoints,
            boolean showRadar,
            boolean radarActive,
            boolean showClaims,
            boolean claimsActive,
            boolean showZoomButtons,
            XaeroMapSurface.Drawer drawer,
            String settingsTooltip,
            String recenterTooltip,
            String caveTooltip,
            String dimensionTooltip,
            String waypointsTooltip,
            String playersTooltip,
            String radarTooltip,
            String claimsTooltip,
            String exportTooltip,
            String controlsTooltip,
            String zoomOutTooltip,
            String zoomInTooltip
    ) {
    }

    record ContextBounds(float x, float y, float width, float height) {
    }
}
