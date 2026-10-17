/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.features.gui.schematic.view3d.FaceFrame;
import combatant.client.features.gui.schematic.view3d.FaceList;
import combatant.client.features.gui.schematic.view3d.FaceMesher;
import combatant.client.features.gui.schematic.view3d.OrbitCamera;
import combatant.client.features.gui.schematic.view3d.ViewMode;
import combatant.client.features.gui.schematic.view3d.VoxelPicker;
import combatant.client.features.gui.schematic.view3d.VoxelVolume;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The schematic as an orbitable block model, drawn on the CPU: faces come from {@link FaceMesher} (rebuilt only
 * when the schematic, selected layer, view mode or gap change; off-thread for big builds), and every frame
 * {@link FaceFrame} culls, projects and depth-sorts them into one batch of 2D quads. Main thread only.
 */
final class SchematicViewport {

    static final float MAX_GAP = 4f;
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant/Schematic");
    private static final int MAX_FACES = 25_000;
    private static final int SYNC_VOLUME = 64 * 64 * 64;
    private static final float CLICK_SLOP = 4f;
    private static final long TINT_REFRESH_NANOS = 250_000_000L;
    private static final ExecutorService MESHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Combatant-Schematic-Mesh");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private final OrbitCamera camera = new OrbitCamera();
    private final FaceFrame frame = new FaceFrame();
    private final float[] ray = new float[6];
    private final float[] point = new float[16];
    private final VoxelPicker.Solid pickTest = this::pickable;
    private final Consumer<Renderer2D.QuadBatch> drawScene = this::drawScene;

    private ViewMode mode = ViewMode.SOLID_BELOW;
    private float gap = 1f;
    private boolean autoRotate;

    private Schematic schematic;
    private VoxelVolume volume;
    private int[] paletteColors = new int[0];
    private int layer;

    private FaceList faces;
    private ViewMode facesMode;
    private int facesLayer = -1;
    private float facesGap = -1f;
    private CompletableFuture<FaceList> pending;
    private ViewMode pendingMode;
    private int pendingLayer;
    private float pendingGap;
    // The mesh key whose build threw; remembered so a failing build is not retried every frame.
    private boolean meshFailed;
    private ViewMode failedMode;
    private int failedLayer;
    private float failedGap;

    private byte[] faceStatus = new byte[0];
    private FaceList statusFaces;
    private SchematicDiff statusDiff;
    private int statusRevision = -1;
    private long statusNanos;
    private SchematicDiff diff;

    private float x, y, w, h;
    private float alpha;
    private int hovered = -1;
    private boolean orbiting, panning;
    private float lastX, lastY, dragDistance;

    ViewMode mode() {
        return mode;
    }

    void cycleMode() {
        mode = mode.next();
    }

    float gap() {
        return gap;
    }

    void setGap(float next) {
        gap = Math.max(0f, Math.min(MAX_GAP, Math.round(next * 4f) / 4f));
    }

    boolean autoRotate() {
        return autoRotate;
    }

    void toggleAutoRotate() {
        autoRotate = !autoRotate;
    }

    void resetCamera() {
        camera.reset();
    }

    int hovered() {
        return hovered;
    }

    boolean dragging() {
        return orbiting || panning;
    }

    /** True while a big build is being re-meshed on the worker and the previous faces are still shown. */
    boolean meshing() {
        return pending != null;
    }

    /** True when the mesh for the current view could not be built; the screen shows an error instead of a spinner. */
    boolean meshFailed() {
        return meshFailed;
    }

    FaceList faces() {
        return faces;
    }

    // ---------------------------------------------------------------- frame

