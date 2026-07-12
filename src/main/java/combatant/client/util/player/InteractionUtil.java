/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import combatant.client.mixins.accessors.ClientLevelAccessor;

public enum InteractionUtil {
    ;

    public static Vec3 getEyesPos(Entity entity) {
        return entity.position().add(0, entity.getEyeHeight(entity.getPose()), 0);
    }

    public static float[] calculateAngle(Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return new float[]{0f, 0f};
        return calculateAngle(getEyesPos(mc.player), to);
    }

    public static float[] calculateAngle(Vec3 from, Vec3 to) {
        double difX = to.x - from.x;
        double difY = (to.y - from.y) * -1.0;
        double difZ = to.z - from.z;
        double dist = Mth.sqrt((float) (difX * difX + difZ * difZ));

        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        float pitch = (float) Mth.clamp(Mth.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    public static void sendSequencedPacket(PredictiveAction packetCreator) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.getConnection() == null) return;
        BlockStatePredictionHandler pending = ((ClientLevelAccessor) mc.level).combatant$getPendingUpdateManager();
        try (BlockStatePredictionHandler pendingUpdateManager = pending.startPredicting()) {
            int id = pendingUpdateManager.currentSequence();
            mc.getConnection().send(packetCreator.predict(id));
        }
    }
}
