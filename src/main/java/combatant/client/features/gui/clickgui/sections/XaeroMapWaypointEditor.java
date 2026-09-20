/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import xaero.map.mods.gui.Waypoint;

/**
 * Map-native waypoint composer.
 *
 * <p>The composer is intentionally one glass object. Inputs and color selection are drawn as
 * lightweight content inside that object instead of nesting more glass/squircle cards into it.</p>
 */
final class XaeroMapWaypointEditor {
    private static final float PANEL_WIDTH = 372.0f;
    private static final float PANEL_HEIGHT = 194.0f;
    private static final float PANEL_TIP = 12.0f;
    private static final float PANEL_ROUNDING = 6.0f;
    private static final float PANEL_PAD = 17.0f;
    private static final float FIELD_HEIGHT = 31.0f;
    private static final float COLOR_STEP = 20.0f;
    private static final float COLOR_RADIUS = 6.4f;

    private final Waypoint edited;
    private final SaveHandler saveHandler;
    private float anchorX;
    private float anchorY;
    private final Spring open = new Spring();
    private final Spring cancelHover = new Spring();
    private final Spring saveHover = new Spring();
    private final Spring savePress = new Spring();
    private final Spring colorCursor = new Spring();

    private String name;
    private String symbol;
    private int colorIndex;
    private int focusedField;

    private float x;
    private float y;
    private float width;
    private float height;
    private float contentLeft;
    private float contentRight;
    private float nameX;
    private float nameY;
    private float nameW;
    private float symbolX;
    private float symbolY;
    private float symbolW;
    private float colorStartX;
    private float colorY;
    private float cancelX;
    private float cancelY;
    private float cancelW;
    private float saveX;
    private float saveY;
    private float saveW;
    private boolean opensRight;
    private float tipNormY = 0.5f;

    private int pressedAction;
    private boolean closeRequested;
    private boolean closed;
    private boolean saved;

    XaeroMapWaypointEditor(Waypoint edited, float anchorX, float anchorY, SaveHandler saveHandler) {
        this.edited = edited;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.saveHandler = saveHandler;
        this.name = edited == null ? tr("gui.combatant.map.waypoint.default_name", "Waypoint") : edited.getName();
        this.symbol = edited == null ? "W" : edited.getSymbol();
        if (edited != null && edited.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint source) {
            this.colorIndex = Math.floorMod(source.getColor(), 16);
        } else {
            this.colorIndex = 6;
        }
        this.colorCursor.value = this.colorIndex;
    }

    void render(float areaX, float areaY, float areaWidth, float areaHeight,
                float mouseX, float mouseY, SettingsGuiPalette palette) {
        float dt = frameDt();
        open.step(closeRequested ? 0.0f : 1.0f, dt, 180.0f, 22.0f);
        if (closeRequested && open.value <= 0.008f && Math.abs(open.velocity) < 0.02f) {
            closed = true;
            return;
        }

        float reveal = clamp01(open.value);
        float eased = 1.0f - (1.0f - reveal) * (1.0f - reveal) * (1.0f - reveal);
        width = PANEL_WIDTH;
        height = PANEL_HEIGHT;

        boolean anchorValid = Float.isFinite(anchorX) && Float.isFinite(anchorY)
                && anchorX >= areaX - 32.0f && anchorX <= areaX + areaWidth + 32.0f
                && anchorY >= areaY - 32.0f && anchorY <= areaY + areaHeight + 32.0f;
        opensRight = !anchorValid || anchorX < areaX + areaWidth * 0.5f;

        float targetX;
        if (anchorValid) {
            targetX = opensRight ? anchorX + 12.0f : anchorX - width - 12.0f;
            if (targetX < areaX + 14.0f || targetX + width > areaX + areaWidth - 14.0f) {
                targetX = areaX + (areaWidth - width) * 0.5f;
            }
        } else {
            targetX = areaX + (areaWidth - width) * 0.5f;
        }

        float targetY = anchorValid
                ? anchorY - height * 0.5f
                : areaY + (areaHeight - height) * 0.5f;
        targetY = clamp(targetY, areaY + 14.0f, areaY + areaHeight - height - 14.0f);

        float travel = (1.0f - eased) * 10.0f;
        x = targetX + (opensRight ? travel : -travel);
        y = targetY + (1.0f - eased) * 3.0f;
        tipNormY = anchorValid ? clamp((anchorY - y) / height, 0.22f, 0.78f) : 0.5f;

        layout();
        updateMotion(mouseX, mouseY, dt);
        updateCursor(mouseX, mouseY);

        Renderer2D renderer = Renderer2D.COLOR;
        UiPrimitive panel = panelShape();
        int panelBase = SettingsGuiPalette.mix(
                palette.panelBgRight(), palette.controlSurfaceHover(), 0.045f);
        int panelTint = SettingsGuiPalette.withAlpha(panelBase, Math.round(104.0f * eased));
        int inner = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(8.0f * eased));
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withInnerGlow(0.010f * eased, 3.0f, inner);

