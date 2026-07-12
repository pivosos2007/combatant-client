/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;


//todo Description
@ModuleInfo(
        id = "blockhighlight",
        displayName = "BlockHighlight",
        category = ModuleCategory.VISUALS
)
public class BlockHighlight extends Module {

    private static final double RAINBOW_SPATIAL_SCALE = 12.0;
    private static final double GEOMETRY_EXPAND = 0.0025;
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue outlineEnabled =
            bool(
                    "outline_enabled",
                    true
            );
    private final EnumValue<OutlineMode> outlineMode =
            visibleWhen(enumMode(
                    "outline_mode",
                    OutlineMode.LINES,
                    OutlineMode.values()
            ), outlineEnabled::get);
    private final RGBAColorValue outlineColor =
            visibleWhen(color(
                    "outline_color",
                    "#66FFFFFF"
            ), outlineEnabled::get);
    private final EnumValue<ColorMode> outlineColorMode =
            visibleWhen(enumCommon(
                    "outline_color_mode",
                    "outline_color_mode",
                    CommonSettingSchemas.RENDER_COLOR_MODE,
                    ColorMode.STATIC,
                    ColorMode.values()
            ), outlineEnabled::get);
    private final RGBAColorValue outlineColor2 =
            visibleWhen(color(
                    "outline_color2",
                    "#FFFFAA00"
            ), this::usesSecondaryOutlineColor);
    private final NumberValue<Integer> outlineColorSpeed =
            visibleWhen(num(
                    "outline_color_speed",
                    18,
                    2,
                    54
            ), outlineEnabled::get);
    private final NumberValue<Float> outlineThickness =
            visibleWhen(num(
                    "outline_thickness",
                    1.0f,
                    0.5f,
                    4.0f
            ), outlineEnabled::get);
    private final BooleanValue outlineDepthTest =
            visibleWhen(bool(
                    "outline_depth_test",
                    false
            ), outlineEnabled::get);
    private final BooleanValue breakingEnabled =
            bool(
                    "breaking_enabled",
                    true
            );
    private final BooleanValue breakingSelf =
            visibleWhen(bool(
                    "breaking_self",
                    true
            ), breakingEnabled::get);
    private final BooleanValue breakingOtherPlayers =
            visibleWhen(bool(
                    "breaking_other_players",
                    true
            ), breakingEnabled::get);
    private final EnumValue<BreakAnimation> breakingAnimation =
            visibleWhen(enumMode(
                    "breaking_animation",
                    BreakAnimation.GROW,
                    BreakAnimation.values()
            ), breakingEnabled::get);
    private final RGBAColorValue breakingColor =
            visibleWhen(color(
                    "breaking_color",
                    "#66FFFFFF"
            ), breakingEnabled::get);
    private final RGBAColorValue breakingColor2 =
            visibleWhen(color(
                    "breaking_color2",
                    "#22FFAA00"
            ), breakingEnabled::get);

    private static void addGradientLine(MeshBuilder mesh,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        int argb1,
                                        int argb2) {
        mesh.ensureLineCapacity();

        putColorVertex(mesh, x1, y1, z1, argb1);
        int i1 = mesh.next();

        putColorVertex(mesh, x2, y2, z2, argb2);
        int i2 = mesh.next();

        mesh.line(i1, i2);
    }

    private static void addGradientQuad(MeshBuilder mesh,
                                        double x1, double y1, double z1, int c1,
                                        double x2, double y2, double z2, int c2,
                                        double x3, double y3, double z3, int c3,
                                        double x4, double y4, double z4, int c4) {
        mesh.ensureQuadCapacity();

        putColorVertex(mesh, x1, y1, z1, c1);
        int i1 = mesh.next();

        putColorVertex(mesh, x2, y2, z2, c2);
        int i2 = mesh.next();

        putColorVertex(mesh, x3, y3, z3, c3);
        int i3 = mesh.next();

        putColorVertex(mesh, x4, y4, z4, c4);
        int i4 = mesh.next();

        mesh.quad(i1, i2, i3, i4);
    }

