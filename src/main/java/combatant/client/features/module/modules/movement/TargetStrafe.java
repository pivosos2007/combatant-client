/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
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
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.features.module.modules.movement.holesnap.Steering;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.combat.SprintController;
import combatant.client.util.player.navigation.OrbitNavigator;
import combatant.client.util.player.navigation.HazardAvoidance;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "targetstrafe",
        displayName = "TargetStrafe",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.targetstrafe.description")
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
    private final BooleanValue autoJump = bool("auto_jump", true);
    private final BooleanValue onlyKeyPressed = bool("only_key_pressed", false);
    private final BooleanValue inFrontOfTarget = bool("in_front_of_target", false);
    private final EnumValue<DirectionMode> directionMode =
            enumMode("direction_mode", DirectionMode.CLOCKWISE,
                    DirectionMode.CLOCKWISE, DirectionMode.COUNTERCLOCKWISE, DirectionMode.RANDOM);

    private final OrbitNavigator navigator = new OrbitNavigator();
    private LivingEntity lastDirectionTarget;
    private DirectionMode lastDirectionMode;
    private int randomDirection = 1;

    @Override
    public void onEnable() {
        navigator.reset();
        lastDirectionTarget = null;
        lastDirectionMode = null;
    }

    @Override
    public void onDisable() {
        navigator.reset();
        lastDirectionTarget = null;
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) {
            navigator.reset();
            return;
        }
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) {
            navigator.reset();
            return;
        }

        PointType type = isMatrixMode() ? matrixPointType.get() : grimPointType.get();
        if (!isMatrixMode()) return;
        navigator.update(player, target, radius.get(),
                resolveDirectionMultiplier(target), inFrontOfTarget.get(),
                type == PointType.CUBE, type == PointType.CENTER, autoJump.get());
        Vec3 direction = navigator.direction(player);
        if (direction == null) {
            stopHorizontal(player);
            return;
        }
        boolean jump = autoJump.get() && navigator.needsJump(player);
        boolean trapped = HazardAvoidance.exposure(player.level(), player.getBoundingBox()) > 0;
        // Preserve the old dynamic hopping on clear ground. On a climb, jump
        // first and allow the horizontal traversal once the player is airborne.
        boolean hop = autoJump.get() && player.onGround() && !trapped;
        if ((jump || hop) && player.onGround()) player.jumpFromGround();

        double velocity = speed.get();
        Vec3 horizontal = direction.scale(velocity);
        if (!navigator.safeMotion(player, horizontal)) {
            // Keep moving at a shorter safe step if the configured Matrix speed
            // is larger than the clearance in front of us. Never push into a
            // wall/void, but do not turn a minor speed overshoot into a freeze.
            if (!jump) {
                for (int attempt = 0; attempt < 4 && velocity >= 0.11; attempt++) {
                    velocity *= 0.70;
                    horizontal = direction.scale(velocity);
                    if (navigator.safeMotion(player, horizontal)) break;
                }
            }
            if (jump || velocity < 0.11 || !navigator.safeMotion(player, horizontal)) {
                // A climb starts with vertical velocity. Do not invalidate the
                // path until the airborne player has a chance to cross the rise.
                if (!jump) navigator.invalidate();
                stopHorizontal(player);
                return;
            }
        }
        player.setDeltaMovement(horizontal.x, player.getDeltaMovement().y, horizontal.z);
        float yaw = (float) Math.toDegrees(Math.atan2(horizontal.z, horizontal.x)) - 90.0f;
        float angleDiff = Mth.wrapDegrees(yaw - resolveControlYaw());
        SprintController.INSTANCE.requestStartSprinting(mc, player,
                velocity > 0.01 && hasForwardMovement(angleDiff));
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || !isGrimMode()) return;
        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) return;

        PointType type = grimPointType.get();
        navigator.update(player, target, grimRadius.get(), resolveDirectionMultiplier(target),
                inFrontOfTarget.get(), type == PointType.CUBE,
                type == PointType.CENTER, autoJump.get());
        Vec3 direction = navigator.direction(player);
        if (direction == null) {
            clearMovement(event);
            return;
        }
        float controlYaw = resolveControlYaw();
        int keys = Steering.keysToward(controlYaw, direction.x, direction.z);
        boolean jump = autoJump.get() && player.onGround() && navigator.needsJump(player);
        boolean trapped = HazardAvoidance.exposure(player.level(), player.getBoundingBox()) > 0;
        Vec3 inputMotion = motionFromKeys(controlYaw, keys).scale(0.36);
        // WASD quantization may cut a corner even when the waypoint path is valid.
        // Validate the actual input vector against the same collision/edge policy.
        if (keys != 0 && !navigator.safeMotion(player, inputMotion)) {
            if (!jump) navigator.invalidate();
            clearMovement(event);
            if (jump) event.setJump(true);
            return;
        }
        // Hop on clear ground as before, but not while escaping webs/lava.
        boolean hop = autoJump.get() && player.onGround() && !trapped;
        event.setForward((keys & Steering.FORWARD) != 0);
        event.setBackward((keys & Steering.BACKWARD) != 0);
        event.setLeft((keys & Steering.LEFT) != 0);
        event.setRight((keys & Steering.RIGHT) != 0);
        event.setJump(event.isJump() || jump || hop);
        event.setSprint(SprintController.INSTANCE.canStartSprinting(player)
                && (keys & Steering.FORWARD) != 0);
    }

    private static Vec3 motionFromKeys(float yaw, int keys) {
        double forward = ((keys & Steering.FORWARD) != 0 ? 1.0 : 0.0)
                - ((keys & Steering.BACKWARD) != 0 ? 1.0 : 0.0);
        double sideways = ((keys & Steering.LEFT) != 0 ? 1.0 : 0.0)
                - ((keys & Steering.RIGHT) != 0 ? 1.0 : 0.0);
        double angle = Math.toRadians(yaw);
        Vec3 vector = new Vec3(-Math.sin(angle) * forward + Math.cos(angle) * sideways,
                0.0, Math.cos(angle) * forward + Math.sin(angle) * sideways);
        return vector.lengthSqr() > 1.0e-6 ? vector.normalize() : Vec3.ZERO;
    }

    private static void clearMovement(MovementInputEvent event) {
        event.setForward(false);
        event.setBackward(false);
        event.setLeft(false);
        event.setRight(false);
        event.setSprint(false);
        // Do not clear the user's explicit jump/sneak keys.
    }

    private static void stopHorizontal(LocalPlayer player) {
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
    }

    private LivingEntity currentTarget() {
        KillAura aura = Modules.get(KillAura.class);
        return aura != null && aura.isEnabled() ? aura.getCurrentTarget() : null;
    }

    public boolean shouldDisableAuraFreeCorrection() {
        return isEnabled() && isGrimMode() && currentTarget() != null;
    }

    private int resolveDirectionMultiplier(LivingEntity target) {
        DirectionMode setting = directionMode.get();
        if (lastDirectionTarget != target || lastDirectionMode != setting) {
            lastDirectionTarget = target;
            lastDirectionMode = setting;
            randomDirection = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        }
        return switch (setting) {
            case CLOCKWISE -> 1;
            case COUNTERCLOCKWISE -> -1;
            case RANDOM -> randomDirection;
        };
    }

    private static float resolveControlYaw() {
        var rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation != null) return rotation.yaw();
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0f : player.getYRot();
    }

    private static boolean hasForwardMovement(float angleDiff) {
        return angleDiff > -67.5f && angleDiff < 67.5f;
    }

    private boolean isAnyMovementKeyPressed() {
        return mc.options != null && (mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown());
    }

    private boolean isMatrixMode() { return mode.get() == StrafeMode.MATRIX; }
    private boolean isGrimMode() { return mode.get() == StrafeMode.GRIM; }
    private boolean usesGrimRadius() {
        return isGrimMode() && grimPointType.get() != PointType.CENTER;
    }

    @Getter @RequiredArgsConstructor
    private enum StrafeMode implements EnumValue.IdProvider {
        MATRIX("Matrix"), GRIM("Grim");
        private final String id;
    }

    @Getter @RequiredArgsConstructor
    private enum PointType implements EnumValue.IdProvider {
        CUBE("Cube"), CENTER("Center"), CIRCLE("Circle");
        private final String id;
    }

    @Getter @RequiredArgsConstructor
    private enum DirectionMode implements EnumValue.IdProvider {
        CLOCKWISE("Clockwise"), COUNTERCLOCKWISE("Counterclockwise"), RANDOM("Random");
        private final String id;
    }
}
