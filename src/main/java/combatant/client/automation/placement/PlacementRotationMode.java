/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

/** How a placement aims. Constant names are persisted in module profiles; never rename them. */
public enum PlacementRotationMode {
    /** Rotate to the support face and click once the rotation of this tick hits it. */
    NORMAL,
    /** Click without turning; the click is built from the computed hit point. */
    NO_ROTATION
}
