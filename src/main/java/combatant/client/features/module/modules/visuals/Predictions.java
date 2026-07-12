/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.module.*;
import combatant.client.mixins.accessors.PersistentProjectileEntityAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.ScreenProjection;
import combatant.client.util.target.TargetManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

//todo Description
@ModuleInfo(id = "predictions", displayName = "Predictions", category = ModuleCategory.VISUALS)
public class Predictions extends Module {
    private static final String SETTING_LINE_COLOR = "line_color";
    private static final String SETTING_PEARL_CUSTOM_COLOR = "pearl_custom_color";
    private static final String SETTING_PEARL_COLOR = "pearl_color";
    private static final String SETTING_MAX_SIM_TICKS = "max_sim_ticks";
    private static final String SETTING_PEARL_MAX_SIM_TICKS = "pearl_max_ticks";
    private static final String SETTING_MAX_PROJECTILES = "max_projectiles";
    private static final String SETTING_PEARL_MAX_PROJECTILES = "pearl_max_projectiles";
    private static final String SETTING_SHOW_TIMER_PLATE = "show_plate";
    private static final String SETTING_AIM_TRAJECTORY = "aim_trajectory";
    private static final String SETTING_AIM_INDICATOR = "aim_indicator";
    private static final String SETTING_TARGETHUD_ON_AIM = "targethud_on_aim";
    private static final String SETTING_GLOW_WALLS = "glow_walls";
    private static final String SETTING_TNT_TIMER = "tnt_timer";
    private static final String SETTING_TNT_COLOR = "tnt_color";
    private static final String SETTING_PROJECTILES = "projectiles";

    private static final int HIT_SELF_COLOR = 0xFFFF4444;
    private static final int HIT_ENTITY_COLOR = 0xFFFFCC44;
    private static final int GLOW_SUBSTEPS = 10;
    private static final float GLOW_CORE_SIZE = 0.085f;
    private static final float GLOW_SOFT_SIZE = 0.16f;
    private static final float GLOW_CORE_ALPHA = 0.74f;
    private static final float GLOW_SOFT_ALPHA = 0.32f;
    private static final int AIM_GLOW_SUBSTEPS = 7;
    private static final float AIM_GLOW_CORE_SIZE = 0.055f;
    private static final float AIM_GLOW_SOFT_SIZE = 0.105f;
    private static final float AIM_GLOW_CORE_ALPHA = 0.42f;
    private static final float AIM_GLOW_SOFT_ALPHA = 0.17f;
    private static final double AIM_GLOW_MIN_CAMERA_DISTANCE_SQ = 0.56;
    private static final float IMPACT_CORE_SIZE = 0.24f;
    private static final float IMPACT_SOFT_SIZE = 0.48f;
    private static final float IMPACT_CORE_ALPHA = 0.95f;
    private static final float IMPACT_SOFT_ALPHA = 0.42f;
    private static final double IMPACT_BOX_EXPAND = 0.03;
    private static final int ENTITY_HIT_FILL_ALPHA = 34;
    private static final int ENTITY_HIT_LINE_ALPHA = 170;
    private static final float ENTITY_HIT_LINE_WIDTH = 1.5f;
    private static final float PLATE_RADIUS = 1.7f;
    private static final float PLATE_TEXT_SCALE = 0.68f;
    private static final float PLATE_TEXT_PADDING = 24.0f;
    private static final float PLATE_HEIGHT = 16.0f;
    private static final float PLATE_BG_OFFSET_X = 2.0f;
    private static final float PLATE_BG_OFFSET_Y = 0.0f;
    private static final float PLATE_BG_SHRINK = 3.0f;
    private static final float PLATE_ICON_SIZE = 13.0f;
    private static final float PLATE_ICON_OFFSET_X = 4.0f;
    private static final float PLATE_ICON_TEXT_GAP = 4.0f;
    private static final float PLATE_TEXT_OFFSET_Y = 0.0f;
    private static final int PLATE_SHADOW = 0xAA000000;
    private static final float PLATE_SHADOW_BLUR = 3.6f;
    private static final float PLATE_SHADOW_INNER_ALPHA = 0.18f;
    private static final float TRAJECTORY_LINE_WIDTH = 1.35f;
    private static final float TRAJECTORY_LINE_ALPHA_START = 0.16f;
    private static final float TRAJECTORY_LINE_ALPHA_END = 0.78f;
    private static final int IMPACT_RING_SEGMENTS = 64;
    private static final float IMPACT_RING_RADIUS = 0.30f;
    private static final float IMPACT_RING_CROSS_RADIUS = 0.18f;
    private static final float IMPACT_RING_LINE_WIDTH = 1.2f;
    private static final float IMPACT_RING_ALPHA = 0.88f;
    private static final float IMPACT_DECAL_RADIUS = 0.34f;
    private static final float IMPACT_DECAL_EDGE_SOFTNESS = 0.085f;
    private static final float IMPACT_DECAL_STROKE_WIDTH = 0.10f;
    private static final float IMPACT_DECAL_PATTERN_SCALE = 4.25f;
    private static final float IMPACT_DECAL_FILL_ALPHA = 0.48f;
    private static final float IMPACT_DECAL_STROKE_ALPHA = 0.92f;
    private static final float IMPACT_DECAL_PATTERN_STRENGTH = 0.10f;
    private static final float IMPACT_DECAL_QUAD_PADDING = 1.18f;
    private static final double IMPACT_DECAL_OFFSET = 0.004;

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBColorValue lineColorValue = colorNoAlpha("pred_line_color", SETTING_LINE_COLOR, "#11C7C7");
    private final BooleanValue pearlColorToggle = bool("pred_pearl_custom_color", SETTING_PEARL_CUSTOM_COLOR, false);
    private final RGBColorValue pearlColorValue =
            visibleWhen(colorNoAlpha("pred_pearl_color", SETTING_PEARL_COLOR, "#C68CFF"), pearlColorToggle::get);
    private final NumberValue<Integer> maxSimTicks =
            num("pred_max_ticks", SETTING_MAX_SIM_TICKS, 120, 20, 600);
    private final NumberValue<Integer> pearlMaxSimTicks =
            num("pred_pearl_max_ticks", SETTING_PEARL_MAX_SIM_TICKS, 160, 20, 1000);
    private final NumberValue<Integer> maxProjectiles =
            num("pred_max_projectiles", SETTING_MAX_PROJECTILES, 48, 1, 256);
    private final NumberValue<Integer> pearlMaxProjectiles =
            num("pred_pearl_max_projectiles", SETTING_PEARL_MAX_PROJECTILES, 12, 1, 128);
    private final BooleanValue showTimerPlateValue = bool("pred_show_timer_plate", SETTING_SHOW_TIMER_PLATE, true);
    private final BooleanValue aimTrajectoryValue = bool("pred_aim_trajectory", SETTING_AIM_TRAJECTORY, true);
    private final BooleanValue aimIndicatorValue = bool("pred_aim_indicator", SETTING_AIM_INDICATOR, false);
    private final BooleanValue targetHudOnAimValue = bool("pred_targethud_on_aim", SETTING_TARGETHUD_ON_AIM, false);
    private final BooleanValue glowWallsValue = bool("pred_glow_walls", SETTING_GLOW_WALLS, false);
    private final BooleanValue tntTimerValue = bool("pred_tnt_timer", SETTING_TNT_TIMER, true);
    private final RGBColorValue tntColorValue =
            visibleWhen(colorNoAlpha("pred_tnt_color", SETTING_TNT_COLOR, "#FF5555"), tntTimerValue::get);
    private final BooleanMapValue kindToggles = group("pred_kind_toggles", SETTING_PROJECTILES, new java.util.LinkedHashMap<>() {{
        put("arrow", true);
        put("trident", true);
        put("fireball", true);
        put("wind_charge", true);
        put("firework", true);
        put("snowball", true);
        put("egg", true);
        put("potion_splash", true);
        put("potion_lingering", true);
    }});

