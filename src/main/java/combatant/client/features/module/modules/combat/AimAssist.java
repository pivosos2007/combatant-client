/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.FreeLook;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.target.TargetingUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Legit aim assist: nudges the real camera toward a target, never sets a server-only rotation.
 *
 * <p>The target is picked once per tick through {@link TargetingUtil} (so AntiBot and the
 * shared target filters apply); the pull runs every frame for smoothness. Each step is
 * exponentially eased, capped in degrees per second, and rounded to the sensitivity GCD so
 * the resulting turns are multiples of what a real mouse could produce.</p>
 */
@ModuleInfo(
        id = "aimassist",
        displayName = "AimAssist",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.LEGIT,
        description = "module.aimassist.description")
public final class AimAssist extends Module {

    // Vanilla LocalPlayer.turn: one mouse unit rotates 0.15 degrees.
    private static final float DEGREES_PER_MOUSE_UNIT = 0.15f;
    // A dropped frame should not turn into one giant snap.
    private static final float MAX_FRAME_SECONDS = 0.1f;
    // Keeps NEAREST aim points off the exact hitbox edge, where a tiny error misses.
    private static final double NEAREST_EDGE_MARGIN = 0.1;

    private final NumberValue<Float> range = num("range", 4.5f, 1.0f, 8.0f);
    private final NumberValue<Float> fov = num("fov", 90.0f, 10.0f, 180.0f);
    private final NumberValue<Float> speed = num("speed", 6.0f, 1.0f, 20.0f);
    private final NumberValue<Float> maxTurnSpeed = num("max_turn_speed", 180.0f, 10.0f, 720.0f);
    private final EnumValue<AimPoint> aimPoint = enumMode("aim_point", AimPoint.NEAREST);
    private final BooleanValue vertical = bool("vertical", true);
    private final BooleanValue onlyWhileClicking = bool("only_while_clicking", false);
    private final BooleanValue weaponOnly = bool("weapon_only", false);
    private final BooleanValue pauseOnTarget = bool("pause_on_target", true);
    private final BooleanValue stickyTarget = bool("sticky_target", true);
    private final BooleanValue pauseWhileMining = bool("pause_while_mining", true);
    // Constant-ratio easing leaves a perfectly smooth curve that aim checks look for; per-frame
    // noise on the ease speed (up to +-50% at 1.0) breaks that pattern.
    private final NumberValue<Float> randomization = num("randomization", 0.25f, 0.0f, 1.0f);
    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "priority",
            CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.ANGLE,
            TargetingUtil.TargetPriority.values()
    );
    private final BooleanMapValue targets = groupCommon("targets", CommonSettingSchemas.TARGET_FILTERS);

    private final Minecraft mc = Minecraft.getInstance();
    private LivingEntity target;

    @Override
    public void onDisable() {
        target = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        target = canAssist(mc.player) ? selectTarget() : null;
    }

    @Override
    public void onFrame(float tickDelta) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        LivingEntity current = target;
        if (current == null || !canAssist(player) || !current.isAlive() || current.isRemoved()) return;

        Vec3 eye = player.getEyePosition(tickDelta);
        AABB box = interpolatedBox(current, tickDelta);
        if (pauseOnTarget.get() && isLookingAt(player, eye, box, tickDelta)) return;

        float[] wanted = RotationUtil.getRotations(eye, resolveAimPoint(player, eye, box, current, tickDelta));
        float yawError = RotationUtil.angleDifference(wanted[0], player.getYRot());
        float pitchError = vertical.get() ? wanted[1] - player.getXRot() : 0.0f;

        float dt = Math.min(AnimationUtility.deltaTime(), MAX_FRAME_SECONDS);
        float noise = randomization.get() * (ThreadLocalRandom.current().nextFloat() * 2.0f - 1.0f);
        float ease = 1.0f - (float) Math.exp(-speed.get() * Math.max(0.1f, 1.0f + noise * 0.5f) * dt);
        float maxStep = maxTurnSpeed.get() * dt;

        float yawStep = quantize(Mth.clamp(yawError * ease, -maxStep, maxStep));
        float pitchStep = quantize(Mth.clamp(pitchError * ease, -maxStep, maxStep));
        if (yawStep == 0.0f && pitchStep == 0.0f) return;

        // turn() also shifts yRotO/xRotO, so the partial-tick camera does not jitter.
        player.turn(yawStep / DEGREES_PER_MOUSE_UNIT, pitchStep / DEGREES_PER_MOUSE_UNIT);
    }

    private boolean canAssist(LocalPlayer player) {
        if (player == null || mc.level == null || !player.isAlive()) return false;
        if (ClientScreen.current() != null) return false;
        if (onlyWhileClicking.get() && !mc.options.keyAttack.isDown()) return false;
        if (weaponOnly.get() && !isWeapon(player.getMainHandItem())) return false;
        // Dragging the crosshair while digging moves it off the block and resets break progress.
        if (pauseWhileMining.get() && mc.gameMode != null && mc.gameMode.isDestroying()) return false;
        if (isFreecamActive() || isFreeLookActive()) return false;
        // KillAura and friends drive rotation through the manager; fighting them looks awful.
        return RotationManager.INSTANCE.getActiveRotationTarget() == null;
    }

    private LivingEntity selectTarget() {
        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                range.get(),
                fov.get(),
                targets.get(TargetFilters.PLAYERS_ONLY),
                targets.get(TargetFilters.IGNORE_FRIENDS),
                targets.get(TargetFilters.IGNORE_STAFF),
                targets.get(TargetFilters.IGNORE_ENEMIES),
                targets.get(TargetFilters.IGNORE_NAKED),
                targets.get(TargetFilters.IGNORE_ENTITIES),
                targets.get(TargetFilters.VISIBLE_ONLY),
                priority.get()
        );
        List<LivingEntity> candidates = TargetingUtil.findTargets(mc, settings);
        if (candidates.isEmpty()) return null;
        if (stickyTarget.get() && target != null && candidates.contains(target)) return target;
        return candidates.get(0);
    }

    private Vec3 resolveAimPoint(LocalPlayer player, Vec3 eye, AABB box, LivingEntity entity, float tickDelta) {
        return switch (aimPoint.get()) {
            case HEAD -> entity.getEyePosition(tickDelta);
            case CHEST -> box.getCenter().add(0.0, box.getYsize() * 0.15, 0.0);
            case NEAREST -> nearestPointToCrosshair(player, eye, box, tickDelta);
        };
    }

    /**
     * Projects the current look ray to the target's depth and clamps it into the hitbox, so the
     * assist only corrects the part of the aim that is actually off the target.
     */
    private static Vec3 nearestPointToCrosshair(LocalPlayer player, Vec3 eye, AABB box, float tickDelta) {
        Vec3 look = player.getViewVector(tickDelta);
        Vec3 projected = eye.add(look.scale(eye.distanceTo(box.getCenter())));
        double mx = Math.min(NEAREST_EDGE_MARGIN, box.getXsize() * 0.25);
        double my = Math.min(NEAREST_EDGE_MARGIN, box.getYsize() * 0.25);
        double mz = Math.min(NEAREST_EDGE_MARGIN, box.getZsize() * 0.25);
        return new Vec3(
                Mth.clamp(projected.x, box.minX + mx, box.maxX - mx),
                Mth.clamp(projected.y, box.minY + my, box.maxY - my),
                Mth.clamp(projected.z, box.minZ + mz, box.maxZ - mz)
        );
    }

    private boolean isLookingAt(LocalPlayer player, Vec3 eye, AABB box, float tickDelta) {
        Vec3 end = eye.add(player.getViewVector(tickDelta).scale(range.get()));
        return box.contains(eye) || box.clip(eye, end).isPresent();
    }

    private static AABB interpolatedBox(LivingEntity entity, float tickDelta) {
        Vec3 offset = entity.getPosition(tickDelta).subtract(entity.position());
        return entity.getBoundingBox().move(offset);
    }

    /** Rounds a turn to whole mouse-sensitivity steps; sub-step errors become a dead zone. */
    private static float quantize(float degrees) {
        double step = RotationUtil.gcd();
        if (step <= 0.0) return degrees;
        return (float) (Math.round(degrees / step) * step);
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(Items.MACE)
                || stack.is(Items.TRIDENT);
    }

    private static boolean isFreecamActive() {
        Freecam freecam = Modules.get(Freecam.class);
        return freecam != null && freecam.isEnabled();
    }

    private static boolean isFreeLookActive() {
        FreeLook freeLook = Modules.get(FreeLook.class);
        return freeLook != null && freeLook.isCameraActive();
    }

    public enum AimPoint {
        NEAREST,
        CHEST,
        HEAD
    }
}
