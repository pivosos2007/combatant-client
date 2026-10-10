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

package combatant.client.util.aiming;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;

import java.util.List;

/**
 * Rotation target plan.
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public final class RotationTarget {

    public final Rotation rotation;
    public final List<RotationProcessor> processors;
    public final Vec3 targetPoint;
    public final int ticksUntilReset;
    public final float resetThreshold;
    public final boolean considerInventory;
    public final MovementCorrection movementCorrection;
    public final boolean freeCorrection;
    public final RestrictedSingleUseAction whenReached;
    public Entity entity;
    private boolean resettingPass;

    public RotationTarget(Rotation rotation,
                          Entity entity,
                          List<RotationProcessor> processors,
                          int ticksUntilReset,
                          float resetThreshold,
                          boolean considerInventory,
                          MovementCorrection movementCorrection,
                          RestrictedSingleUseAction whenReached) {
        this(rotation, entity, null, processors, ticksUntilReset, resetThreshold, considerInventory,
                movementCorrection, movementCorrection == MovementCorrection.SILENT, whenReached);
    }

    public RotationTarget(Rotation rotation,
                          Entity entity,
                          List<RotationProcessor> processors,
                          int ticksUntilReset,
                          float resetThreshold,
                          boolean considerInventory,
                          MovementCorrection movementCorrection,
                          boolean freeCorrection,
                          RestrictedSingleUseAction whenReached) {
        this(rotation, entity, null, processors, ticksUntilReset, resetThreshold, considerInventory,
                movementCorrection, freeCorrection, whenReached);
    }

    public RotationTarget(Rotation rotation,
                          Entity entity,
                          Vec3 targetPoint,
                          List<RotationProcessor> processors,
                          int ticksUntilReset,
                          float resetThreshold,
                          boolean considerInventory,
                          MovementCorrection movementCorrection,
                          RestrictedSingleUseAction whenReached) {
        this(rotation, entity, targetPoint, processors, ticksUntilReset, resetThreshold, considerInventory,
                movementCorrection, movementCorrection == MovementCorrection.SILENT, whenReached);
    }

    public RotationTarget(Rotation rotation,
                          Entity entity,
                          Vec3 targetPoint,
                          List<RotationProcessor> processors,
                          int ticksUntilReset,
                          float resetThreshold,
                          boolean considerInventory,
                          MovementCorrection movementCorrection,
                          boolean freeCorrection,
                          RestrictedSingleUseAction whenReached) {
        this.rotation = rotation;
        this.entity = entity;
        this.targetPoint = targetPoint;
        this.processors = processors != null ? processors : List.of();
        this.ticksUntilReset = ticksUntilReset;
        this.resetThreshold = resetThreshold;
        this.considerInventory = considerInventory;
        this.movementCorrection = movementCorrection != null ? movementCorrection : MovementCorrection.SILENT;
        this.freeCorrection = freeCorrection && this.movementCorrection == MovementCorrection.SILENT;
        this.whenReached = whenReached;
    }

    public Rotation towards(Rotation currentRotation, boolean isResetting) {
        resettingPass = isResetting;
        try {
            if (isResetting) {
                // Preserve the legacy public contract: expired entity targets stop exposing their entity
                // while the manager performs the smooth return. Block/world-point identity remains intact.
                this.entity = null;
                var player = RotationManager.player();
                Rotation base = player != null ? new Rotation(player.getYRot(), player.getXRot(), false) : Rotation.ZERO;
                return process(currentRotation, base);
            }

            return process(currentRotation, rotation);
        } finally {
            resettingPass = false;
        }
    }

    public boolean isResetting() {
        return resettingPass;
    }

    /** Stable identity for both entity and non-entity rotation targets. */
    public int targetIdentity() {
        if (entity != null) return entity.getId();
        return targetPoint != null ? targetPoint.hashCode() : Integer.MIN_VALUE;
    }

    /** Distance used by generic smoothers; supports block/world points as well as entities. */
    public double distanceToPlayer() {
        if (entity != null) return RotationManager.boxedDistanceToPlayer(entity);
        var player = RotationManager.player();
        return player != null && targetPoint != null ? player.getEyePosition().distanceTo(targetPoint) : 0.0;
    }

    private Rotation process(Rotation currentRotation, Rotation targetRotation) {
        if (processors.isEmpty()) {
            return targetRotation;
        }

        Rotation out = targetRotation;
        Rotation base = currentRotation;
        for (RotationProcessor processor : processors) {
            out = processor.process(this, base, out);
            base = out;
        }
        return out;
    }
}
