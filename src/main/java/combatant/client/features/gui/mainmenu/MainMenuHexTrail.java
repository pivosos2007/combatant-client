package combatant.client.features.gui.mainmenu;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiHoneycombMaterialSpec;

final class MainMenuHexTrail {
    static final int MAX_SOURCES = 48;
    private static final int CAPACITY = 192;
    private static final float LIFETIME = 4.4f;
    private static final int EMISSIONS_PER_STEP = 4;
    private static final long EMISSION_INTERVAL_NS = 36_000_000L;
    private static final float SQRT3 = 1.7320508f;
    private final Pulse[] pulses = new Pulse[CAPACITY];
    private final PlasmaSource[] sources = new PlasmaSource[MAX_SOURCES];
    private int next;
    private int sourceCount;
    private int sourceCursor;
    private long lastFrame;
    private long lastPlasmaEmission;
    private long serial;
    private float lastX;
    private float lastY;
    private float width;
    private float height;
    private boolean tracking;

    MainMenuHexTrail() {
        for (int i = 0; i < CAPACITY; i++) pulses[i] = new Pulse();
        for (int i = 0; i < MAX_SOURCES; i++) sources[i] = new PlasmaSource();
    }

    void advance(float viewportWidth, float viewportHeight, float mouseX, float mouseY,
                 MainMenuBackdrop.GridLayout grid, long now) {
        if (viewportWidth != width || viewportHeight != height || now - lastFrame > 2_000_000_000L) {
            reset();
            width = viewportWidth;
            height = viewportHeight;
        }
        lastFrame = now;
        sourceCount = 0;
        if (mouseX >= 0f && mouseX <= width && mouseY >= 0f && mouseY <= height) {
            updateMouse(mouseX, mouseY, grid.radius(), now);
        } else {
            tracking = false;
        }
        if (now - lastPlasmaEmission >= EMISSION_INTERVAL_NS) {
            float radius = grid.radius();
            int examined = 0;
            int emitted = 0;
            while (examined < CAPACITY && emitted < EMISSIONS_PER_STEP) {
                int index = (sourceCursor + examined) % CAPACITY;
                examined++;
                Pulse pulse = pulses[index];
                if (pulse.created == 0L) continue;
                float age = (now - pulse.created) * 1.0e-9f;
                if (age < 1.40f || age > LIFETIME || now - pulse.emitted < 110_000_000L) continue;
                float fade = 1f - smoothstep(1.95f, LIFETIME, age);
                if (fade <= 0.01f) continue;
                float baseAngle = (float)Math.atan2(pulse.dy, pulse.dx);
                if (pulse.energy <= 0.001f) baseAngle = (float)(pulse.serial * 2.399963229728653);
                float swirl = (float)Math.sin(age * 2.15f + pulse.serial * 0.37f);
                float angle = baseAngle + swirl * 0.65f;
                float distance = radius * Math.min(2.95f, 0.36f + age * (0.78f + pulse.energy * 0.34f));
                float x = pulse.x + (float)Math.cos(angle) * distance;
                float y = pulse.y + (float)Math.sin(angle) * distance;
                int before = sourceCount;
                emitOnEdge(x, y, grid, angle, 0.24f + fade * (0.38f + pulse.energy * 0.26f),
                        (float)((pulse.serial * 0.61803398875) % 1.0), radius,
                        pulse.dx, pulse.dy, pulse.energy);
                if (sourceCount > before) {
                    pulse.emitted = now;
                    emitted++;
                }
            }
            sourceCursor = (sourceCursor + examined) % CAPACITY;
            lastPlasmaEmission = now;
        }
    }

    void emitAmbient(float x, float y, float angle, float strength, MainMenuBackdrop.GridLayout grid) {
        emitOnEdge(x, y, grid, angle, strength, 0.24f + 0.50f * (float)Math.sin(angle * 1.7f),
                grid.radius(), (float)Math.cos(angle), (float)Math.sin(angle), strength * 0.55f);
    }

    private void emitOnEdge(float x, float y, MainMenuBackdrop.GridLayout grid,
                            float phase, float strength, float tint, float radius,
                            float impulseX, float impulseY, float impulseEnergy) {
        if (sourceCount >= MAX_SOURCES || x < -radius || x > width + radius
                || y < -radius || y > height + radius) return;
        float px = x - grid.originX();
        float py = y - grid.originY();
        float q = (px / SQRT3 - py / 3f) / radius;
        float r = (2f * py / 3f) / radius;
        float cx = Math.round(q);
        float cy = Math.round(-q - r);
        float cz = Math.round(r);
        float ex = Math.abs(cx - q);
        float ey = Math.abs(cy + q + r);
        float ez = Math.abs(cz - r);
        if (ex > ey && ex > ez) cx = -cy - cz;
        else if (!(ey > ez)) cz = -cx - cy;
        float ox = grid.originX() + SQRT3 * radius * (cx + cz * 0.5f);
        float oy = grid.originY() + 1.5f * radius * cz;

        float energy = clamp(strength, 0.01f, 1f);
        float il = Math.max(0.001f, (float)Math.hypot(impulseX, impulseY));
        float ix = impulseX / il;
        float iy = impulseY / il;
        float memory = clamp(impulseEnergy, 0f, 1f);
        float edgeSpread = 0.88f + memory * 0.22f;
        float radiusScale = clamp(radius * 0.52f + memory * 5.5f, 12f, 26f);

        for (int k = 0; k < 6 && sourceCount < MAX_SOURCES; k++) {
            double a = -Math.PI * 0.5 + k * Math.PI / 3.0;
            double b = a + Math.PI / 3.0;
            float ax = ox + (float)Math.cos(a) * radius;
            float ay = oy + (float)Math.sin(a) * radius;
            float bx = ox + (float)Math.cos(b) * radius;
            float by = oy + (float)Math.sin(b) * radius;
            float mx = (ax + bx) * 0.5f;
            float my = (ay + by) * 0.5f;
            float dx = bx - ax;
            float dy = by - ay;
            float inv = 1f / Math.max((float)Math.hypot(dx, dy), 0.001f);
            float tx = dx * inv;
            float ty = dy * inv;
            float nx = mx - ox;
            float ny = my - oy;
            float nn = 1f / Math.max((float)Math.hypot(nx, ny), 0.001f);
            nx *= nn;
            ny *= nn;

            float alignment = Math.max(0f, nx * ix + ny * iy);
            float side = ((k & 1) == 0 ? 1f : -1f) * ((float)Math.sin(phase * 1.29f + k * 0.91f) >= 0f ? 1f : -1f);
            float blend = 0.26f + memory * 0.34f + alignment * 0.22f;
            float dirX = tx * side * (1f - blend) + ix * blend + nx * (0.30f + alignment * 0.28f);
            float dirY = ty * side * (1f - blend) + iy * blend + ny * (0.30f + alignment * 0.28f);
            float dl = Math.max(0.001f, (float)Math.hypot(dirX, dirY));
            dirX /= dl;
            dirY /= dl;

            float sourceStrength = energy * (0.46f + alignment * 0.44f);
            float offset = radius * (0.08f + alignment * 0.05f);
            appendSource(mx + nx * offset, my + ny * offset,
                    dirX * edgeSpread, dirY * edgeSpread,
                    sourceStrength, clamp(tint + k * 0.07f, 0f, 1f), radiusScale + alignment * 3.5f,
                    memory);
        }
    }