    private ItemStack tntTimerIcon = ItemStack.EMPTY;
    private final List<TimerPlate> timerPlates = new ArrayList<>();

    private static boolean canHitPredictedEntity(Entity projectile, Entity entity, AABB collisionBox) {
        if (projectile == null || entity == null || !entity.isAlive() || !entity.canBeHitByProjectile()) {
            return false;
        }

        if (projectile instanceof Projectile projectileEntity) {
            Entity owner = projectileEntity.getOwner();
            if (owner != null && !hasPredictedLeftOwner(owner, entity, collisionBox)) {
                return !owner.isPassengerOfSameVehicle(entity);
            }
        }

        return true;
    }

    private static boolean hasPredictedLeftOwner(Entity owner, Entity entity, AABB collisionBox) {
        if (owner == null || collisionBox == null) {
            return true;
        }
        return owner.getRootVehicle()
                .getSelfAndPassengers()
                .filter(Entity::isPickable)
                .noneMatch(passenger -> collisionBox.intersects(passenger.getBoundingBox()));
    }

    private static void addGradientLine(MeshBuilder mesh, Vec3 from, Vec3 to, int startColor, int endColor) {
        if (mesh == null || from == null || to == null) return;
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(from.x, from.y, from.z).color(new RenderColor(startColor)).next();
        int i2 = mesh.vec3(to.x, to.y, to.z).color(new RenderColor(endColor)).next();
        mesh.line(i1, i2);
    }

    private static void addImpactRing(MeshBuilder mesh, Vec3 center, Vec3 normal, float radius, int argb) {
        if (mesh == null || center == null) return;

        Vec3 useNormal = normal == null || normal.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 1.0, 0.0)
                : normal.normalize();
        Vec3 axis = Math.abs(useNormal.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 tangent = useNormal.cross(axis);
        if (tangent.lengthSqr() < 1.0E-6) {
            tangent = new Vec3(1.0, 0.0, 0.0);
        } else {
            tangent = tangent.normalize();
        }
        Vec3 bitangent = useNormal.cross(tangent).normalize();

        for (int i = 0; i < IMPACT_RING_SEGMENTS; i++) {
            double angle0 = (Math.PI * 2.0 * i) / IMPACT_RING_SEGMENTS;
            double angle1 = (Math.PI * 2.0 * (i + 1)) / IMPACT_RING_SEGMENTS;
            Vec3 p0 = center
                    .add(tangent.scale(Math.cos(angle0) * radius))
                    .add(bitangent.scale(Math.sin(angle0) * radius));
            Vec3 p1 = center
                    .add(tangent.scale(Math.cos(angle1) * radius))
                    .add(bitangent.scale(Math.sin(angle1) * radius));
            addGradientLine(mesh, p0, p1, argb, argb);
        }

        Vec3 crossTangent = tangent.scale(IMPACT_RING_CROSS_RADIUS);
        Vec3 crossBitangent = bitangent.scale(IMPACT_RING_CROSS_RADIUS);
        addGradientLine(mesh, center.subtract(crossTangent), center.add(crossTangent), argb, argb);
        addGradientLine(mesh, center.subtract(crossBitangent), center.add(crossBitangent), argb, argb);
    }

    private static void addImpactDecal(MeshBuilder mesh, Vec3 center, Vec3 normal, float radius, int argb) {
        if (mesh == null || center == null || radius <= 0.0f) {
            return;
        }

        Vec3 useNormal = normal == null || normal.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 1.0, 0.0)
                : normal.normalize();
        Vec3 axis = Math.abs(useNormal.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 tangent = useNormal.cross(axis);
        if (tangent.lengthSqr() < 1.0E-6) {
            tangent = new Vec3(1.0, 0.0, 0.0);
        } else {
            tangent = tangent.normalize();
        }
        Vec3 bitangent = useNormal.cross(tangent).normalize();
        Vec3 offsetCenter = center.add(useNormal.scale(IMPACT_DECAL_OFFSET));
        float quadRadius = radius * IMPACT_DECAL_QUAD_PADDING;
        float sdfRadius = 1.0f / IMPACT_DECAL_QUAD_PADDING;
        Vec3 tangentScaled = tangent.scale(quadRadius);
        Vec3 bitangentScaled = bitangent.scale(quadRadius);

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(offsetCenter.subtract(tangentScaled).subtract(bitangentScaled).x,
                        offsetCenter.subtract(tangentScaled).subtract(bitangentScaled).y,
                        offsetCenter.subtract(tangentScaled).subtract(bitangentScaled).z)
                .vec2(0.0, 0.0)
                .color(new RenderColor(argb))
                .vec4(sdfRadius, IMPACT_DECAL_EDGE_SOFTNESS, IMPACT_DECAL_STROKE_WIDTH, IMPACT_DECAL_PATTERN_SCALE)
                .vec4(IMPACT_DECAL_FILL_ALPHA, IMPACT_DECAL_STROKE_ALPHA, IMPACT_DECAL_PATTERN_STRENGTH, 0.0f)
                .next();
        int i2 = mesh.vec3(offsetCenter.add(tangentScaled).subtract(bitangentScaled).x,
                        offsetCenter.add(tangentScaled).subtract(bitangentScaled).y,
                        offsetCenter.add(tangentScaled).subtract(bitangentScaled).z)
                .vec2(1.0, 0.0)
                .color(new RenderColor(argb))
                .vec4(sdfRadius, IMPACT_DECAL_EDGE_SOFTNESS, IMPACT_DECAL_STROKE_WIDTH, IMPACT_DECAL_PATTERN_SCALE)
                .vec4(IMPACT_DECAL_FILL_ALPHA, IMPACT_DECAL_STROKE_ALPHA, IMPACT_DECAL_PATTERN_STRENGTH, 0.0f)
                .next();
        int i3 = mesh.vec3(offsetCenter.add(tangentScaled).add(bitangentScaled).x,
                        offsetCenter.add(tangentScaled).add(bitangentScaled).y,
                        offsetCenter.add(tangentScaled).add(bitangentScaled).z)
                .vec2(1.0, 1.0)
                .color(new RenderColor(argb))
                .vec4(sdfRadius, IMPACT_DECAL_EDGE_SOFTNESS, IMPACT_DECAL_STROKE_WIDTH, IMPACT_DECAL_PATTERN_SCALE)
                .vec4(IMPACT_DECAL_FILL_ALPHA, IMPACT_DECAL_STROKE_ALPHA, IMPACT_DECAL_PATTERN_STRENGTH, 0.0f)
                .next();
        int i4 = mesh.vec3(offsetCenter.subtract(tangentScaled).add(bitangentScaled).x,
                        offsetCenter.subtract(tangentScaled).add(bitangentScaled).y,
                        offsetCenter.subtract(tangentScaled).add(bitangentScaled).z)
                .vec2(0.0, 1.0)
                .color(new RenderColor(argb))
                .vec4(sdfRadius, IMPACT_DECAL_EDGE_SOFTNESS, IMPACT_DECAL_STROKE_WIDTH, IMPACT_DECAL_PATTERN_SCALE)
                .vec4(IMPACT_DECAL_FILL_ALPHA, IMPACT_DECAL_STROKE_ALPHA, IMPACT_DECAL_PATTERN_STRENGTH, 0.0f)
                .next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static Vec3 resolveImpactNormal(HitResult hit, Vec3 velocity) {
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            return Vec3.atLowerCornerOf(blockHit.getDirection().getUnitVec3i());
        }
        if (velocity != null && velocity.lengthSqr() > 1.0E-6) {
            return velocity.normalize();
        }
        return new Vec3(0.0, 1.0, 0.0);
    }

