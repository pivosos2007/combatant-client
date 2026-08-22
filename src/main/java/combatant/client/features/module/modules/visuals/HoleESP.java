/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "holeesp",
        displayName = "HoleESP",
        aliases = {"holes"},
        category = ModuleCategory.VISUALS,
        description = "module.holeesp.description"
)
public final class HoleESP extends Module {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };
    private static final int RESCAN_TICKS = 10;
    private static final int BASE_FILL_ALPHA = 60;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> rangeXZ =
            num("holeesp_range_xz", "range_xz", 10, 1, 128);
    private final NumberValue<Integer> rangeY =
            num("holeesp_range_y", "range_y", 5, 1, 128);
    private final NumberValue<Float> height =
            num("holeesp_height", "height", 1.0f, 0.01f, 5.0f);
    private final NumberValue<Float> lineWidth =
            num("holeesp_line_width", "line_width", 0.5f, 0.01f, 5.0f);
    private final BooleanValue culling =
            bool("holeesp_culling", "culling", true);
    private final RGBAColorValue indestructibleColor =
            color("holeesp_indestructible_color", "indestructible_color", "#FF7A00FF");
    private final RGBAColorValue bedrockColor =
            color("holeesp_bedrock_color", "bedrock_color", "#FF00FF51");

    private List<Hole> holes = List.of();
    private int ticks;

    @Override
    public void onDisable() {
        holes = List.of();
        ticks = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            holes = List.of();
            return;
        }

        if (ticks++ % RESCAN_TICKS == 0) {
            holes = scanHoles(mc.level, mc.player.position());
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null || holes.isEmpty()) {
            return;
        }

        float previousLineWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(0.01f, lineWidth.get());
        try {
            for (Hole hole : holes) {
                if (culling.get() && !shouldRender(hole.box())) {
                    continue;
                }
                renderFade(renderer, hole);
            }
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }
    }

    private List<Hole> scanHoles(ClientLevel level, Vec3 center) {
        int xz = rangeXZ.get();
        int y = rangeY.get();
        List<Hole> found = new ArrayList<>();
        List<AABB> acceptedBoxes = new ArrayList<>();
        LongOpenHashSet consumed = new LongOpenHashSet();

        int minX = Mth.floor(center.x - xz);
        int minY = Math.max(level.getMinY(), Mth.floor(center.y - y));
        int minZ = Mth.floor(center.z - xz);
        int maxX = Mth.floor(center.x + xz);
        int maxY = Math.min(level.getMaxY() - 1, Mth.floor(center.y + y));
        int maxZ = Mth.floor(center.z + xz);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos floor = new BlockPos.MutableBlockPos();

        /*
         * Do not materialize the whole search cuboid as BlockPos + entry objects. Most
         * positions are impossible candidates because their floor is not a safe block, so
         * reject those using two reusable cursors before allocating an immutable position.
         */
        for (int x = minX; x <= maxX; x++) {
            if (Math.abs((x + 0.5) - center.x) > xz) continue;
            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs((z + 0.5) - center.z) > xz) continue;
                for (int scanY = minY; scanY <= maxY; scanY++) {
                    if (Math.abs((scanY + 0.5) - center.y) > y) continue;
                    cursor.set(x, scanY, z);
                    if (!isReplaceable(level.getBlockState(cursor))) continue;

                    floor.set(x, scanY - 1, z);
                    if (!isSafeBlock(level, floor)) continue;

                    long packed = cursor.asLong();
                    if (consumed.contains(packed)) continue;

                    BlockPos pos = cursor.immutable();
                    Hole hole = resolveHole(level, pos);
                    if (hole == null || intersectsAny(hole.box(), acceptedBoxes)) continue;

                    found.add(hole);
                    acceptedBoxes.add(hole.box());
                    for (BlockPos holePos : hole.positions()) {
                        consumed.add(holePos.asLong());
                    }
                }
            }
        }

        return List.copyOf(found);
    }

    private Hole resolveHole(ClientLevel level, BlockPos pos) {
        if (validIndestructible(level, pos)) {
            return new Hole(box(pos, 1, 1), indestructibleColor.getArgb(), List.of(pos));
        }
        if (validBedrock(level, pos)) {
            return new Hole(box(pos, 1, 1), bedrockColor.getArgb(), List.of(pos));
        }

        List<BlockPos> two = twoBlockShape(level, pos);
        if (two != null) {
            if (validBedrockShape(level, two)) {
                return new Hole(box(two), bedrockColor.getArgb(), two);
            }
            if (validIndestructibleShape(level, two)) {
                return new Hole(box(two), indestructibleColor.getArgb(), two);
            }
        }

        List<BlockPos> quad = quadShape(level, pos);
        if (quad != null) {
            if (validBedrockShape(level, quad)) {
                return new Hole(box(quad), bedrockColor.getArgb(), quad);
            }
            if (validIndestructibleShape(level, quad)) {
                return new Hole(box(quad), indestructibleColor.getArgb(), quad);
            }
        }

        return null;
    }

    private void renderFade(Renderer3D renderer, Hole hole) {
        AABB box = hole.box();
        int fillBottom = colorForDistance(box, hole.argb(), BASE_FILL_ALPHA);
        int fillTop = colorForDistance(box, hole.argb(), 0);
        int line = colorForDistance(box, hole.argb(), (hole.argb() >>> 24) & 0xFF);

        addVerticalFadeBox(renderer, box, fillBottom, fillTop);
        addBottomOutline(renderer, box, line);
    }

    private int colorForDistance(AABB box, int argb, int alpha) {
        Vec3 center = box.getCenter();
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        double distSqr = dx * dx + dz * dz;
        double maxSqr = Math.max(1.0, (double) rangeXZ.get() * rangeXZ.get());
        float factor = (float) (distSqr / maxSqr);
        factor = 1.0f - easeOutExpo(factor);
        factor = Mth.clamp(factor, 0.0f, 1.0f);

        int baseAlpha = (argb >>> 24) & 0xFF;
        int outAlpha = Mth.clamp((int) (factor * Math.min(alpha, baseAlpha)), 0, 255);
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static float easeOutExpo(float x) {
        return x >= 1.0f ? 1.0f : (float) (1.0f - Math.pow(2.0, -10.0f * x));
    }

    private static boolean shouldRender(AABB box) {
        return Renderer3D.Culling.isInFrustum(box) && Renderer3D.Culling.isSectionVisible(box);
    }

    private static boolean intersectsAny(AABB box, List<AABB> boxes) {
        for (AABB other : boxes) {
            if (other.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private AABB box(BlockPos pos, int widthX, int widthZ) {
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + widthX,
                pos.getY() + height.get(),
                pos.getZ() + widthZ
        );
    }

    private AABB box(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new AABB(minX, minY, minZ, maxX + 1, minY + height.get(), maxZ + 1);
    }

    private static List<BlockPos> twoBlockShape(ClientLevel level, BlockPos pos) {
        if (!isReplaceable(level, pos)) {
            return null;
        }
        for (Direction direction : HORIZONTAL) {
            BlockPos other = pos.relative(direction);
            if (isReplaceable(level, other)) {
                return List.of(pos, other);
            }
        }
        return null;
    }

    private static List<BlockPos> quadShape(ClientLevel level, BlockPos pos) {
        if (!isReplaceable(level, pos)) {
            return null;
        }

        List<BlockPos> eastSouth = quadIfReplaceable(level, pos, pos.east(), pos.south(), pos.east().south());
        if (eastSouth != null) return eastSouth;

        List<BlockPos> westNorth = quadIfReplaceable(level, pos, pos.west(), pos.north(), pos.west().north());
        if (westNorth != null) return westNorth;

        List<BlockPos> eastNorth = quadIfReplaceable(level, pos, pos.east(), pos.north(), pos.east().north());
        if (eastNorth != null) return eastNorth;

        return quadIfReplaceable(level, pos, pos.west(), pos.south(), pos.west().south());
    }

    private static List<BlockPos> quadIfReplaceable(ClientLevel level, BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        if (isReplaceable(level, b) && isReplaceable(level, c) && isReplaceable(level, d)) {
            return List.of(a, b, c, d);
        }
        return null;
    }

    private static boolean validBedrock(ClientLevel level, BlockPos pos) {
        return isBedrock(level, pos.below())
                && isBedrock(level, pos.east())
                && isBedrock(level, pos.west())
                && isBedrock(level, pos.south())
                && isBedrock(level, pos.north())
                && isReplaceable(level, pos)
                && isReplaceable(level, pos.above())
                && isReplaceable(level, pos.above(2));
    }

    private static boolean validIndestructible(ClientLevel level, BlockPos pos) {
        // resolveHole() has already rejected the all-bedrock variant.
        return isSafeBlock(level, pos.below())
                && isSafeBlock(level, pos.east())
                && isSafeBlock(level, pos.west())
                && isSafeBlock(level, pos.south())
                && isSafeBlock(level, pos.north())
                && isReplaceable(level, pos)
                && isReplaceable(level, pos.above())
                && isReplaceable(level, pos.above(2));
    }

    private static boolean validBedrockShape(ClientLevel level, List<BlockPos> positions) {
        for (BlockPos check : positions) {
            if (!isReplaceable(level, check) || !isReplaceable(level, check.above()) || !isReplaceable(level, check.above(2))) {
                return false;
            }
            if (!isBedrock(level, check.below())) {
                return false;
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos surround = check.relative(direction);
                if (!positions.contains(surround) && !isBedrock(level, surround)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validIndestructibleShape(ClientLevel level, List<BlockPos> positions) {
        boolean hasIndestructible = false;
        for (BlockPos check : positions) {
            if (!isReplaceable(level, check) || !isReplaceable(level, check.above()) || !isReplaceable(level, check.above(2))) {
                return false;
            }

            BlockPos below = check.below();
            if (isIndestructible(level, below)) {
                hasIndestructible = true;
            } else if (!isBedrock(level, below)) {
                return false;
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos surround = check.relative(direction);
                if (positions.contains(surround)) {
                    continue;
                }
                if (isIndestructible(level, surround)) {
                    hasIndestructible = true;
                } else if (!isBedrock(level, surround)) {
                    return false;
                }
            }
        }
        return hasIndestructible;
    }

    private static boolean isSafeBlock(ClientLevel level, BlockPos pos) {
        return isBedrock(level, pos) || isIndestructible(level, pos);
    }

    private static boolean isBedrock(ClientLevel level, BlockPos pos) {
        return level != null && block(level, pos) == Blocks.BEDROCK;
    }

    private static boolean isIndestructible(ClientLevel level, BlockPos pos) {
        Block block = block(level, pos);
        return block == Blocks.OBSIDIAN
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR;
    }

    private static boolean isReplaceable(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return false;
        }
        return isReplaceable(level.getBlockState(pos));
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private static Block block(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return Blocks.AIR;
        }
        return level.getBlockState(pos).getBlock();
    }

    private static void addVerticalFadeBox(Renderer3D renderer, AABB box, int bottomArgb, int topArgb) {
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.MAIN);
        if (mesh == null) {
            return;
        }

        int bottomA = (bottomArgb >>> 24) & 0xFF;
        int bottomR = (bottomArgb >>> 16) & 0xFF;
        int bottomG = (bottomArgb >>> 8) & 0xFF;
        int bottomB = bottomArgb & 0xFF;
        int topA = (topArgb >>> 24) & 0xFF;
        int topR = (topArgb >>> 16) & 0xFF;
        int topG = (topArgb >>> 8) & 0xFF;
        int topB = topArgb & 0xFF;

        addGradientQuad(mesh, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
    }

    private static void addGradientQuad(MeshBuilder mesh,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        double x3, double y3, double z3,
                                        double x4, double y4, double z4,
                                        int bottomR, int bottomG, int bottomB, int bottomA,
                                        int topR, int topG, int topB, int topA) {
        mesh.ensureQuadCapacity();
        double minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        double maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        double midY = (minY + maxY) * 0.5;
        int i1 = fadeVertex(mesh, x1, y1, z1, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i2 = fadeVertex(mesh, x2, y2, z2, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i3 = fadeVertex(mesh, x3, y3, z3, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i4 = fadeVertex(mesh, x4, y4, z4, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        mesh.quad(i1, i2, i3, i4);
    }

    private static int fadeVertex(MeshBuilder mesh,
                                  double x, double y, double z, double midY,
                                  int bottomR, int bottomG, int bottomB, int bottomA,
                                  int topR, int topG, int topB, int topA) {
        boolean bottom = y <= midY;
        return mesh.vec3(x, y, z)
                .color(
                        bottom ? bottomR : topR,
                        bottom ? bottomG : topG,
                        bottom ? bottomB : topB,
                        bottom ? bottomA : topA
                )
                .next();
    }

    private static void addBottomOutline(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) {
            return;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);
    }

    private record Hole(AABB box, int argb, List<BlockPos> positions) {
    }
}
