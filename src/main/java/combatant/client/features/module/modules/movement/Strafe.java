/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.util.OmniItemUtils;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.MovementUtil;

import java.util.List;

//todo Description
@ModuleInfo(
        id = "strafe",
        displayName = "Strafe",
        category = ModuleCategory.MOVEMENT
)
public final class Strafe extends Module {

    private static final int ROTATION_PRIORITY = 15;

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumMode("mode", Mode.MATRIX);
    private final NumberValue<Float> speed =
            visibleWhen(num("speed", 0.42f, 0.0f, 1.0f), this::isMatrixMode);
    private final NumberValue<Float> grimEffectiveDegrees =
            visibleWhen(num("grim_effective_degrees", 45.0f, 0.0f, 90.0f), this::isGrimMode);
    private final BooleanValue grimOverrideCrosshairTarget =
            visibleWhen(bool("grim_override_crosshair_target", false), this::isGrimMode);
    private float lastYaw;
    private float lastPitch;

    private static float resolveMatrixYaw(LocalPlayer player, float fallbackYaw) {
        if (player == null) {
            return fallbackYaw;
        }
        return Mth.wrapDegrees(MovementUtil.getMovementDirectionYaw(player, fallbackYaw));
    }

    private static void setHorizontalVelocity(LocalPlayer player, float yaw, double speed) {
        double x = Math.cos(Math.toRadians(yaw + 90.0f)) * speed;
        double z = Math.sin(Math.toRadians(yaw + 90.0f)) * speed;
        player.setDeltaMovement(x, player.getDeltaMovement().y, z);
    }

    @EventHandler(priority = 2000)
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        boolean moving = MovementUtil.isMoving();
        float yaw = player.getYRot();

        if (isMatrixMode()) {
            handleMatrixMode(player, moving, yaw);
            lastYaw = yaw;
            lastPitch = 0.0f;
        } else if (isGrimMode()) {
            handleGrimMode(player, moving, yaw);
            lastYaw = yaw;
            lastPitch = 0.0f;
        }
    }

    @EventHandler(priority = 2000)
    private void onCrosshairTargetUpdate(CrosshairTargetUpdateEvent event) {
        LocalPlayer player = mc.player;
        if (!shouldOverrideGrimCrosshair(player)) {
            return;
        }

        float yaw = resolveGrimYaw(player, player.getYRot());
        float pitch = player.getXRot();
        HitResult hitResult = raycastRotationPoint(player, event.getTickDelta(), yaw, pitch);
        event.setHitResult(hitResult);
        event.setTargetedEntity(hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
    }

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        lastYaw = player != null ? player.getYRot() : 0.0f;
        lastPitch = player != null ? player.getXRot() : 0.0f;
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
    }

    private void handleMatrixMode(LocalPlayer player, boolean moving, float yaw) {
        if (moving) {
            yaw = resolveMatrixYaw(player, yaw);
            double motion = speed.get() * 1.5f;
            setHorizontalVelocity(player, yaw, motion);
        } else {
            setHorizontalVelocity(player, yaw, 0.0);
        }

        player.setDeltaMovement(player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z);
    }

    private void handleGrimMode(LocalPlayer player, boolean moving, float yaw) {
        if (!moving) {
            return;
        }
        if (OmniItemUtils.isProjectileWeaponInHand(player)) {
            return;
        }

        yaw = resolveGrimYaw(player, yaw);
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura != null && killAura.getCurrentTarget() != null) {
            return;
        }

        RotationTarget target = new RotationTarget(
                new Rotation(yaw, player.getXRot(), true),
                player,
                List.of(),
                1,
                1.0f,
                false,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private boolean shouldOverrideGrimCrosshair(LocalPlayer player) {
        if (!isEnabled()
                || !isGrimMode()
                || !grimOverrideCrosshairTarget.get()
                || !canOperate(player)
                || !MovementUtil.isMoving()) {
            return false;
        }
        if (OmniItemUtils.isProjectileWeaponInHand(player)) {
            return false;
        }
        KillAura killAura = Modules.get(KillAura.class);
        return killAura == null || killAura.getCurrentTarget() == null;
    }

    private HitResult raycastRotationPoint(LocalPlayer player, float tickDelta, float yaw, float pitch) {
        Vec3 start = player.getEyePosition(tickDelta);
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw).normalize();

        double entityRange = player.entityInteractionRange();
        double blockRange = player.blockInteractionRange();
        Vec3 blockEnd = start.add(direction.scale(blockRange));

        HitResult blockHit = mc.level.clip(new ClipContext(
                start,
                blockEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        double entityRangeSq = entityRange * entityRange;
        if (blockHit.getType() != HitResult.Type.MISS) {
            entityRangeSq = Math.min(entityRangeSq, start.distanceToSqr(blockHit.getLocation()));
        }

        Vec3 entityEnd = start.add(direction.scale(entityRange));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                entityEnd,
                player.getBoundingBox().expandTowards(direction.scale(entityRange)).inflate(1.0),
                this::isCrosshairCandidate,
                entityRangeSq
        );

        return entityHit != null ? entityHit : blockHit;
    }

    private boolean isCrosshairCandidate(Entity entity) {
        if (!EntitySelector.CAN_BE_PICKED.test(entity)) {
            return false;
        }
        return entity instanceof LivingEntity living && living.isAlive();
    }

    private float resolveGrimYaw(LocalPlayer player, float yaw) {
        if (player == null || mc.options == null) {
            return yaw;
        }

        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        if (left == right) {
            return yaw;
        }

        float delta = left ? -grimEffectiveDegrees.get() : grimEffectiveDegrees.get();
        return yaw + delta;
    }

    private boolean isMatrixMode() {
        return mode.get() == Mode.MATRIX;
    }

    private boolean isGrimMode() {
        return mode.get() == Mode.GRIM;
    }

    private boolean canOperate(LocalPlayer player) {
        return player != null && !isFreecamActive();
    }

    private boolean isFreecamActive() {
        return Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled();
    }

    private enum Mode {
        MATRIX,
        GRIM
    }
}