    private static double partialStepProgress(Vec3 from, Vec3 hitPos, double segmentLengthSq) {
        if (from == null || hitPos == null || segmentLengthSq <= 1.0E-6) {
            return 1.0;
        }
        return Mth.clamp(from.distanceTo(hitPos) / Math.sqrt(segmentLengthSq), 0.0, 1.0);
    }

    private static Vec3 resolveEntityImpactNormal(AABB box, Vec3 hitPos, Vec3 fallbackVelocity) {
        if (box == null || hitPos == null) {
            return resolveImpactNormal(null, fallbackVelocity);
        }

        double dxMin = Math.abs(hitPos.x - box.minX);
        double dxMax = Math.abs(box.maxX - hitPos.x);
        double dyMin = Math.abs(hitPos.y - box.minY);
        double dyMax = Math.abs(box.maxY - hitPos.y);
        double dzMin = Math.abs(hitPos.z - box.minZ);
        double dzMax = Math.abs(box.maxZ - hitPos.z);

        double best = dxMin;
        Vec3 normal = new Vec3(-1.0, 0.0, 0.0);
        if (dxMax < best) {
            best = dxMax;
            normal = new Vec3(1.0, 0.0, 0.0);
        }
        if (dyMin < best) {
            best = dyMin;
            normal = new Vec3(0.0, -1.0, 0.0);
        }
        if (dyMax < best) {
            best = dyMax;
            normal = new Vec3(0.0, 1.0, 0.0);
        }
        if (dzMin < best) {
            best = dzMin;
            normal = new Vec3(0.0, 0.0, -1.0);
        }
        if (dzMax < best) {
            normal = new Vec3(0.0, 0.0, 1.0);
        }
        return normal;
    }

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         float size, Quaternionf camRot, int argb) {
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot).mul(size);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot).mul(size);

        double p1x = cx - right.x() - up.x();
        double p1y = cy - right.y() - up.y();
        double p1z = cz - right.z() - up.z();
        double p2x = cx + right.x() - up.x();
        double p2y = cy + right.y() - up.y();
        double p2z = cz + right.z() - up.z();
        double p3x = cx + right.x() + up.x();
        double p3y = cy + right.y() + up.y();
        double p3z = cz + right.z() + up.z();
        double p4x = cx - right.x() + up.x();
        double p4y = cy - right.y() + up.y();
        double p4z = cz - right.z() + up.z();

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0.0, 1.0).color(new RenderColor(argb)).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1.0, 1.0).color(new RenderColor(argb)).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1.0, 0.0).color(new RenderColor(argb)).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0.0, 0.0).color(new RenderColor(argb)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

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

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static int multiplyAlpha(int argb, float alphaMultiplier) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * Math.max(0.0f, alphaMultiplier))));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static Vec3 advanceTickVelocity(Entity entity, Vec3 velocity) {
        return velocity.scale(0.99).add(0.0, -entity.getGravity(), 0.0);
    }

    private static Kind kindFromParams(ItemStack stack) {
        if (stack == null) return Kind.ARROW;
        if (stack.is(Items.TRIDENT)) return Kind.TRIDENT;
        if (stack.is(Items.SPLASH_POTION)) return Kind.POTION_SPLASH;
        if (stack.is(Items.LINGERING_POTION)) return Kind.POTION_LINGERING;
        if (stack.is(Items.SNOWBALL)) return Kind.SNOWBALL;
        if (stack.is(Items.EGG)) return Kind.EGG;
        if (stack.is(Items.FIREWORK_ROCKET)) return Kind.FIREWORK;
        if (stack.is(Items.WIND_CHARGE)) return Kind.WIND_CHARGE;
        if (stack.is(Items.FIRE_CHARGE)) return Kind.FIREBALL;
        if (stack.is(Items.ENDER_PEARL)) return Kind.PEARL_ENTITY;
        return Kind.ARROW;
    }

    private static Kind kindFromProjectile(Entity entity) {
        if (entity instanceof ThrownTrident) return Kind.TRIDENT;
        if (entity instanceof AbstractThrownPotion potion) {
            return potion.getItem().is(Items.LINGERING_POTION) ? Kind.POTION_LINGERING : Kind.POTION_SPLASH;
        }
        if (entity instanceof Snowball) return Kind.SNOWBALL;
        if (entity instanceof ThrownEgg) return Kind.EGG;
        if (entity instanceof FireworkRocketEntity) return Kind.FIREWORK;
        if (entity instanceof WindCharge) return Kind.WIND_CHARGE;
        if (entity instanceof Fireball) return Kind.FIREBALL;
        if (entity instanceof ThrownEnderpearl) return Kind.PEARL_ENTITY;
        return Kind.ARROW;
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
    public void onDisable() {
        TargetManager.setPredictionTarget(null);
    }

    @Override
    public void onRenderWorld(PoseStack matrices, SubmitNodeCollector consumers, float tickDelta) {
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null) {
            TargetManager.setPredictionTarget(null);
            return;
        }

        List<TrajectoryResult> results = simulateProjectiles(tickDelta);
        updatePredictionTarget(tickDelta);

        int baseColor = lineColorValue.getArgb();
        if (aimTrajectoryValue.get()) {
            renderAimTrajectoryGlow(renderer, tickDelta, baseColor);
        }
        if (aimIndicatorValue.get()) {
            renderAimIndicatorWorld(renderer, tickDelta);
        }

        if (results.isEmpty()) return;

        renderLineTrajectories(renderer, results, baseColor);
        renderImpactEffects(renderer, results, baseColor, tickDelta);
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, net.minecraft.client.gui.GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null) return;

        boolean showPlate = showTimerPlateValue.get();
        timerPlates.clear();
        List<TrajectoryResult> results = showPlate ? simulateProjectiles(tickDelta) : Collections.emptyList();
        if (showPlate && !results.isEmpty()) {
            for (TrajectoryResult res : results) {
                if (res.points.isEmpty()) continue;
                Vec3 end = res.points.getLast();
                Vec3 screen = ScreenProjection.worldToScreen(end, tickDelta);
                if (screen == null) continue;
                drawTimerPlate(renderer, textRenderer, res, (float) screen.x, (float) screen.y);
            }
        }

        if (tntTimerValue.get()) {
            drawTntTimers(renderer, textRenderer, tickDelta);
        }

    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, net.minecraft.client.gui.GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || mc.level == null) return;
        if (timerPlates.isEmpty()) return;

        int seed = 0;
        List<TimerPlate> plates = timerPlates.isEmpty() ? List.of() : new ArrayList<>(timerPlates);
        for (TimerPlate plate : plates) {
            if (plate.iconStack().isEmpty()) continue;
            renderer.item(
                    plate.iconStack(),
                    plate.iconX(),
                    plate.iconY(),
                    plate.iconScale(),
                    seed++,
                    Renderer2D.ITEM_OVERLAY_NONE,
                    null
            );
        }
        if (!plates.isEmpty()) {
            TextRenderer tr = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, textRenderer);
            tr.begin(PLATE_TEXT_SCALE);
            for (TimerPlate plate : plates) {
                tr.render(plate.text(), plate.textX(), plate.textY(), new RenderColor(0xFFFFFFFF), false);
            }
            tr.end();
        }
    }

    private List<TrajectoryResult> simulateProjectiles(float tickDelta) {
        ClientLevel world = mc.level;
        if (world == null) return Collections.emptyList();

        List<TrajectoryResult> out = new ArrayList<>();

        List<Entity> candidates = new ArrayList<>();
        for (Entity e : world.entitiesForRendering()) {
            candidates.add(e);
        }
        candidates.sort((a, b) -> {
            double da = mc.player == null ? 0.0 : a.distanceToSqr(mc.player);
            double db = mc.player == null ? 0.0 : b.distanceToSqr(mc.player);
            return Double.compare(da, db);
        });
        int limit = Math.max(1, maxProjectiles.get());
        int processed = 0;
        int pearlProcessed = 0;

        for (Entity e : candidates) {
            if (!e.isAlive()) continue;
            if (!(e instanceof ThrownEnderpearl)) continue;
            if (pearlProcessed >= pearlMaxProjectiles.get()) break;

            TrajectoryResult res = simulateEntity(e, Kind.PEARL_ENTITY, tickDelta);
            if (res != null) out.add(res);
            pearlProcessed++;
        }

        for (Entity e : candidates) {
            if (!e.isAlive()) continue;
            if (e instanceof ThrownEnderpearl) continue;
            if (processed >= limit) break;

            switch (e) {
                case ThrownTrident trident -> {
                    if (isKindDisabled("trident")) continue;
                    if (trident.isNoPhysics() || isInGround(trident)) continue;
                    TrajectoryResult res = simulateEntity(trident, Kind.TRIDENT, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case AbstractArrow arrow -> {
                    if (isKindDisabled("arrow")) continue;
                    if (arrow.isNoPhysics() || isInGround(arrow)) continue;
                    TrajectoryResult res = simulateEntity(arrow, Kind.ARROW, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case Fireball fireball -> {
                    if (isKindDisabled("fireball")) continue;
                    TrajectoryResult res = simulateEntity(fireball, Kind.FIREBALL, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case WindCharge windCharge -> {
                    if (isKindDisabled("wind_charge")) continue;
                    TrajectoryResult res = simulateEntity(windCharge, Kind.WIND_CHARGE, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case FireworkRocketEntity firework -> {
                    if (isKindDisabled("firework")) continue;
                    TrajectoryResult res = simulateEntity(firework, Kind.FIREWORK, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case Snowball snowball -> {
                    if (isKindDisabled("snowball")) continue;
                    TrajectoryResult res = simulateEntity(snowball, Kind.SNOWBALL, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case ThrownEgg egg -> {
                    if (isKindDisabled("egg")) continue;
                    TrajectoryResult res = simulateEntity(egg, Kind.EGG, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                case AbstractThrownPotion potion -> {
                    boolean lingering = potion.getItem().is(Items.LINGERING_POTION);
                    boolean splash = potion.getItem().is(Items.SPLASH_POTION) || !lingering;
                    if (lingering && isKindDisabled("potion_lingering")) continue;
                    if (splash && isKindDisabled("potion_splash")) continue;
                    Kind kind = lingering ? Kind.POTION_LINGERING : Kind.POTION_SPLASH;
                    TrajectoryResult res = simulateEntity(potion, kind, tickDelta);
                    if (res != null) out.add(res);
                    processed++;
                }
                default -> {
                }
            }
        }

        return out;
    }

    private TrajectoryResult simulateEntity(Entity e, Kind kind, float tickDelta) {
        ClientLevel world = mc.level;
        if (world == null) return null;

        Vec3 pos = e.position();
        Vec3 vel = e.getDeltaMovement();

        List<Vec3> points = new ArrayList<>();
        points.add(pos);

        double t = 0.0;
        int baseMaxTicks = Math.max(1, maxSimTicks.get());
        int maxTicks = kind == Kind.PEARL_ENTITY
                ? Math.max(1, pearlMaxSimTicks.get())
                : baseMaxTicks;
        int hitColor = 0;
        long impactTick = world.getGameTime();
        Vec3 impactPos = null;
        Vec3 impactNormal = null;
        Entity hitEntity = null;
        boolean hitBlock = false;

        for (int i = 0; i < maxTicks; i++) {
            Vec3 segmentStart = pos;
            Vec3 simulatedVelocity = advanceTickVelocity(e, vel);
            Vec3 simulatedPos = pos.add(simulatedVelocity);
            double segmentLengthSq = segmentStart.distanceToSqr(simulatedPos);

            HitResult hit = world.clip(new ClipContext(
                    segmentStart,
                    simulatedPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    e
            ));

            EntityCollision entityCollision = getEntityCollision(e, segmentStart, simulatedPos);
            boolean hasBlockHit = hit.getType() != HitResult.Type.MISS;
            boolean useEntityHit = entityCollision != null
                    && (!hasBlockHit || segmentStart.distanceToSqr(entityCollision.hitPos()) <= segmentStart.distanceToSqr(hit.getLocation()));

            if (useEntityHit) {
                hitColor = (entityCollision.entity() == mc.player) ? HIT_SELF_COLOR : HIT_ENTITY_COLOR;
                Vec3 entityHitPos = entityCollision.hitPos();
                t += partialStepProgress(segmentStart, entityHitPos, segmentLengthSq);
                points.add(entityHitPos);
                impactTick = world.getGameTime() + (long) Math.ceil(t);
                impactPos = entityHitPos;
                impactNormal = entityCollision.hitNormal();
                hitEntity = entityCollision.entity();
                break;
            } else if (hasBlockHit) {
                Vec3 blockHitPos = hit.getLocation();
                t += partialStepProgress(segmentStart, blockHitPos, segmentLengthSq);
                points.add(blockHitPos);
                impactTick = world.getGameTime() + (long) Math.ceil(t);
                impactPos = blockHitPos;
                impactNormal = resolveImpactNormal(hit, simulatedVelocity);
                hitBlock = true;
                break;
            }

            pos = simulatedPos;
            vel = simulatedVelocity;
            t += 1.0;
            points.add(pos);
            impactTick = world.getGameTime() + (long) Math.ceil(t);
        }

        ItemStack icon = switch (kind) {
            case PEARL_ENTITY -> new ItemStack(Items.ENDER_PEARL);
            case TRIDENT -> new ItemStack(Items.TRIDENT);
            case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
            case WIND_CHARGE -> new ItemStack(Items.WIND_CHARGE);
            case FIREWORK -> new ItemStack(Items.FIREWORK_ROCKET);
            case SNOWBALL -> new ItemStack(Items.SNOWBALL);
            case EGG -> new ItemStack(Items.EGG);
            case POTION_SPLASH -> new ItemStack(Items.SPLASH_POTION);
            case POTION_LINGERING -> new ItemStack(Items.LINGERING_POTION);
            default -> new ItemStack(Items.ARROW);
        };

        int color = hitColor;
        if (color == 0 && kind == Kind.PEARL_ENTITY && pearlColorToggle.get()) {
            color = pearlColorValue.getArgb();
        }

        return new TrajectoryResult(points, t, impactTick, icon, color, kind, impactPos, impactNormal, hitEntity, hitBlock);
    }

    private EntityCollision getEntityCollision(Entity movingEntity, Vec3 startPos, Vec3 endPos) {
        Vec3 direction = endPos.subtract(startPos);
        if (direction.lengthSqr() == 0.0) {
            return null;
        }

        AABB projectileBox = movingEntity.getBoundingBox().move(startPos.subtract(movingEntity.position()));
        AABB collisionBox = projectileBox.expandTowards(direction).inflate(0.5);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                movingEntity,
                startPos,
                endPos,
                collisionBox,
                entity -> canHitPredictedEntity(movingEntity, entity, collisionBox)
                        && entity instanceof LivingEntity
                        && !(entity instanceof ItemEntity)
                        && !(entity instanceof ExperienceOrb)
                        && entity != movingEntity,
                direction.lengthSqr()
        );
        if (hitResult == null) {
            return null;
        }

        return new EntityCollision(
                hitResult.getEntity(),
                hitResult.getLocation(),
                resolveEntityImpactNormal(hitResult.getEntity().getBoundingBox(), hitResult.getLocation(), direction)
        );
    }

    private AimPrediction computeAimPrediction(Player player, float tickDelta) {
        Projectile projectile = createHeldProjectile(player, tickDelta);
        if (projectile == null) {
            return null;
        }

        Kind kind = kindFromProjectile(projectile);
        TrajectoryResult result = simulateEntity(projectile, kind, tickDelta);
        if (result == null || result.points().isEmpty()) {
            return null;
        }

        Vec3 hitPos = result.impactPos() != null ? result.impactPos() : result.points().getLast();
        Entity hitEntity = result.hitEntity();
        Vec3 anchorPos;
        if (hitEntity instanceof LivingEntity living) {
            anchorPos = living.getPosition(tickDelta).add(0.0, living.getBbHeight() * 0.5, 0.0);
        } else if (hitEntity != null) {
            anchorPos = hitEntity.getBoundingBox().getCenter();
        } else {
            anchorPos = hitPos;
        }

        return new AimPrediction(hitEntity, hitPos, anchorPos, result.impactNormal(), result.iconStack(), result);
    }

    private Projectile createHeldProjectile(Player player, float tickDelta) {
        if (mc.level == null) {
            return null;
        }

        InteractionHand hand = null;
        ItemStack handStack = ItemStack.EMPTY;

        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isAimUsable(player, mainHand, InteractionHand.MAIN_HAND)) {
            hand = InteractionHand.MAIN_HAND;
            handStack = mainHand;
        } else {
            ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
            if (isAimUsable(player, offHand, InteractionHand.OFF_HAND)) {
                hand = InteractionHand.OFF_HAND;
                handStack = offHand;
            }
        }

        if (hand == null || handStack.isEmpty()) {
            return null;
        }

        Projectile projectile = null;
        float speed = 0.0f;
        float roll = 0.0f;

        if (handStack.is(Items.ENDER_PEARL)) {
            projectile = new ThrownEnderpearl(mc.level, player, handStack);
            speed = 1.5f;
        } else if (handStack.is(Items.SNOWBALL)) {
            projectile = new Snowball(mc.level, player, handStack);
            speed = 1.5f;
        } else if (handStack.is(Items.EGG)) {
            projectile = new ThrownEgg(mc.level, player, handStack);
            speed = 1.5f;
        } else if (handStack.is(Items.SPLASH_POTION) || handStack.is(Items.LINGERING_POTION)) {
            projectile = handStack.is(Items.LINGERING_POTION)
                    ? new ThrownLingeringPotion(mc.level, player, handStack)
                    : new ThrownSplashPotion(mc.level, player, handStack);
            speed = 0.5f;
            roll = -20.0f;
        } else if (handStack.is(Items.EXPERIENCE_BOTTLE)) {
            projectile = new ThrownExperienceBottle(mc.level, player, handStack);
            speed = 0.7f;
            roll = -20.0f;
        } else if (handStack.is(Items.WIND_CHARGE)) {
            projectile = new WindCharge(player, mc.level, player.position().x, player.getEyePosition().y, player.position().z);
            speed = 1.5f;
        } else if (handStack.is(Items.FIRE_CHARGE)) {
            projectile = new SmallFireball(mc.level, player, Vec3.ZERO);
            speed = 1.0f;
        } else if (handStack.is(Items.TRIDENT) && player.isUsingItem() && player.getUsedItemHand() == hand) {
            projectile = new ThrownTrident(mc.level, player, handStack);
            speed = 2.5f;
        } else if (handStack.is(Items.BOW) && player.isUsingItem() && player.getUsedItemHand() == hand) {
            float pull = BowItem.getPowerForTime(player.getTicksUsingItem());
            if (pull <= 0.0f) {
                return null;
            }
            ItemStack arrowStack = new ItemStack(Items.ARROW);
            projectile = new net.minecraft.world.entity.projectile.arrow.Arrow(mc.level, player, arrowStack, handStack);
            speed = 3.0f * pull;
        } else if (handStack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(handStack)) {
            ItemStack arrowStack = new ItemStack(Items.ARROW);
            projectile = new net.minecraft.world.entity.projectile.arrow.Arrow(mc.level, player, arrowStack, handStack);
            speed = 3.15f;
        }

        if (projectile == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition(tickDelta);
        projectile.absSnapTo(eye.x, eye.y, eye.z);
        setHeldProjectileVelocity(projectile, player, player.getXRot(), player.getYRot(), roll, speed);
        return projectile;
    }

    private void setHeldProjectileVelocity(Projectile projectile, Entity shooter, float pitch, float yaw, float roll, float speed) {
        float x = -Mth.sin(yaw * ((float) Math.PI / 180.0f)) * Mth.cos(pitch * ((float) Math.PI / 180.0f));
        float y = -Mth.sin((pitch + roll) * ((float) Math.PI / 180.0f));
        float z = Mth.cos(yaw * ((float) Math.PI / 180.0f)) * Mth.cos(pitch * ((float) Math.PI / 180.0f));

        Vec3 velocity = new Vec3(x, y, z).normalize().scale(speed);
        projectile.setDeltaMovement(velocity);

        Vec3 movement = shooter.getKnownMovement();
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(movement.x, shooter.onGround() ? 0.0 : movement.y, movement.z));
    }

    private boolean isAimUsable(Player player, ItemStack stack, InteractionHand hand) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.is(Items.BOW) || stack.is(Items.TRIDENT)) {
            return player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItem() == stack;
        }

        if (stack.is(Items.CROSSBOW)) {
            return net.minecraft.world.item.CrossbowItem.isCharged(stack);
        }

        return stack.is(Items.ENDER_PEARL)
                || stack.is(Items.SNOWBALL)
                || stack.is(Items.EGG)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.EXPERIENCE_BOTTLE)
                || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.WIND_CHARGE);
    }

    private boolean isKindDisabled(String key) {
        // Ender pearls are always shown; caller must skip check for them.
        return !kindToggles.get(key);
    }

    private boolean isInGround(AbstractArrow arrow) {
        if (arrow == null) return false;
        try {
            return ((PersistentProjectileEntityAccessor) arrow).combatant$isInGround();
        } catch (Throwable t) {
            return false;
        }
    }

    private void drawTimerPlate(Renderer2D renderer, TextRenderer textRenderer, TrajectoryResult res, float sx, float sy) {
        double seconds = res.flightTicks / 20.0;
        if (seconds <= 0.0) return;

        ItemStack iconStack = useItemIcon(res.kind) ? resolveTimerItem(res.kind) : ItemStack.EMPTY;
        Identifier iconId = iconStack.isEmpty() ? resolveTimerIcon(res.kind) : null;
        drawTimerPlate(renderer, textRenderer, formatTime(seconds), sx, sy, res.argb, iconStack, iconId);
    }

    private void drawTntTimers(Renderer2D renderer, TextRenderer textRenderer, float tickDelta) {
        ClientLevel world = mc.level;
        if (world == null) return;

        int plateColor = tntColorValue.getArgb();
        for (Entity entity : world.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt) || !tnt.isAlive() || tnt.isRemoved()) continue;

            int fuse = tnt.getFuse();
            if (fuse < 0) continue;

            Vec3 anchor = tnt.getPosition(tickDelta).add(0.0, tnt.getBbHeight() + 0.5, 0.0);
            Vec3 screen = ScreenProjection.worldToScreen(anchor, tickDelta);
            if (screen == null) continue;

            double seconds = Math.max(0.0, (fuse - tickDelta) / 20.0);
            drawTimerPlate(
                    renderer,
                    textRenderer,
                    formatTime(seconds),
                    (float) screen.x,
                    (float) screen.y,
                    plateColor,
                    tntTimerIcon(),
                    null
            );
        }
    }

    private ItemStack tntTimerIcon() {
        if (tntTimerIcon.isEmpty()) {
            tntTimerIcon = new ItemStack(Items.TNT);
        }
        return tntTimerIcon;
    }

    private void drawTimerPlate(Renderer2D renderer,
                                TextRenderer textRenderer,
                                String txt,
                                float sx,
                                float sy,
                                int argb,
                                ItemStack iconStack,
                                Identifier iconId) {
        if (txt == null || txt.isEmpty()) return;

        TextRenderer tr = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, textRenderer);
        float textW = (float) tr.getWidth(txt) * PLATE_TEXT_SCALE;
        float plateW = textW + PLATE_TEXT_PADDING;
        float plateX = sx - plateW * 0.5f;
        float plateY = sy;

        float bgX = plateX + PLATE_BG_OFFSET_X;
        float bgY = plateY + PLATE_BG_OFFSET_Y;
        float bgW = plateW - PLATE_BG_SHRINK;
        int plateShadow = argb != 0
                ? (argb & 0x00FFFFFF) | (PLATE_SHADOW & 0xFF000000)
                : PLATE_SHADOW;
        renderer.roundedRectSoftShadow(bgX, bgY, bgW, PLATE_HEIGHT, PLATE_RADIUS, PLATE_SHADOW_BLUR, PLATE_SHADOW_INNER_ALPHA, plateShadow);

        float iconX = plateX + PLATE_ICON_OFFSET_X;
        float iconY = plateY + (PLATE_HEIGHT - PLATE_ICON_SIZE) * 0.5f;
        float iconScale = PLATE_ICON_SIZE / 16.0f;
        if (iconStack == null || iconStack.isEmpty()) {
            iconStack = ItemStack.EMPTY;
            drawTimerIcon(iconId, iconX, iconY, PLATE_ICON_SIZE);
        }

        float textX = iconX + PLATE_ICON_SIZE + PLATE_ICON_TEXT_GAP;
        float textH = (float) tr.getHeight() * PLATE_TEXT_SCALE;
        float textY = plateY + (PLATE_HEIGHT - textH) * 0.5f + PLATE_TEXT_OFFSET_Y;
        timerPlates.add(new TimerPlate(textX, textY, txt, iconX, iconY, iconScale, iconStack));
    }

    private void drawTimerIcon(Identifier iconId, float x, float y, float size) {
        if (iconId == null) return;
        Renderer2D.TEXTURE.roundedTexRect(
                x,
                y,
                size,
                size,
                1.5f,
                1.0f,
                0,
                0,
                1,
                1,
                0xFFFFFFFF,
                iconId
        );
    }

    private boolean useItemIcon(Kind kind) {
        return kind == Kind.SNOWBALL
                || kind == Kind.EGG
                || kind == Kind.FIREBALL
                || kind == Kind.FIREWORK
                || kind == Kind.WIND_CHARGE;
    }

    private ItemStack resolveTimerItem(Kind kind) {
        return switch (kind) {
            case SNOWBALL -> new ItemStack(Items.SNOWBALL);
            case EGG -> new ItemStack(Items.EGG);
            case FIREBALL -> new ItemStack(Items.FIRE_CHARGE);
            case FIREWORK -> new ItemStack(Items.FIREWORK_ROCKET);
            case WIND_CHARGE -> new ItemStack(Items.WIND_CHARGE);
            default -> ItemStack.EMPTY;
        };
    }

    private Identifier resolveTimerIcon(Kind kind) {
        return switch (kind) {
            case PEARL_ENTITY -> Identifier.fromNamespaceAndPath("combatant", "textures/timers/pearl.png");
            case TRIDENT -> Identifier.fromNamespaceAndPath("combatant", "textures/timers/trident.png");
            case POTION_SPLASH, POTION_LINGERING ->
                    Identifier.fromNamespaceAndPath("combatant", "textures/timers/potion.png");
            case ARROW -> Identifier.fromNamespaceAndPath("combatant", "textures/timers/arrow.png");
            case FIREBALL, FIREWORK, WIND_CHARGE, SNOWBALL, EGG -> null;
        };
    }

    private void renderAimTrajectoryGlow(Renderer3D renderer, float tickDelta, int baseColor) {
        Player player = mc.player;
        if (player == null) return;

        AimPrediction pred = computeAimPrediction(player, tickDelta);
        if (pred == null || pred.trajectory() == null) return;

        List<Vec3> points = pred.trajectory().points();
        if (points.size() < 2) return;

        boolean walls = glowWallsValue.get();
        RenderPipeline pipeline = walls
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE;
        Renderer3D.DepthMode depthMode = walls ? Renderer3D.DepthMode.MAIN : Renderer3D.DepthMode.PRE_DEPTH;
        MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.BLOOM, depthMode);
        if (mesh == null) return;

        int color = pred.entity() != null
                ? HIT_ENTITY_COLOR
                : pred.trajectory().argb() != 0 ? pred.trajectory().argb() : baseColor;
        Quaternionf camRot = RenderState.cameraRotation;
        Vec3 cameraPos = RenderState.cameraPos != null ? RenderState.cameraPos : player.getEyePosition(tickDelta);
        int totalSegments = points.size() - 1;

        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);

            for (int step = 0; step <= AIM_GLOW_SUBSTEPS; step++) {
                float stepT = step / (float) AIM_GLOW_SUBSTEPS;
                Vec3 pos = from.lerp(to, stepT);
                if (pos.distanceToSqr(cameraPos) < AIM_GLOW_MIN_CAMERA_DISTANCE_SQ) {
                    continue;
                }
                float segmentProgress = ((i - 1) + stepT) / Math.max(1.0f, totalSegments);
                float strength = 0.28f + 0.72f * AnimationUtility.easeOutCubic(segmentProgress);

                addBillboardQuad(
                        mesh,
                        pos.x, pos.y, pos.z,
                        AIM_GLOW_CORE_SIZE,
                        camRot,
                        multiplyAlpha(color, AIM_GLOW_CORE_ALPHA * strength)
                );
                addBillboardQuad(
                        mesh,
                        pos.x, pos.y, pos.z,
                        AIM_GLOW_SOFT_SIZE,
                        camRot,
                        multiplyAlpha(color, AIM_GLOW_SOFT_ALPHA * strength)
                );
            }
        }
    }

    private void renderAimIndicatorWorld(Renderer3D renderer, float tickDelta) {
        Player player = mc.player;
        if (player == null) return;

        AimPrediction pred = computeAimPrediction(player, tickDelta);
        if (pred == null) return;

        RenderPipeline pipeline = CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE;
        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.NONE;

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = IMPACT_RING_LINE_WIDTH;
        try {
            MeshBuilder mesh = renderer.batch(pipeline, depthMode);
            if (mesh == null) return;

            int color = pred.entity() != null ? HIT_ENTITY_COLOR : lineColorValue.getArgb();
            float pulseTime = (mc.level.getGameTime() + tickDelta) * 0.085f;
            float pulse = 0.9f + 0.1f * AnimationUtility.smoothstep((float) ((Math.sin(pulseTime) + 1.0) * 0.5));
            Vec3 normal = pred.hitNormal() != null ? pred.hitNormal() : new Vec3(0.0, 1.0, 0.0);
            addImpactRing(mesh, pred.hitPos(), normal, IMPACT_RING_RADIUS * pulse, multiplyAlpha(color, IMPACT_RING_ALPHA));
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private void updatePredictionTarget(float tickDelta) {
        if (!targetHudOnAimValue.get()) {
            TargetManager.setPredictionTarget(null);
            return;
        }

        Player player = mc.player;
        if (player == null || mc.level == null) {
            TargetManager.setPredictionTarget(null);
            return;
        }

        AimPrediction pred = computeAimPrediction(player, tickDelta);
        if (pred != null && pred.entity() instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
            TargetManager.setPredictionTarget(living);
            return;
        }

        TargetManager.setPredictionTarget(null);
    }

    private String formatTime(double seconds) {
        String s = String.format(Locale.US, "%.1f", seconds);
        s = s.replace('.', ',');
        return s + " s";
    }

    private void renderGlowTrajectories(Renderer3D renderer, List<TrajectoryResult> results, int baseColor) {
        boolean walls = glowWallsValue.get();
        RenderPipeline pipeline = walls
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE;
        Renderer3D.DepthMode depthMode = walls ? Renderer3D.DepthMode.MAIN : Renderer3D.DepthMode.PRE_DEPTH;
        MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.BLOOM, depthMode);
        if (mesh == null) return;

        Quaternionf camRot = RenderState.cameraRotation;
        for (TrajectoryResult res : results) {
            List<Vec3> points = res.points();
            if (points.size() < 2) continue;

            int color = res.argb() != 0 ? res.argb() : baseColor;
            int totalSegments = points.size() - 1;

            for (int i = 1; i < points.size(); i++) {
                Vec3 from = points.get(i - 1);
                Vec3 to = points.get(i);

                for (int step = 0; step <= GLOW_SUBSTEPS; step++) {
                    float stepT = step / (float) GLOW_SUBSTEPS;
                    Vec3 pos = from.lerp(to, stepT);
                    float segmentProgress = ((i - 1) + stepT) / Math.max(1.0f, totalSegments);
                    float strength = 0.45f + 0.55f * AnimationUtility.easeOutCubic(segmentProgress);
                    float sizeBoost = 0.92f + 0.25f * strength;

                    addBillboardQuad(
                            mesh,
                            pos.x, pos.y, pos.z,
                            GLOW_CORE_SIZE * sizeBoost,
                            camRot,
                            multiplyAlpha(color, GLOW_CORE_ALPHA * strength)
                    );
                    addBillboardQuad(
                            mesh,
                            pos.x, pos.y, pos.z,
                            GLOW_SOFT_SIZE * sizeBoost,
                            camRot,
                            multiplyAlpha(color, GLOW_SOFT_ALPHA * strength)
                    );
                }
            }
        }
    }

    private void renderLineTrajectories(Renderer3D renderer, List<TrajectoryResult> results, int baseColor) {
        boolean walls = glowWallsValue.get();
        RenderPipeline pipeline = walls
                ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_BLEND
                : CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE;
        Renderer3D.DepthMode depthMode = walls ? Renderer3D.DepthMode.MAIN : Renderer3D.DepthMode.PRE_DEPTH;

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = TRAJECTORY_LINE_WIDTH;
        try {
            MeshBuilder mesh = renderer.batch(pipeline, depthMode);
            if (mesh == null) return;

            for (TrajectoryResult res : results) {
                List<Vec3> points = res.points();
                if (points.size() < 2) continue;

                int color = res.argb() != 0 ? res.argb() : baseColor;
                int totalSegments = points.size() - 1;
                for (int i = 1; i < points.size(); i++) {
                    Vec3 from = points.get(i - 1);
                    Vec3 to = points.get(i);

                    float startT = (i - 1) / (float) totalSegments;
                    float endT = i / (float) totalSegments;
                    float startAlpha = Mth.lerp(AnimationUtility.easeOutCubic(startT), TRAJECTORY_LINE_ALPHA_START, TRAJECTORY_LINE_ALPHA_END);
                    float endAlpha = Mth.lerp(AnimationUtility.easeOutCubic(endT), TRAJECTORY_LINE_ALPHA_START, TRAJECTORY_LINE_ALPHA_END);

                    addGradientLine(mesh, from, to, multiplyAlpha(color, startAlpha), multiplyAlpha(color, endAlpha));
                }
            }
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private void renderImpactEffects(Renderer3D renderer, List<TrajectoryResult> results, int baseColor, float tickDelta) {
        boolean walls = glowWallsValue.get();
        RenderPipeline linePipeline = walls
                ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_BLEND
                : CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE;
        Renderer3D.DepthMode lineDepthMode = walls ? Renderer3D.DepthMode.MAIN : Renderer3D.DepthMode.PRE_DEPTH;
        RenderPipeline decalPipeline = walls
                ? CombatantRenderPipelines.WORLD_DECAL_SDF
                : CombatantRenderPipelines.WORLD_DECAL_SDF_DEPTH;
        Renderer3D.DepthMode decalDepthMode = walls ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.PRE_DEPTH;
        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = IMPACT_RING_LINE_WIDTH;
        try {
            MeshBuilder lineMesh = renderer.batch(linePipeline, lineDepthMode);
            MeshBuilder decalMesh = renderer.batch(decalPipeline, decalDepthMode);
            for (TrajectoryResult res : results) {
                Vec3 impact = res.impactPos();
                if (impact != null && decalMesh != null) {
                    int color = res.argb() != 0 ? res.argb() : baseColor;
                    float pulseTime = (mc.level.getGameTime() + tickDelta) * 0.085f;
                    float pulse = 0.85f + 0.15f * AnimationUtility.smoothstep((float) ((Math.sin(pulseTime) + 1.0) * 0.5));
                    Vec3 normal = res.impactNormal() != null ? res.impactNormal() : new Vec3(0.0, 1.0, 0.0);
                    addImpactDecal(decalMesh, impact, normal, IMPACT_DECAL_RADIUS * pulse, color);
                }

                if (res.hitEntity() != null && res.hitEntity().isAlive()) {
                    int color = res.argb() != 0 ? res.argb() : baseColor;
                    AABB box = res.hitEntity().getBoundingBox().inflate(IMPACT_BOX_EXPAND);
                    addFilledBox(renderer, box, withAlpha(color, ENTITY_HIT_FILL_ALPHA));
                    addOutlineBox(renderer, box, withAlpha(color, ENTITY_HIT_LINE_ALPHA));
                }
            }
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private enum Kind {
        PEARL_ENTITY,
        ARROW,
        TRIDENT,
        FIREBALL,
        WIND_CHARGE,
        FIREWORK,
        SNOWBALL,
        EGG,
        POTION_SPLASH,
        POTION_LINGERING
    }

    private record AimPrediction(Entity entity, Vec3 hitPos, Vec3 anchorPos, Vec3 hitNormal, ItemStack displayStack,
                                 TrajectoryResult trajectory) {
        private AimPrediction(Entity entity, Vec3 hitPos, Vec3 anchorPos, Vec3 hitNormal, ItemStack displayStack,
                              TrajectoryResult trajectory) {
            this.entity = entity;
            this.hitPos = hitPos;
            this.anchorPos = anchorPos;
            this.hitNormal = hitNormal;
            this.displayStack = displayStack == null ? ItemStack.EMPTY : displayStack;
            this.trajectory = trajectory;
        }
    }

    private record TrajectoryResult(List<Vec3> points, double flightTicks, long impactTick, ItemStack iconStack,
                                    int argb, Kind kind, Vec3 impactPos, Vec3 impactNormal, Entity hitEntity,
                                    boolean hitBlock) {
        private TrajectoryResult(List<Vec3> points, double flightTicks, long impactTick, ItemStack iconStack,
                                 int argb, Kind kind, Vec3 impactPos, Vec3 impactNormal, Entity hitEntity, boolean hitBlock) {
            this.points = points;
            this.flightTicks = flightTicks;
            this.impactTick = impactTick;
            this.iconStack = iconStack == null ? ItemStack.EMPTY : iconStack;
            this.argb = argb;
            this.kind = kind;
            this.impactPos = impactPos;
            this.impactNormal = impactNormal;
            this.hitEntity = hitEntity;
            this.hitBlock = hitBlock;
        }
    }

    private record EntityCollision(Entity entity, Vec3 hitPos, Vec3 hitNormal) {
    }

    private record TimerPlate(float textX, float textY, String text,
                              float iconX, float iconY, float iconScale, ItemStack iconStack) {
        private TimerPlate(float textX, float textY, String text,
                           float iconX, float iconY, float iconScale, ItemStack iconStack) {
            this.textX = textX;
            this.textY = textY;
            this.text = text;
            this.iconX = iconX;
            this.iconY = iconY;
            this.iconScale = iconScale;
            this.iconStack = iconStack == null ? ItemStack.EMPTY : iconStack;
        }
    }
}
