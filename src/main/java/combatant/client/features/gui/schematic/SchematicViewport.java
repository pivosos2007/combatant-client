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
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicDiff;

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
    private static final int MAX_FACES = 25_000;
    /** Most sprite repeats drawn on one coarse face before the sprite is stretched over it instead. */
    private static final int MAX_TILES = 16;
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
    private final Consumer<Renderer2D.QuadBatch> footprintBatch = this::drawFootprint;
    private final Consumer<Renderer2D.QuadBatch> hoverBatch = this::drawHover;
    private final Consumer<Renderer2D.QuadBatch> flatRun = this::drawFlatRun;
    private final Consumer<Renderer2D.TexturedQuadBatch> texturedRun = this::drawTexturedRun;

    private ViewMode mode = ViewMode.SOLID_BELOW;
    private float gap = 1f;
    private boolean autoRotate;

    private Schematic schematic;
    private VoxelVolume volume;
    private int[] paletteColors = new int[0];
    private SchematicTextures.Entry[] textures;
    private combatant.client.features.gui.schematic.view3d.BlockShape[] shapes;
    private Schematic texturesOwner;
    private int texturesGeneration = -1;
    private GpuTextureView atlasView;
    private GpuSampler atlasSampler;
    private boolean faceTinted;
    private int runFrom, runTo;
    private final float[] cornerX = new float[4], cornerY = new float[4];
    private final float[] tileX = new float[4], tileY = new float[4], tileU = new float[4], tileV = new float[4];
    private final int[] tileC = new int[4];
    private int layer;

    private FaceList faces;
    private ViewMode facesMode;
    private int facesLayer = -1;
    private float facesGap = -1f;
    private CompletableFuture<FaceList> pending;
    private ViewMode pendingMode;
    private int pendingLayer;
    private float pendingGap;

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
        refreshTextures();
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
            drawScene();
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
        hovered = -1;
        camera.clearFrame();
        camera.reset();
    }

    /** Palette sprites are resolved once per schematic and atlas generation; the per-frame cost is two getters. */
    private void refreshTextures() {
        int generation = SchematicTextures.generation();
        if (textures == null || texturesOwner != schematic || texturesGeneration != generation) {
            textures = SchematicTextures.resolvePalette(schematic);
            texturesOwner = schematic;
            texturesGeneration = generation;
            shapes = SchematicTextures.shapesOf(textures);
            faces = null;
        }
        atlasView = SchematicTextures.view();
        atlasSampler = SchematicTextures.sampler();
        if (atlasSampler == null) atlasView = null;
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
            FaceList built = pending.getNow(null);
            if (built != null) adopt(built, pendingMode, pendingLayer, pendingGap);
            pending = null;
        }
        if (faces != null && facesMode == mode && facesLayer == layer && facesGap == meshGap) return;
        if (schematic.volume() <= SYNC_VOLUME) {
            adopt(FaceMesher.build(volume, shapes, paletteColors, mode, layer, meshGap, MAX_FACES), mode, layer, meshGap);
            return;
        }
        if (pending != null) return;
        pendingMode = mode;
        pendingLayer = layer;
        pendingGap = meshGap;
        VoxelVolume source = volume;
        combatant.client.features.gui.schematic.view3d.BlockShape[] jobShapes = shapes;
        int[] colors = paletteColors;
        ViewMode jobMode = mode;
        int jobLayer = layer;
        pending = CompletableFuture.supplyAsync(() -> FaceMesher.build(source, jobShapes, colors, jobMode, jobLayer, meshGap, MAX_FACES), MESHER);
    }

    private void adopt(FaceList built, ViewMode builtMode, int builtLayer, float builtGap) {
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

    private void drawScene() {
        Renderer2D.COLOR.quadBatch(footprintBatch);
        faceTinted = statusFaces == faces && statusFaces != null;
        for (int i = 0, n = frame.visible(); i < n; ) {
            boolean textured = textured(frame.face(i));
            int j = i + 1;
            while (j < n && textured(frame.face(j)) == textured) j++;
            runFrom = i;
            runTo = j;
            if (textured) {
                Renderer2D.COLOR.texturedQuadBatch(atlasView, atlasSampler, texturedRun);
            } else {
                Renderer2D.COLOR.quadBatch(flatRun);
            }
            i = j;
        }
        if (hovered >= 0) Renderer2D.COLOR.quadBatch(hoverBatch);
    }

    private void drawHover(Renderer2D.QuadBatch batch) {
        drawVoxelBox(batch, hovered, SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 235f)));
    }

    private void drawFlatRun(Renderer2D.QuadBatch batch) {
        for (int i = runFrom; i < runTo; i++) {
            int f = frame.face(i);
            drawFace(batch, f, faceColor(f, faces.color(f)));
        }
    }

    private void drawTexturedRun(Renderer2D.TexturedQuadBatch batch) {
        for (int i = runFrom; i < runTo; i++) {
            int f = frame.face(i);
            drawTexturedFace(batch, f, faceColor(f, faces.light(f)));
        }
    }

    /** Diff-state tint and hover highlight over a face's base colour (map colour when flat, white light when textured). */
    private int faceColor(int f, int base) {
        int color = base;
        if (faceTinted) color = tint(color, faceStatus[f]);
        if (hovered >= 0 && faces.voxel(f) == hovered) color = keepAlpha(FaceMesher.mix(color, SchematicUi.ACCENT, 0.45f), color);
        return SchematicUi.scaleAlpha(color, alpha);
    }

    /** True when the face's block has an atlas sprite on that side, so it can be drawn from the block atlas. */
    private boolean textured(int f) {
        if (textures == null || atlasView == null) return false;
        if (faces.isQuad(f)) return true;
        return textures[volume.cell(faces.voxel(f))].has[faces.direction(f) * SchematicTextures.LAYERS];
    }

    /**
     * One face from the block atlas, the sprite repeated once per block it spans (coarse level-of-detail blocks
     * span several; past {@link #MAX_TILES} tiles the sprite is stretched over the face instead). {@code color}
     * multiplies the sprite, so the diff and hover tints sit on top of the texture rather than replacing it.
     */
    private void drawTexturedFace(Renderer2D.TexturedQuadBatch batch, int f, int color) {
        if (faces.isQuad(f)) {
            int lit = SchematicTextures.multiply(color, faces.quadTint(f));
            batch.quad(
                    frame.screen(f, 0, 0), frame.screen(f, 0, 1), faces.quadUv(f, 0, 0), faces.quadUv(f, 0, 1), lit,
                    frame.screen(f, 1, 0), frame.screen(f, 1, 1), faces.quadUv(f, 1, 0), faces.quadUv(f, 1, 1), lit,
                    frame.screen(f, 2, 0), frame.screen(f, 2, 1), faces.quadUv(f, 2, 0), faces.quadUv(f, 2, 1), lit,
                    frame.screen(f, 3, 0), frame.screen(f, 3, 1), faces.quadUv(f, 3, 0), faces.quadUv(f, 3, 1), lit);
            return;
        }
        int d = faces.direction(f);
        SchematicTextures.Entry entry = textures[volume.cell(faces.voxel(f))];
        boolean side = d >= FaceList.EAST;
        for (int c = 0; c < 4; c++) {
            int slot = FaceList.textureUv(d, c, 0) + 2 * FaceList.textureUv(d, c, 1);
            cornerX[slot] = frame.screen(f, c, 0);
            cornerY[slot] = frame.screen(f, c, 1);
        }
        int nu = Math.max(1, (int) Math.ceil(faces.textureSpan(f, 0) - 1.0e-3f));
        int nv = Math.max(1, (int) Math.ceil(faces.textureSpan(f, 1) - 1.0e-3f));
        if (nu * nv > MAX_TILES) {
            nu = 1;
            nv = 1;
        }
        for (int layer = 0; layer < SchematicTextures.LAYERS; layer++) {
            int slot = d * SchematicTextures.LAYERS + layer;
            if (!entry.has[slot]) continue;
            int lit = SchematicTextures.multiply(color, entry.tint[slot]);
            // Sides fade a little towards their foot, as on the flat faces.
            int upper = side ? keepAlpha(FaceMesher.mix(lit, 0xFFFFFFFF, 0.05f), lit) : lit;
            int lower = side ? keepAlpha(FaceMesher.mix(lit, 0xFF000000, 0.14f), lit) : lit;
            float u0 = entry.uv[slot * 4], v0 = entry.uv[slot * 4 + 1], u1 = entry.uv[slot * 4 + 2], v1 = entry.uv[slot * 4 + 3];
            for (int tj = 0; tj < nv; tj++) {
                float b0 = tj / (float) nv, b1 = (tj + 1) / (float) nv;
                int top = side ? FaceMesher.mix(upper, lower, b0) : upper;
                int bottom = side ? FaceMesher.mix(upper, lower, b1) : lower;
                for (int ti = 0; ti < nu; ti++) {
                    float a0 = ti / (float) nu, a1 = (ti + 1) / (float) nu;
                    for (int c = 0; c < 4; c++) {
                        int bu = FaceList.textureUv(d, c, 0), bv = FaceList.textureUv(d, c, 1);
                        float a = bu == 0 ? a0 : a1, b = bv == 0 ? b0 : b1;
                        tileX[c] = lerp(lerp(cornerX[0], cornerX[1], a), lerp(cornerX[2], cornerX[3], a), b);
                        tileY[c] = lerp(lerp(cornerY[0], cornerY[1], a), lerp(cornerY[2], cornerY[3], a), b);
                        tileU[c] = bu == 0 ? u0 : u1;
                        tileV[c] = bv == 0 ? v0 : v1;
                        tileC[c] = bv == 0 ? top : bottom;
                    }
                    batch.quad(tileX[0], tileY[0], tileU[0], tileV[0], tileC[0],
                            tileX[1], tileY[1], tileU[1], tileV[1], tileC[1],
                            tileX[2], tileY[2], tileU[2], tileV[2], tileC[2],
                            tileX[3], tileY[3], tileU[3], tileV[3], tileC[3]);
                }
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
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
            case SchematicDiff.CORRECT -> keepAlpha(FaceMesher.mix(color, SchematicUi.GOOD, 0.35f), color);
            case SchematicDiff.WRONG -> keepAlpha(FaceMesher.mix(color, SchematicUi.BAD, 0.55f), color);
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
