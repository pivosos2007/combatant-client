/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.features.module.*;
import combatant.client.features.relations.CategoryService;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.List;

//todo Description
@ModuleInfo(id = "tracers", displayName = "Tracers", aliases = {"arrows", "lines"}, category = ModuleCategory.VISUALS)
public class Tracers extends Module {

    private static final String SETTING_TRACE_FRIENDS = "trace_friends";
    private static final String SETTING_MOVE_RADIUS_BONUS = "move_radius_bonus";
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_ARROW_TEXTURE = "arrow_texture";
    private static final Identifier ARROW_1 = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/arrow.png");
    private static final Identifier ARROW_2 = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/arrow2.png");
    private static final Identifier ARROW_3 = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/arrow3.png");
    private static final Identifier ARROW_4 = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/arrow4.png");
    private static final float BASE_RADIUS = 48.0f;
    private static final float INVENTORY_RADIUS_BONUS = 50.0f;
    private static final float MOVE_RADIUS_BONUS = 1.5f;
    private static final float ARROW_W = 10.0f;
    private static final float ARROW_H = 10.0f;
    private static final float ARROW_OFFSET_X = -5.0f;
    private static final float ARROW_OFFSET_Y = -6.0f;
    private static final int ARROW_ALPHA = 235;
    private static final float ARROW_ROTATION_OFFSET_DEG = 90.0f;
    private static final float ARROW_DISTANCE_NEAR = 6.0f;
    private static final float ARROW_DISTANCE_FAR = 60.0f;
    private static final float ARROW_RADIUS_MIN_FACTOR = 0.55f;
    private static final float ARROW_RADIUS_MAX_FACTOR = 1.0f;
    private static final MeshBuilder ARROW_MESH =
            new MeshBuilder(CombatantRenderPipelines.UI_TEXTURED_ADDITIVE);
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue traceFriendsValue = bool("tracersTraceFriends", SETTING_TRACE_FRIENDS, false);
    private final ModeValue tracersMode =
            modeSetting("tracersMode", SETTING_MODE, "Lines", "Lines", "Arrows");
    private final BooleanValue moveRadiusBonusValue =
            visibleWhen(bool("tracersMoveRadiusBonus", SETTING_MOVE_RADIUS_BONUS, false), this::isArrowsMode);
    private final ModeValue arrowTexture =
            visibleWhen(modeSetting("tracersArrowTexture", SETTING_ARROW_TEXTURE, "Arrow", "Arrow", "Arrow2", "Arrow3", "Arrow4"),
                    this::isArrowsMode);
    private float animationStep;
    private float animatedYaw;
    private float animatedPitch;
    private float yaw;

    private static boolean isMoving(Vec2 move) {
        if (move == null) return false;
        return move.x != 0.0f || move.y != 0.0f;
    }

