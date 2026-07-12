/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat.protocol;

/**
 * Simple protocol version holder (client-side).
 */
public record ClientProtocolVersion(String name, int version) {
}
