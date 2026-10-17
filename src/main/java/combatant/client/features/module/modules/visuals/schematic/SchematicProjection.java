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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;

/**
 * Ghost render of the active schematic placement: missing blocks as translucent boxes, wrong blocks outlined.
 *
 * <p>The box list is rebuilt on the tick thread only when the placement, layer window, diff or the player's
 * position (in 8-block steps) changes, into preallocated arrays. Frames only walk those arrays, so a frame
 * costs nothing but vertex writes.</p>
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
    private static final double GHOST_INSET = 0.08;
    private static final double OVERLAY_GROW = 0.004;

    private final EnumValue<LayerMode> mode = enumMode("mode", LayerMode.CURRENT_LAYER);
    private final NumberValue<Integer> layerRange =
            visibleWhen(num("layer_range", 3, 1, 64), () -> mode.get() == LayerMode.LAYER_RANGE);
    private final BooleanValue showMissing = bool("show_missing", true);
    private final BooleanValue showWrong = bool("show_wrong", true);
    private final BooleanValue showCorrect = bool("show_correct", false);
    private final NumberValue<Integer> renderDistance = num("render_distance", 64, 8, 256);
    private final NumberValue<Integer> maxBoxes = num("max_boxes", 4000, 100, 30000);
    private final NumberValue<Integer> opacity = num("opacity", 30, 5, 100);
    private final BooleanValue outline = bool("outline", true);
    private final BooleanValue throughWalls = bool("through_walls", false);
    private final NumberValue<Integer> scanPerTick = num("scan_per_tick", 512, 64, 8192);
    private final RGBAColorValue missingColor = color("missing_color", "#FF4FC3F7");
    private final RGBAColorValue wrongColor = color("wrong_color", "#FFFF9800");
    private final RGBAColorValue correctColor = color("correct_color", "#FF66BB6A");

    private final Minecraft mc = Minecraft.getInstance();
    private final SchematicState state = SchematicState.INSTANCE;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final int[] distanceBuckets = new int[258];

    private int[] boxX = new int[0];
    private int[] boxY = new int[0];
    private int[] boxZ = new int[0];
    private byte[] boxStatus = new byte[0];
    private byte[] faceMask = new byte[0];
    private final SchematicFaceCulling faceCulling = new SchematicFaceCulling();
    private int boxCount;
    // Reused one-cell box moved in place for the frustum test; Frustum#isVisible(AABB) would need a fresh AABB per cell.
    private final BoundingBox cullBox = new BoundingBox(0, 0, 0, 0, 0, 0);
    private int cullX, cullY, cullZ;

    private LayerMode appliedMode;
    private int builtStateRevision = -1;
    private int builtDiffRevision = -1;
    private SchematicDiff builtDiff;
    private long builtPlayerCell = Long.MIN_VALUE;
    private int builtSettingsKey;
    private int ticksSinceRebuild;

    {
        addAction(ACTION_LAYER_UP, "PAGE_UP", BindMode.PRESS);
        addAction(ACTION_LAYER_DOWN, "PAGE_DOWN", BindMode.PRESS);
    }

    @Override
    public void onEnable() {
        appliedMode = null;
        invalidate();
    }

    @Override
    public void onDisable() {
        boxCount = 0;
        builtDiff = null;
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
            faceMask = new byte[cap];
        }
        int range = renderDistance.get();
        int total = countByDistance(diff, player, range);
        int cutoff = total > cap ? distanceCutoff(cap) : range;
        boxCount = collect(diff, player, range, cutoff, cap);
        faceCulling.compute(boxX, boxY, boxZ, boxStatus, boxCount, faceMask);
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
            // Bucket 0 alone over the cap still keeps the nearest cells (collect stops at the cap) instead of none.
            if (sum > cap) return Math.max(0, d - 1);
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
        if (!isEnabled() || renderer == null || boxCount == 0) return;
        Renderer3D.DepthMode depth = throughWalls.get() ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN;
        MeshBuilder tris = renderer.batch(WorldRenderPipelines.WORLD_COLORED, depth);
        MeshBuilder lines = null;
        if (outline.get()) {
            float previous = RenderState.lineWidth;
            try {
                RenderState.lineWidth = 1.5f;
                lines = renderer.batch(WorldRenderPipelines.WORLD_COLORED_LINES, depth);
            } finally {
                RenderState.lineWidth = previous;
            }
        }
        drawBoxes(tris, lines);
    }

    /** Whole-cell frustum test; Frustum returns -2 (inside) / -1 (intersecting) for anything visible. */
    private boolean cellVisible(Frustum frustum, int x, int y, int z) {
        cullBox.move(x - cullX, y - cullY, z - cullZ);
        cullX = x;
        cullY = y;
        cullZ = z;
        return frustum.cubeInFrustum(cullBox) < 0;
    }

    private void drawBoxes(MeshBuilder tris, MeshBuilder lines) {
        int fillAlpha = Math.round(opacity.get() * 2.55f);
        int missing = missingColor.getArgb();
        int wrong = wrongColor.getArgb();
        int correct = correctColor.getArgb();
        Frustum frustum = RenderState.frustum;
        for (int i = 0; i < boxCount; i++) {
            int x = boxX[i], y = boxY[i], z = boxZ[i];
            // The whole cell, not its centre: a box half on screen at the frustum edge must still draw.
            if (frustum != null && !cellVisible(frustum, x, y, z)) continue;
            byte status = boxStatus[i];
            int argb = status == SchematicDiff.MISSING ? missing : status == SchematicDiff.WRONG ? wrong : correct;
            // Missing cells are empty, so a slightly smaller cube reads as a ghost; overlays on real blocks
            // grow a hair instead so they do not z-fight with the block faces.
            double pad = status == SchematicDiff.MISSING ? GHOST_INSET : -OVERLAY_GROW;
            double minX = x + pad, minY = y + pad, minZ = z + pad;
            double maxX = x + 1.0 - pad, maxY = y + 1.0 - pad, maxZ = z + 1.0 - pad;
            // Real blocks already show their own texture, so their tint is half as strong as a ghost's.
            int alpha = Math.min(fillAlpha, (argb >>> 24) & 0xFF);
            if (status != SchematicDiff.MISSING) alpha >>= 1;
            filledBox(tris, minX, minY, minZ, maxX, maxY, maxZ, (argb & 0x00FFFFFF) | (alpha << 24), faceMask[i]);
            if (lines != null) outlineBox(lines, minX, minY, minZ, maxX, maxY, maxZ, argb);
        }
    }

    private static void filledBox(MeshBuilder tris, double x1, double y1, double z1, double x2, double y2, double z2, int argb, int faces) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        if ((faces & SchematicFaceCulling.FACE_DOWN) != 0) quad(tris, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        if ((faces & SchematicFaceCulling.FACE_UP) != 0) quad(tris, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        if ((faces & SchematicFaceCulling.FACE_SOUTH) != 0) quad(tris, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        if ((faces & SchematicFaceCulling.FACE_NORTH) != 0) quad(tris, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        if ((faces & SchematicFaceCulling.FACE_EAST) != 0) quad(tris, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
        if ((faces & SchematicFaceCulling.FACE_WEST) != 0) quad(tris, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
    }

    private static void outlineBox(MeshBuilder lines, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        line(lines, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(lines, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(lines, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(lines, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(lines, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(lines, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(lines, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(lines, x1, y2, z2, x1, y2, z1, r, g, b, a);
        line(lines, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(lines, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(lines, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(lines, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(MeshBuilder lines, double x1, double y1, double z1, double x2, double y2, double z2,
                             int r, int g, int b, int a) {
        lines.ensureLineCapacity();
        int i1 = lines.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = lines.vec3(x2, y2, z2).color(r, g, b, a).next();
        lines.line(i1, i2);
    }

    private static void quad(MeshBuilder tris,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             int r, int g, int b, int a) {
        tris.ensureQuadCapacity();
        int i1 = tris.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = tris.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = tris.vec3(x3, y3, z3).color(r, g, b, a).next();
        int i4 = tris.vec3(x4, y4, z4).color(r, g, b, a).next();
        tris.quad(i1, i2, i3, i4);
    }
}
