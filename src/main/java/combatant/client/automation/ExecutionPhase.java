/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Points later in the client tick where an action can run deferred work. Actions tick once per
 * client tick before the rotation step; interactions that need this tick's rotation on the wire
 * are deferred to one of these phases.
 *
 * <ol>
 *   <li>{@code RotationUpdateEvent PRE}: tasks tick, rotations are requested.</li>
 *   <li>{@link #AFTER_ROTATION}: {@code RotationUpdateEvent POST}, the rotation for this tick is
 *       resolved; block/item interactions that are validated against it run here.</li>
 *   <li>{@code MovementInputEvent}: leased movement keys are applied.</li>
 *   <li>{@link #AFTER_MOVEMENT_PACKET}: {@code EventPostSync}, the movement packet carrying this
 *       tick's rotation has been sent.</li>
 * </ol>
 */
public enum ExecutionPhase {
    AFTER_ROTATION,
    AFTER_MOVEMENT_PACKET
}
