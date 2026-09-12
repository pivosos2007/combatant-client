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
    private final Map<String, UiMotionSignal> motionSignals = new Object2ObjectOpenHashMap<>();
    private boolean hovered;
    private boolean active;
    private boolean focused;
    private boolean disabled;
    private boolean visible = true;
    private float scrollX;
    private float scrollY;
    private float targetScrollX;
    private float targetScrollY;
    private long lastScrollInteractionNanos;
    private long lastScrollTickNanos;
    private boolean scrollbarHovered;
    private boolean scrollbarDragging;
    private float scrollbarDragGrab;

    private float marqueeOffset;
    private boolean marqueeForward = true;
    private long marqueeHoldUntilNanos;
    private long marqueeLastNanos;
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

    public float contentWidth() {
        return contentWidth;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public Map<String, UiAnimationState> animations() {
        return animations;
    }

    public float motion(String name, float target, long durationMs, UiEasing easing, long nowNanos) {
        if (name == null || name.isBlank()) return target;
        UiMotionSignal signal = motionSignals.computeIfAbsent(name, ignored -> new UiMotionSignal());
        return signal.sample(target, durationMs, easing, nowNanos);
    }

    public float targetScrollX() { return targetScrollX; }
    public float targetScrollY() { return targetScrollY; }
    public long lastScrollInteractionNanos() { return lastScrollInteractionNanos; }
    public long lastScrollTickNanos() { return lastScrollTickNanos; }
    public boolean scrollbarHovered() { return scrollbarHovered; }
    public boolean scrollbarDragging() { return scrollbarDragging; }
    public float scrollbarDragGrab() { return scrollbarDragGrab; }

    public float marqueeOffset() { return marqueeOffset; }
    public boolean marqueeForward() { return marqueeForward; }
    public long marqueeHoldUntilNanos() { return marqueeHoldUntilNanos; }
    public long marqueeLastNanos() { return marqueeLastNanos; }

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

    /** Immediate scroll assignment used by layout/runtime correction paths. */
    public void setScroll(float scrollX, float scrollY) {
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.targetScrollX = scrollX;
        this.targetScrollY = scrollY;
    }

    public void setCurrentScroll(float scrollX, float scrollY) {
        this.scrollX = scrollX;
        this.scrollY = scrollY;
    }

    public void setTargetScroll(float scrollX, float scrollY) {
        this.targetScrollX = scrollX;
        this.targetScrollY = scrollY;
    }

    public void markScrollInteraction(long nowNanos) {
        this.lastScrollInteractionNanos = nowNanos;
    }

    public void setLastScrollTickNanos(long value) { this.lastScrollTickNanos = value; }

    public void setScrollbarHovered(boolean value) { this.scrollbarHovered = value; }
    public void setScrollbarDragging(boolean value) { this.scrollbarDragging = value; }
    public void setScrollbarDragGrab(float value) { this.scrollbarDragGrab = value; }

    public void setMarqueeOffset(float value) { this.marqueeOffset = Math.max(0.0f, value); }
    public void setMarqueeForward(boolean value) { this.marqueeForward = value; }
    public void setMarqueeHoldUntilNanos(long value) { this.marqueeHoldUntilNanos = value; }
    public void setMarqueeLastNanos(long value) { this.marqueeLastNanos = value; }

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
