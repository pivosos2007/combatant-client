/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.surface;

import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.IntFunction;

/**
 * Surface interaction renderer used by HitEffect radial waves and TotemFX shell/world contact.
 *
 * <p>The old implementation expanded block outlines on the CPU and therefore never produced a real
 * analytic radial boundary. The current version submits exposed world faces and lets the fragment
 * shader evaluate the actual distance field (planar ring or spherical shell) per-fragment.</p>
 */
public final class CurrentSurfaceWaveRenderer {
    private static final double FACE_EPS = 0.0016;
    private static final int SURFACE_SCAN_UP = 6;
    private static final int SURFACE_SCAN_DOWN = 12;
    private static final float OUTLINE_HALF_WIDTH = 0.115f;

    private CurrentSurfaceWaveRenderer() {
    }

    public static void render(Renderer3D renderer,
                              Level level,
                              SurfaceWaveDescriptor descriptor,
                              long nowMs,
                              IntFunction<Integer> palette) {
        if (renderer == null || level == null || descriptor == null || palette == null) return;

        float progress = descriptor.progress(nowMs);
        if (progress >= 1.0f) return;

        float radiusProgress = smoothstep(progress);
        float currentRadius = radiusProgress * descriptor.maxRadius();
        float globalAlpha = smoothstep(1.0f - progress);
        if (currentRadius <= 0.0001f || globalAlpha <= descriptor.minAlpha()) return;
        float extent = currentRadius + Math.max(descriptor.frontWidth(), descriptor.trailWidth()) + 1.0f;
        AABB bounds = new AABB(
                descriptor.center().x - extent, descriptor.center().y - 5.0, descriptor.center().z - extent,
                descriptor.center().x + extent, descriptor.center().y + 3.0, descriptor.center().z + extent
        );
        if (!Renderer3D.Culling.isInFrustum(bounds)) return;

        renderPlanarShell(
                renderer,
                level,
                descriptor.center(),
                currentRadius,
                descriptor.frontWidth(),
                descriptor.trailWidth(),
                globalAlpha,
                progress,
                descriptor.depthTest(),
                descriptor.maxCellsPerFrame(),
                palette,
                0.0f
        );
    }

    public static void renderSphereInteraction(Renderer3D renderer,
                                               Level level,
                                               Vec3 center,
                                               float radius,
                                               float frontWidth,
                                               float trailWidth,
                                               float opacity,
                                               float progress,
                                               boolean depthTest,
                                               int maxCellsPerFrame,
                                               IntFunction<Integer> palette,
                                               float profile) {
        if (renderer == null || level == null || center == null || palette == null) return;
        if (radius <= 0.0f || opacity <= 0.001f) return;
        float extent = radius + Math.max(frontWidth, trailWidth) + 0.75f;
        AABB bounds = new AABB(
                center.x - extent, center.y - extent, center.z - extent,
                center.x + extent, center.y + extent, center.z + extent
        );
        if (!Renderer3D.Culling.isInFrustum(bounds)) return;

        renderShell(
                renderer,
                level,
                center,
                radius,
                frontWidth,
                trailWidth,
                opacity,
                progress,
                depthTest,
                Math.max(1, maxCellsPerFrame),
                palette,
                true,
                profile
        );
    }

    public static void renderRadialInteraction(Renderer3D renderer,
                                               Level level,
                                               Vec3 center,
                                               float radius,
                                               float frontWidth,
                                               float trailWidth,
                                               float opacity,
                                               float progress,
                                               boolean depthTest,
                                               int maxCellsPerFrame,
                                               IntFunction<Integer> palette,
                                               float profile) {
        if (renderer == null || level == null || center == null || palette == null) return;
        if (radius <= 0.0f || opacity <= 0.001f) return;
        float extent = radius + Math.max(frontWidth, trailWidth) + 0.75f;
        AABB bounds = new AABB(
                center.x - extent, center.y - 5.0, center.z - extent,
                center.x + extent, center.y + 3.0, center.z + extent
        );
        if (!Renderer3D.Culling.isInFrustum(bounds)) return;
        renderPlanarShell(
                renderer, level, center, radius, frontWidth, trailWidth, opacity, progress,
                depthTest, Math.max(1, maxCellsPerFrame), palette, profile
        );
    }

    private static void renderPlanarShell(Renderer3D renderer,
                                          Level level,
                                          Vec3 center,
                                          float radius,
                                          float frontWidth,
                                          float trailWidth,
                                          float opacity,
                                          float progress,
                                          boolean depthTest,
                                          int maxCellsPerFrame,
                                          IntFunction<Integer> palette,
                                          float profile) {
        renderShell(
                renderer,
                level,
                center,
                radius,
                frontWidth,
                trailWidth,
                opacity,
                progress,
                depthTest,
                Math.max(1, maxCellsPerFrame),
                palette,
                false,
                profile
        );
    }

