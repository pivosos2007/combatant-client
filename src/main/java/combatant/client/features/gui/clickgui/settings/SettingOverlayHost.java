/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.render.engine.animation.AnimationUtility;

/**
 * Shared ownership for setting popovers/dropdowns. Hosts keep a single instance and pass it through
 * {@link SettingRenderContext}; concrete setting renderers own only the control-specific content.
 */
public final class SettingOverlayHost {
    private Setting active;
    private boolean closing;
    private float reveal;

    private float anchorX;
    private float anchorY;
    private float anchorW;
    private float anchorH;

    private float viewportX;
    private float viewportY;
    private float viewportW;
    private float viewportH;

    private float popupX;
    private float popupY;
    private float popupW;
    private float popupH;

    public void setViewport(float x, float y, float w, float h) {
        viewportX = x;
        viewportY = y;
        viewportW = Math.max(1f, w);
        viewportH = Math.max(1f, h);
    }

    public boolean hasActiveOverlay() {
        return active != null;
    }

    public boolean isActive(Setting setting) {
        return active == setting && !closing;
    }

    public boolean owns(Setting setting) {
        return active == setting;
    }

    public float reveal() {
        return reveal;
    }

    public float anchorX() { return anchorX; }
    public float anchorY() { return anchorY; }
    public float anchorW() { return anchorW; }
    public float anchorH() { return anchorH; }
    public float viewportX() { return viewportX; }
    public float viewportY() { return viewportY; }
    public float viewportW() { return viewportW; }
    public float viewportH() { return viewportH; }
    public float popupX() { return popupX; }
    public float popupY() { return popupY; }
    public float popupW() { return popupW; }
    public float popupH() { return popupH; }

    public void open(Setting setting, float x, float y, float w, float h) {
        if (setting == null) return;
        if (active != setting) {
            if (active != null) UnifiedSettingRenderer.overlayClosed(active);
            active = setting;
            reveal = 0f;
        }
        anchorX = x;
        anchorY = y;
        anchorW = Math.max(1f, w);
        anchorH = Math.max(1f, h);
        closing = false;
        popupX = popupY = popupW = popupH = 0f;
        UnifiedSettingRenderer.overlayOpened(setting);
    }

    public void toggle(Setting setting, float x, float y, float w, float h) {
        if (active == setting && !closing) {
            requestClose();
        } else {
            open(setting, x, y, w, h);
        }
    }

    public void requestClose() {
        if (active == null || closing) return;
        closing = true;
        UnifiedSettingRenderer.overlayClosing(active);
    }

    public void closeImmediately() {
        if (active != null) UnifiedSettingRenderer.overlayClosed(active);
        active = null;
        closing = false;
        reveal = 0f;
        popupX = popupY = popupW = popupH = 0f;
    }

    public void render(float mouseX, float mouseY) {
        if (active == null) return;
        float dt = AnimationUtility.deltaTime();
        reveal = AnimationUtility.approach(reveal, closing ? 0f : 1f, dt, closing ? 18f : 16f);
        reveal = AnimationUtility.snap(reveal, closing ? 0f : 1f, 0.002f);
        if (closing && reveal <= 0.001f) {
            closeImmediately();
            return;
        }
        UnifiedSettingRenderer.renderOverlay(active, this, mouseX, mouseY, reveal);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active == null) return false;
        if (closing) return true;
        if (inside(mouseX, mouseY, anchorX, anchorY, anchorW, anchorH)) {
            requestClose();
            return true;
        }
        if (inside(mouseX, mouseY, popupX, popupY, popupW, popupH)) {
            return UnifiedSettingRenderer.overlayMouseClicked(active, this, mouseX, mouseY, button);
        }
        requestClose();
        return true; // outside click closes, never clicks through to the row below
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (active == null) return false;
        UnifiedSettingRenderer.overlayMouseReleased(active, mouseX, mouseY, button);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (active == null) return false;
        if (closing) return true;
        if (!inside(mouseX, mouseY, popupX, popupY, popupW, popupH)) return true;
        UnifiedSettingRenderer.overlayMouseScrolled(active, mouseX, mouseY, amount);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active == null) return false;
        if (keyCode == combatant.client.input.InputKeys.ESCAPE) {
            requestClose();
            return true;
        }
        if (!closing) UnifiedSettingRenderer.overlayKeyPressed(active, keyCode, scanCode, modifiers);
        return true; // active overlays are modal; never leak keys into rows underneath
    }

    public boolean charTyped(char chr, int modifiers) {
        if (active == null) return false;
        if (!closing) UnifiedSettingRenderer.overlayCharTyped(active, chr, modifiers);
        return true; // same rule for text input
    }

    /** Places a popup against the anchor while keeping it within the host viewport. */
    float[] place(float desiredW, float desiredH, float gap) {
        float inset = Math.max(2f, UnifiedSettingsSkin.modernSettings()
                ? UnifiedSettingsSkin.modernMetric(4f, 3f)
                : UnifiedSettingsSkin.metric(4f, 3f));
        float maxW = Math.max(1f, viewportW - inset * 2f);
        float maxH = Math.max(1f, viewportH - inset * 2f);
        float w = Math.min(Math.max(1f, desiredW), maxW);
        float h = Math.min(Math.max(1f, desiredH), maxH);

        float minX = viewportX + inset;
        float maxX = viewportX + viewportW - inset - w;
        float x = clamp(anchorX + anchorW - w, minX, Math.max(minX, maxX));

        float below = anchorY + anchorH + gap;
        float above = anchorY - gap - h;
        float minY = viewportY + inset;
        float maxY = viewportY + viewportH - inset - h;
        float y;
        if (below + h <= viewportY + viewportH - inset) {
            y = below;
        } else if (above >= minY) {
            y = above;
        } else {
            y = clamp(below, minY, Math.max(minY, maxY));
        }
        setPopupBounds(x, y, w, h);
        return new float[]{x, y, w, h};
    }

    void setPopupBounds(float x, float y, float w, float h) {
        popupX = x;
        popupY = y;
        popupW = Math.max(1f, w);
        popupH = Math.max(1f, h);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return w > 0f && h > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