    private static boolean isNameValid(String name) {
        if (name == null) return false;
        int len = name.length();
        if (len < 3 || len > 16) return false;
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c == '_') continue;
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    private static int withAlpha(int argb, int alpha) {
        int a = Mth.clamp(alpha, 0, 255);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static float radiusFactorForDistance(double distance) {
        if (distance <= ARROW_DISTANCE_NEAR) return ARROW_RADIUS_MIN_FACTOR;
        if (distance >= ARROW_DISTANCE_FAR) return ARROW_RADIUS_MAX_FACTOR;
        float t = (float) ((distance - ARROW_DISTANCE_NEAR) / (ARROW_DISTANCE_FAR - ARROW_DISTANCE_NEAR));
        t = Mth.clamp(t, 0.0f, 1.0f);
        return Mth.lerp(t, ARROW_RADIUS_MIN_FACTOR, ARROW_RADIUS_MAX_FACTOR);
    }

    private static void renderArrow(Identifier textureId,
                                    double x,
                                    double y,
                                    float angleRad,
                                    int argb,
                                    float scale) {
        if (textureId == null) return;
        Renderer2D.deferRenderThreadAction(() -> renderArrowImmediate(textureId, x, y, angleRad, argb, scale));
    }

    private static void renderArrowImmediate(Identifier textureId,
                                             double x,
                                             double y,
                                             float angleRad,
                                             int argb,
                                             float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) return;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        if (tex == null) return;
        GpuTextureView view = tex.getTextureView();
        GpuSampler sampler = tex.getSampler();
        if (view == null || sampler == null) return;

        RenderColor c = new RenderColor(argb);
        ARROW_MESH.begin();
        ARROW_MESH.ensureQuadCapacity();
        int i1 = ARROW_MESH.vec2(ARROW_OFFSET_X * scale, ARROW_OFFSET_Y * scale)
                .vec2(0, 0).color(c.r, c.g, c.b, c.a).next();
        int i2 = ARROW_MESH.vec2(ARROW_OFFSET_X * scale, (ARROW_OFFSET_Y + ARROW_H) * scale)
                .vec2(0, 1).color(c.r, c.g, c.b, c.a).next();
        int i3 = ARROW_MESH.vec2((ARROW_OFFSET_X + ARROW_W) * scale, (ARROW_OFFSET_Y + ARROW_H) * scale)
                .vec2(1, 1).color(c.r, c.g, c.b, c.a).next();
        int i4 = ARROW_MESH.vec2((ARROW_OFFSET_X + ARROW_W) * scale, ARROW_OFFSET_Y * scale)
                .vec2(1, 0).color(c.r, c.g, c.b, c.a).next();
        ARROW_MESH.quad(i1, i2, i3, i4);
        ARROW_MESH.end();

        Matrix4f transform = new Matrix4f()
                .translation((float) x, (float) y, 0.0f)
                .rotateZ(angleRad);

        MeshRenderer.begin()
                .attachments(mc.gameRenderer.mainRenderTarget())
                .pipeline(CombatantRenderPipelines.UI_TEXTURED_ADDITIVE)
                .mesh(ARROW_MESH)
                .transform(transform)
                .sampler("u_Texture", view, sampler)
                .end();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.BEFORE_MISC_OVERLAYS;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (!isLinesMode()) return;

        Vec3 camPos = getActiveCameraPos(tickDelta);
        Vec3 look = getActiveCameraLook(tickDelta);
        Vec3 start = camPos.add(look.scale(10.0));

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (!traceFriendsValue.get() && CategoryService.isFriend(p)) continue;

            int c = resolveColor(p);
            Vec3 targetWorld = obtainEntityLerpedPos(p, tickDelta);

            renderer.line(
                    start.x, start.y, start.z,
                    targetWorld.x, targetWorld.y, targetWorld.z,
                    (c >> 16) & 255,
                    (c >> 8) & 255,
                    c & 255,
                    (c >> 24) & 255
            );
        }
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, net.minecraft.client.gui.GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (!isArrowsMode()) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Vec2 move = mc.player.input != null ? mc.player.input.getMoveVector() : Vec2.ZERO;
        float moveX = move.x;
        float moveZ = move.y;

        float scale = ViewportContext.getScaleFactor();
        float moveScale = moveRadiusBonusValue.get() ? 10.0f : 0.0f;
        animatedYaw = AnimationUtility.fast(animatedYaw, moveX * moveScale * scale, 5.0f);
        animatedPitch = AnimationUtility.fast(animatedPitch, moveZ * moveScale * scale, 5.0f);

        yaw = AnimationUtility.fast(yaw, getActiveCameraYaw(), 10.0f);

        float size = BASE_RADIUS;
        if (ClientScreen.current() instanceof InventoryScreen) {
            size += INVENTORY_RADIUS_BONUS;
        }
        if (moveRadiusBonusValue.get() && isMoving(move)) {
            size += MOVE_RADIUS_BONUS;
        }
        animationStep = AnimationUtility.fast(animationStep, size * scale, 6.0f);

        double centerX = mc.getWindow().getWidth() * 0.5;
        double centerY = mc.getWindow().getHeight() * 0.5;

        Vec3 camPos = getActiveCameraPos(tickDelta);
        Identifier textureId = resolveArrowTexture();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (!traceFriendsValue.get() && CategoryService.isFriend(player)) continue;

