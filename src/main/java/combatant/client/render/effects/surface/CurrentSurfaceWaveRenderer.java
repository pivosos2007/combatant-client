/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.surface;

import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.IntFunction;

/** Current-renderer adapter for SurfaceWaveDescriptor. */
public final class CurrentSurfaceWaveRenderer {
    private static final double OUTLINE_EPS = 0.0015;
    private static final double FILL_EPS = 0.0010;
    private static final float LINE_WIDTH_BASE = 0.72f;
    private static final float LINE_WIDTH_BOOST = 0.72f;
    private static final int SURFACE_SCAN_UP = 2;
    private static final int SURFACE_SCAN_DOWN = 4;

    private CurrentSurfaceWaveRenderer() {
    }

    public static void render(Renderer3D renderer,
                              Level level,
                              SurfaceWaveDescriptor descriptor,
                              long nowMs,
                              IntFunction<Integer> palette) {
        if (renderer == null || level == null || descriptor == null || palette == null) {
            return;
        }
        float progress = descriptor.progress(nowMs);
        if (progress >= 1.0f) {
            return;
        }

        float radiusProgress = smoothstep(progress);
        float currentRadius = radiusProgress * descriptor.maxRadius();
        float globalAlpha = smoothstep(1.0f - progress);
        float minRad = Math.max(0.0f, currentRadius - descriptor.trailWidth());
        float maxRad = currentRadius + descriptor.frontWidth();
        float minRadSq = minRad * minRad;
        float maxRadSq = maxRad * maxRad;

        Renderer3D.DepthMode depthMode = descriptor.depthTest()
                ? Renderer3D.DepthMode.PRE_DEPTH
                : Renderer3D.DepthMode.MAIN;
        MeshBuilder fillMesh = renderer.batch(
                descriptor.depthTest()
                        ? CombatantRenderPipelines.WORLD_COLORED_LIQUID_IGNORE
                        : CombatantRenderPipelines.WORLD_COLORED,
                depthMode
        );

        float previousWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(0.5f, LINE_WIDTH_BASE + globalAlpha * LINE_WIDTH_BOOST);
        MeshBuilder outlineMesh;
        try {
            outlineMesh = renderer.batch(
                    descriptor.depthTest()
                            ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE
                            : CombatantRenderPipelines.WORLD_COLORED_LINES,
                    depthMode
            );
        } finally {
            RenderState.lineWidth = previousWidth;
        }
        if (fillMesh == null && outlineMesh == null) {
            return;
        }

        BlockPos center = BlockPos.containing(descriptor.center().x, descriptor.center().y, descriptor.center().z);
        int scanRadius = Math.max(1, Mth.ceil(descriptor.maxRadius()));
        int rendered = 0;
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                if (rendered >= descriptor.maxCellsPerFrame()) {
                    return;
                }
                float distSq = x * x + z * z;
                if (distSq < minRadSq || distSq > maxRadSq) {
                    continue;
                }

                BlockPos renderPos = findSurface(level, center.offset(x, 0, z));
                if (renderPos == null) {
                    continue;
                }
                BlockState state = level.getBlockState(renderPos);
                VoxelShape shape = state.getShape(level, renderPos);
                if (shape.isEmpty()) {
                    continue;
                }

                float distance = (float) Math.sqrt(distSq);
                float ring = 1.0f - Math.abs(distance - currentRadius) / descriptor.frontWidth();
                float ringCoverage = smoothstep(ring);
                float ringAlpha = ringCoverage * globalAlpha;

                float behind = currentRadius - distance;
                float trail = behind >= 0.0f
                        ? 1.0f - smoothstep(behind / descriptor.trailWidth())
                        : 0.0f;
                float fillAlpha = globalAlpha * (ringCoverage * descriptor.fillStrength()
                        + trail * descriptor.trailStrength());
                if (ringAlpha <= descriptor.minAlpha() && fillAlpha <= descriptor.minAlpha()) {
                    continue;
                }
                rendered++;

                int colorIndex = (int) (Math.toDegrees(Math.atan2(z, x)) + 180.0);
                int baseColor = palette.apply(colorIndex);
                if (fillMesh != null && fillAlpha > descriptor.minAlpha()) {
                    addShapeFill(fillMesh, renderPos, shape, applyOpacity(baseColor, fillAlpha), FILL_EPS);
                }
                if (outlineMesh != null && ringAlpha > descriptor.minAlpha()) {
                    addShapeOutline(outlineMesh, renderPos, shape, applyOpacity(baseColor, ringAlpha), OUTLINE_EPS);
                }
            }
        }
    }

    private static BlockPos findSurface(Level level, BlockPos pos) {
        for (int y = SURFACE_SCAN_UP; y >= -SURFACE_SCAN_DOWN; y--) {
            BlockPos candidate = pos.above(y);
            BlockState state = level.getBlockState(candidate);
            if (!state.isAir() && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static float smoothstep(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(alpha * Mth.clamp(opacity, 0.0f, 1.0f)), 0, 255);
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static void addShapeOutline(MeshBuilder mesh, BlockPos pos, VoxelShape shape, int argb, double eps) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        VoxelShape offset = shape.move(pos.getX(), pos.getY(), pos.getZ());
        offset.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            double ax1 = x1 + Math.signum(x1 - cx) * eps;
            double ay1 = y1 + Math.signum(y1 - cy) * eps;
            double az1 = z1 + Math.signum(z1 - cz) * eps;
            double ax2 = x2 + Math.signum(x2 - cx) * eps;
            double ay2 = y2 + Math.signum(y2 - cy) * eps;
            double az2 = z2 + Math.signum(z2 - cz) * eps;
            mesh.ensureLineCapacity();
            int i1 = mesh.vec3(ax1, ay1, az1).color(r, g, b, a).next();
            int i2 = mesh.vec3(ax2, ay2, az2).color(r, g, b, a).next();
            mesh.line(i1, i2);
        });
    }

    private static void addShapeFill(MeshBuilder mesh, BlockPos pos, VoxelShape shape, int argb, double eps) {
        VoxelShape offset = shape.move(pos.getX(), pos.getY(), pos.getZ());
        offset.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> addFilledBox(
                mesh,
                minX - eps, minY - eps, minZ - eps,
                maxX + eps, maxY + eps, maxZ + eps,
                argb
        ));
    }

    private static void addFilledBox(MeshBuilder mesh,
                                     double x1, double y1, double z1,
                                     double x2, double y2, double z2,
                                     int argb) {
        addColorQuad(mesh, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, argb);
        addColorQuad(mesh, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, argb);
        addColorQuad(mesh, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, argb);
        addColorQuad(mesh, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, argb);
        addColorQuad(mesh, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, argb);
        addColorQuad(mesh, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, argb);
    }

    private static void addColorQuad(MeshBuilder mesh,
                                     double x1, double y1, double z1,
                                     double x2, double y2, double z2,
                                     double x3, double y3, double z3,
                                     double x4, double y4, double z4,
                                     int argb) {
        RenderColor color = new RenderColor(argb);
        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(color).next();
        int i2 = mesh.vec3(x2, y2, z2).color(color).next();
        int i3 = mesh.vec3(x3, y3, z3).color(color).next();
        int i4 = mesh.vec3(x4, y4, z4).color(color).next();
        mesh.quad(i1, i2, i3, i4);
    }
}
