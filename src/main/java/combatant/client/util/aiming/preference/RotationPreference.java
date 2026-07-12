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

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

import java.util.Comparator;

/**
 * Rotation preference (comparator + preferred spot on box).
 * <p>
 * Inspired by LiquidBounce (CCBlueX).
 */
public interface RotationPreference extends Comparator<Rotation> {

    Vec3 getPreferredSpotOnBox(AABB box, Vec3 eyes, double range);
}
