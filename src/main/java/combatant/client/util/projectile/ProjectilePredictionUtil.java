/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.projectile;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.entity.simulation.MountedEntityPrediction;
import combatant.client.util.player.simulation.PlayerSimulationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public enum ProjectilePredictionUtil {
    ;

    public static Rotation calculateForHeldItem(Player player, LivingEntity target, boolean alwaysShowBow) {
        if (player == null || target == null) {
            return null;
        }

        Rotation mainHand = calculateForItem(player, player.getItemInHand(InteractionHand.MAIN_HAND), target, alwaysShowBow);
        if (mainHand != null) {
            return mainHand;
        }

        return calculateForItem(player, player.getItemInHand(InteractionHand.OFF_HAND), target, alwaysShowBow);
    }

    public static Rotation calculateForItem(Player player, ItemStack stack, LivingEntity target, boolean alwaysShowBow) {
        if (player == null || target == null || stack == null || stack.isEmpty()) {
            return null;
        }

        TrajectoryInfo.Typed typed = TrajectoryData.getRenderedTrajectoryInfo(player, stack, alwaysShowBow);
        if (typed == null) {
            return null;
        }

        return SituationalProjectileAngleCalculator.INSTANCE.calculateAngleForEntity(
                typed.info(),
                target,
                player.getEyePosition()
        );
    }

    public static Rotation calculateForCurrentPlayer(ItemStack stack, LivingEntity target, boolean alwaysShowBow) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : calculateForItem(mc.player, stack, target, alwaysShowBow);
    }

    public static LivingEntity getHypotheticalHit(Player player,
                                                  ItemStack stack,
                                                  Rotation rotation,
                                                  Predicate<LivingEntity> predicate) {
        return getHypotheticalHit(player, stack, rotation, predicate, false);
    }

    public static LivingEntity getHypotheticalHit(Player player,
                                                  ItemStack stack,
                                                  Rotation rotation,
                                                  Predicate<LivingEntity> predicate,
                                                  boolean alwaysShowBow) {
        if (player == null || rotation == null || stack == null || stack.isEmpty()) {
            return null;
        }

        TrajectoryInfo.Typed typed = TrajectoryData.getRenderedTrajectoryInfo(player, stack, alwaysShowBow);
        if (typed == null || !(player.level() instanceof net.minecraft.client.multiplayer.ClientLevel world)) {
            return null;
        }

        double velocity = typed.info().initialVelocity();
        float yaw = rotation.yaw();
        float pitch = rotation.pitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
        double vY = -Math.sin(pitchRad) * velocity;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
        Vec3 initialVelocity = new Vec3(vX, vY, vZ);
        if (typed.info().copiesPlayerVelocity()) {
            initialVelocity = initialVelocity.add(player.getDeltaMovement());
        }

        SimulatedArrow arrow = new SimulatedArrow(
                world,
                player.getEyePosition(),
                initialVelocity,
                false
        );

        List<SimulatedTarget> targets = findSimulatedTargets(player, predicate);
        if (targets.isEmpty()) {
            return null;
        }

        for (int i = 0; i < 40; i++) {
            Vec3 lastPos = arrow.getPos();
            HitResult hitResult = arrow.tick();
            Vec3 currentPos = arrow.getPos();

            for (SimulatedTarget target : targets) {
                Vec3 predictedPos = target.getPositionInTicks(i);
                AABB entityBox = target.entity().getBoundingBox()
                        .inflate(0.3)
                        .move(predictedPos.subtract(target.entity().position()));
                if (entityBox.clip(lastPos, currentPos).isPresent()) {
                    return target.entity();
                }
            }

            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                break;
            }
        }

        return null;
    }

    private static List<SimulatedTarget> findSimulatedTargets(Player player,
                                                              Predicate<LivingEntity> predicate) {
        List<SimulatedTarget> targets = new ArrayList<>();
        if (player == null || player.level() == null) {
            return targets;
        }

        AABB search = player.getBoundingBox().inflate(160.0);
        for (Entity entity : player.level().getEntities(player, search, entity -> entity instanceof LivingEntity)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == player || !living.isAlive()) {
                continue;
            }
            if (predicate != null && !predicate.test(living)) {
                continue;
            }

            targets.add(new SimulatedTarget(living));
        }

        return targets;
    }

    private record SimulatedTarget(LivingEntity entity) {
        private Vec3 getPositionInTicks(int ticks) {
            if (entity.isPassenger()) {
                return MountedEntityPrediction.predictMountedPosition(entity, ticks);
            }

            if (entity instanceof Player) {
                return PlayerSimulationCache.getSimulationForOtherPlayers((Player) entity).getSnapshotAt(ticks).pos();
            }

            return entity.position().add(entity.getDeltaMovement().scale(ticks));
        }
    }
}
