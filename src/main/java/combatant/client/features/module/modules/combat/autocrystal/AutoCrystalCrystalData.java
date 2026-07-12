/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

public record AutoCrystalCrystalData(
        EndCrystal crystal,
        float damage,
        float selfDamage,
        boolean overrideDamage
) implements AutoCrystalDamageCandidate {
}