    private static void renderShell(Renderer3D renderer,
                                    Level level,
                                    Vec3 center,
                                    float radius,
                                    float frontWidth,
                                    float trailWidth,
                                    float opacity,
                                    float progress,
                                    boolean depthTest,
                                    int maxCellsPerFrame,
                                    IntFunction<Integer> palette,
                                    boolean spherical,
                                    float profile) {
        var pipeline = depthTest
                ? CombatantRenderPipelines.WORLD_SURFACE_SHELL_DEPTH
                : CombatantRenderPipelines.WORLD_SURFACE_SHELL;
        MeshBuilder mesh = renderer.batch(pipeline, depthTest ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE);
        if (mesh == null) return;

        float clampedOpacity = Mth.clamp(opacity, 0.0f, 1.0f);
        float clampedProgress = Mth.clamp(progress, 0.0f, 1.0f);
        float front = Math.max(0.04f, frontWidth);
        float trail = Math.max(0.04f, trailWidth);

        if (spherical) {
            renderSphericalCandidates(mesh, level, center, radius, front, trail, clampedOpacity,
                    clampedProgress, maxCellsPerFrame, palette, profile);
            return;
        }
        renderSurfaceCandidates(mesh, level, center, radius, front, trail, clampedOpacity,
                clampedProgress, maxCellsPerFrame, palette, profile);
    }

