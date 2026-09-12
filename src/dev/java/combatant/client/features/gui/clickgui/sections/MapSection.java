/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.logging.DebugLog;
import org.lwjgl.glfw.GLFW;

@ClickGuiSectionInfo(id = "combatant:map", label = "Map", order = 200, requiredMods = "xaeroworldmap")
public final class MapSection implements ClickGuiSection {
    private float x;
    private float y;
    private float width;
    private float height;
    private XaeroMapSurface surface;
    private boolean fatalFailure;
    private long retryAfterNanos;
    private boolean selected;

    @Override
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        Renderer2D renderer = ClickGuiRenderer.currentRenderer();
        if (renderer == null) return;
        renderer.quad(x, y, width, height, 0xFF090B0E);

        if (fatalFailure) {
            drawStatus("World Map integration is incompatible");
            return;
        }
        if (System.nanoTime() < retryAfterNanos) {
            drawStatus("World Map is recovering…");
            return;
        }

        try {
            if (surface == null) surface = new XaeroMapSurface();
            XaeroMapSurface.Frame frame = surface.render(x, y, width, height, mouseX, mouseY);
            if (!frame.ready()) drawStatus(frame.status());
        } catch (RuntimeException error) {
            recover(error);
        } catch (LinkageError error) {
            failFatal(error);
        }
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        return surface != null && surface.mousePressed(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (surface != null) surface.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return surface != null && surface.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (surface != null) {
            if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (keyCode == GLFW.GLFW_KEY_R) {
                surface.recenter();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return surface != null && surface.charTyped(chr, modifiers);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean usesFullViewport() {
        return true;
    }

    @Override
    public void onSelected() {
        selected = true;
        if (surface != null) surface.resume();
    }

    @Override
    public void onDeselected() {
        selected = false;
        if (surface != null) surface.suspend();
    }

    @Override
    public boolean isVisible() {
        return selected;
    }

    private void drawStatus(String status) {
        TextRenderer font = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        float fontSize = 14.0f;
        float textWidth = ClickGuiRenderer.textWidth(font, status, fontSize);
        ClickGuiRenderer.drawText(
                font,
                status,
                x + (width - textWidth) * 0.5f,
                y + (height - fontSize) * 0.5f,
                fontSize,
                0xFFB7BEC9,
                false
        );
    }

    private void recover(RuntimeException error) {
        surface = null;
        retryAfterNanos = System.nanoTime() + 1_000_000_000L;
        DebugLog.warnOnce("clickgui-map-render-failure", "ClickGUI map surface failed and will retry", error);
        drawStatus("World Map is recovering…");
    }

    private void failFatal(LinkageError error) {
        surface = null;
        fatalFailure = true;
        DebugLog.warnOnce("clickgui-map-unavailable", "ClickGUI map integration is incompatible", error);
        drawStatus("World Map integration is incompatible");
    }
}
