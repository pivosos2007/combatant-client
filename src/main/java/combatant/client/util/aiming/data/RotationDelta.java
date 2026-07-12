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

package combatant.client.util.aiming.data;

/**
 * Yaw/Pitch delta container.
 * <p>
 * Inspired by LiquidBounce (CCBlueX).
 */
public record RotationDelta(float deltaYaw, float deltaPitch) {

    public float length() {
        return (float) Math.hypot(deltaYaw, deltaPitch);
    }

    public net.minecraft.world.phys.Vec2 toVec2f() {
        return new net.minecraft.world.phys.Vec2(deltaYaw, deltaPitch);
    }
}
