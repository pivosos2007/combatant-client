/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiHoneycombMaterialSpec;

final class MainMenuHexTrail {
    static final int MAX_SOURCES = 64;
    private static final int AMBIENT_RESERVED_SOURCES = 5;
    private static final int CAPACITY = 768;
    private static final long TRAIL_SAMPLE_NS = 12_000_000L;
    private static final float LIFETIME = 7.0f;

    private final Pulse[] pulses = new Pulse[CAPACITY];
    private final PlasmaSource[] sources = new PlasmaSource[MAX_SOURCES];
    private int next;
    private int sourceCount;
    private long lastFrame;
    private long serial;
    private long directSince;
    private long lastTrailEmission;
    private float lastX;
    private float lastY;
    private float cursorDirX;
    private float cursorDirY;
    private float cursorEnergy;
    private float width;
    private float height;
    private boolean tracking;
    private boolean directMode;
    private boolean pointerPressed;

    MainMenuHexTrail() {
        for (int i = 0; i < CAPACITY; i++) pulses[i] = new Pulse();
        for (int i = 0; i < MAX_SOURCES; i++) sources[i] = new PlasmaSource();
    }

    void advance(float viewportWidth, float viewportHeight, float mouseX, float mouseY,
                 MainMenuBackdrop.GridLayout grid, long now, boolean directCursorFluid,
                 boolean pointerDown) {
        if (viewportWidth != width || viewportHeight != height || now - lastFrame > 2_000_000_000L) {
            reset();
            width = viewportWidth;
            height = viewportHeight;
        }
        float dt = lastFrame == 0L ? 1f / 60f : clamp((now - lastFrame) * 1.0e-9f, 1f / 240f, 0.05f);
        lastFrame = now;
        sourceCount = 0;
        if (directMode != directCursorFluid) {
            for (Pulse pulse : pulses) pulse.created = 0L;
            next = 0;
            tracking = false;
            pointerPressed = false;
            directMode = directCursorFluid;
        }
        if (mouseX < 0f || mouseX > width || mouseY < 0f || mouseY > height) {
            tracking = false;
            pointerPressed = false;
            return;
        }
        if (directCursorFluid) {
            advanceDirectCursor(mouseX, mouseY, now, dt, pointerDown);
        } else {
            updateMouse(mouseX, mouseY, grid.radius(), now, true);
            collectPulses(now);
        }
    }

    private void collectPulses(long now) {
        int active = 0;
        for (int i = 0; i < CAPACITY; i++) {
            Pulse pulse = pulses[(next - 1 - i + CAPACITY) % CAPACITY];
            if (pulse.created != 0L && (now - pulse.created) * 1.0e-9f <= LIFETIME) active++;
        }
        int budget = MAX_SOURCES - AMBIENT_RESERVED_SOURCES;
        int stride = Math.max(1, (active + budget - 1) / budget);
        int rank = 0;
        for (int i = 0; i < CAPACITY && sourceCount < budget; i++) {
            Pulse pulse = pulses[(next - 1 - i + CAPACITY) % CAPACITY];
            if (pulse.created == 0L) continue;
            float age = (now - pulse.created) * 1.0e-9f;
            if (age < 0f || age > LIFETIME) continue;
            if (rank++ % stride != 0) continue;
            PlasmaSource source = sources[sourceCount++];
            source.x = pulse.x;
            source.y = pulse.y;
            source.vx = pulse.dx * (80f + 300f * pulse.energy);
            source.vy = pulse.dy * (80f + 300f * pulse.energy);
            source.intensity = 0.28f + 0.72f * pulse.energy;
            source.tint = fract(pulse.serial * 0.61803398875f);
            source.age = age;
            source.momentum = pulse.energy;
        }
    }

    void emitAmbient(float x, float y, float angle, float strength, float age) {
        if (sourceCount >= MAX_SOURCES || x < 0f || y < 0f || x > width || y > height) return;
        PlasmaSource source = sources[sourceCount++];
        source.x = x;
        source.y = y;
        source.vx = (float)Math.cos(angle) * 28f;
        source.vy = (float)Math.sin(angle) * 28f;
        source.intensity = clamp(strength, 0f, 0.55f);
        source.tint = fract(angle * 0.15915494f);
        source.age = age;
        source.momentum = -1f;
    }

    private void advanceDirectCursor(float x, float y, long now, float dt, boolean down) {
        if (!down) {
            pointerPressed = false;
            tracking = true;
            lastX = x;
            lastY = y;
            return;
        }
        if (!pointerPressed || !tracking) {
            pointerPressed = true;
            tracking = true;
            directSince = now;
            lastX = x;
            lastY = y;
            emitCursor(x, y, 0f, 0f, 0.90f, now);
            return;
        }
        float dx = x - lastX;
        float dy = y - lastY;
        float distance = (float)Math.hypot(dx, dy);
        if (distance < 0.4f || distance > Math.max(width, height) * 0.45f) {
            lastX = x;
            lastY = y;
            return;
        }
        float speed = distance / Math.max(dt, 0.001f);
        float energy = clamp(speed / 1300f, 0.24f, 1.35f);
        float vx = dx / dt;
        float vy = dy / dt;
        float forceScale = Math.min(1f, 1850f / Math.max(speed, 1f));
        vx *= forceScale;
        vy *= forceScale;
        int splats = Math.min(MAX_SOURCES - AMBIENT_RESERVED_SOURCES, Math.max(1, (int)Math.ceil(distance / 13f)));
        float sampleStrength = energy / (float)Math.sqrt(splats);
        for (int i = 1; i <= splats; i++) {
            float t = i / (float)splats;
            emitCursor(lastX + dx * t, lastY + dy * t, vx, vy, sampleStrength, now);
        }
        lastX = x;
        lastY = y;
    }