        // One optical surface; no extra drop shadow and no nested glass inputs/previews.
        renderer.withLiquidGlassMaterial(material, () ->
                drawLiquidGlassPrimitive(
                        renderer,
                        panel,
                        panelTint,
                        1.0f * eased,
                        1.0f * eased,
                        Renderer2D.LiquidGlassPreset.BALANCED));

        drawAnchorMarker(renderer, areaX, areaY, areaWidth, areaHeight, eased);
        drawHeader(renderer, palette, eased);
        drawFields(renderer, mouseX, mouseY, palette, eased);
        drawColors(renderer, palette, eased);
        drawActions(renderer, mouseX, mouseY, palette, eased);
    }

    private void updateCursor(float mouseX, float mouseY) {
        if (inside(mouseX, mouseY, nameX, nameY, nameW, FIELD_HEIGHT)
                || inside(mouseX, mouseY, symbolX, symbolY, symbolW, FIELD_HEIGHT)) {
            SystemCursor.set(SystemCursor.CursorType.TEXT);
            return;
        }
        if (inside(mouseX, mouseY, cancelX, cancelY, cancelW, 30.0f)
                || inside(mouseX, mouseY, saveX, saveY, saveW, 30.0f)) {
            SystemCursor.set(SystemCursor.CursorType.HAND);
            return;
        }
        for (int i = 0; i < 16; i++) {
            float cx = colorStartX + i * COLOR_STEP;
            if (inside(mouseX, mouseY, cx - 8.0f, colorY - 8.0f, 16.0f, 16.0f)) {
                SystemCursor.set(SystemCursor.CursorType.HAND);
                return;
            }
        }
    }

    private static void drawLiquidGlassPrimitive(Renderer2D renderer,
                                                 UiPrimitive primitive,
                                                 int tint,
                                                 float glassAlpha,
                                                 float blurAlpha,
                                                 Renderer2D.LiquidGlassPreset preset) {
        if (renderer == null || primitive == null) return;
        if (primitive.shaderEligible()) {
            renderer.liquidGlassPrimitive(primitive, tint, glassAlpha, blurAlpha, preset);
            return;
        }
        var bounds = primitive.bounds();
        renderer.liquidGlassRect(
                bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                primitive.rounding(), tint, glassAlpha, blurAlpha, preset);
    }

    private UiPrimitive panelShape() {
        double tipX = PANEL_TIP / width;
        if (opensRight) {
            return UiPrimitive.builder(x, y, width, height)
                    .customConvex(
                            0.0, tipNormY,
                            tipX, 0.0,
                            1.0, 0.0,
                            1.0, 1.0,
                            tipX, 1.0)
                    .rounding(PANEL_ROUNDING)
                    .build();
        }
        return UiPrimitive.builder(x, y, width, height)
                .customConvex(
                        0.0, 0.0,
                        1.0 - tipX, 0.0,
                        1.0, tipNormY,
                        1.0 - tipX, 1.0,
                        0.0, 1.0)
                .rounding(PANEL_ROUNDING)
                .build();
    }

    private void layout() {
        contentLeft = x + PANEL_PAD + (opensRight ? PANEL_TIP : 0.0f);
        contentRight = x + width - PANEL_PAD - (!opensRight ? PANEL_TIP : 0.0f);

        symbolW = 50.0f;
        symbolX = contentRight - symbolW;
        nameX = contentLeft;
        nameW = Math.max(120.0f, symbolX - nameX - 12.0f);
        nameY = y + 59.0f;
        symbolY = nameY;

        colorStartX = contentLeft + 1.0f;
        colorY = y + 124.0f;

        saveW = edited == null ? 82.0f : 70.0f;
        saveX = contentRight - saveW;
        saveY = y + height - 39.0f;
        cancelW = 58.0f;
        cancelX = saveX - cancelW - 10.0f;
        cancelY = saveY;
    }

    private void updateMotion(float mouseX, float mouseY, float dt) {
        cancelHover.step(inside(mouseX, mouseY, cancelX, cancelY, cancelW, 30.0f) ? 1.0f : 0.0f,
                dt, 175.0f, 22.0f);
        saveHover.step(inside(mouseX, mouseY, saveX, saveY, saveW, 30.0f) ? 1.0f : 0.0f,
                dt, 190.0f, 21.0f);
        savePress.step(pressedAction == 2 ? 1.0f : 0.0f, dt, 285.0f, 18.0f);
        colorCursor.step(colorIndex, dt, 160.0f, 20.0f);
    }

    private void drawAnchorMarker(Renderer2D renderer,
                                  float areaX, float areaY, float areaWidth, float areaHeight,
                                  float reveal) {
        if (!Float.isFinite(anchorX) || !Float.isFinite(anchorY)
                || anchorX < areaX || anchorX > areaX + areaWidth
                || anchorY < areaY || anchorY > areaY + areaHeight
                || inside(anchorX, anchorY, x - 6.0f, y - 6.0f, width + 12.0f, height + 12.0f)) {
            return;
        }
        int color = waypointColor();
        renderer.circleStroke(anchorX, anchorY, 8.5f, 1.15f,
                SettingsGuiPalette.withAlpha(color, Math.round(175.0f * reveal)));
        renderer.circle(anchorX, anchorY, 2.2f,
                SettingsGuiPalette.withAlpha(color, Math.round(245.0f * reveal)));
    }

    private void drawHeader(Renderer2D renderer, SettingsGuiPalette palette, float reveal) {
        int text = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(255.0f * reveal));
        int color = waypointColor();
        String title = edited == null
                ? tr("gui.combatant.map.waypoint.create_title", "Create waypoint")
                : tr("gui.combatant.map.waypoint.edit_title", "Edit waypoint");

        renderer.svg("map-pin", contentLeft, y + 15.5f, 17.0f, 17.0f,
                SvgRenderOptions.overrideColor(
                        SettingsGuiPalette.withAlpha(color, Math.round(240.0f * reveal))));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                contentLeft + 23.5f, y + 16.0f, 16.0f, text, false);
    }

    private void drawFields(Renderer2D renderer,
                            float mouseX, float mouseY,
                            SettingsGuiPalette palette,
                            float reveal) {
        int muted = SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(220.0f * reveal));
        int text = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(255.0f * reveal));

        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.waypoint.name", "Name"),
                nameX, y + 45.0f, 11.0f, muted, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.waypoint.symbol", "Symbol"),
                symbolX, y + 45.0f, 11.0f, muted, false);

        drawField(renderer, nameX, nameY, nameW, FIELD_HEIGHT,
                focusedField == 1, inside(mouseX, mouseY, nameX, nameY, nameW, FIELD_HEIGHT),
                palette, reveal);
        drawField(renderer, symbolX, symbolY, symbolW, FIELD_HEIGHT,
                focusedField == 2, inside(mouseX, mouseY, symbolX, symbolY, symbolW, FIELD_HEIGHT),
                palette, reveal);

        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                nameX + 6.0f, nameY + 7.0f, 14.0f, text, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), symbol,
                symbolX + 6.0f, symbolY + 7.0f, 14.0f, text, false);

        if (focusedField == 1) {
            drawCaret(renderer, name, nameX + 6.0f, nameY + 6.0f, 14.0f, palette, reveal);
        } else if (focusedField == 2) {
            drawCaret(renderer, symbol, symbolX + 6.0f, symbolY + 6.0f, 14.0f, palette, reveal);
        }
    }

    private void drawField(Renderer2D renderer,
                           float fx, float fy, float fw, float fh,
                           boolean focused, boolean hovered,
                           SettingsGuiPalette palette, float reveal) {
        float state = focused ? 1.0f : hovered ? 0.48f : 0.0f;
        int fill = SettingsGuiPalette.withAlpha(
                palette.controlSurfaceHover(), Math.round((12.0f + state * 12.0f) * reveal));
        renderer.quad(fx, fy, fw, fh, fill);

        int line = focused
                ? SettingsGuiPalette.withAlpha(waypointColor(), Math.round(190.0f * reveal))
                : SettingsGuiPalette.withAlpha(palette.panelText(), Math.round((24.0f + state * 25.0f) * reveal));
        renderer.roundedRect(fx, fy + fh - 1.25f, fw, 1.25f, 0.625f, line);
    }

    private void drawCaret(Renderer2D renderer, String value,
                           float tx, float ty, float fontSize,
                           SettingsGuiPalette palette, float reveal) {
        float caretX = tx + ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), value, fontSize) + 1.2f;
        float pulse = 0.40f + 0.60f * (float) Math.abs(Math.sin(System.nanoTime() * 0.0000000045));
        int caret = SettingsGuiPalette.withAlpha(
                palette.panelText(), Math.round(225.0f * reveal * pulse));
        renderer.roundedRect(caretX, ty + 1.0f, 1.15f, 15.0f, 0.575f, caret);
    }

    private void drawColors(Renderer2D renderer, SettingsGuiPalette palette, float reveal) {
        int muted = SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(215.0f * reveal));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.waypoint.color", "Color"),
                contentLeft, y + 108.0f, 11.0f, muted, false);

        for (int i = 0; i < 16; i++) {
            float cx = colorStartX + i * COLOR_STEP + COLOR_RADIUS;
            int color = xaero.hud.minimap.waypoint.WaypointColor.fromIndex(i).getHex() | 0xFF000000;
            renderer.circle(cx, colorY + COLOR_RADIUS, COLOR_RADIUS,
                    SettingsGuiPalette.withAlpha(color, Math.round(235.0f * reveal)));
        }

        float selectedCenterX = colorStartX
                + clamp(colorCursor.value, 0.0f, 15.0f) * COLOR_STEP
                + COLOR_RADIUS;
        float selectedCenterY = colorY + COLOR_RADIUS;
        int selected = SettingsGuiPalette.withAlpha(waypointColor(), Math.round(220.0f * reveal));
        renderer.circleStroke(selectedCenterX, selectedCenterY, COLOR_RADIUS + 3.8f,
                1.4f, selected);
    }

    private void drawActions(Renderer2D renderer,
                             float mouseX, float mouseY,
                             SettingsGuiPalette palette,
                             float reveal) {
        float cancelState = clamp01(cancelHover.value);
        float saveState = clamp01(saveHover.value);
        float press = clamp01(savePress.value);

        int cancelColor = SettingsGuiPalette.mix(
                palette.panelMuted(), palette.panelText(), cancelState * 0.86f);
        cancelColor = SettingsGuiPalette.withAlpha(cancelColor, Math.round(245.0f * reveal));
        float cancelTextW = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), "Cancel", 13.5f);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.common.cancel", "Cancel"),
                cancelX + (cancelW - cancelTextW) * 0.5f,
                cancelY + 8.0f,
                13.5f, cancelColor, false);

        float scaleX = 1.0f + saveState * 0.025f - press * 0.050f;
        float scaleY = 1.0f + saveState * 0.035f - press * 0.070f;
        float w = saveW * scaleX;
        float h = 30.0f * scaleY;
        float bx = saveX + (saveW - w) * 0.5f;
        float by = saveY + (30.0f - h) * 0.5f;
        UiPrimitive saveShape = UiPrimitive.builder(bx, by, w, h)
                .preset(UiPrimitive.Preset.DIRECTIONAL_RIGHT)
                .cut(7.5f)
                .rounding(4.0f)
                .build();

        int accent = waypointColor();
        int saveFill = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.panelBgRight(), accent, 0.58f + saveState * 0.16f),
                Math.round((150.0f + saveState * 42.0f - press * 12.0f) * reveal));
        // Keep the composer optically singular: this is a shaped accent action inside the glass,
        // not another refractive surface stacked on top of it.
        renderer.primitive(saveShape, UiPaint.solid(saveFill));

        String primary = edited == null
                ? tr("gui.combatant.map.common.create", "Create")
                : tr("gui.combatant.map.common.save", "Save");
        int primaryText = SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(255.0f * reveal));
        float saveTextW = ClickGuiRenderer.textWidth(
                ClickGuiRenderer.getOnestMedium(), primary, 13.5f);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), primary,
                bx + (w - saveTextW) * 0.5f - 1.0f,
                by + (h - 13.5f) * 0.5f - 0.5f,
                13.5f, primaryText, false);
    }


    void setAnchor(float anchorX, float anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (closeRequested) return true;
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            requestClose();
            return true;
        }
        if (inside(mouseX, mouseY, nameX, nameY, nameW, FIELD_HEIGHT)) {
            focusedField = 1;
            return true;
        }
        if (inside(mouseX, mouseY, symbolX, symbolY, symbolW, FIELD_HEIGHT)) {
            focusedField = 2;
            return true;
        }

        for (int i = 0; i < 16; i++) {
            float cx = colorStartX + i * COLOR_STEP + COLOR_RADIUS;
            float cy = colorY + COLOR_RADIUS;
            float hit = COLOR_RADIUS + 4.5f;
            if (inside(mouseX, mouseY, cx - hit, cy - hit, hit * 2.0f, hit * 2.0f)) {
                colorIndex = i;
                return true;
            }
        }

        if (inside(mouseX, mouseY, cancelX, cancelY, cancelW, 30.0f)) {
            pressedAction = 1;
            return true;
        }
        if (inside(mouseX, mouseY, saveX, saveY, saveW, 30.0f)) {
            pressedAction = 2;
            return true;
        }

        focusedField = 0;
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        int released = pressedAction;
        pressedAction = 0;
        if (closeRequested) return;
        if (released == 1 && inside(mouseX, mouseY, cancelX, cancelY, cancelW, 30.0f)) {
            requestClose();
        } else if (released == 2 && inside(mouseX, mouseY, saveX, saveY, saveW, 30.0f)) {
            save();
        }
    }

    boolean keyPressed(int keyCode, int modifiers) {
        if (closeRequested) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
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
        if (closeRequested) return true;
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

    private int waypointColor() {
        return xaero.hud.minimap.waypoint.WaypointColor
                .fromIndex(Math.floorMod(colorIndex, 16)).getHex() | 0xFF000000;
    }

    private void save() {
        if (saved) return;
        saved = true;
        if (saveHandler != null) saveHandler.save(edited, name, symbol, colorIndex);
        requestClose();
    }

    private void requestClose() {
        closeRequested = true;
        pressedAction = 0;
        focusedField = 0;
    }

    private static float frameDt() {
        return Math.min(1.0f / 30.0f,
                Math.max(1.0f / 240.0f, AnimationUtility.deltaTime()));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static String tr(String key, String fallback) {
        try {
            String translated = I18n.get(key);
            if (translated != null && !translated.equals(key) && !translated.startsWith("Format error:")) {
                return translated;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static final class Spring {
        float value;
        float velocity;

        void step(float target, float dt, float stiffness, float damping) {
            velocity += (target - value) * stiffness * dt;
            velocity *= (float) Math.exp(-damping * dt);
            value += velocity * dt;
            if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.0005f) {
                value = target;
                velocity = 0.0f;
            }
        }
    }

    @FunctionalInterface
    interface SaveHandler {
        void save(Waypoint edited, String name, String symbol, int colorIndex);
    }
}