    private void appendSource(float x, float y, float dirX, float dirY,
                              float intensity, float tint, float radius, float momentum) {
        if (sourceCount >= MAX_SOURCES) return;
        PlasmaSource src = sources[sourceCount++];
        src.x = clamp(x, 0f, width);
        src.y = clamp(y, 0f, height);
        src.vx = dirX * (115f + intensity * 240f + momentum * 180f);
        src.vy = dirY * (115f + intensity * 240f + momentum * 180f);
        src.intensity = clamp(intensity, 0.01f, 1.35f);
        src.tint = clamp(tint, 0f, 1f);
        src.radius = clamp(radius, 12f, 30f);
        src.momentum = clamp(momentum, 0f, 1f);
    }

    private void updateMouse(float x, float y, float radius, long now) {
        if (!tracking) {
            tracking = true;
            lastX = x;
            lastY = y;
            addPulse(x, y, 0f, 0f, 0f, now);
            return;
        }
        float dx = x - lastX;
        float dy = y - lastY;
        float distance = (float)Math.hypot(dx, dy);
        float step = Math.max(9f, radius * 0.54f);
        if (distance < step) return;
        if (distance >= Math.max(width, height) * 0.45f) {
            lastX = x;
            lastY = y;
            return;
        }
        int count = Math.min(14, Math.max(1, (int)Math.floor(distance / step)));
        float inv = 1f / Math.max(distance, 0.001f);
        float dirX = dx * inv;
        float dirY = dy * inv;
        float energy = clamp(distance / (radius * 2.6f), 0.08f, 1f);
        for (int i = 0; i < count; i++) {
            float f = (i + 1f) / (float)count;
            addPulse(lastX + dx * f, lastY + dy * f, dirX, dirY, energy, now);
        }
        lastX = x;
        lastY = y;
    }

    private void addPulse(float x, float y, float dx, float dy, float energy, long now) {
        Pulse pulse = pulses[next];
        pulse.x = x;
        pulse.y = y;
        pulse.created = now;
        pulse.emitted = now;
        pulse.serial = ++serial;
        pulse.dx = dx;
        pulse.dy = dy;
        pulse.energy = energy;
        next = (next + 1) % CAPACITY;
    }

    void render(float viewportWidth, float viewportHeight, float opacity,
                MainMenuBackdrop.GridLayout grid, MainMenuBackdrop.Cutout cutout,
                int color, int coldColor, UiHoneycombMaterialSpec material) {
        for (int index = 0; index < CAPACITY; index++) {
            Pulse pulse = pulses[(next + index) % CAPACITY];
            if (pulse.created == 0L) continue;
            float age = (lastFrame - pulse.created) * 1e-9f;
            if (age < 0f || age > LIFETIME) continue;
            Renderer2D.COLOR.honeycombPulse(viewportWidth, viewportHeight,
                    grid.radius(), MainMenuBackdrop.HONEYCOMB_RIM_WIDTH, opacity,
                    pulse.x, pulse.y, age, grid.radius() * 4.2f,
                    grid.originX(), grid.originY(), color, coldColor, material,
                    cutout.x(), cutout.y(), cutout.w(), cutout.h(), cutout.cut());
        }
    }

    int sourceCount() { return sourceCount; }
    PlasmaSource source(int i) { return sources[i]; }

    void clear() { reset(); }

    private void reset() {
        for (Pulse pulse : pulses) pulse.created = 0L;
        next = 0;
        lastFrame = 0L;
        serial = 0L;
        sourceCursor = 0;
        sourceCount = 0;
        lastPlasmaEmission = 0L;
        tracking = false;
    }

    private static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }
    private static float smoothstep(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static final class PlasmaSource {
        float x;
        float y;
        float vx;
        float vy;
        float intensity;
        float tint;
        float radius;
        float momentum;
    }

    private static final class Pulse {
        float x;
        float y;
        float dx;
        float dy;
        float energy;
        long created;
        long emitted;
        long serial;
    }
}
