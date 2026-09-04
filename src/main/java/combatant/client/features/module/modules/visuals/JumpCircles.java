/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.time.Timer;

import java.util.*;
import java.util.List;

//todo Description
@ModuleInfo(
        id = "jumpcircles",
        displayName = "JumpCircles",
        category = ModuleCategory.VISUALS
)
public class JumpCircles extends Module {
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<CircleMode> mode =
            enumSetting("jumpCirclesMode", "mode", CircleMode.DEFAULT);
    private final BooleanValue easeOut = bool("jumpCirclesEaseOut", "ease_out", true);
    private final NumberValue<Float> rotateSpeed = num("jumpCirclesRotateSpeed", "rotate_speed", 2f, 0.5f, 5f);
    private final NumberValue<Float> circleScale = common(
            num("jumpCirclesCircleScale", "circle_scale", 1f, 0.5f, 5f),
            CommonSettingSchemas.RENDER_SCALE.commonI18nKey()
    );
    private final NumberValue<Float> pulseMaxSize = visibleWhen(num(
            "jumpCirclesPulseMaxSize", "pulse_max_size", 2.5f, 1.0f, 3.0f
    ), this::isPulseMode);
    private final NumberValue<Integer> pulseLifetime = visibleWhen(num(
            "jumpCirclesPulseLifetime", "pulse_lifetime", 1000, 500, 5000
    ), this::isPulseMode);
    private final BooleanValue onlySelf = bool("jumpCirclesOnlySelf", "only_self", false);
    private final BooleanValue depthTest = common(
            bool("jumpCirclesDepthTest", "depth_test", true),
            CommonSettingSchemas.RENDER_DEPTH_TEST.commonI18nKey()
    );
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
    private final Map<UUID, Double> groundedFeetY = new HashMap<>();

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

            // Cache the exact feet height while the player is grounded. Using
            // floor(Y) snaps slabs, stairs, snow layers, etc. to an integer
            // block height. On the first airborne tick the player Y has already
            // moved upward, so the last grounded bounding-box minY is the stable
            // takeoff surface height we want for the circle.
            if (nowGround) {
                groundedFeetY.put(id, pl.getBoundingBox().minY);
            }

            if (prevGround && !nowGround) {
                double surfaceY = groundedFeetY.getOrDefault(id, pl.getBoundingBox().minY);
                circles.add(new Circle(
                        new Vec3(pl.getX(), surfaceY + 0.015625, pl.getZ()),
                        new Timer()
                ));
            }
            groundState.put(id, nowGround);
        }

        groundState.keySet().removeIf(id -> !active.contains(id));
        groundedFeetY.keySet().removeIf(id -> !active.contains(id));
        circles.removeIf(c -> c.timer.passedMs(lifetimeMs()));
    }

    @Override
    public void onDisable() {
        circles.clear();
        groundState.clear();
        groundedFeetY.clear();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null || circles.isEmpty()) return;

        boolean useDepth = depthTest.get();
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
        MeshBuilder mesh = renderer.batchTextured(pipeline, resolveTexture(), depthMode);
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

        Vec3 right = new Vec3(cos, 0.0, -sin);
        Vec3 forward = new Vec3(sin, 0.0, cos);

        int c1 = ColorMath.scaleAlpha(getColorArgb(270), alpha);
        int c2 = ColorMath.scaleAlpha(getColorArgb(0), alpha);
        int c3 = ColorMath.scaleAlpha(getColorArgb(180), alpha);
        int c4 = ColorMath.scaleAlpha(getColorArgb(90), alpha);

        Vec3 p1 = center.add(right.scale(-half)).add(forward.scale(half));
        Vec3 p2 = center.add(right.scale(half)).add(forward.scale(half));
        Vec3 p3 = center.add(right.scale(half)).add(forward.scale(-half));
        Vec3 p4 = center.add(right.scale(-half)).add(forward.scale(-half));

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).vec2(0, 1).color(new RenderColor(c1)).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).vec2(1, 1).color(new RenderColor(c2)).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).vec2(1, 0).color(new RenderColor(c3)).next();
        int i4 = mesh.vec3(p4.x, p4.y, p4.z).vec2(0, 0).color(new RenderColor(c4)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private boolean isPulseMode() {
        return mode.get() == CircleMode.PULSE;
    }

    private long lifetimeMs() {
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
            case DEFAULT -> TextureStorage.DEFAULT_CIRCLE;
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