    private void emitCursor(float x, float y, float vx, float vy, float intensity, long now) {
        if (sourceCount >= MAX_SOURCES - AMBIENT_RESERVED_SOURCES) return;
        PlasmaSource source = sources[sourceCount++];
        source.x = x;
        source.y = y;
        source.vx = vx;
        source.vy = vy;
        source.intensity = intensity;
        source.tint = fract((float)(now * 1.0e-9 * 0.12));
        source.age = (now - directSince) * 1.0e-9f;
        source.momentum = 0f;
    }

    private void updateMouse(float x, float y, float radius, long now, boolean recordPulses) {
        if (!tracking) {
            tracking = true;
            lastX = x;
            lastY = y;
            cursorDirX = 0f;
            cursorDirY = 0f;
            cursorEnergy = 0.10f;
            if (recordPulses) addPulse(x, y, 0f, -1f, 0.16f, now);
            return;
        }
        float dx = x - lastX;
        float dy = y - lastY;
        float distance = (float)Math.hypot(dx, dy);
        float step = Math.max(8f, radius * 0.48f);
        if (distance < step) {
            if (!recordPulses) {
                cursorDirX *= 0.86f;
                cursorDirY *= 0.86f;
            }
            cursorEnergy = Math.max(0.10f, cursorEnergy * 0.93f);
            return;
        }
        if (distance >= Math.max(width, height) * 0.45f) {
            lastX = x;
            lastY = y;
            return;
        }
        float invDistance = 1f / Math.max(distance, 0.001f);
        cursorDirX = dx * invDistance;
        cursorDirY = dy * invDistance;
        cursorEnergy = clamp(distance / (radius * 2.8f), 0.10f, 1f);
        if (recordPulses) {
            long elapsed = now - lastTrailEmission;
            if (elapsed < TRAIL_SAMPLE_NS) return;
            int steps = Math.min(3, Math.max(1, (int)(elapsed / TRAIL_SAMPLE_NS)));
            for (int i = 0; i < steps; i++) {
                float fraction = (i + 1f) / steps;
                addPulse(lastX + dx * fraction, lastY + dy * fraction,
                        cursorDirX, cursorDirY, cursorEnergy, now);
            }
        }
        lastX = x;
        lastY = y;
    }

    private void addPulse(float x, float y, float dx, float dy, float energy, long now) {
        Pulse pulse = pulses[next];
        pulse.x = x;
        pulse.y = y;
        pulse.dx = dx;
        pulse.dy = dy;
        pulse.energy = energy;
        pulse.created = now;
        pulse.serial = ++serial;
        lastTrailEmission = now;
        next = (next + 1) % CAPACITY;
    }

    void render(float viewportWidth, float viewportHeight, float opacity,
                MainMenuBackdrop.GridLayout grid, MainMenuBackdrop.Cutout cutout,
                int color, int coldColor, UiHoneycombMaterialSpec material) {
        if (directMode) return;
        for (int index = 0; index < CAPACITY; index++) {
            Pulse pulse = pulses[(next + index) % CAPACITY];
            if (pulse.created == 0L) continue;
            float age = (lastFrame - pulse.created) * 1.0e-9f;
            if (age < 0f || age > LIFETIME) continue;
            Renderer2D.COLOR.honeycombPulse(viewportWidth, viewportHeight,
                    grid.radius(), MainMenuBackdrop.HONEYCOMB_RIM_WIDTH, opacity,
                    pulse.x, pulse.y, age, grid.radius() * 4.2f,
                    grid.originX(), grid.originY(), color, coldColor, material,
                    cutout.x(), cutout.y(), cutout.w(), cutout.h(), cutout.cut());
        }
    }

    int sourceCount() { return sourceCount; }
    PlasmaSource source(int index) { return sources[index]; }

    void clear() { reset(); }

    private void reset() {
        for (Pulse pulse : pulses) pulse.created = 0L;
        next = 0;
        sourceCount = 0;
        lastFrame = 0L;
        serial = 0L;
        directSince = 0L;
        lastTrailEmission = 0L;
        tracking = false;
        pointerPressed = false;
        directMode = false;
        cursorDirX = 0f;
        cursorDirY = -1f;
        cursorEnergy = 0f;
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float smoothstep(float min, float max, float value) {
        float t = clamp((value - min) / (max - min), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
    private static float fract(float value) { return value - (float)Math.floor(value); }

    static final class PlasmaSource {
        float x;
        float y;
        float vx;
        float vy;
        float intensity;
        float tint;
        float age;
        float momentum;
    }

    private static final class Pulse {
        float x;
        float y;
        float dx;
        float dy;
        float energy;
        long created;
        long serial;
    }
}
