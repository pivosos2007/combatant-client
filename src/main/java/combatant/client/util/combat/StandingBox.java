/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Standing-pose bounding box of an entity, for targeting that must ignore pose shrinking. */
public final class StandingBox {
    private StandingBox() {
    }

    /** True for the player poses whose box is smaller than the standing one. */
    public static boolean shrinksBox(Pose pose) {
        return pose == Pose.CROUCHING
                || pose == Pose.SWIMMING
                || pose == Pose.FALL_FLYING
                || pose == Pose.SPIN_ATTACK;
    }

    /** Whether an entity in {@code pose} should be given its standing box. */
    public static boolean applies(boolean player, Pose pose) {
        return player ? shrinksBox(pose) : pose != Pose.STANDING;
    }

    /**
     * The box {@code entity} has when standing at its position, or {@code current} when the pose does not
     * change it or the standing box would not be taller.
     */
    public static AABB of(Entity entity, AABB current) {
        if (!applies(entity instanceof Player, entity.getPose())) return current;
        AABB standing = entity.getDimensions(Pose.STANDING).makeBoundingBox(entity.position());
        return standing.getYsize() > current.getYsize() ? standing : current;
    }
}
