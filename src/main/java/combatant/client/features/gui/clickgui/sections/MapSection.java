/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.UiBlurResources;
import combatant.client.render.engine.renderer.ui.UiDeferredScheduler;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureBoundary;
import combatant.client.runtime.error.FailureExecution;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.resources.language.I18n;
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

        // The map background is an auto-flushed batch. Request the underlay before emitting it,
        // otherwise large UI-underlay glass surfaces only see later map tiles and sample transparent
        // black wherever no tile draw covered the dedicated target.
        UiBlurResources.requestUiUnderlay(UiDeferredScheduler.layerForCurrentPhase(false));
        renderer.quad(x, y, width, height, 0xFF090B0E);

        if (fatalFailure) {
            drawStatus(tr("gui.combatant.map.error.incompatible", "World Map integration is incompatible"));
            return;
        }
        if (System.nanoTime() < retryAfterNanos) {
            drawStatus(tr("gui.combatant.map.error.recovering", "World Map is recovering…"));
            return;
        }
        if (ErrorHandler.blocked(this)) ErrorHandler.unregister(this);

        try {
            if (surface == null) surface = new XaeroMapSurface();
            XaeroMapSurface.Frame frame = surface.render(x, y, width, height, mouseX, mouseY);
            if (!frame.ready()) drawStatus(frame.status());
        } catch (RuntimeException error) {
            recover(error);
        } catch (LinkageError error) {
            failFatal(error);
            drawStatus(tr("gui.combatant.map.error.incompatible", "World Map integration is incompatible"));
        }
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        return guardInput("mousePressed",
                () -> surface != null && surface.mousePressed(mouseX, mouseY, button), false);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        guardInput("mouseReleased", () -> {
            if (surface != null) surface.mouseReleased(mouseX, mouseY, button);
            return true;
        }, false);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return guardInput("mouseScrolled",
                () -> surface != null && surface.mouseScrolled(mouseX, mouseY, amount), false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return guardInput("keyPressed", () -> {
            if (surface == null) return false;
            if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (keyCode == GLFW.GLFW_KEY_R) {
                surface.recenter();
                return true;
            }
            return false;
        }, false);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return guardInput("charTyped",
                () -> surface != null && surface.charTyped(chr, modifiers), false);
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
        guardInput("resume", () -> {
            if (surface != null) surface.resume();
            return true;
        }, false);
    }

    @Override
    public void onDeselected() {
        selected = false;
        guardInput("suspend", () -> {
            if (surface != null) surface.suspend();
            return true;
        }, false);
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
        FailureExecution.reportComponent(this, "World Map UI", "render", error, FailureBoundary.ISOLATE);
        DebugLog.warnOnce("clickgui-map-render-failure", "ClickGUI map surface failed and will retry", error);
        drawStatus(tr("gui.combatant.map.error.recovering", "World Map is recovering…"));
    }

    private void failFatal(LinkageError error) {
        surface = null;
        fatalFailure = true;
        DebugLog.warnOnce("clickgui-map-unavailable", "ClickGUI map integration is incompatible", error);
    }

    private <T> T guardInput(String phase, java.util.function.Supplier<T> input, T fallback) {
        if (fatalFailure || System.nanoTime() < retryAfterNanos || ErrorHandler.blocked(this)) return fallback;
        try {
            return input.get();
        } catch (RuntimeException error) {
            surface = null;
            retryAfterNanos = System.nanoTime() + 1_000_000_000L;
            FailureExecution.reportComponent(this, "World Map UI", phase, error, FailureBoundary.ISOLATE);
            DebugLog.warnOnce("clickgui-map-input-failure-" + phase,
                    "ClickGUI map input failed and will retry", error);
            return fallback;
        } catch (LinkageError error) {
            failFatal(error);
            return fallback;
        }
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
}
