/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;

import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class AutoCrystalEntityBlocker {
    public Result check(
            ClientLevel level,
            LocalPlayer player,
            BlockPos base,
            boolean calcPhase,
            IntPredicate deadCrystalPredicate,
            IntPredicate blockedCrystalPredicate,
            Predicate<EndCrystal> attackableCrystalPredicate
    ) {
        if (level == null || player == null || base == null) {
            return Result.notBlocked();
        }

        AABB box = new AABB(base.above()).inflate(0.0, 1.0, 0.0);
        EndCrystal secondaryCrystal = null;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }

            if (!entity.getBoundingBox().intersects(box)) {
                continue;
            }

            if (entity instanceof ExperienceOrb) {
                continue;
            }

            if (entity instanceof EndCrystal crystal) {
                int id = crystal.getId();
                if (deadCrystalPredicate != null && deadCrystalPredicate.test(id)) {
                    continue;
                }

                if (blockedCrystalPredicate != null && blockedCrystalPredicate.test(id)) {
                    return Result.blockedEntity();
                }

                if (calcPhase) {
                    if (attackableCrystalPredicate != null && attackableCrystalPredicate.test(crystal)) {
                        continue;
                    }
                } else if (crystal.position().distanceToSqr(box.getCenter()) > 0.3) {
                    secondaryCrystal = crystal;
                }

                return Result.blockedEntity(secondaryCrystal);
            }

            return Result.blockedEntity();
        }

        return Result.notBlocked();
    }

    public record Result(boolean blocked, EndCrystal secondaryCrystal) {
        public static Result notBlocked() {
            return new Result(false, null);
        }

        public static Result blockedEntity() {
            return new Result(true, null);
        }

        public static Result blockedEntity(EndCrystal secondaryCrystal) {
            return new Result(true, secondaryCrystal);
        }
    }
}
