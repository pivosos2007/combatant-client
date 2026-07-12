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

package combatant.client.util.aiming.preference;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Prefers rotations with the least angular difference.
 * <p>
 * Inspired by LiquidBounce (CCBlueX).
 */
public final class LeastDifferencePreference implements RotationPreference {

    public static final LeastDifferencePreference LEAST_DISTANCE_TO_CURRENT_ROTATION =
            new LeastDifferencePreference(LeastDifferencePreference::currentRotation);
    private final Supplier<Rotation> baseSupplier;
    private final Vec3 basePoint;

    public LeastDifferencePreference(Rotation baseRotation) {
        this(() -> baseRotation, null);
    }

    private LeastDifferencePreference(Supplier<Rotation> supplier) {
        this(supplier, null);
    }

    private LeastDifferencePreference(Supplier<Rotation> supplier, Vec3 basePoint) {
        this.baseSupplier = Objects.requireNonNull(supplier, "supplier");
        this.basePoint = basePoint;
    }

    public static RotationPreference leastDifferenceToLastPoint(Vec3 eyes, Vec3 lastPoint) {
        if (eyes == null || lastPoint == null) {
            return LEAST_DISTANCE_TO_CURRENT_ROTATION;
        }
        return new LeastDifferencePreference(() -> Rotation.lookingAt(lastPoint, eyes), lastPoint);
    }

    private static Rotation currentRotation() {
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        if (current != null) {
            return current;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            return new Rotation(mc.player.getYRot(), mc.player.getXRot(), false).normalize();
        }
        return Rotation.ZERO;
    }

    @Override
    public int compare(Rotation a, Rotation b) {
        Rotation base = baseSupplier.get();
        return Float.compare(base.angleTo(a), base.angleTo(b));
    }

    @Override
    public Vec3 getPreferredSpotOnBox(AABB box, Vec3 eyes, double range) {
        if (basePoint != null) {
            return basePoint;
        }

        Rotation base = baseSupplier.get();
        Vec3 dir = RotationUtil.getRotationVector(base.pitch(), base.yaw()).normalize();
        return eyes.add(dir.scale(range));
    }
}
