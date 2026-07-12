/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.entity.simulation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.player.simulation.PlayerSimulationCache;

/**
 * Predicts passengers by following the predicted vehicle position and rotating
 * the current passenger offset by the vehicle yaw delta.
 */
public enum MountedEntityPrediction {
    ;

    public static Vec3 predictMountedPosition(Entity entity, double ticks) {
        if (entity == null) {
            return Vec3.ZERO;
        }

        Entity vehicle = entity.getVehicle();
        if (vehicle == null) {
            return entity.position();
        }

        Vec3 predictedVehiclePos = predictVehiclePosition(vehicle, ticks);
        float currentVehicleYaw = vehicle.getYRot();
        float predictedVehicleYaw = predictVehicleYaw(vehicle, ticks);

        Vec3 currentOffset = entity.position().subtract(vehicle.position());
        Vec3 localOffset = rotateY(currentOffset, -currentVehicleYaw);
        Vec3 rotatedOffset = rotateY(localOffset, predictedVehicleYaw);

        return predictedVehiclePos.add(rotatedOffset);
    }

    private static Vec3 predictVehiclePosition(Entity vehicle, double ticks) {
        if (vehicle.isPassenger()) {
            return predictMountedPosition(vehicle, ticks);
        }

        if (vehicle instanceof AbstractBoat boat) {
            return BoatSimulationCache.getSimulation(boat).getSnapshotAt((int) Math.ceil(ticks)).pos();
        }

        if (vehicle instanceof Player player) {
            return PlayerSimulationCache.getSimulationForOtherPlayers(player).getSnapshotAt((int) Math.ceil(ticks)).pos();
        }

        return PositionPredictor.predictEntityPos(vehicle, ticks);
    }

    private static float predictVehicleYaw(Entity vehicle, double ticks) {
        if (vehicle instanceof AbstractBoat boat) {
            return BoatSimulationCache.getSimulation(boat).getSnapshotAt((int) Math.ceil(ticks)).yaw();
        }

        float yaw = vehicle.getYRot();
        float yawDelta = Mth.wrapDegrees(yaw - vehicle.yRotO);
        return yaw + yawDelta * (float) ticks;
    }

    private static Vec3 rotateY(Vec3 vec, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = vec.x * cos - vec.z * sin;
        double z = vec.x * sin + vec.z * cos;
        return new Vec3(x, vec.y, z);
    }
}
