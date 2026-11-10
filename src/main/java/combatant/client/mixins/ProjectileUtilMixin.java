/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.events.Events;
import combatant.client.events.impl.ProjectileRaycastBoxEvent;
import combatant.client.events.impl.ProjectileRaycastCandidatesEvent;

import java.util.List;
import java.util.function.Predicate;

/** Exposes projectile entity candidates and target boxes to synchronous hit-test listeners. */
@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

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

        if (!Events.BUS.hasListeners(ProjectileRaycastCandidatesEvent.class)) return original;
        return Events.BUS.post(new ProjectileRaycastCandidatesEvent(original)).getEntities();
    }

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
        AABB bb = target.getBoundingBox();
        if (!Events.BUS.hasListeners(ProjectileRaycastBoxEvent.class)) return bb;
        return Events.BUS.post(new ProjectileRaycastBoxEvent(target, start, maxDistance, bb)).getBox();
    }
}
