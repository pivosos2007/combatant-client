/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.config.values;

/** Optional slider precision for values requiring finer resolution than 0.01. */
public interface PrecisionNumberValue {
    int decimalPlaces();
}
