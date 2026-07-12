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

package combatant.client.util.aiming.features.processors;

import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;

/**
 * Processes the rotation from current to target.
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public interface RotationProcessor {

    Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation);
}
