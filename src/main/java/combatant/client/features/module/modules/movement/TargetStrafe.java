/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.combat.SprintController;

//todo Description
@ModuleInfo(
        id = "targetstrafe",
        displayName = "TargetStrafe",
        category = ModuleCategory.MOVEMENT
)
public final class TargetStrafe extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<StrafeMode> mode =
            enumMode("mode", StrafeMode.MATRIX, StrafeMode.MATRIX, StrafeMode.GRIM);
    private final EnumValue<PointType> grimPointType =
            visibleWhen(enumMode("grim_point_type", PointType.CUBE, PointType.CUBE, PointType.CENTER, PointType.CIRCLE), this::isGrimMode);
    private final NumberValue<Float> grimRadius =
            visibleWhen(num("grim_radius", 0.87f, 0.1f, 1.5f), this::usesGrimRadius);
    private final EnumValue<PointType> matrixPointType =
            visibleWhen(enumMode("matrix_point_type", PointType.CIRCLE, PointType.CUBE, PointType.CIRCLE), this::isMatrixMode);
    private final NumberValue<Float> radius =
            visibleWhen(num("radius", 2.5f, 0.1f, 7.0f), this::isMatrixMode);
    private final NumberValue<Float> speed =
            visibleWhen(num("speed", 0.3f, 0.1f, 1.0f), this::isMatrixMode);
    private final BooleanValue autoJump =
            bool("auto_jump", true);
    private final BooleanValue onlyKeyPressed =
            bool("only_key_pressed", false);
    private final BooleanValue inFrontOfTarget =
            bool("in_front_of_target", false);
    private final EnumValue<DirectionMode> directionMode =
            enumMode("direction_mode", DirectionMode.CLOCKWISE,
                    DirectionMode.CLOCKWISE, DirectionMode.COUNTERCLOCKWISE, DirectionMode.RANDOM);
    private int pointIndex;

    private static float resolveControlYaw() {
        var rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation != null) {
            return rotation.yaw();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getYRot() : 0.0f;
    }

    private static void setHorizontalVelocity(LocalPlayer player, float yaw, double speed) {
        player.setDeltaMovement(
                -Math.sin(Math.toRadians(yaw)) * speed,
                player.getDeltaMovement().y,
                Math.cos(Math.toRadians(yaw)) * speed
        );
    }

    private static boolean hasForwardMovement(float angleDiff) {
        return angleDiff > -67.5f && angleDiff < 67.5f;
    }

    @Override
    public void onEnable() {
        pointIndex = 0;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || !isGrimMode()) return;

        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) return;

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        double r = grimRadius.get();
        int directionMultiplier = resolveDirectionMultiplier();

        Vec3 nextPoint;
        if (inFrontOfTarget.get()) {
            float targetYaw = target.getYRot();
            if (grimPointType.get() == PointType.CENTER) {
                nextPoint = targetPos.add(
                        -Math.sin(Math.toRadians(targetYaw)) * r * directionMultiplier,
                        0.0,
                        Math.cos(Math.toRadians(targetYaw)) * r * directionMultiplier
                );
            } else {
                double offset = Math.cos(System.currentTimeMillis() / 500.0) * r * directionMultiplier;
                nextPoint = targetPos.add(
                        -Math.sin(Math.toRadians(targetYaw)) * r + Math.cos(Math.toRadians(targetYaw)) * offset,
                        0.0,
                        Math.cos(Math.toRadians(targetYaw)) * r + Math.sin(Math.toRadians(targetYaw)) * offset
                );
            }
        } else {
            nextPoint = switch (grimPointType.get()) {
                case CUBE -> nextCubePoint(playerPos, targetPos, r, directionMultiplier);
                case CIRCLE -> nextCirclePoint(playerPos, targetPos, r, directionMultiplier);
                case CENTER -> new Vec3(targetPos.x, playerPos.y, targetPos.z);
            };
        }

        Vec3 direction = nextPoint.subtract(playerPos);
        if (direction.lengthSqr() < 1.0E-6) return;
        direction = direction.normalize();

        float yaw = resolveControlYaw();
        float movementAngle = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f;
        float angleDiff = Mth.wrapDegrees(movementAngle - yaw);

        boolean forward = false;
        boolean backward = false;
        boolean left = false;
        boolean right = false;

        if (angleDiff >= -22.5f && angleDiff < 22.5f) {
            forward = true;
        } else if (angleDiff >= 22.5f && angleDiff < 67.5f) {
            forward = true;
            right = true;
        } else if (angleDiff >= 67.5f && angleDiff < 112.5f) {
            right = true;
        } else if (angleDiff >= 112.5f && angleDiff < 157.5f) {
            backward = true;
            right = true;
        } else if (angleDiff >= -67.5f && angleDiff < -22.5f) {
            forward = true;
            left = true;
        } else if (angleDiff >= -112.5f && angleDiff < -67.5f) {
            left = true;
        } else if (angleDiff >= -157.5f && angleDiff < -112.5f) {
            backward = true;
            left = true;
        } else {
            backward = true;
        }

        event.setForward(forward);
        event.setBackward(backward);
        event.setLeft(left);
        event.setRight(right);
        event.setSprint(SprintController.INSTANCE.canStartSprinting(player)
                && hasForwardMovement(angleDiff));

        if (autoJump.get() && player.onGround()) {
            event.setJump(true);
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled() || !isMatrixMode()) return;

        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) return;

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        double r = radius.get();

        if (autoJump.get() && player.onGround()) {
            player.jumpFromGround();
        }

        int directionMultiplier = resolveDirectionMultiplier();

        if (inFrontOfTarget.get()) {
            float targetYaw = target.getYRot();
            double x = targetPos.x - Math.sin(Math.toRadians(targetYaw)) * r * directionMultiplier;
            double z = targetPos.z + Math.cos(Math.toRadians(targetYaw)) * r * directionMultiplier;

            float yaw = (float) Math.toDegrees(Math.atan2(z - playerPos.z, x - playerPos.x)) - 90.0f;
            setHorizontalVelocity(player, yaw, speed.get());
            requestSprintForMovementYaw(player, yaw);
            return;
        }

        if (matrixPointType.get() == PointType.CUBE) {
            Vec3 nextPoint = nextCubePoint(playerPos, targetPos, r, directionMultiplier);
            Vec3 dirVec = nextPoint.subtract(playerPos);
            if (dirVec.lengthSqr() < 1.0E-6) return;
            dirVec = dirVec.normalize();

            float yaw = (float) Math.toDegrees(Math.atan2(dirVec.z, dirVec.x)) - 90.0f;
            setHorizontalVelocity(player, yaw, speed.get());
            requestSprintForMovementYaw(player, yaw);
            return;
        }

        double angle = Math.atan2(playerPos.z - targetPos.z, playerPos.x - targetPos.x);
        angle += directionMultiplier * speed.get() / Math.max(playerPos.distanceTo(targetPos), r);

        double x = targetPos.x + r * Math.cos(angle);
        double z = targetPos.z + r * Math.sin(angle);
        float yaw = (float) Math.toDegrees(Math.atan2(z - playerPos.z, x - playerPos.x)) - 90.0f;
        setHorizontalVelocity(player, yaw, speed.get());
        requestSprintForMovementYaw(player, yaw);
    }

    private LivingEntity currentTarget() {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return null;
        }
        return killAura.getCurrentTarget();
    }

    public boolean shouldDisableAuraFreeCorrection() {
        return isEnabled() && isGrimMode() && currentTarget() != null;
    }

    private int resolveDirectionMultiplier() {
        return switch (directionMode.get()) {
            case COUNTERCLOCKWISE -> -1;
            case RANDOM -> ((System.currentTimeMillis() / 3000L) % 2L == 0L) ? 1 : -1;
            case CLOCKWISE -> 1;
        };
    }

    private Vec3 nextCubePoint(Vec3 playerPos, Vec3 targetPos, double radius, int directionMultiplier) {
        Vec3[] points = new Vec3[]{
                new Vec3(targetPos.x - radius, playerPos.y, targetPos.z - radius),
                new Vec3(targetPos.x - radius, playerPos.y, targetPos.z + radius),
                new Vec3(targetPos.x + radius, playerPos.y, targetPos.z + radius),
                new Vec3(targetPos.x + radius, playerPos.y, targetPos.z - radius)
        };

        if (playerPos.distanceTo(points[pointIndex]) < 0.5) {
            pointIndex = (pointIndex + directionMultiplier + points.length) % points.length;
        }

        return points[pointIndex];
    }

    private Vec3 nextCirclePoint(Vec3 playerPos, Vec3 targetPos, double radius, int directionMultiplier) {
        double baseAngle = (System.currentTimeMillis() % 3600L) / 3600.0 * 4.0 * Math.PI;
        double angle = directionMultiplier > 0 ? baseAngle : (2.0 * Math.PI - baseAngle);
        return new Vec3(
                targetPos.x + Math.cos(angle) * radius,
                playerPos.y,
                targetPos.z + Math.sin(angle) * radius
        );
    }

    private boolean isAnyMovementKeyPressed() {
        return mc.options != null
                && (mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown());
    }

    private void requestSprintForMovementYaw(LocalPlayer player, float movementYaw) {
        float controlYaw = resolveControlYaw();
        float angleDiff = Mth.wrapDegrees(movementYaw - controlYaw);
        SprintController.INSTANCE.requestStartSprinting(mc, player, hasForwardMovement(angleDiff));
    }

    private boolean isMatrixMode() {
        return mode.get() == StrafeMode.MATRIX;
    }

    private boolean isGrimMode() {
        return mode.get() == StrafeMode.GRIM;
    }

    private boolean usesGrimRadius() {
        return isGrimMode() && (grimPointType.get() == PointType.CUBE || grimPointType.get() == PointType.CIRCLE);
    }

    @Getter
    @RequiredArgsConstructor
    private enum StrafeMode implements EnumValue.IdProvider {
        MATRIX("Matrix"),
        GRIM("Grim");

        private final String id;
    }

    @Getter
    @RequiredArgsConstructor
    private enum PointType implements EnumValue.IdProvider {
        CUBE("Cube"),
        CENTER("Center"),
        CIRCLE("Circle");

        private final String id;
    }

    @Getter
    @RequiredArgsConstructor
    private enum DirectionMode implements EnumValue.IdProvider {
        CLOCKWISE("Clockwise"),
        COUNTERCLOCKWISE("Counterclockwise"),
        RANDOM("Random");

        private final String id;
    }
}
