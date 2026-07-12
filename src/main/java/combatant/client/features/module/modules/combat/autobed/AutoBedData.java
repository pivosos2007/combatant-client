/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public record AutoBedData(
        BlockPos footPos,
        BlockPos headPos,
        Direction facing,
        BlockPos interactPos,
        BlockHitResult placeHitResult,
        BlockHitResult explodeHitResult,
        Vec3 explosionVec,
        float damage,
        float selfDamage,
        boolean overrideDamage,
        boolean existingBed
) implements AutoBedDamageCandidate {
    public BlockPos pos() {
        return footPos;
    }
}
