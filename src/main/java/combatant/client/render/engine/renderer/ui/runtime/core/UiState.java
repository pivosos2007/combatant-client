/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import combatant.client.render.engine.renderer.ui.runtime.animation.UiAnimationState;
import combatant.client.render.engine.renderer.ui.runtime.animation.UiEasing;
import combatant.client.render.engine.renderer.ui.runtime.animation.UiMotionSignal;

import java.util.Map;

public final class UiState {
    private final Map<String, UiAnimationState> animations = new Object2ObjectOpenHashMap<>();
    private final Map<String, UiMotionSignal> motions = new Object2ObjectOpenHashMap<>();
    private boolean hovered;
    private boolean active;
    private boolean focused;
    private boolean disabled;
    private boolean visible = true;
    private float scrollX;
    private float scrollY;
    private float targetScrollX;
    private float targetScrollY;
    private long lastScrollTickNanos;
    private float contentWidth;
    private float contentHeight;

    public boolean hovered() {
        return hovered;
    }

    public boolean active() {
        return active;
    }

    public boolean focused() {
        return focused;
    }

    public boolean disabled() {
        return disabled;
    }

    public boolean visible() {
        return visible;
    }

    public float scrollX() {
        return scrollX;
    }

    public float scrollY() {
        return scrollY;
    }

    public float targetScrollX() {
        return targetScrollX;
    }

    public float targetScrollY() {
        return targetScrollY;
    }

    public long lastScrollTickNanos() {
        return lastScrollTickNanos;
    }

    public float contentWidth() {
        return contentWidth;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public Map<String, UiAnimationState> animations() {
        return animations;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setScroll(float scrollX, float scrollY) {
        setCurrentScroll(scrollX, scrollY);
        setTargetScroll(scrollX, scrollY);
    }

    public void setCurrentScroll(float scrollX, float scrollY) {
        this.scrollX = scrollX;
        this.scrollY = scrollY;
    }

    public void setTargetScroll(float scrollX, float scrollY) {
        this.targetScrollX = scrollX;
        this.targetScrollY = scrollY;
    }

    public void setLastScrollTickNanos(long lastScrollTickNanos) {
        this.lastScrollTickNanos = lastScrollTickNanos;
    }

    public float motion(String name, float target, long durationMs, UiEasing easing, long nowNanos) {
        String key = name != null ? name : "";
        return motions.computeIfAbsent(key, ignored -> new UiMotionSignal())
                .sample(target, durationMs, easing, nowNanos);
    }

    public void setContentSize(float contentWidth, float contentHeight) {
        this.contentWidth = Math.max(0.0f, contentWidth);
        this.contentHeight = Math.max(0.0f, contentHeight);
    }

    public int flags() {
        int flags = 0;
        if (hovered) flags |= 1;
        if (active) flags |= 1 << 1;
        if (focused) flags |= 1 << 2;
        if (disabled) flags |= 1 << 3;
        if (visible) flags |= 1 << 4;
        return flags;
    }
}