            Vec3 targetWorld = obtainEntityLerpedPos(player, tickDelta);
            double relX = targetWorld.x - camPos.x;
            double relZ = targetWorld.z - camPos.z;

            double yawRad = Math.toRadians(yaw);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double rotY = -(relZ * cos - relX * sin);
            double rotX = -(relX * cos + relZ * sin);
            float angleDeg = (float) Math.toDegrees(Math.atan2(rotY, rotX));

            double dist = targetWorld.distanceTo(camPos);
            float radiusFactor = shouldUseAdaptiveRadius() ? radiusFactorForDistance(dist) : 1.0f;
            double radius = animationStep * radiusFactor;
            double x2 = radius * Mth.cos((float) Math.toRadians(angleDeg)) + centerX + animatedYaw;
            double y2 = radius * Mth.sin((float) Math.toRadians(angleDeg)) + centerY + animatedPitch;

            int color = withAlpha(resolveColor(player), ARROW_ALPHA);
            float rotRad = (float) Math.toRadians(angleDeg + ARROW_ROTATION_OFFSET_DEG);
            renderArrow(textureId, x2, y2, rotRad, color, scale);
        }
    }

    private Vec3 getActiveCameraPos(float tickDelta) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            return new Vec3(
                    Mth.lerp(tickDelta, fc.camPosPrev.x, fc.camPos.x),
                    Mth.lerp(tickDelta, fc.camPosPrev.y, fc.camPos.y),
                    Mth.lerp(tickDelta, fc.camPosPrev.z, fc.camPos.z)
            );
        }
        return mc.gameRenderer.mainCamera().position();
    }

    private Vec3 getActiveCameraLook(float tickDelta) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            float yaw = (float) Math.toRadians(fc.camYaw);
            float pitch = (float) Math.toRadians(fc.camPitch);

            return new Vec3(
                    -Math.sin(yaw) * Math.cos(pitch),
                    -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(pitch)
            ).normalize();
        }
        return mc.player.getViewVector(tickDelta).normalize();
    }

    private float getActiveCameraYaw() {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled()) {
            return fc.camYaw;
        }
        return mc.gameRenderer.mainCamera().yRot();
    }

    public void render3DTracersRaw(
            Renderer3D renderer,
            float tickDelta,
            List<Vec3> targets,
            int argb
    ) {
        if (mc.player == null || mc.level == null || targets == null || targets.isEmpty()) return;

        Vec3 camPos = getActiveCameraPos(tickDelta);
        Vec3 look = getActiveCameraLook(tickDelta);
        Vec3 start = camPos.add(look.scale(10.0));

        int a = (argb >> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;

        for (Vec3 targetWorld : targets) {
            renderer.line(
                    start.x, start.y, start.z,
                    targetWorld.x, targetWorld.y, targetWorld.z,
                    r, g, b, a
            );
        }
    }

    private Vec3 obtainEntityLerpedPos(Entity e, float f) {
        try {
            return e.getPosition(f);
        } catch (Throwable t) {
            if (e instanceof IEntity a) {
                return a.get$InstantRenderPos().lerp(e.position(), f);
            }
            return e.position();
        }
    }

    private int resolveColor(Player p) {
        return CategoryService.getColor(p);
    }

    private boolean shouldUseAdaptiveRadius() {
        if (mc.player == null) return true;
        return !(ClientScreen.current() instanceof InventoryScreen);
    }

    private Identifier resolveArrowTexture() {
        String mode = arrowTexture.get();
        if ("Arrow2".equalsIgnoreCase(mode)) return ARROW_2;
        if ("Arrow3".equalsIgnoreCase(mode)) return ARROW_3;
        if ("Arrow4".equalsIgnoreCase(mode)) return ARROW_4;
        return ARROW_1;
    }

    private boolean isLinesMode() {
        return "Lines".equalsIgnoreCase(tracersMode.get());
    }

    private boolean isArrowsMode() {
        return "Arrows".equalsIgnoreCase(tracersMode.get());
    }

}
