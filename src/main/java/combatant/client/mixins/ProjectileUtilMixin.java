/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.Hitbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    // Intercept getOtherEntities in ProjectileUtil.raycast:
    // - If Hitbox is disabled/panic -> keep vanilla list.
    // - If enabled -> drop dead LivingEntity and those Hitbox wants to ignore.
    @Redirect(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private static List<Entity> combatant$filterEntities(Level world, Entity source, AABB box, Predicate<Entity> predicate) {
        List<Entity> original = world.getEntities(source, box, predicate);
        if (original == null || original.isEmpty()) return original;

        Hitbox hb = Modules.get(Hitbox.class);
        if (hb == null || !hb.isEnabled()) return original;

        List<Entity> filtered = new ArrayList<>(original.size());
        for (Entity e : original) {
            if (e instanceof LivingEntity living && !living.isAlive()) continue;
            if (hb.shouldIgnore(e)) continue;
            filtered.add(e);
        }

        return filtered;
    }

    // Intercept getBoundingBox: add padding only for live targets allowed by Hitbox.
    @Redirect(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB combatant$padBoundingBox(Entity target,
                                                 Entity source,
                                                 Vec3 start,
                                                 Vec3 end,
                                                 AABB box,
                                                 Predicate<Entity> predicate,
                                                 double maxDistance) {
        Hitbox hb = Modules.get(Hitbox.class);
        AABB bb = target.getBoundingBox();
        if (hb == null || !hb.shouldExpandFor(target)) {
            return bb;
        }

        double baseDistanceSq = Hitbox.boxDistanceSq(bb, start);
        if (baseDistanceSq > maxDistance) {
            return bb;
        }

        return bb.inflate(hb.getPadding());
    }
}