    private static void renderSurfaceCandidates(MeshBuilder mesh,
                                                Level level,
                                                Vec3 center,
                                                float radius,
                                                float frontWidth,
                                                float trailWidth,
                                                float opacity,
                                                float progress,
                                                int maxCellsPerFrame,
                                                IntFunction<Integer> palette,
                                                float profile) {
        BlockPos base = BlockPos.containing(center.x, center.y, center.z);
        int scanRadius = Math.max(1, Mth.ceil(radius + Math.max(frontWidth, trailWidth) + 1.5f));
        int rendered = 0;

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                if (rendered >= maxCellsPerFrame) return;

                int cellX = base.getX() + x;
                int cellZ = base.getZ() + z;
                if (!boxMayIntersectPlanar(center, radius, frontWidth, trailWidth,
                        cellX, cellZ, cellX + 1.0, cellZ + 1.0)) continue;

                BlockPos surface = findSurface(level, base.offset(x, 0, z));
                if (surface == null) continue;

                BlockState state = level.getBlockState(surface);
                VoxelShape shape = state.getShape(level, surface);
                if (shape.isEmpty()) continue;

                if (!shapeMayIntersectPlanar(center, radius, frontWidth, trailWidth, surface, shape)) continue;
                if (addProjectedShape(mesh, level, surface, shape, center, radius, frontWidth, trailWidth,
                        opacity, progress, palette, false, profile)) {
                    rendered++;
                }
            }
        }
    }

    private static void renderSphericalCandidates(MeshBuilder mesh,
                                                  Level level,
                                                  Vec3 center,
                                                  float radius,
                                                  float frontWidth,
                                                  float trailWidth,
                                                  float opacity,
                                                  float progress,
                                                  int maxCellsPerFrame,
                                                  IntFunction<Integer> palette,
                                                  float profile) {
        int scanRadius = Math.max(1, Mth.ceil(radius + Math.max(frontWidth, trailWidth) + 1.5f));
        BlockPos min = BlockPos.containing(center.x - scanRadius, center.y - scanRadius, center.z - scanRadius);
        BlockPos max = BlockPos.containing(center.x + scanRadius, center.y + scanRadius, center.z + scanRadius);
        int rendered = 0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (rendered >= maxCellsPerFrame) return;
                    if (!boxMayIntersectSphere(center, radius, frontWidth, trailWidth,
                            x, y, z, x + 1.0, y + 1.0, z + 1.0)) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getShape(level, pos);
                    if (shape.isEmpty()) continue;

                    if (!shapeMayIntersectSphere(center, radius, frontWidth, trailWidth, pos, shape)) continue;
                    if (addProjectedShape(mesh, level, pos, shape, center, radius, frontWidth, trailWidth,
                            opacity, progress, palette, true, profile)) {
                        rendered++;
                    }
                }
            }
        }
    }

    private static boolean addProjectedShape(MeshBuilder mesh,
                                             Level level,
                                             BlockPos pos,
                                             VoxelShape shape,
                                             Vec3 center,
                                             float radius,
                                             float frontWidth,
                                             float trailWidth,
                                             float opacity,
                                             float progress,
                                             IntFunction<Integer> palette,
                                             boolean spherical,
                                             float profile) {
        final boolean[] wrote = {false};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double x1 = pos.getX() + minX;
            double y1 = pos.getY() + minY;
            double z1 = pos.getZ() + minZ;
            double x2 = pos.getX() + maxX;
            double y2 = pos.getY() + maxY;
            double z2 = pos.getZ() + maxZ;

            if (spherical) {
                if (!boxMayIntersectSphere(center, radius, frontWidth, trailWidth, x1, y1, z1, x2, y2, z2)) return;
            } else {
                if (!boxMayIntersectPlanar(center, radius, frontWidth, trailWidth, x1, z1, x2, z2)) return;
            }

            int argb = samplePalette(palette, center, (x1 + x2) * 0.5, (z1 + z2) * 0.5, opacity);

            if (isFaceExposed(level, pos, Direction.UP)) {
                addFace(mesh,
                        x1, y2 + FACE_EPS, z1,
                        x1, y2 + FACE_EPS, z2,
                        x2, y2 + FACE_EPS, z2,
                        x2, y2 + FACE_EPS, z1,
                        center, radius, frontWidth, trailWidth, progress, spherical, profile, argb);
                wrote[0] = true;
            }
            if (isFaceExposed(level, pos, Direction.NORTH)) {
                addFace(mesh,
                        x1, y1, z1 - FACE_EPS,
                        x1, y2, z1 - FACE_EPS,
                        x2, y2, z1 - FACE_EPS,
                        x2, y1, z1 - FACE_EPS,
                        center, radius, frontWidth, trailWidth, progress, spherical, profile, argb);
                wrote[0] = true;
            }
            if (isFaceExposed(level, pos, Direction.SOUTH)) {
                addFace(mesh,
                        x1, y1, z2 + FACE_EPS,
                        x2, y1, z2 + FACE_EPS,
                        x2, y2, z2 + FACE_EPS,
                        x1, y2, z2 + FACE_EPS,
                        center, radius, frontWidth, trailWidth, progress, spherical, profile, argb);
                wrote[0] = true;
            }
            if (isFaceExposed(level, pos, Direction.WEST)) {
                addFace(mesh,
                        x1 - FACE_EPS, y1, z1,
                        x1 - FACE_EPS, y1, z2,
                        x1 - FACE_EPS, y2, z2,
                        x1 - FACE_EPS, y2, z1,
                        center, radius, frontWidth, trailWidth, progress, spherical, profile, argb);
                wrote[0] = true;
            }
            if (isFaceExposed(level, pos, Direction.EAST)) {
                addFace(mesh,
                        x2 + FACE_EPS, y1, z1,
                        x2 + FACE_EPS, y2, z1,
                        x2 + FACE_EPS, y2, z2,
                        x2 + FACE_EPS, y1, z2,
                        center, radius, frontWidth, trailWidth, progress, spherical, profile, argb);
                wrote[0] = true;
            }
        });
        return wrote[0];
    }

    private static int samplePalette(IntFunction<Integer> palette,
                                     Vec3 center,
                                     double x,
                                     double z,
                                     float opacity) {
        int idx = (int) (Math.toDegrees(Math.atan2(z - center.z, x - center.x)) + 180.0);
        return applyOpacity(palette.apply(idx), opacity);
    }

    private static void addFace(MeshBuilder mesh,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                Vec3 center,
                                float radius,
                                float frontWidth,
                                float trailWidth,
                                float progress,
                                boolean spherical,
                                float profile,
                                int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        // Keep visual profile and distance metric independent. Profiles 0..2 describe the material;
        // bit/value 4 selects a true 3D spherical metric. Planar TotemFX waves can therefore use
        // their own profile without accidentally becoming spherical in the fragment shader.
        float packedProfile = profile + (spherical ? 4.0f : 0.0f);
        float centerX = (float) (center.x - mesh.cameraAnchorX());
        float centerY = (float) center.y;
        float centerZ = (float) (center.z - mesh.cameraAnchorZ());

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(x1, y1, z1).vec2(0.0f, 0.0f).color(r, g, b, a)
                .vec4(centerX, centerY, centerZ, radius)
                .vec4(progress, frontWidth, trailWidth, packedProfile)
                .next();
        int i2 = mesh.vec3(x2, y2, z2).vec2(0.0f, 1.0f).color(r, g, b, a)
                .vec4(centerX, centerY, centerZ, radius)
                .vec4(progress, frontWidth, trailWidth, packedProfile)
                .next();
        int i3 = mesh.vec3(x3, y3, z3).vec2(1.0f, 1.0f).color(r, g, b, a)
                .vec4(centerX, centerY, centerZ, radius)
                .vec4(progress, frontWidth, trailWidth, packedProfile)
                .next();
        int i4 = mesh.vec3(x4, y4, z4).vec2(1.0f, 0.0f).color(r, g, b, a)
                .vec4(centerX, centerY, centerZ, radius)
                .vec4(progress, frontWidth, trailWidth, packedProfile)
                .next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static boolean shapeMayIntersectPlanar(Vec3 center,
                                                   float radius,
                                                   float frontWidth,
                                                   float trailWidth,
                                                   BlockPos pos,
                                                   VoxelShape shape) {
        final boolean[] result = {false};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (result[0]) return;
            double x1 = pos.getX() + minX;
            double z1 = pos.getZ() + minZ;
            double x2 = pos.getX() + maxX;
            double z2 = pos.getZ() + maxZ;
            result[0] = boxMayIntersectPlanar(center, radius, frontWidth, trailWidth, x1, z1, x2, z2);
        });
        return result[0];
    }

    private static boolean shapeMayIntersectSphere(Vec3 center,
                                                   float radius,
                                                   float frontWidth,
                                                   float trailWidth,
                                                   BlockPos pos,
                                                   VoxelShape shape) {
        final boolean[] result = {false};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (result[0]) return;
            double x1 = pos.getX() + minX;
            double y1 = pos.getY() + minY;
            double z1 = pos.getZ() + minZ;
            double x2 = pos.getX() + maxX;
            double y2 = pos.getY() + maxY;
            double z2 = pos.getZ() + maxZ;
            result[0] = boxMayIntersectSphere(center, radius, frontWidth, trailWidth, x1, y1, z1, x2, y2, z2);
        });
        return result[0];
    }

    private static boolean boxMayIntersectPlanar(Vec3 center,
                                                 float radius,
                                                 float frontWidth,
                                                 float trailWidth,
                                                 double minX,
                                                 double minZ,
                                                 double maxX,
                                                 double maxZ) {
        float minDist = (float) Math.sqrt(minDistanceSqRectXZ(center.x, center.z, minX, minZ, maxX, maxZ));
        float maxDist = (float) Math.sqrt(maxDistanceSqRectXZ(center.x, center.z, minX, minZ, maxX, maxZ));
        return minDist <= radius + frontWidth + OUTLINE_HALF_WIDTH
                && maxDist >= Math.max(0.0f, radius - trailWidth - OUTLINE_HALF_WIDTH);
    }

    private static boolean boxMayIntersectSphere(Vec3 center,
                                                 float radius,
                                                 float frontWidth,
                                                 float trailWidth,
                                                 double minX,
                                                 double minY,
                                                 double minZ,
                                                 double maxX,
                                                 double maxY,
                                                 double maxZ) {
        float minDist = (float) Math.sqrt(minDistanceSqAabb(center, minX, minY, minZ, maxX, maxY, maxZ));
        float maxDist = (float) Math.sqrt(maxDistanceSqAabb(center, minX, minY, minZ, maxX, maxY, maxZ));
        return minDist <= radius + frontWidth + OUTLINE_HALF_WIDTH
                && maxDist >= Math.max(0.0f, radius - trailWidth - OUTLINE_HALF_WIDTH);
    }

    private static boolean isFaceExposed(Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.isAir()) return true;
        return neighbor.getShape(level, neighborPos).isEmpty();
    }

    private static BlockPos findSurface(Level level, BlockPos pos) {
        for (int y = 0; y >= -SURFACE_SCAN_DOWN; y--) {
            BlockPos candidate = pos.above(y);
            BlockState state = level.getBlockState(candidate);
            if (!state.getShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        for (int y = 1; y <= SURFACE_SCAN_UP; y++) {
            BlockPos candidate = pos.above(y);
            BlockState state = level.getBlockState(candidate);
            if (!state.getShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getShape(level, candidate.above()).isEmpty()) {
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

    private static double minDistanceSqRectXZ(double px, double pz,
                                              double minX, double minZ,
                                              double maxX, double maxZ) {
        double dx = px < minX ? minX - px : Math.max(0.0, px - maxX);
        double dz = pz < minZ ? minZ - pz : Math.max(0.0, pz - maxZ);
        return dx * dx + dz * dz;
    }

    private static double maxDistanceSqRectXZ(double px, double pz,
                                              double minX, double minZ,
                                              double maxX, double maxZ) {
        double dx = Math.max(Math.abs(px - minX), Math.abs(px - maxX));
        double dz = Math.max(Math.abs(pz - minZ), Math.abs(pz - maxZ));
        return dx * dx + dz * dz;
    }

    private static double minDistanceSqAabb(Vec3 p,
                                            double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ) {
        double dx = p.x < minX ? minX - p.x : Math.max(0.0, p.x - maxX);
        double dy = p.y < minY ? minY - p.y : Math.max(0.0, p.y - maxY);
        double dz = p.z < minZ ? minZ - p.z : Math.max(0.0, p.z - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double maxDistanceSqAabb(Vec3 p,
                                            double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.abs(p.x - minX), Math.abs(p.x - maxX));
        double dy = Math.max(Math.abs(p.y - minY), Math.abs(p.y - maxY));
        double dz = Math.max(Math.abs(p.z - minZ), Math.abs(p.z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
