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
    private boolean insideLast, pressedLast;

    public UiGlassInteraction sample(float pointerX, float pointerY, boolean down,
                                     float left, float top, float width, float height,
                                     float viscosity, float strength, long nowNanos) {
        if (!(width > 0f && height > 0f) || strength <= 0f)
            return new UiGlassInteraction(x, y, 0f, 0f, 0f, 0f, 0f, true);
        float nx = (pointerX - left) / width;
        float ny = (pointerY - top) / height;
        // Bounding-box gate only: the GPU performs the exact analytic shape/SDF gate.
        boolean inside = nx >= 0f && nx <= 1f && ny >= 0f && ny <= 1f;
        if (previousNanos == 0L) {
            previousNanos = nowNanos;
            x = nx; y = ny;
            previousX = nx; previousY = ny;
            previousWidth = width; previousHeight = height;
            insideLast = inside;
            pressedLast = down;
            return new UiGlassInteraction(x, y, 0f, 0f, 0f, 0f, 0f, true);
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
        if (inside && down && !pressedLast) pressed = 1f;
        opening *= (float) Math.exp(-dt / 310f);
        if (Math.abs(width - previousWidth) + Math.abs(height - previousHeight) > 2f) {
            opening = Math.max(opening, 0.45f);
        }
        if (!insideLast && inside) opening = Math.max(opening, 0.25f);
        previousWidth = width; previousHeight = height;
        previousX = nx; previousY = ny;
        insideLast = inside;
        pressedLast = down;
        return new UiGlassInteraction(x, y, dx, dy, energy, pressed, opening, true);
    }
}
