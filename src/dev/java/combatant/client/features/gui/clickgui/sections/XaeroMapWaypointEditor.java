/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import org.lwjgl.glfw.GLFW;
import xaero.map.mods.gui.Waypoint;

/** UI/editor layer only; map state mutation is delegated back to the surface through SaveHandler. */
final class XaeroMapWaypointEditor {
    private final Waypoint edited;
    private final SaveHandler saveHandler;
    private String name;
    private String symbol;
    private int colorIndex;
    private int focusedField;
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean closed;

    XaeroMapWaypointEditor(Waypoint edited, SaveHandler saveHandler) {
        this.edited = edited;
        this.saveHandler = saveHandler;
        this.name = edited == null ? "Waypoint" : edited.getName();
        this.symbol = edited == null ? "W" : edited.getSymbol();
        if (edited != null && edited.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint source) {
            this.colorIndex = source.getColor();
        } else {
            this.colorIndex = 6;
        }
    }

    void render(float areaX, float areaY, float areaWidth, float areaHeight,
                float mouseX, float mouseY, SettingsGuiPalette palette) {
        width = 420.0f;
        height = 330.0f;
        x = areaX + (areaWidth - width) * 0.5f;
        y = areaY + (areaHeight - height) * 0.5f;
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 16.0f, 14.0f,
                0.06f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 16.0f,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 16.0f, 1.3f, palette.panelStroke());
        String title = edited == null ? "Create waypoint" : "Edit waypoint";
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                x + 22.0f, y + 22.0f, 18.0f, palette.panelText(), false);
        renderer.svg("map-pin", x + width - 46.0f, y + 18.0f, 24.0f, 24.0f,
                SvgRenderOptions.overrideColor(xaero.hud.minimap.waypoint.WaypointColor
                        .fromIndex(Math.floorMod(colorIndex, 16)).getHex() | 0xFF000000));
        drawField("Name", name, 0, y + 72.0f, palette);
        drawField("Symbol", symbol, 1, y + 140.0f, palette);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Color",
                x + 22.0f, y + 204.0f, 14.0f, palette.panelMuted(), false);
        float chipX = x + 22.0f;
        float chipY = y + 228.0f;
        for (int i = 0; i < 16; i++) {
            int color = xaero.hud.minimap.waypoint.WaypointColor.fromIndex(i).getHex() | 0xFF000000;
            float chip = 18.0f;
            renderer.roundedRect(chipX, chipY, chip, chip, chip * 0.38f, color);
            if (i == colorIndex) {
                renderer.roundedRectStroke(chipX - 2.0f, chipY - 2.0f,
                        chip + 4.0f, chip + 4.0f, chip * 0.48f, 1.5f, palette.panelText());
            }
            chipX += 24.0f;
        }
        drawButton("Cancel", x + width - 198.0f, y + height - 54.0f,
                82.0f, mouseX, mouseY, palette, false);
        drawButton(edited == null ? "Create" : "Save", x + width - 102.0f,
                y + height - 54.0f, 80.0f, mouseX, mouseY, palette, true);
    }

    private void drawField(String label, String value, int index, float fieldY,
                           SettingsGuiPalette palette) {
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), label,
                x + 22.0f, fieldY, 14.0f, palette.panelMuted(), false);
        float y0 = fieldY + 20.0f;
        Renderer2D.COLOR.roundedRect(x + 22.0f, y0, width - 44.0f,
                40.0f, 9.0f,
                focusedField == index + 1 ? palette.controlSurfaceHover() : palette.controlSurface());
        Renderer2D.COLOR.roundedRectStroke(x + 22.0f, y0, width - 44.0f,
                40.0f, 9.0f, 1.1f, palette.panelStroke());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), value,
                x + 34.0f, y0 + 11.0f, 16.0f, palette.panelText(), false);
    }

    private void drawButton(String label, float bx, float by, float bw,
                            float mouseX, float mouseY, SettingsGuiPalette palette,
                            boolean primary) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, 36.0f);
        Renderer2D.COLOR.roundedRect(bx, by, bw, 36.0f, 9.0f,
                primary ? palette.panelPillActive()
                        : hover ? palette.controlSurfaceHover() : palette.controlSurface());
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), label, 15.0f);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), label,
                bx + (bw - textWidth) * 0.5f, by + 10.0f,
                15.0f, palette.panelText(), false);
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            closed = true;
            return true;
        }
        if (inside(mouseX, mouseY, x + 22.0f, y + 92.0f, width - 44.0f, 40.0f)) {
            focusedField = 1;
            return true;
        }
        if (inside(mouseX, mouseY, x + 22.0f, y + 160.0f, width - 44.0f, 40.0f)) {
            focusedField = 2;
            return true;
        }
        float chipX = x + 22.0f;
        for (int i = 0; i < 16; i++) {
            if (inside(mouseX, mouseY, chipX, y + 228.0f, 18.0f, 18.0f)) {
                colorIndex = i;
                return true;
            }
            chipX += 24.0f;
        }
        if (inside(mouseX, mouseY, x + width - 198.0f,
                y + height - 54.0f, 82.0f, 36.0f)) {
            closed = true;
            return true;
        }
        if (inside(mouseX, mouseY, x + width - 102.0f,
                y + height - 54.0f, 80.0f, 36.0f)) {
            save();
            return true;
        }
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
    }

    boolean keyPressed(int keyCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closed = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusedField = focusedField == 1 ? 2 : 1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusedField != 0) {
            if (focusedField == 1 && !name.isEmpty()) name = name.substring(0, name.length() - 1);
            if (focusedField == 2 && !symbol.isEmpty()) symbol = symbol.substring(0, symbol.length() - 1);
            return true;
        }
        return focusedField != 0;
    }

    boolean charTyped(char chr) {
        if (focusedField == 1 && !Character.isISOControl(chr) && name.length() < 48) {
            name += chr;
            return true;
        }
        if (focusedField == 2 && !Character.isISOControl(chr) && symbol.length() < 2) {
            symbol += chr;
            return true;
        }
        return focusedField != 0;
    }

    boolean isClosed() {
        return closed;
    }

    private void save() {
        if (saveHandler != null) saveHandler.save(edited, name, symbol, colorIndex);
        closed = true;
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    @FunctionalInterface
    interface SaveHandler {
        void save(Waypoint edited, String name, String symbol, int colorIndex);
    }
}