    private static void putColorVertex(MeshBuilder mesh, double x, double y, double z, int argb) {
        mesh.vec3(x, y, z).color(red(argb), green(argb), blue(argb), alpha(argb));
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = alpha(argb);
        int r = red(argb);
        int g = green(argb);
        int b = blue(argb);

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static float stageToProgress(int stage) {
        if (stage < 0) return 0.0f;
        return Mth.clamp(stage / 10.0f, 0.0f, 1.0f);
    }

    private static int interpolateArgb(int from, int to, float amount) {
        amount = Mth.clamp(amount, 0.0f, 1.0f);

        int a = interpolateChannel(alpha(from), alpha(to), amount);
        int r = interpolateChannel(red(from), red(to), amount);
        int g = interpolateChannel(green(from), green(to), amount);
        int b = interpolateChannel(blue(from), blue(to), amount);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateChannel(int from, int to, float amount) {
        return from + Math.round((to - from) * amount);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int red(int argb) {
        return (argb >>> 16) & 255;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 255;
    }

    private static int blue(int argb) {
        return argb & 255;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 255;
    }

    public boolean isOutlineActive() {
        return isEnabled() && outlineEnabled.get();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.BEFORE_TRANSLUCENT;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (outlineEnabled.get()) {
            renderTargetOutline(renderer);
        }

        if (breakingEnabled.get()) {
            renderBreakingHighlight(renderer);
        }
    }

    private void renderTargetOutline(Renderer3D renderer) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) return;
        if (hit.getType() == HitResult.Type.MISS) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        Entity cameraEntity = mc.getCameraEntity();
        CollisionContext context = cameraEntity != null ? CollisionContext.of(cameraEntity) : CollisionContext.empty();
        VoxelShape shape = state.getShape(mc.level, pos, context);
        if (shape.isEmpty()) return;

        VoxelShape worldShape = shape.move(pos.getX(), pos.getY(), pos.getZ());
        OutlineMode mode = outlineMode.get();

        if (mode == OutlineMode.BOX || mode == OutlineMode.BOTH) {
            renderShapeBoxes(renderer, worldShape);
        }

        if (mode == OutlineMode.LINES || mode == OutlineMode.BOTH) {
            renderShapeLines(renderer, worldShape);
        }
    }

    private void renderShapeLines(Renderer3D renderer, VoxelShape worldShape) {
        MeshBuilder mesh = renderer.batch(
                outlineDepthTest.get()
                        ? CombatantRenderPipelines.WORLD_COLORED_DEPTH
                        : CombatantRenderPipelines.WORLD_COLORED,
                outlineDepthTest.get()
                        ? Renderer3D.DepthMode.PRE_DEPTH
                        : Renderer3D.DepthMode.NONE
        );

        if (mesh == null) return;

        double overlap = outlineDepthTest.get()
                ? Math.max(0.0015, outlineThickness.get() * 0.003)
                : 0.0008;

        worldShape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            int c1 = getOutlineColor(x1, y1, z1);
            int c2 = getOutlineColor(x2, y2, z2);

            addEdgeRibbon(
                    mesh,
                    x1, y1, z1,
                    x2, y2, z2,
                    overlap,
                    c1,
                    c2
            );
        });
    }

    private void addEdgeRibbon(MeshBuilder mesh,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               double overlap,
                               int argb1,
                               int argb2) {
        double mx = (x1 + x2) * 0.5;
        double my = (y1 + y2) * 0.5;
        double mz = (z1 + z2) * 0.5;

        double ex = x2 - x1;
        double ey = y2 - y1;
        double ez = z2 - z1;
        double edgeLenSq = ex * ex + ey * ey + ez * ez;
        if (edgeLenSq <= 1.0e-10) {
            return;
        }

        double toCamX = RenderState.cameraPos.x - mx;
        double toCamY = RenderState.cameraPos.y - my;
        double toCamZ = RenderState.cameraPos.z - mz;
        double toCamLenSq = toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ;
        if (toCamLenSq <= 1.0e-10) {
            return;
        }

        double sideX = ey * toCamZ - ez * toCamY;
        double sideY = ez * toCamX - ex * toCamZ;
        double sideZ = ex * toCamY - ey * toCamX;
        double sideLenSq = sideX * sideX + sideY * sideY + sideZ * sideZ;
        if (sideLenSq <= 1.0e-10) {
            return;
        }

        double sideInv = 1.0 / Math.sqrt(sideLenSq);
        double halfWidth = computeRibbonHalfWidth(mx, my, mz);

        sideX *= sideInv * halfWidth;
        sideY *= sideInv * halfWidth;
        sideZ *= sideInv * halfWidth;

        double camInv = 1.0 / Math.sqrt(toCamLenSq);
        double biasX = toCamX * camInv * overlap;
        double biasY = toCamY * camInv * overlap;
        double biasZ = toCamZ * camInv * overlap;

        addGradientQuad(
                mesh,
                x1 - sideX + biasX, y1 - sideY + biasY, z1 - sideZ + biasZ, argb1,
                x1 + sideX + biasX, y1 + sideY + biasY, z1 + sideZ + biasZ, argb1,
                x2 + sideX + biasX, y2 + sideY + biasY, z2 + sideZ + biasZ, argb2,
                x2 - sideX + biasX, y2 - sideY + biasY, z2 - sideZ + biasZ, argb2
        );
    }

    private double computeRibbonHalfWidth(double x, double y, double z) {
        double dx = RenderState.cameraPos.x - x;
        double dy = RenderState.cameraPos.y - y;
        double dz = RenderState.cameraPos.z - z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        return 0.003 + outlineThickness.get() * (0.0025 + dist * 0.00018);
    }

    private void renderShapeBoxes(Renderer3D renderer, VoxelShape worldShape) {
        MeshBuilder mesh = renderer.batch(
                outlineDepthTest.get()
                        ? CombatantRenderPipelines.WORLD_COLORED_DEPTH
                        : CombatantRenderPipelines.WORLD_COLORED,
                outlineDepthTest.get()
                        ? Renderer3D.DepthMode.PRE_DEPTH
                        : Renderer3D.DepthMode.NONE
        );

        if (mesh == null) return;

        worldShape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double x1 = minX - GEOMETRY_EXPAND;
            double y1 = minY - GEOMETRY_EXPAND;
            double z1 = minZ - GEOMETRY_EXPAND;
            double x2 = maxX + GEOMETRY_EXPAND;
            double y2 = maxY + GEOMETRY_EXPAND;
            double z2 = maxZ + GEOMETRY_EXPAND;

            addGradientBox(mesh, x1, y1, z1, x2, y2, z2);
        });
    }

    private void addGradientBox(MeshBuilder mesh,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2) {
        int c000 = getOutlineColor(x1, y1, z1);
        int c001 = getOutlineColor(x1, y1, z2);
        int c010 = getOutlineColor(x1, y2, z1);
        int c011 = getOutlineColor(x1, y2, z2);
        int c100 = getOutlineColor(x2, y1, z1);
        int c101 = getOutlineColor(x2, y1, z2);
        int c110 = getOutlineColor(x2, y2, z1);
        int c111 = getOutlineColor(x2, y2, z2);

        addGradientQuad(mesh, x1, y1, z1, c000, x2, y1, z1, c100, x2, y2, z1, c110, x1, y2, z1, c010);
        addGradientQuad(mesh, x2, y1, z2, c101, x1, y1, z2, c001, x1, y2, z2, c011, x2, y2, z2, c111);
        addGradientQuad(mesh, x1, y1, z2, c001, x1, y1, z1, c000, x1, y2, z1, c010, x1, y2, z2, c011);
        addGradientQuad(mesh, x2, y1, z1, c100, x2, y1, z2, c101, x2, y2, z2, c111, x2, y2, z1, c110);
        addGradientQuad(mesh, x1, y2, z1, c010, x2, y2, z1, c110, x2, y2, z2, c111, x1, y2, z2, c011);
        addGradientQuad(mesh, x1, y1, z2, c001, x2, y1, z2, c101, x2, y1, z1, c100, x1, y1, z1, c000);
    }

    private void renderBreakingHighlight(Renderer3D renderer) {
        if (breakingSelf.get()) {
            renderSelfBreaking(renderer);
        }

        if (breakingOtherPlayers.get() && mc.level != null) {
            var destructionProgress = mc.level.destructionProgress();
            if (destructionProgress != null && !destructionProgress.isEmpty()) {
                destructionProgress.forEach((packedPos, infos) -> {
                    if (infos == null || infos.isEmpty()) {
                        return;
                    }

                    BlockDestructionProgress info = infos.last();
                    if (mc.player != null && info.getId() == mc.player.getId()) {
                        return;
                    }

                    renderBreakingInfo(renderer, info.getPos(), stageToProgress(info.getProgress()));
                });
            }
        }
    }

    private void renderSelfBreaking(Renderer3D renderer) {
        MultiPlayerGameMode interactionManager = mc.gameMode;
        if (interactionManager == null || !interactionManager.isDestroying()) return;
        if (!(interactionManager instanceof MultiPlayerGameModeAccessor accessor)) return;

        BlockPos pos = accessor.combatant$getCurrentBreakingPos();
        if (pos == null) return;

        float progress = Mth.clamp(accessor.combatant$getCurrentBreakingProgress(), 0.0f, 1.0f);
        renderBreakingInfo(renderer, pos, progress);
    }

    private void renderBreakingInfo(Renderer3D renderer, BlockPos pos, float progress) {
        if (mc.level == null || pos == null) return;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.empty());
        if (shape.isEmpty()) return;

        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        int argb = interpolateArgb(breakingColor.getArgb(), breakingColor2.getArgb(), clamped);

        shape.move(pos.getX(), pos.getY(), pos.getZ()).forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            AABB box = animateBox(new AABB(minX, minY, minZ, maxX, maxY, maxZ), clamped);
            if (isRenderable(box)) {
                addFilledBox(renderer, box, argb);
            }
        });
    }

    private AABB animateBox(AABB box, float progress) {
        double scale = switch (breakingAnimation.get()) {
            case GROW -> progress;
            case SHRINK -> 1.0 - progress;
            case STATIC -> 1.0;
        };

        scale = Mth.clamp((float) scale, 0.0f, 1.0f);
        if (scale >= 0.999f) {
            return box;
        }

        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double hx = (box.maxX - box.minX) * 0.5 * scale;
        double hy = (box.maxY - box.minY) * 0.5 * scale;
        double hz = (box.maxZ - box.minZ) * 0.5 * scale;

        return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private boolean isRenderable(AABB box) {
        return box.maxX - box.minX > 1.0e-4
                && box.maxY - box.minY > 1.0e-4
                && box.maxZ - box.minZ > 1.0e-4;
    }

    private int getOutlineColor(double x, double y, double z) {
        int base = outlineColor.getArgb();
        return AnimatedRenderColors.resolve(
                animatedOutlineColorMode(),
                outlineColorSpeed.get(),
                spatialIndex(x, y, z),
                base,
                outlineColor2.getArgb(),
                true
        );
    }

    private int spatialIndex(double x, double y, double z) {
        int xs = (int) Math.round(x * (RAINBOW_SPATIAL_SCALE + 1.0));
        int ys = (int) Math.round(y * (RAINBOW_SPATIAL_SCALE - 2.0));
        int zs = (int) Math.round(z * (RAINBOW_SPATIAL_SCALE + 3.0));
        return Math.floorMod(xs + ys + zs, 3600);
    }

    private boolean usesSecondaryOutlineColor() {
        if (!outlineEnabled.get()) return false;
        return AnimatedRenderColors.usesSecondary(animatedOutlineColorMode());
    }

    private AnimatedRenderColors.Mode animatedOutlineColorMode() {
        return switch (outlineColorMode.get()) {
            case RAINBOW -> AnimatedRenderColors.Mode.RAINBOW;
            case LIGHT_RAINBOW -> AnimatedRenderColors.Mode.LIGHT_RAINBOW;
            case SKY -> AnimatedRenderColors.Mode.SKY;
            case FADE -> AnimatedRenderColors.Mode.FADE;
            case DOUBLE_COLOR -> AnimatedRenderColors.Mode.DOUBLE_COLOR;
            case ANALOGOUS -> AnimatedRenderColors.Mode.ANALOGOUS;
            case THEME -> AnimatedRenderColors.Mode.THEME;
            case STATIC -> AnimatedRenderColors.Mode.STATIC;
        };
    }

    public enum ColorMode implements EnumValue.IdProvider {
        STATIC("static"),
        RAINBOW("rainbow"),
        LIGHT_RAINBOW("light_rainbow"),
        SKY("sky"),
        FADE("fade"),
        DOUBLE_COLOR("double_color"),
        ANALOGOUS("analogous"),
        THEME("theme");

        private final String id;

        ColorMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum OutlineMode implements EnumValue.IdProvider {
        LINES("lines"),
        BOX("box"),
        BOTH("both");

        private final String id;

        OutlineMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum BreakAnimation implements EnumValue.IdProvider {
        GROW("grow"),
        SHRINK("shrink"),
        STATIC("static");

        private final String id;

        BreakAnimation(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
