/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.render.engine.rig.core.RigDefinition;
import combatant.client.render.engine.rig.core.RigTransform;
import combatant.client.render.engine.rig.shader.RigShaderLimits;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Compiled immutable anatomical player skeleton. */
public final class PlayerRigDefinition {
    public static final float MODEL_UNIT = 1f / 16f;
    private static final RigDefinition DEFINITION = build();

    private PlayerRigDefinition() {
    }

    public static RigDefinition get() {
        return DEFINITION;
    }

    public static int index(PlayerRigBone bone) {
        if (bone == null) throw new IllegalArgumentException("Player rig bone must not be null");
        return bone.ordinal();
    }

    public static int socketIndex(PlayerRigSocket socket) {
        if (socket == null) throw new IllegalArgumentException("Player rig socket must not be null");
        return socket.ordinal();
    }

    private static RigDefinition build() {
        if (PlayerRigBone.values().length > RigShaderLimits.MAX_BONES) {
            throw new IllegalStateException("Player anatomy exceeds rig shader bone capacity: "
                    + PlayerRigBone.values().length + "/" + RigShaderLimits.MAX_BONES);
        }

        RigDefinition.Builder builder = RigDefinition.builder();
        for (PlayerRigBone bone : PlayerRigBone.values()) {
            int parentIndex = bone.parent() != null ? bone.parent().ordinal() : -1;
            int actual = builder.bone(
                    bone.id(),
                    parentIndex,
                    new RigTransform(
                            new Vector3f(
                                    bone.xPixels() * MODEL_UNIT,
                                    bone.yPixels() * MODEL_UNIT,
                                    bone.zPixels() * MODEL_UNIT
                            ),
                            new Quaternionf(),
                            new Vector3f(1f, 1f, 1f)
                    )
            );
            if (actual != bone.ordinal()) {
                throw new IllegalStateException("Player rig semantic index drift for " + bone.id());
            }
        }

        for (PlayerRigSocket socket : PlayerRigSocket.values()) {
            builder.socket(socket.id(), socket.bone().ordinal(), RigTransform.identity());
        }
        return builder.build();
    }
}
