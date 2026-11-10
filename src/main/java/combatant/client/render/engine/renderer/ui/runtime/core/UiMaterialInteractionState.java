/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.core;

import combatant.client.render.engine.renderer.ui.draw.UiGlassInteraction;

/**
 * Per reconciled UI-node interaction, adapted from liquidGL 3.0's _updateInteraction.
 * Copyright (c) NaughtyDuk, MIT, revision 88f681ab7035fd55b04f63edff1841e32c4199e9.
 * See THIRD_PARTY_LICENSES/liquidGL-MIT.txt for the upstream license and exclusions.
 * No static registry, per-fragment time animation or cross-element shared velocity.
 * A node's lifecycle owns its fluid state and an idle node produces no optical motion.
 */
public final class UiMaterialInteractionState {
    private long previousNanos;
    private float x = 0.5f, y = 0.5f, dx, dy, energy, pressed, opening;
    private float previousX, previousY, previousWidth, previousHeight;
    private boolean insideLast, pressedLast, dismissingLast;
    // Event excitation is distinct from hover/press: target hits and player controls
    // can create local optical motion even when the pointer is elsewhere.
    private float eventX = 0.5f, eventY = 0.5f, eventEnergy;
    private boolean eventMotionEnabled;

    public void opticalImpulse(float localX, float localY, float magnitude) {
        if (!Float.isFinite(localX) || !Float.isFinite(localY) || !Float.isFinite(magnitude)) return;
        if (magnitude <= 0f) return;
        eventX = Math.max(0f, Math.min(1f, localX));
        eventY = Math.max(0f, Math.min(1f, localY));
        eventEnergy = Math.max(eventEnergy, Math.min(1f, magnitude));
        eventMotionEnabled = true;
    }

    public void opticalActivity(float magnitude) {
        if (!Float.isFinite(magnitude) || magnitude <= 0f) return;
        eventX = 0.5f;
        eventY = 0.5f;
        eventEnergy = Math.max(eventEnergy, Math.min(1f, magnitude));
        eventMotionEnabled = false;
    }

    public UiGlassInteraction sample(float pointerX, float pointerY, boolean down,
                                     float left, float top, float width, float height,
                                     float viscosity, float strength, long nowNanos) {
        return sample(pointerX, pointerY, down, left, top, width, height,
                viscosity, strength, nowNanos, false);
    }

    /** Explicit reveal/dismiss lifecycle; the caller must keep the node alive while dismissing. */
    public UiGlassInteraction sample(float pointerX, float pointerY, boolean down,
                                     float left, float top, float width, float height,
                                     float viscosity, float strength, long nowNanos,
                                     boolean dismissing) {
        if (!(width > 0f && height > 0f) || strength <= 0f)
            return new UiGlassInteraction(x, y, 0f, 0f, 0f, 0f, 0f, true);
        float nx = (pointerX - left) / width;
        float ny = (pointerY - top) / height;
        // Bounding-box gate only: the GPU performs the exact analytic shape/SDF gate.
        boolean inside = nx >= 0f && nx <= 1f && ny >= 0f && ny <= 1f;
        if (previousNanos == 0L) {
            previousNanos = nowNanos;
            x = inside ? nx : 0.5f;
            y = inside ? ny : 0.5f;
            previousX = nx; previousY = ny;
            previousWidth = width; previousHeight = height;
            insideLast = inside;
            pressedLast = down;
            dismissingLast = dismissing;
            opening = dismissing ? -0.85f : 0.85f;
            if (eventEnergy > 0.001f) {
                x = eventX; y = eventY;
            }
            return new UiGlassInteraction(x, y, eventMotion(true), eventMotion(false),
                    eventEnergy, eventEnergy, opening, true);
        }
        float elapsed = (nowNanos - previousNanos) * 1.0e-6f;
        previousNanos = nowNanos;
        boolean continuous = elapsed > 0f && elapsed < 180f;
        float dt = Math.max(1f, Math.min(50f, elapsed));
        float follow = 1f - (float) Math.exp(-dt / (25f + viscosity * 180f));
        float settle = 1f - (float) Math.exp(-dt / (80f + viscosity * 620f));
        if (!continuous) {
            x = nx; y = ny; dx = dy = energy = pressed = opening = 0f;
        }
        if (inside) {
            if (!insideLast && Math.abs(dx) + Math.abs(dy) < 0.0001f) {
                x = nx; y = ny;
            }
            x += (nx - x) * follow;
            y += (ny - y) * follow;
        }
        float vx = continuous && inside && insideLast ? (nx - previousX) / dt : 0f;
        float vy = continuous && inside && insideLast ? (ny - previousY) / dt : 0f;
        float speed = (float) Math.hypot(vx * width, vy * height);
        // liquidGL: min(size*.12, pointerSpeed*45) in CSS px. dt is milliseconds.
        float impulsePx = Math.min(Math.min(width, height) * 0.12f, speed * 45f)
                * strength;
        float direction = (float) Math.hypot(vx, vy);
        float targetX = direction > 1e-6f ? vx / direction * impulsePx / width : 0f;
        float targetY = direction > 1e-6f ? vy / direction * impulsePx / height : 0f;
        float rate = Math.hypot(targetX, targetY) > Math.hypot(dx, dy) ? follow : settle;
        dx += (targetX - dx) * rate;
        dy += (targetY - dy) * rate;
        float targetEnergy = inside ? Math.min(1f, Math.max(speed * 5.0f, (down ? 0.30f : 0f))) : 0f;
        energy += (targetEnergy - energy) * (targetEnergy > energy ? follow : settle);
        pressed *= (float) Math.exp(-dt / 210f);
        eventEnergy *= (float) Math.exp(-dt / 280f);
        if (inside && down && !pressedLast) pressed = 1f;
        opening *= (float) Math.exp(-dt / 340f);
        if (dismissing != dismissingLast) {
            opening = dismissing ? -1.0f : 0.85f;
        } else if (!dismissing && Math.abs(width - previousWidth)
                + Math.abs(height - previousHeight) > 2f) {
            opening = Math.max(opening, 0.45f);
        }
        // Hover/leave is a pointer event, not a panel reveal. Do not restart the
        // whole glass lifecycle whenever the mouse crosses the element.
        dismissingLast = dismissing;
        previousWidth = width; previousHeight = height;
        previousX = nx; previousY = ny;
        insideLast = inside;
        pressedLast = down;
        float sourceX = eventEnergy > 0.02f ? eventX : x;
        float sourceY = eventEnergy > 0.02f ? eventY : y;
        return new UiGlassInteraction(sourceX, sourceY,
                dx + eventMotion(true), dy + eventMotion(false),
                Math.max(energy, eventEnergy), Math.max(pressed, eventEnergy), opening, true);
    }

    private float eventMotion(boolean horizontal) {
        if (!eventMotionEnabled) return 0f;
        float axisX = eventX - 0.5f;
        float axisY = eventY - 0.5f;
        float length = (float) Math.hypot(axisX, axisY);
        if (length < 0.05f) {
            axisX = 0f;
            axisY = -1f;
            length = 1f;
        }
        float amount = eventEnergy * 0.06f / length;
        return (horizontal ? axisX : axisY) * amount;
    }
}
