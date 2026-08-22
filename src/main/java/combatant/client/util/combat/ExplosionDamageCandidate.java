/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

public interface ExplosionDamageCandidate {
    float damage();

    float selfDamage();

    boolean overrideDamage();
}
