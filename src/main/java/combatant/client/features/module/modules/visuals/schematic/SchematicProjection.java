/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.schematic;

import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.schematic.SchematicState.LayerMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Ghost render of the active schematic placement. Missing blocks are drawn with their real block model at reduced
 * alpha (stairs, slabs and fences keep their shape), wrong blocks get a red-orange tint, and only the exposed
 * boundary of the visible layers is outlined with a soft glow. A translucent grid marks the active layer and an
 * optional dashed box marks the schematic bounds and origin.
 *
 * <p>The box list is rebuilt on the tick thread only when the placement, layer window, diff or the player's
 * position (in 8-block steps) changes, into preallocated arrays together with each box's block model and its
 * neighbour mask. Block geometry is baked once per state ({@link SchematicGhostModel}); frames only walk those
 * arrays and write vertices.</p>
 */
@ModuleInfo(
        id = "schematicprojection",
        displayName = "SchematicProjection",
        aliases = {"Litematica", "SchematicRender", "BuildGhost"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.schematicprojection.description")
public final class SchematicProjection extends Module {

    private static final String ACTION_LAYER_UP = "layer_up";
    private static final String ACTION_LAYER_DOWN = "layer_down";
    // Diff changes stream in while the round-robin scan runs; rebuilding on every one of them is wasted work.
    private static final int DIFF_REBUILD_INTERVAL = 5;
    private static final double OVERLAY_GROW = 0.004;
    /** Textured model ghosts are only written for the nearest boxes; the rest fall back to flat shaded shapes. */
    private static final double MODEL_DISTANCE = 56.0;
    private static final int MAX_MODEL_BOXES = 3000;
    private static final double GLOW_DISTANCE = 40.0;
    private static final float GLOW_WIDTH = 4.0f;
    private static final float EDGE_WIDTH = 1.4f;
    private static final int GRID_RADIUS = 12;
    private static final double DASH = 0.7;
    private static final double DASH_PERIOD = 1.15;
    private static final double DASH_RANGE = 40.0;
    private static final float[] FACE_SHADE = {0.55f, 1.0f, 0.82f, 0.82f, 0.66f, 0.66f};

    private final EnumValue<LayerMode> mode = enumMode("mode", LayerMode.CURRENT_LAYER);
    private final NumberValue<Integer> layerRange =
            visibleWhen(num("layer_range", 3, 1, 64), () -> mode.get() == LayerMode.LAYER_RANGE);
    private final BooleanValue showMissing = bool("show_missing", true);
    private final BooleanValue showWrong = bool("show_wrong", true);
    private final BooleanValue showCorrect = bool("show_correct", false);
    private final NumberValue<Integer> renderDistance = num("render_distance", 64, 8, 256);
    private final NumberValue<Integer> maxBoxes = num("max_boxes", 4000, 100, 30000);
    private final NumberValue<Integer> opacity = num("opacity", 55, 5, 100);
    private final BooleanValue blockModels = bool("block_models", true);
    private final BooleanValue outline = bool("outline", true);
    private final NumberValue<Integer> fadeDistance = num("fade_distance", 56, 8, 256);
    private final BooleanValue pulse = bool("pulse", true);
    private final BooleanValue showLayerPlane = bool("show_layer_plane", true);
    private final BooleanValue showBoundingBox = bool("show_bounding_box", false);
    private final BooleanValue throughWalls = bool("through_walls", false);
    private final NumberValue<Integer> scanPerTick = num("scan_per_tick", 512, 64, 8192);
    private final RGBAColorValue missingColor = color("missing_color", "#FFA8DCFF");
    private final RGBAColorValue wrongColor = color("wrong_color", "#FFFF5B34");
    private final RGBAColorValue correctColor = color("correct_color", "#FF66BB6A");
    private final RGBAColorValue planeColor = color("plane_color", "#FF7FC8FF");
    private final RGBAColorValue boxColor = color("box_color", "#FFFFD27A");

    private final Minecraft mc = Minecraft.getInstance();
    private final SchematicState state = SchematicState.INSTANCE;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
    private final int[] distanceBuckets = new int[258];
    private final LongOpenHashSet solid = new LongOpenHashSet();

    private int[] boxX = new int[0];
    private int[] boxY = new int[0];
    private int[] boxZ = new int[0];
    private byte[] boxStatus = new byte[0];
    /** Bit per {@link net.minecraft.core.Direction} ordinal: that neighbour is a shown full cube. */
    private byte[] boxMask = new byte[0];
    private SchematicGhostModel[] boxModel = new SchematicGhostModel[0];
    private int boxCount;

    /** minX, minY, minZ, maxX, maxY, maxZ in world space (max exclusive). */
    private final double[] bounds = new double[6];
    private double planeY;
    private BlockPos origin = BlockPos.ZERO;

    private LayerMode appliedMode;
    private int builtStateRevision = -1;
    private int builtDiffRevision = -1;
    private SchematicDiff builtDiff;
    private long builtPlayerCell = Long.MIN_VALUE;
    private int builtSettingsKey;
    private int ticksSinceRebuild;

    // Per-frame scratch (render thread only).
    private Identifier frameAtlas;
    private MeshBuilder frameAtlasMesh;
    private Renderer3D frameRenderer;
    private Renderer3D.DepthMode frameDepth;
    private boolean frameThroughWalls;

    {
        addAction(ACTION_LAYER_UP, "PAGE_UP", BindMode.PRESS);
        addAction(ACTION_LAYER_DOWN, "PAGE_DOWN", BindMode.PRESS);
    }

    @Override
    public void onEnable() {
        appliedMode = null;
        SchematicGhostModel.clearCache();
        invalidate();
    }

    @Override
    public void onDisable() {
        boxCount = 0;
        builtDiff = null;
        solid.clear();
        SchematicGhostModel.clearCache();
        Arrays.fill(boxModel, null);
    }

    /** Called by the schematic menu so the setting and the shared state never disagree. */
    public void setLayerMode(LayerMode next) {
        if (next == null) return;
        mode.set(next);
        appliedMode = next;
        state.setLayerMode(next);
    }

    public LayerMode layerMode() {
        return mode.get();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null) return;

        if (appliedMode != mode.get()) {
            appliedMode = mode.get();
            state.setLayerMode(appliedMode);
        }
        state.setRangeSize(layerRange.get());
        if (isActionPressedOnce(ACTION_LAYER_UP)) state.stepLayer(1);
        if (isActionPressedOnce(ACTION_LAYER_DOWN)) state.stepLayer(-1);

        int budget = scanPerTick.get();
        state.tickDiff(mc, budget / 2, budget - budget / 2);

        ticksSinceRebuild++;
        SchematicDiff diff = state.isPlaced() ? state.diff(mc.level) : null;
        if (diff == null) {
            boxCount = 0;
            builtDiff = null;
            return;
        }
        if (needsRebuild(diff, player)) rebuild(diff, player);
    }

    private boolean needsRebuild(SchematicDiff diff, LocalPlayer player) {
        long cell = BlockPos.asLong(player.getBlockX() >> 3, player.getBlockY() >> 3, player.getBlockZ() >> 3);
        if (diff != builtDiff || state.revision() != builtStateRevision
                || cell != builtPlayerCell || settingsKey() != builtSettingsKey) return true;
        return diff.revision() != builtDiffRevision && ticksSinceRebuild >= DIFF_REBUILD_INTERVAL;
    }

    private int settingsKey() {
        int key = renderDistance.get();
        key = key * 31 + maxBoxes.get();
        key = key * 31 + (showMissing.get() ? 1 : 0);
        key = key * 31 + (showWrong.get() ? 1 : 0);
        return key * 31 + (showCorrect.get() ? 1 : 0);
    }

    private void invalidate() {
        builtDiff = null;
        builtStateRevision = -1;
    }

    private boolean shown(byte status) {
        return switch (status) {
            case SchematicDiff.MISSING -> showMissing.get();
            case SchematicDiff.WRONG -> showWrong.get();
            case SchematicDiff.CORRECT -> showCorrect.get();
            default -> false;
        };
    }

    private void rebuild(SchematicDiff diff, LocalPlayer player) {
        builtDiff = diff;
        builtStateRevision = state.revision();
        builtDiffRevision = diff.revision();
        builtPlayerCell = BlockPos.asLong(player.getBlockX() >> 3, player.getBlockY() >> 3, player.getBlockZ() >> 3);
        builtSettingsKey = settingsKey();
        ticksSinceRebuild = 0;

        int cap = maxBoxes.get();
        if (boxX.length != cap) {
            boxX = new int[cap];
            boxY = new int[cap];
            boxZ = new int[cap];
            boxStatus = new byte[cap];
            boxMask = new byte[cap];
            boxModel = new SchematicGhostModel[cap];
        }
        int range = renderDistance.get();
        int total = countByDistance(diff, player, range);
        int cutoff = total > cap ? distanceCutoff(cap) : range;
        boxCount = collect(diff, player, range, cutoff, cap);
        computeMasks();
        computeBounds(diff);
    }

    private void computeBounds(SchematicDiff diff) {
        var schematic = diff.schematic();
        var placement = diff.placement();
        int[] b = placement.worldBounds(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ());
        bounds[0] = b[0];
        bounds[1] = b[1];
        bounds[2] = b[2];
        bounds[3] = b[3] + 1.0;
        bounds[4] = b[4] + 1.0;
        bounds[5] = b[5] + 1.0;
        planeY = placement.worldY(state.layer());
        origin = placement.origin();
    }

    /** Marks, per box, which face neighbours are shown full cubes, so shared faces and edges are never drawn. */
    private void computeMasks() {
        solid.clear();
        for (int i = 0; i < boxCount; i++) {
            if (boxModel[i].fullCube) solid.add(BlockPos.asLong(boxX[i], boxY[i], boxZ[i]));
        }
        net.minecraft.core.Direction[] dirs = net.minecraft.core.Direction.values();
        for (int i = 0; i < boxCount; i++) {
            int mask = 0;
            for (int d = 0; d < 6; d++) {
                neighbour.set(boxX[i] + dirs[d].getStepX(), boxY[i] + dirs[d].getStepY(), boxZ[i] + dirs[d].getStepZ());
                if (solid.contains(neighbour.asLong())) mask |= 1 << d;
            }
            boxMask[i] = (byte) mask;
        }
    }

    /** First pass: how many shown cells sit at each whole-block distance, so the nearest {@code cap} can be kept. */
    private int countByDistance(SchematicDiff diff, LocalPlayer player, int range) {
        Arrays.fill(distanceBuckets, 0);
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double rangeSq = (double) range * range;
        int total = 0;
        for (int y = state.visibleLow(); y <= state.visibleHigh(); y++) {
            if (Math.abs(diff.placement().worldY(y) - py) > range) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                if (!shown(diff.status(cell))) continue;
                diff.worldPos(cell, scratch);
                double dx = scratch.getX() + 0.5 - px, dy = scratch.getY() + 0.5 - py, dz = scratch.getZ() + 0.5 - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > rangeSq) continue;
                distanceBuckets[Math.min(distanceBuckets.length - 1, (int) Math.sqrt(distSq))]++;
                total++;
            }
        }
        return total;
    }

    private int distanceCutoff(int cap) {
        int sum = 0;
        for (int d = 0; d < distanceBuckets.length; d++) {
            sum += distanceBuckets[d];
            if (sum > cap) return d - 1;
        }
        return distanceBuckets.length - 1;
    }

    private int collect(SchematicDiff diff, LocalPlayer player, int range, int cutoff, int cap) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double rangeSq = (double) range * range;
        double cutoffSq = (cutoff + 1.0) * (cutoff + 1.0);
        int count = 0;
        for (int y = state.visibleLow(); y <= state.visibleHigh() && count < cap; y++) {
            if (Math.abs(diff.placement().worldY(y) - py) > range) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end && count < cap; cell++) {
                byte status = diff.status(cell);
                if (!shown(status)) continue;
                diff.worldPos(cell, scratch);
                double dx = scratch.getX() + 0.5 - px, dy = scratch.getY() + 0.5 - py, dz = scratch.getZ() + 0.5 - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > rangeSq || distSq >= cutoffSq) continue;
                boxX[count] = scratch.getX();
                boxY[count] = scratch.getY();
                boxZ[count] = scratch.getZ();
                boxStatus[count] = status;
                BlockState expected = status == SchematicDiff.MISSING ? diff.expectedState(cell) : null;
                boxModel[count] = expected == null ? SchematicGhostModel.UNIT_CUBE
                        : SchematicGhostModel.of(expected, scratch);
                count++;
            }
        }
        return count;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || builtDiff == null) return;
        boolean walls = throughWalls.get();
        Renderer3D.DepthMode depth = walls ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN;
        Vec3 cam = RenderState.cameraPos;

        MeshBuilder tris = renderer.batch(WorldRenderPipelines.WORLD_COLORED, depth);
        MeshBuilder glow = null;
        MeshBuilder core;
        float previous = RenderState.lineWidth;
        try {
            if (outline.get() && boxCount > 0) {
                RenderState.lineWidth = GLOW_WIDTH;
                glow = renderer.batch(WorldRenderPipelines.WORLD_COLORED_LINES, depth);
            }
            RenderState.lineWidth = EDGE_WIDTH;
            core = renderer.batch(WorldRenderPipelines.WORLD_COLORED_LINES, depth);
        } finally {
            RenderState.lineWidth = previous;
        }

        float time = (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000.0f;
        if (showLayerPlane.get()) drawLayerPlane(tris, core, cam);
        if (showBoundingBox.get()) drawBounds(core, cam);

        frameRenderer = renderer;
        frameDepth = depth;
        frameThroughWalls = walls;
        frameAtlas = null;
        frameAtlasMesh = null;
        try {
            drawBoxes(tris, core, glow, cam, time);
        } finally {
            frameRenderer = null;
            frameAtlas = null;
            frameAtlasMesh = null;
        }
    }

    // ------------------------------------------------------------------ ghost blocks

    private void drawBoxes(MeshBuilder tris, MeshBuilder core, MeshBuilder glow, Vec3 cam, float time) {
        float fillOpacity = opacity.get() / 100.0f;
        int missing = missingColor.getArgb();
        int wrong = wrongColor.getArgb();
        int correct = correctColor.getArgb();
        // Ghosts are tinted toward the missing colour but stay mostly white so the real texture reads through.
        int ghostTint = mix(0xFFFFFF, missing & 0xFFFFFF, 0.4f);
        float missingAlpha = ((missing >>> 24) & 0xFF) / 255.0f;
        float wrongAlpha = ((wrong >>> 24) & 0xFF) / 255.0f;
        float correctAlpha = ((correct >>> 24) & 0xFF) / 255.0f;
        boolean models = blockModels.get();
        boolean doPulse = pulse.get();
        boolean doOutline = outline.get();
        double fadeEnd = fadeDistance.get();
        double fadeStart = fadeEnd * 0.5;
        double modelRange = Math.min(MODEL_DISTANCE, fadeEnd + 8.0);
        int modelBudget = MAX_MODEL_BOXES;
        Frustum frustum = RenderState.frustum;

        for (int i = 0; i < boxCount; i++) {
            int x = boxX[i], y = boxY[i], z = boxZ[i];
            if (frustum != null && !frustum.pointInFrustum(x + 0.5, y + 0.5, z + 0.5)) continue;
            double dx = x + 0.5 - cam.x, dy = y + 0.5 - cam.y, dz = z + 0.5 - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float fade = fade(dist, fadeStart, fadeEnd);
            byte status = boxStatus[i];
            int mask = boxMask[i];

            if (status == SchematicDiff.MISSING) {
                float breathe = doPulse ? 0.82f + 0.18f * (float) Math.sin(time * 2.2f + (x + z) * 0.35f + y * 0.2f) : 1.0f;
                float alpha = fillOpacity * fade * breathe * missingAlpha;
                SchematicGhostModel model = boxModel[i];
                if (alpha > 0.01f) {
                    if (models && model.quadCount > 0 && dist < modelRange && modelBudget > 0) {
                        modelBudget--;
                        drawModel(model, x, y, z, mask, ghostTint, alpha);
                    } else {
                        drawShape(tris, model, x, y, z, mask, missing & 0xFFFFFF, alpha * 0.6f);
                    }
                }
                if (doOutline) {
                    float edge = Math.min(1.0f, 0.9f * fade * (0.75f + 0.25f * breathe) * missingAlpha);
                    drawEdges(core, glow, model, x, y, z, mask, missing & 0xFFFFFF, edge, dist);
                }
            } else {
                boolean isWrong = status == SchematicDiff.WRONG;
                int rgb = (isWrong ? wrong : correct) & 0xFFFFFF;
                float colorAlpha = isWrong ? wrongAlpha : correctAlpha;
                float alpha = fillOpacity * fade * colorAlpha * (isWrong ? 0.8f : 0.3f);
                if (alpha > 0.01f) {
                    flatBox(tris, x - OVERLAY_GROW, y - OVERLAY_GROW, z - OVERLAY_GROW,
                            x + 1.0 + OVERLAY_GROW, y + 1.0 + OVERLAY_GROW, z + 1.0 + OVERLAY_GROW,
                            mask, rgb, alpha);
                }
                if (doOutline) {
                    float edge = Math.min(1.0f, (isWrong ? 0.95f : 0.3f) * fade * colorAlpha);
                    drawEdges(core, glow, SchematicGhostModel.UNIT_CUBE, x, y, z, mask, rgb, edge, dist);
                }
            }
        }
    }

    private static float fade(double dist, double start, double end) {
        if (dist <= start) return 1.0f;
        float t = (float) Math.min(1.0, (dist - start) / Math.max(1.0, end - start));
        t = t * t * (3.0f - 2.0f * t);
        return 1.0f - 0.8f * t;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
    }

    /** Writes the real model quads of the block at reduced alpha, skipping quads hidden by a full-cube neighbour. */
    private void drawModel(SchematicGhostModel m, int x, int y, int z, int mask, int tint, float alpha) {
        int a = clampAlpha(alpha) << 24;
        int tr = (tint >>> 16) & 255, tg = (tint >>> 8) & 255, tb = tint & 255;
        for (int q = 0; q < m.quadCount; q++) {
            int cull = m.cullFace[q];
            if (cull >= 0 && (mask >> cull & 1) != 0) continue;
            MeshBuilder mesh = atlasMesh(m.atlas[q]);
            if (mesh == null) continue;
            int rgb = m.rgb[q];
            int r = tr * ((rgb >>> 16) & 255) / 255;
            int g = tg * ((rgb >>> 8) & 255) / 255;
            int b = tb * (rgb & 255) / 255;
            int argb = a | (r << 16) | (g << 8) | b;
            mesh.ensureQuadCapacity();
            int p = q * 12, t = q * 8;
            int i0 = mesh.vec3(x + m.pos[p], y + m.pos[p + 1], z + m.pos[p + 2])
                    .vec2(m.uv[t], m.uv[t + 1]).colorArgb(argb).next();
            int i1 = mesh.vec3(x + m.pos[p + 3], y + m.pos[p + 4], z + m.pos[p + 5])
                    .vec2(m.uv[t + 2], m.uv[t + 3]).colorArgb(argb).next();
            int i2 = mesh.vec3(x + m.pos[p + 6], y + m.pos[p + 7], z + m.pos[p + 8])
                    .vec2(m.uv[t + 4], m.uv[t + 5]).colorArgb(argb).next();
            int i3 = mesh.vec3(x + m.pos[p + 9], y + m.pos[p + 10], z + m.pos[p + 11])
                    .vec2(m.uv[t + 6], m.uv[t + 7]).colorArgb(argb).next();
            mesh.quad(i0, i1, i2, i3);
        }
    }

    private MeshBuilder atlasMesh(Identifier atlas) {
        if (atlas.equals(frameAtlas)) return frameAtlasMesh;
        frameAtlas = atlas;
        frameAtlasMesh = frameRenderer.batchTextured(
                frameThroughWalls ? WorldRenderPipelines.WORLD_TEXTURED : WorldRenderPipelines.WORLD_TEXTURED_DEPTH,
                atlas, frameDepth);
        return frameAtlasMesh;
    }

    /** Flat shaded stand-in for far ghosts: the block's collision boxes, so stairs and slabs keep their silhouette. */
    private static void drawShape(MeshBuilder tris, SchematicGhostModel m, int x, int y, int z, int mask,
                                  int rgb, float alpha) {
        float[] s = m.shape;
        int faceMask = m.fullCube ? mask : 0;
        for (int i = 0; i < s.length; i += 6) {
            flatBox(tris, x + s[i], y + s[i + 1], z + s[i + 2], x + s[i + 3], y + s[i + 4], z + s[i + 5],
                    faceMask, rgb, alpha);
        }
    }

    private static void flatBox(MeshBuilder tris, double x1, double y1, double z1, double x2, double y2, double z2,
                                int mask, int rgb, float alpha) {
        int a = clampAlpha(alpha);
        if (a == 0) return;
        if ((mask & 1) == 0) quad(tris, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, rgb, FACE_SHADE[0], a);
        if ((mask & 2) == 0) quad(tris, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, rgb, FACE_SHADE[1], a);
        if ((mask & 4) == 0) quad(tris, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, rgb, FACE_SHADE[2], a);
        if ((mask & 8) == 0) quad(tris, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, rgb, FACE_SHADE[3], a);
        if ((mask & 16) == 0) quad(tris, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, rgb, FACE_SHADE[4], a);
        if ((mask & 32) == 0) quad(tris, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, rgb, FACE_SHADE[5], a);
    }

    // ------------------------------------------------------------------ edges

    private void drawEdges(MeshBuilder core, MeshBuilder glow, SchematicGhostModel m, int x, int y, int z,
                           int mask, int rgb, float alpha, double dist) {
        int coreA = clampAlpha(alpha);
        if (coreA < 6) return;
        int glowA = glow != null && dist < GLOW_DISTANCE ? clampAlpha(alpha * 0.28f) : 0;
        float[] s = m.shape;
        if (m.fullCube) {
            boxEdges(core, glow, x, y, z, x + 1.0, y + 1.0, z + 1.0, mask, rgb, coreA, glowA);
        } else {
            for (int i = 0; i < s.length; i += 6) {
                boxEdges(core, glow, x + s[i], y + s[i + 1], z + s[i + 2], x + s[i + 3], y + s[i + 4], z + s[i + 5],
                        0, rgb, coreA, glowA);
            }
        }
    }

    /** Draws only edges whose two adjoining faces both lack a full-cube neighbour: the exposed boundary. */
    private static void boxEdges(MeshBuilder core, MeshBuilder glow, double x1, double y1, double z1,
                                 double x2, double y2, double z2, int mask, int rgb, int coreA, int glowA) {
        for (int ey = 0; ey < 2; ey++) {
            double y = ey == 0 ? y1 : y2;
            for (int ez = 0; ez < 2; ez++) {
                if ((mask >> ey & 1) != 0 || (mask >> (2 + ez) & 1) != 0) continue;
                double z = ez == 0 ? z1 : z2;
                edge(core, glow, x1, y, z, x2, y, z, rgb, coreA, glowA);
            }
        }
        for (int ex = 0; ex < 2; ex++) {
            double x = ex == 0 ? x1 : x2;
            for (int ez = 0; ez < 2; ez++) {
                if ((mask >> (4 + ex) & 1) != 0 || (mask >> (2 + ez) & 1) != 0) continue;
                double z = ez == 0 ? z1 : z2;
                edge(core, glow, x, y1, z, x, y2, z, rgb, coreA, glowA);
            }
            for (int ey = 0; ey < 2; ey++) {
                if ((mask >> (4 + ex) & 1) != 0 || (mask >> ey & 1) != 0) continue;
                double y = ey == 0 ? y1 : y2;
                edge(core, glow, x, y, z1, x, y, z2, rgb, coreA, glowA);
            }
        }
    }

    private static void edge(MeshBuilder core, MeshBuilder glow, double x1, double y1, double z1,
                             double x2, double y2, double z2, int rgb, int coreA, int glowA) {
        if (glowA > 0) line(glow, x1, y1, z1, x2, y2, z2, rgb, glowA);
        line(core, x1, y1, z1, x2, y2, z2, rgb, coreA);
    }

    // ------------------------------------------------------------------ layer plane / bounds

    private void drawLayerPlane(MeshBuilder tris, MeshBuilder lines, Vec3 cam) {
        int color = planeColor.getArgb();
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0f;
        int rgb = color & 0xFFFFFF;
        double x1 = bounds[0], z1 = bounds[2], x2 = bounds[3], z2 = bounds[5];
        double y = planeY + 0.01;

        flatPlane(tris, x1, y, z1, x2, z2, rgb, 0.06f * colorAlpha);

        // Border: dashed, a touch brighter than the grid.
        int borderA = clampAlpha(0.7f * colorAlpha);
        dashed(lines, x1, y, z1, 0, x2 - x1, cam.x, rgb, borderA);
        dashed(lines, x1, y, z2, 0, x2 - x1, cam.x, rgb, borderA);
        dashed(lines, x1, y, z1, 2, z2 - z1, cam.z, rgb, borderA);
        dashed(lines, x2, y, z1, 2, z2 - z1, cam.z, rgb, borderA);

        if (Math.abs(cam.y - y) > 40.0) return;
        // Grid: one-block cells around the player, fading out radially.
        int gx0 = (int) Math.max(Math.ceil(x1), Math.floor(cam.x) - GRID_RADIUS);
        int gx1 = (int) Math.min(Math.floor(x2), Math.floor(cam.x) + GRID_RADIUS);
        int gz0 = (int) Math.max(Math.ceil(z1), Math.floor(cam.z) - GRID_RADIUS);
        int gz1 = (int) Math.min(Math.floor(z2), Math.floor(cam.z) + GRID_RADIUS);
        float base = 0.32f * colorAlpha;
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz < gz1; gz += 2) {
                int end = Math.min(gz + 2, gz1);
                gridSegment(lines, gx, y, gz, gx, end, cam, rgb, base);
            }
        }
        for (int gz = gz0; gz <= gz1; gz++) {
            for (int gx = gx0; gx < gx1; gx += 2) {
                int end = Math.min(gx + 2, gx1);
                gridSegment(lines, gx, y, gz, end, gz, cam, rgb, base);
            }
        }
    }

    private static void gridSegment(MeshBuilder lines, double ax, double y, double az, double bx, double bz,
                                    Vec3 cam, int rgb, float base) {
        double mx = (ax + bx) * 0.5 - cam.x, mz = (az + bz) * 0.5 - cam.z;
        float t = (float) (Math.sqrt(mx * mx + mz * mz) / (GRID_RADIUS + 1.0));
        if (t >= 1.0f) return;
        int a = clampAlpha(base * (1.0f - t) * (1.0f - t));
        if (a < 4) return;
        line(lines, ax, y, az, bx, y, bz, rgb, a);
    }

    private void drawBounds(MeshBuilder lines, Vec3 cam) {
        int color = boxColor.getArgb();
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0f;
        int rgb = color & 0xFFFFFF;
        int a = clampAlpha(0.75f * colorAlpha);
        double x1 = bounds[0], y1 = bounds[1], z1 = bounds[2], x2 = bounds[3], y2 = bounds[4], z2 = bounds[5];
        for (int ey = 0; ey < 2; ey++) {
            double y = ey == 0 ? y1 : y2;
            for (int ez = 0; ez < 2; ez++) dashed(lines, x1, y, ez == 0 ? z1 : z2, 0, x2 - x1, cam.x, rgb, a);
            for (int ex = 0; ex < 2; ex++) dashed(lines, ex == 0 ? x1 : x2, y, z1, 2, z2 - z1, cam.z, rgb, a);
        }
        for (int ex = 0; ex < 2; ex++) {
            for (int ez = 0; ez < 2; ez++) {
                dashed(lines, ex == 0 ? x1 : x2, y1, ez == 0 ? z1 : z2, 1, y2 - y1, cam.y, rgb, a);
            }
        }
        // Solid corner caps give the dashed frame firmer, rounded-looking corners.
        int capA = clampAlpha(colorAlpha);
        double cap = 1.5;
        for (int ex = 0; ex < 2; ex++) {
            for (int ey = 0; ey < 2; ey++) {
                for (int ez = 0; ez < 2; ez++) {
                    double cx = ex == 0 ? x1 : x2, cy = ey == 0 ? y1 : y2, cz = ez == 0 ? z1 : z2;
                    double sx = ex == 0 ? cap : -cap, sy = ey == 0 ? cap : -cap, sz = ez == 0 ? cap : -cap;
                    line(lines, cx, cy, cz, cx + sx, cy, cz, rgb, capA);
                    line(lines, cx, cy, cz, cx, cy + sy, cz, rgb, capA);
                    line(lines, cx, cy, cz, cx, cy, cz + sz, rgb, capA);
                }
            }
        }
        // Origin marker: a small cube on schematic cell (0, 0, 0) with a pillar above it.
        double ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        boxEdges(lines, null, ox + 0.2, oy + 0.2, oz + 0.2, ox + 0.8, oy + 0.8, oz + 0.8, 0, rgb, capA, 0);
        line(lines, ox + 0.5, oy + 0.8, oz + 0.5, ox + 0.5, oy + 2.2, oz + 0.5, rgb, capA);
    }

    /** Axis-aligned dashed line starting at (x, y, z) running {@code len} along {@code axis}; only near {@code camAlong}. */
    private static void dashed(MeshBuilder lines, double x, double y, double z, int axis, double len,
                               double camAlong, int rgb, int a) {
        if (len <= 0.0 || a < 4) return;
        double start = axis == 0 ? x : axis == 1 ? y : z;
        double lo = Math.max(0.0, camAlong - start - DASH_RANGE);
        double hi = Math.min(len, camAlong - start + DASH_RANGE);
        for (double t = Math.floor(lo / DASH_PERIOD) * DASH_PERIOD; t < hi; t += DASH_PERIOD) {
            double s = Math.max(t, lo), e = Math.min(t + DASH, hi);
            if (e <= s) continue;
            switch (axis) {
                case 0 -> line(lines, x + s, y, z, x + e, y, z, rgb, a);
                case 1 -> line(lines, x, y + s, z, x, y + e, z, rgb, a);
                default -> line(lines, x, y, z + s, x, y, z + e, rgb, a);
            }
        }
    }

    // ------------------------------------------------------------------ primitives

    private static void flatPlane(MeshBuilder tris, double x1, double y, double z1, double x2, double z2, int rgb,
                                  float alpha) {
        int a = clampAlpha(alpha);
        if (a == 0) return;
        int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
        tris.ensureQuadCapacity();
        int i1 = tris.vec3(x1, y, z1).color(r, g, b, a).next();
        int i2 = tris.vec3(x1, y, z2).color(r, g, b, a).next();
        int i3 = tris.vec3(x2, y, z2).color(r, g, b, a).next();
        int i4 = tris.vec3(x2, y, z1).color(r, g, b, a).next();
        tris.quad(i1, i2, i3, i4);
    }

    private static void line(MeshBuilder lines, double x1, double y1, double z1, double x2, double y2, double z2,
                             int rgb, int a) {
        int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
        lines.ensureLineCapacity();
        int i1 = lines.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = lines.vec3(x2, y2, z2).color(r, g, b, a).next();
        lines.line(i1, i2);
    }

    private static void quad(MeshBuilder tris,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             int rgb, float shade, int a) {
        int r = Math.round(((rgb >>> 16) & 255) * shade);
        int g = Math.round(((rgb >>> 8) & 255) * shade);
        int b = Math.round((rgb & 255) * shade);
        tris.ensureQuadCapacity();
        int i1 = tris.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = tris.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = tris.vec3(x3, y3, z3).color(r, g, b, a).next();
        int i4 = tris.vec3(x4, y4, z4).color(r, g, b, a).next();
        tris.quad(i1, i2, i3, i4);
    }
}
