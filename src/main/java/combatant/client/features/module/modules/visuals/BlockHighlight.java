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
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.renderer.GlowSurfaces;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.block.BlockOutlineShapeResolver;


@ModuleInfo(
        id = "blockhighlight",
        displayName = "BlockHighlight",
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.blockhighlight.description")
public class BlockHighlight extends Module {

    private static final double RAINBOW_SPATIAL_SCALE = 12.0;
    private static final double GEOMETRY_EXPAND = 0.0025;
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue outlineEnabled =
            bool(
                    "outline_enabled",
                    true
            );
    private final BooleanValue surfaceEnabled =
            bool("surface_enabled", true);
    private final RGBAColorValue surfaceColor =
            visibleWhen(color("surface_color", "#1F66DDFF"), surfaceEnabled::get);
    private final BooleanValue surfaceGradient =
            visibleWhen(bool("surface_gradient", false), surfaceEnabled::get);
    private final RGBAColorValue surfaceColor2 =
            visibleWhen(color("surface_color2", "#6066AFFF"),
                    () -> surfaceEnabled.get() && surfaceGradient.get());
    private final NumberValue<Float> surfaceGlow =
            visibleWhen(num("surface_glow", 0.65f, 0.0f, 2.0f), surfaceEnabled::get);
    private final NumberValue<Float> lineGlow =
            visibleWhen(num("line_glow", 1.35f, 0.0f, 4.0f), outlineEnabled::get);
    private final EnumValue<GlowSurfaces.Animation> glowAnimation =
            enumMode("glow_animation", GlowSurfaces.Animation.BREATHE);
    private final NumberValue<Float> glowSpeed =
            visibleWhen(num("glow_speed", 1.0f, 0.1f, 5.0f),
                    () -> glowAnimation.get() != GlowSurfaces.Animation.STATIC);
    private final NumberValue<Float> glowAccent =
            num("glow_accent", 0.75f, 0.0f, 1.0f);

    private final RGBAColorValue outlineColor =
            visibleWhen(color(
                    "outline_color",
                    "#66FFFFFF"
            ), this::hasTargetGlow);
    private final EnumValue<ColorMode> outlineColorMode =
            visibleWhen(enumCommon(
                    "outline_color_mode",
                    "outline_color_mode",
                    CommonSettingSchemas.RENDER_COLOR_MODE,
                    ColorMode.STATIC,
                    ColorMode.values()
            ), this::hasTargetGlow);
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
            ), this::hasTargetGlow);
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
            ), this::hasTargetGlow);
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
        return isEnabled() && hasTargetGlow();
    }

    private boolean hasTargetGlow() {
        return outlineEnabled.get() || surfaceEnabled.get();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.BEFORE_TRANSLUCENT;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (hasTargetGlow()) {
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
        VoxelShape shape = BlockOutlineShapeResolver.resolve(
                state, mc.level, pos, context, state.getShape(mc.level, pos, context));
        if (shape.isEmpty()) return;

        VoxelShape worldShape = shape.move(pos.getX(), pos.getY(), pos.getZ());
        renderGlowShape(renderer, worldShape);
    }

    /** Surface and luminous edge ribbons share a single world_glow_surface GPU mesh batch. */
    private void renderGlowShape(Renderer3D renderer, VoxelShape worldShape) {
        GlowSurfaces.Batch batch = GlowSurfaces.begin(
                renderer, outlineDepthTest.get(), glowAnimation.get(), glowSpeed.get(), glowAccent.get());
        if (batch == null) return;

        if (surfaceEnabled.get()) {
            int bottom = surfaceColor.getArgb();
            int top = surfaceGradient.get() ? surfaceColor2.getArgb() : bottom;
            float rim = surfaceGlow.get();
            boolean animatedPalette = outlineColorMode.get() != ColorMode.STATIC;
            worldShape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                AABB box = new AABB(minX - GEOMETRY_EXPAND, minY - GEOMETRY_EXPAND,
                        minZ - GEOMETRY_EXPAND, maxX + GEOMETRY_EXPAND,
                        maxY + GEOMETRY_EXPAND, maxZ + GEOMETRY_EXPAND);
                if (animatedPalette) {
                    // AnimatedRenderColors must reach every face vertex, including both caps.
                    batch.boxColored(box, (x, y, z) -> surfaceAnimatedColor(box, x, y, z), rim, true);
                } else {
                    batch.box(box, bottom, top, rim, false, true);
                }
            });
        }
        if (outlineEnabled.get()) {
            float glow = lineGlow.get();
            float thickness = outlineThickness.get();
            worldShape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                    batch.edge(x1, y1, z1, x2, y2, z2,
                            getOutlineColor(x1, y1, z1), getOutlineColor(x2, y2, z2), glow, thickness));
        }
    }

    private int surfaceAnimatedColor(AABB box, double x, double y, double z) {
        int palette = getOutlineColor(x, y, z);
        int bottomAlpha = alpha(surfaceColor.getArgb());
        int topAlpha = surfaceGradient.get() ? alpha(surfaceColor2.getArgb()) : bottomAlpha;
        float t = (float) ((y - box.minY) / Math.max(1.0e-6, box.maxY - box.minY));
        return withAlpha(palette, interpolateChannel(bottomAlpha, topAlpha, Mth.clamp(t, 0.0f, 1.0f)));
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
                float rim = Mth.clamp(alpha(argb) / 255.0f * 2.0f, 0.25f, 1.0f);
                GlowSurfaces.box(renderer, box, argb, argb, rim,
                        glowAnimation.get(), glowSpeed.get(), glowAccent.get(), false, true, false);
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
        if (!hasTargetGlow()) return false;
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
