/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.effects.shockwave.WorldJumpShockwaves;
import combatant.client.util.time.Timer;

import java.util.*;
import java.util.List;

@ModuleInfo(
        id = "jumpcircles",
        displayName = "JumpCircles",
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.COSMETIC,
        description = "module.jumpcircles.description")
public class JumpCircles extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<CircleMode> mode =
            enumSetting("jumpCirclesMode", "mode", CircleMode.SHOCKWAVE);
    private final BooleanValue easeOut = visibleWhen(
            bool("jumpCirclesEaseOut", "ease_out", true), this::isLegacyMode);
    private final NumberValue<Float> rotateSpeed = visibleWhen(
            num("jumpCirclesRotateSpeed", "rotate_speed", 2f, 0.5f, 5f), this::isLegacyMode);
    private final NumberValue<Float> circleScale = visibleWhen(common(
            num("jumpCirclesCircleScale", "circle_scale", 1f, 0.5f, 5f),
            CommonSettingSchemas.RENDER_SCALE.commonI18nKey()
    ), () -> !isShockwaveMode());
    private final NumberValue<Float> pulseMaxSize = visibleWhen(num(
            "jumpCirclesPulseMaxSize", "pulse_max_size", 2.5f, 1.0f, 3.0f
    ), this::isPulseMode);
    private final NumberValue<Integer> pulseLifetime = visibleWhen(num(
            "jumpCirclesPulseLifetime", "pulse_lifetime", 1000, 500, 5000
    ), this::isPulseMode);
    private final BooleanValue onlySelf = bool("jumpCirclesOnlySelf", "only_self", false);
    private final BooleanValue glowRing = visibleWhen(
            bool("jumpCirclesGlowRing", "glow_ring", true), () -> !isShockwaveMode());
    private final BooleanValue depthTest = common(
            bool("jumpCirclesDepthTest", "depth_test", true),
            CommonSettingSchemas.RENDER_DEPTH_TEST.commonI18nKey()
    );
    private final NumberValue<Float> shockRadius = visibleWhen(
            num("jumpCirclesShockRadius", "shock_radius", 1.5f, 0.5f, 2.5f), this::isShockwaveMode);
    private final NumberValue<Float> shockWidth = visibleWhen(
            num("jumpCirclesShockWidth", "shock_width", 1.0f, 0.05f, 1.5f), this::isShockwaveMode);
    private final NumberValue<Float> shockStrength = visibleWhen(
            num("jumpCirclesShockStrength", "shock_strength", 2.0f, 0.1f, 2.0f), this::isShockwaveMode);
    private final NumberValue<Integer> shockExpandMs = visibleWhen(
            num("jumpCirclesShockExpand", "shock_expand", 800, 100, 800), this::isShockwaveMode);
    private final NumberValue<Integer> shockFadeMs = visibleWhen(
            num("jumpCirclesShockFade", "shock_fade", 1500, 200, 1500), this::isShockwaveMode);
    private final EnumValue<ColorMode> colorMode =
            enumCommon("jumpCirclesColorMode", "color_mode",
                    CommonSettingSchemas.RENDER_COLOR_MODE.commonI18nKey(), ColorMode.STATIC);
    private final NumberValue<Integer> colorSpeed = common(
            num("jumpCirclesColorSpeed", "color_speed", 18, 2, 54),
            CommonSettingSchemas.RENDER_COLOR_SPEED.commonI18nKey()
    );
    private final RGBAColorValue colorValue = common(
            color("jumpCirclesColor", "color", "#FF55FFFF"),
            CommonSettingSchemas.RENDER_PRIMARY_COLOR.commonI18nKey()
    );
    private final RGBAColorValue colorValue2 = visibleWhen(common(
            color("jumpCirclesColor2", "color2", "#FFFF5555"),
            CommonSettingSchemas.RENDER_SECONDARY_COLOR.commonI18nKey()
    ), this::usesSecondaryColor);
    private final List<Circle> circles = new ArrayList<>();
    private final Map<UUID, Boolean> groundState = new HashMap<>();
    private final Map<UUID, Vec3> groundedPosition = new HashMap<>();

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        boolean selfOnly = onlySelf.get();
        Set<UUID> active = new HashSet<>();

        for (Player pl : mc.level.players()) {
            if (selfOnly && pl != mc.player) continue;
            UUID id = pl.getUUID();
            active.add(id);

            boolean prevGround = groundState.getOrDefault(id, false);
            boolean nowGround = pl.onGround();

            if (nowGround) {
                groundedPosition.put(id, new Vec3(pl.getX(), pl.getY(), pl.getZ()));
            }

            if (prevGround && !nowGround) {
                Vec3 grounded = groundedPosition.getOrDefault(id, pl.position());
                boolean rising = pl.getDeltaMovement().y > 0.0 || pl.getY() - grounded.y > 0.02;
                if (rising) {
                    circles.add(new Circle(
                            new Vec3(grounded.x, resolveSurfaceY(grounded.x, grounded.y, grounded.z), grounded.z),
                            new Timer()
                    ));
                }
            }
            groundState.put(id, nowGround);
        }

        groundState.keySet().removeIf(id -> !active.contains(id));
        groundedPosition.keySet().removeIf(id -> !active.contains(id));
        circles.removeIf(c -> c.timer.passedMs(lifetimeMs()));
    }

    private double resolveSurfaceY(double x, double y, double z) {
        if (mc.level == null) return y + 0.01;

        BlockPos base = BlockPos.containing(x, y, z);
        double localX = x - base.getX();
        double localZ = z - base.getZ();
        double best = Double.NEGATIVE_INFINITY;

        for (int blockY = base.getY() + 1; blockY >= base.getY() - 2; blockY--) {
            BlockPos pos = new BlockPos(base.getX(), blockY, base.getZ());
            BlockState state = mc.level.getBlockState(pos);
            VoxelShape shape = state.getShape(mc.level, pos);
            for (AABB box : shape.toAabbs()) {
                if (localX < box.minX || localX > box.maxX || localZ < box.minZ || localZ > box.maxZ) continue;
                double top = pos.getY() + box.maxY;
                boolean sameSurface = Math.abs(top - y) <= 0.01;
                boolean snowSurface = state.is(Blocks.SNOW) && top >= y - 0.01 && top <= y + 0.13;
                if ((sameSurface || snowSurface) && top > best) best = top;
            }
        }

        return (best == Double.NEGATIVE_INFINITY ? y : best) + 0.01;
    }

    @Override
    public void onDisable() {
        circles.clear();
        groundState.clear();
        groundedPosition.clear();
        WorldJumpShockwaves.clear();
    }

    @Override
    public void onPrepareWorldPostProcess(float tickDelta) {
        if (!isEnabled() || !isShockwaveMode() || mc.level == null || mc.player == null || circles.isEmpty()) return;

        int submitted = 0;
        long expand = shockExpandMs.get();
        long fade = shockFadeMs.get();
        long total = Math.max(1L, expand + fade);
        boolean useDepth = depthTest.get();

        for (Circle circle : circles) {
            if (submitted >= 12) break;
            long age = circle.timer.getPassedTimeMs();
            if (age < 0L || age >= total) continue;

            float radius;
            float envelope;
            if (age < expand) {
                float p = Mth.clamp(age / (float) Math.max(1L, expand), 0.0f, 1.0f);
                float eased = 1.0f - (float) Math.pow(1.0f - p, 3.0);
                radius = shockRadius.get() * eased;
                envelope = eased;
            } else {
                float p = Mth.clamp((age - expand) / (float) Math.max(1L, fade), 0.0f, 1.0f);
                radius = shockRadius.get() * (1.0f + p * 0.18f);
                float remaining = 1.0f - p;
                envelope = remaining * remaining;
            }

            if (radius <= 0.001f || envelope <= 0.001f) continue;
            float progress = Mth.clamp(age / (float) total, 0.0f, 1.0f);
            float thickness = shockWidth.get() * (1.0f - 0.5f * progress);
            int color = ColorMath.scaleAlpha(getColorArgb(submitted * 41), envelope);
            WorldJumpShockwaves.submit(
                    circle.pos,
                    radius,
                    thickness,
                    color,
                    shockStrength.get() * envelope,
                    useDepth
            );
            submitted++;
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null || circles.isEmpty() || isShockwaveMode()) return;

        boolean useDepth = depthTest.get();
        boolean glow = glowRing.get();
        RenderPipeline pipeline = glow
                ? (useDepth ? WorldRenderPipelines.WORLD_GLOW_RING_DEPTH : WorldRenderPipelines.WORLD_GLOW_RING)
                : (useDepth
                        ? WorldRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                        : WorldRenderPipelines.WORLD_TEXTURED_ADDITIVE);
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE;
        MeshBuilder mesh = glow
                ? renderer.batch(pipeline, depthMode)
                : renderer.batchTextured(pipeline, resolveTexture(), depthMode);
        if (mesh == null) return;

        for (int i = circles.size() - 1; i >= 0; i--) {
            Circle c = circles.get(i);
            if (isPulseMode()) {
                renderPulseCircle(mesh, c);
            } else {
                renderLegacyCircle(mesh, c);
            }
        }
    }

    private void renderLegacyCircle(MeshBuilder mesh, Circle c) {
        float colorAnim = c.timer.getPassedTimeMs() / 6000f;
        float t = (c.timer.getPassedTimeMs() * (easeOut.get() ? 2f : 1f)) / 5000f;
        float sizeAnim = circleScale.get() - (float) Math.pow(1f - t, 4f);
        if (sizeAnim <= 0f) return;

        float angleDeg = sizeAnim * rotateSpeed.get() * 1000f;
        emitCircleQuad(mesh, c.pos, sizeAnim, angleDeg, 1f - colorAnim);
    }

    private void renderPulseCircle(MeshBuilder mesh, Circle c) {
        float progress = Math.min(c.timer.getPassedTimeMs() / (float) pulseLifetime.get(), 1f);
        if (progress >= 1f) return;

        float scale = bounceOut(progress) * pulseMaxSize.get() * circleScale.get();
        if (scale <= 0f) return;

        float alpha = pulseAlpha(progress);
        emitCircleQuad(mesh, c.pos, scale * 0.5f, 0f, alpha);
    }

    private void emitCircleQuad(MeshBuilder mesh, Vec3 center, float half, float angleDeg, float alpha) {
        double radians = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        int c1 = ColorMath.scaleAlpha(getColorArgb(270), alpha);
        int c2 = ColorMath.scaleAlpha(getColorArgb(0), alpha);
        int c3 = ColorMath.scaleAlpha(getColorArgb(180), alpha);
        int c4 = ColorMath.scaleAlpha(getColorArgb(90), alpha);

        // right = (cos, 0, -sin), forward = (sin, 0, cos); corners are expanded by hand so a frame with
        // many circles allocates no Vec3/RenderColor temporaries.
        double rx = cos * half, rz = -sin * half;
        double fx = sin * half, fz = cos * half;
        double cx = center.x, cy = center.y, cz = center.z;

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(cx - rx + fx, cy, cz - rz + fz).vec2(0, 1).colorArgb(c1).next();
        int i2 = mesh.vec3(cx + rx + fx, cy, cz + rz + fz).vec2(1, 1).colorArgb(c2).next();
        int i3 = mesh.vec3(cx + rx - fx, cy, cz + rz - fz).vec2(1, 0).colorArgb(c3).next();
        int i4 = mesh.vec3(cx - rx - fx, cy, cz - rz - fz).vec2(0, 0).colorArgb(c4).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private boolean isPulseMode() {
        return mode.get() == CircleMode.PULSE;
    }

    private boolean isShockwaveMode() {
        return mode.get() == CircleMode.SHOCKWAVE;
    }

    private boolean isLegacyMode() {
        return mode.get() == CircleMode.DEFAULT || mode.get() == CircleMode.BUBBLE;
    }

    private long lifetimeMs() {
        if (isShockwaveMode()) return shockExpandMs.get() + shockFadeMs.get();
        return isPulseMode() ? pulseLifetime.get() : (easeOut.get() ? 5000L : 6000L);
    }

    private int getColorArgb(int count) {
        return AnimatedRenderColors.resolve(
                animatedColorMode(),
                colorSpeed.get(),
                count,
                colorValue.getArgb(),
                colorValue2.getArgb()
        );
    }

    private AnimatedRenderColors.Mode animatedColorMode() {
        return switch (colorMode.get()) {
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

    private Identifier resolveTexture() {
        return switch (mode.get()) {
            case BUBBLE -> TextureStorage.BUBBLE;
            case PULSE -> TextureStorage.JUMP_CIRCLE;
            case DEFAULT, SHOCKWAVE -> TextureStorage.DEFAULT_CIRCLE;
        };
    }

    private float pulseAlpha(float progress) {
        final float fadeInDuration = 0.15f;
        final float glowStart = 0.65f;
        final float fadeOutStart = 0.85f;
        float alpha;

        if (progress < fadeInDuration) {
            alpha = progress / fadeInDuration;
        } else if (progress >= fadeOutStart) {
            float fadeOutProgress = (progress - fadeOutStart) / (1f - fadeOutStart);
            alpha = 1f - fadeOutProgress;
            if (progress > glowStart) {
                float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
                float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
                alpha += glowPulse * (1f - fadeOutProgress);
            }
        } else if (progress > glowStart) {
            float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
            float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
            alpha = 1f + glowPulse;
        } else {
            alpha = 1f;
        }

        return Math.max(0f, Math.min(1f, alpha));
    }

    private float bounceOut(float value) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (value < 1.0f / d1) {
            return n1 * value * value;
        } else if (value < 2.0f / d1) {
            value -= 1.5f / d1;
            return n1 * value * value + 0.75f;
        } else if (value < 2.5f / d1) {
            value -= 2.25f / d1;
            return n1 * value * value + 0.9375f;
        } else {
            value -= 2.625f / d1;
            return n1 * value * value + 0.984375f;
        }
    }

    private enum CircleMode {
        SHOCKWAVE,
        DEFAULT,
        BUBBLE,
        PULSE
    }

    private enum ColorMode implements EnumValue.AliasProvider {
        STATIC,
        RAINBOW,
        LIGHT_RAINBOW,
        SKY,
        FADE,
        DOUBLE_COLOR,
        ANALOGOUS,
        THEME;

        @Override
        public List<String> aliases() {
            return switch (this) {
                case LIGHT_RAINBOW -> List.of("LightRainbow");
                case DOUBLE_COLOR -> List.of("DoubleColor");
                case THEME -> List.of("Theme");
                default -> List.of();
            };
        }
    }

    private record Circle(Vec3 pos, Timer timer) {
    }
}