    void render(Schematic next, int[] colors, int selectedLayer, SchematicDiff currentDiff, float vx, float vy, float vw, float vh,
                float dt, float mouseX, float mouseY, boolean mouseInside, float opacity) {
        x = vx;
        y = vy;
        w = vw;
        h = vh;
        alpha = opacity;
        diff = currentDiff != null && currentDiff.schematic() == next ? currentDiff : null;
        bind(next, colors);
        layer = selectedLayer;
        ensureMesh();
        frameCamera();
        camera.update(dt, autoRotate && !orbiting);
        camera.computeView(x, y, w, h);
        hovered = mouseInside && !dragging() ? pick(mouseX, mouseY) : -1;
        if (faces == null) return;
        refreshStatus();
        frame.build(faces, camera, x, y, w, h);
        boolean clipped = ScissorFunction.pushRaw(x + 1f, y + 1f, w - 2f, h - 2f);
        try {
            Renderer2D.COLOR.quadBatch(drawScene);
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void bind(Schematic next, int[] colors) {
        paletteColors = colors;
        if (next == schematic) return;
        schematic = next;
        volume = new SchematicVolume(next);
        faces = null;
        facesLayer = -1;
        pending = null;
        meshFailed = false;
        hovered = -1;
        camera.clearFrame();
        camera.reset();
    }

    private void frameCamera() {
        float height = mode.layerBase(schematic.sizeY() - 1, gap) + 1f;
        float sx = schematic.sizeX(), sz = schematic.sizeZ();
        float radius = 0.5f * (float) Math.sqrt(sx * sx + height * height + sz * sz);
        camera.frame(sx * 0.5f, height * 0.5f, sz * 0.5f, radius);
    }

    // ---------------------------------------------------------------- meshing

    private void ensureMesh() {
        float meshGap = mode.exploded() ? gap : 0f;
        if (pending != null && pending.isDone()) {
            // handle() turns a failed job into a null result; getNow on the raw future would throw every frame.
            CompletableFuture<FaceList> done = pending;
            pending = null;
            FaceList built = done.handle((result, error) -> {
                if (error != null) LOGGER.warn("Schematic mesh build failed", error);
                return result;
            }).getNow(null);
            if (built != null) {
                adopt(built, pendingMode, pendingLayer, pendingGap);
            } else {
                markFailed(pendingMode, pendingLayer, pendingGap);
            }
        }
        if (faces != null && facesMode == mode && facesLayer == layer && facesGap == meshGap) return;
        if (meshFailed && failedMode == mode && failedLayer == layer && failedGap == meshGap) return;
        if (schematic.volume() <= SYNC_VOLUME) {
            try {
                adopt(FaceMesher.build(volume, paletteColors, mode, layer, meshGap, MAX_FACES), mode, layer, meshGap);
            } catch (RuntimeException e) {
                LOGGER.warn("Schematic mesh build failed", e);
                markFailed(mode, layer, meshGap);
            }
            return;
        }
        if (pending != null) return;
        pendingMode = mode;
        pendingLayer = layer;
        pendingGap = meshGap;
        VoxelVolume source = volume;
        int[] colors = paletteColors;
        ViewMode jobMode = mode;
        int jobLayer = layer;
        pending = CompletableFuture.supplyAsync(() -> FaceMesher.build(source, colors, jobMode, jobLayer, meshGap, MAX_FACES), MESHER);
    }

    private void markFailed(ViewMode failed, int failedAtLayer, float failedAtGap) {
        meshFailed = true;
        failedMode = failed;
        failedLayer = failedAtLayer;
        failedGap = failedAtGap;
    }

    private void adopt(FaceList built, ViewMode builtMode, int builtLayer, float builtGap) {
        meshFailed = false;
        faces = built;
        facesMode = builtMode;
        facesLayer = builtLayer;
        facesGap = builtGap;
        statusFaces = null;
    }

    /** Placement status per face, looked up once per diff revision instead of once per frame. */
    private void refreshStatus() {
        if (diff == null) {
            statusFaces = null;
            return;
        }
        long now = System.nanoTime();
        boolean sameFaces = statusFaces == faces && statusDiff == diff;
        if (sameFaces && (statusRevision == diff.revision() || now - statusNanos < TINT_REFRESH_NANOS)) return;
        statusFaces = faces;
        statusDiff = diff;
        statusRevision = diff.revision();
        statusNanos = now;
        if (faceStatus.length < faces.count()) faceStatus = new byte[faces.count()];
        for (int f = 0, n = faces.count(); f < n; f++) faceStatus[f] = statusOf(faces.voxel(f));
    }

    private byte statusOf(int schematicIndex) {
        int low = 0, high = diff.cellCount() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int value = diff.schematicIndex(mid);
            if (value < schematicIndex) {
                low = mid + 1;
            } else if (value > schematicIndex) {
                high = mid - 1;
            } else {
                return diff.status(mid);
            }
        }
        return SchematicDiff.UNKNOWN;
    }

    // ---------------------------------------------------------------- drawing

    private void drawScene(Renderer2D.QuadBatch batch) {
        drawFootprint(batch);
        boolean tinted = statusFaces == faces && statusFaces != null;
        for (int i = 0, n = frame.visible(); i < n; i++) {
            int f = frame.face(i);
            int color = faces.color(f);
            if (tinted) color = tint(color, faceStatus[f]);
            if (hovered >= 0 && faces.voxel(f) == hovered) color = keepAlpha(FaceMesher.mix(color, SchematicUi.ACCENT, 0.45f), color);
            drawFace(batch, f, SchematicUi.scaleAlpha(color, alpha));
        }
        if (hovered >= 0) drawVoxelBox(batch, hovered, SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 235f)));
    }

    private void drawFace(Renderer2D.QuadBatch batch, int f, int color) {
        float x0 = frame.screen(f, 0, 0), y0 = frame.screen(f, 0, 1);
        float x1 = frame.screen(f, 1, 0), y1 = frame.screen(f, 1, 1);
        float x2 = frame.screen(f, 2, 0), y2 = frame.screen(f, 2, 1);
        float x3 = frame.screen(f, 3, 0), y3 = frame.screen(f, 3, 1);
        boolean side = faces.direction(f) >= FaceList.EAST;
        // Sides fade a little towards their foot, which reads as soft ambient occlusion on walls.
        int upper = side ? keepAlpha(FaceMesher.mix(color, 0xFFFFFFFF, 0.05f), color) : color;
        int lower = side ? keepAlpha(FaceMesher.mix(color, 0xFF000000, 0.14f), color) : color;
        if (faces.faceClass(f) != ViewMode.SELECTED || Math.abs((x2 - x0) * (y3 - y1) - (x3 - x1) * (y2 - y0)) < 120f) {
            batch.quad(x0, y0, upper, x1, y1, upper, x2, y2, lower, x3, y3, lower);
            return;
        }
        // Selected layer: a darker rim with the face inset over it gives every block a crisp edge.
        int rim = keepAlpha(FaceMesher.mix(color, 0xFF000000, 0.45f), color);
        batch.quad(x0, y0, rim, x1, y1, rim, x2, y2, rim, x3, y3, rim);
        float cx = (x0 + x1 + x2 + x3) * 0.25f, cy = (y0 + y1 + y2 + y3) * 0.25f;
        float t = 0.09f;
        batch.quad(x0 + (cx - x0) * t, y0 + (cy - y0) * t, upper, x1 + (cx - x1) * t, y1 + (cy - y1) * t, upper,
                x2 + (cx - x2) * t, y2 + (cy - y2) * t, lower, x3 + (cx - x3) * t, y3 + (cy - y3) * t, lower);
    }

    private void drawFootprint(Renderer2D.QuadBatch batch) {
        if (camera.eyeY() < 0f) return;
        float sx = schematic.sizeX(), sz = schematic.sizeZ();
        if (!camera.project(0f, 0f, 0f, point, 0) || !camera.project(sx, 0f, 0f, point, 2)
                || !camera.project(sx, 0f, sz, point, 4) || !camera.project(0f, 0f, sz, point, 6)) return;
        int fill = SchematicUi.withAlpha(SchematicUi.mix(SchematicUi.WELL, SchematicUi.ACCENT, 0.12f), Math.round(alpha * 150f));
        batch.quad(point[0], point[1], fill, point[2], point[3], fill, point[4], point[5], fill, point[6], point[7], fill);
        int edge = SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 70f));
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            segment(batch, point[i * 2], point[i * 2 + 1], point[j * 2], point[j * 2 + 1], 1f, edge);
        }
    }

    private void drawVoxelBox(Renderer2D.QuadBatch batch, int index, int color) {
        int vx = schematic.xOf(index), vy = schematic.yOf(index), vz = schematic.zOf(index);
        float base = mode.layerBase(vy, gap);
        for (int c = 0; c < 8; c++) {
            if (!camera.project(vx + (c & 1), base + ((c >> 1) & 1), vz + ((c >> 2) & 1), point, c * 2)) return;
        }
        for (int a = 0; a < 8; a++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((a & bit) != 0) continue;
                int b = a | bit;
                segment(batch, point[a * 2], point[a * 2 + 1], point[b * 2], point[b * 2 + 1], 1.6f, color);
            }
        }
    }

    /** A line as a thin quad so it shares the face batch. */
    static void segment(Renderer2D.QuadBatch batch, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3f) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        batch.quad(x0 + nx, y0 + ny, color, x1 + nx, y1 + ny, color, x1 - nx, y1 - ny, color, x0 - nx, y0 - ny, color);
    }

    private static int tint(int color, byte status) {
        return switch (status) {
            case SchematicDiff.CORRECT -> keepAlpha(FaceMesher.mix(color, SchematicUi.GOOD, 0.5f), color);
            case SchematicDiff.MISSING -> keepAlpha(FaceMesher.mix(color, SchematicUi.BAD, 0.55f), color);
            case SchematicDiff.WRONG -> keepAlpha(FaceMesher.mix(color, SchematicUi.WARN, 0.6f), color);
            default -> color;
        };
    }

    private static int keepAlpha(int rgb, int alphaSource) {
        return (rgb & 0x00FFFFFF) | (alphaSource & 0xFF000000);
    }

    // ---------------------------------------------------------------- picking + input

    private int pick(float mx, float my) {
        if (schematic == null) return -1;
        camera.ray(mx, my, ray);
        return VoxelPicker.pick(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), mode.layerPitch(gap),
                ray[0], ray[1], ray[2], ray[3], ray[4], ray[5], pickTest);
    }

    private boolean pickable(int px, int py, int pz) {
        return schematic.paletteIndex(px, py, pz) != 0 && ViewMode.pickable(mode.classOf(py, layer));
    }

    void beginDrag(int button, float mx, float my) {
        orbiting = button == 0;
        panning = button != 0;
        lastX = mx;
        lastY = my;
        dragDistance = 0f;
    }

    void drag(float mx, float my) {
        float dx = mx - lastX, dy = my - lastY;
        lastX = mx;
        lastY = my;
        dragDistance += Math.abs(dx) + Math.abs(dy);
        if (orbiting) camera.orbit(dx, dy);
        if (panning) camera.pan(dx, dy);
    }

    /** Ends a drag; returns the layer to select when it was a plain left click on a block, else -1. */
    int endDrag(float mx, float my) {
        boolean click = orbiting && dragDistance < CLICK_SLOP;
        orbiting = false;
        panning = false;
        if (!click || schematic == null) return -1;
        int hit = pick(mx, my);
        return hit < 0 ? -1 : schematic.yOf(hit);
    }

    void zoom(float steps) {
        camera.zoom(steps);
    }

    /** Small isometric cube glyph for empty and loading states. */
    static void drawCubeIcon(float cx, float cy, float size, int color, float alpha) {
        Renderer2D.COLOR.quadBatch(batch -> {
            float hw = size * 0.866f, hh = size * 0.5f;
            int top = SchematicUi.scaleAlpha(color, alpha);
            int left = SchematicUi.scaleAlpha(SchematicUi.mix(color, 0xFF000000, 0.35f), alpha);
            int right = SchematicUi.scaleAlpha(SchematicUi.mix(color, 0xFF000000, 0.55f), alpha);
            batch.quad(cx, cy - size, top, cx + hw, cy - hh, top, cx, cy, top, cx - hw, cy - hh, top);
            batch.quad(cx - hw, cy - hh, left, cx, cy, left, cx, cy + size, left, cx - hw, cy + hh, left);
            batch.quad(cx, cy, right, cx + hw, cy - hh, right, cx + hw, cy + hh, right, cx, cy + size, right);
        });
    }

    /** Live view of the schematic's cells; nothing is copied, the mesher reads straight from the schematic. */
    private record SchematicVolume(Schematic schematic) implements VoxelVolume {
        @Override
        public int sizeX() {
            return schematic.sizeX();
        }

        @Override
        public int sizeY() {
            return schematic.sizeY();
        }

        @Override
        public int sizeZ() {
            return schematic.sizeZ();
        }

        @Override
        public int cell(int index) {
            return schematic.paletteIndexAt(index);
        }
    }
}
