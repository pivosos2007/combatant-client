/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.render.engine.rig.deform.RigDeformDefinition;
import org.joml.Vector3f;

/** GPU procedural deformation channels available to both Java and JavaScript animation. */
public enum PlayerRigDeformer {
    SPINE("spine", 0, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 12f),
    NECK("neck", 1, 0f, 2f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 2f),
    LEFT_UPPER_ARM("left_upper_arm", 2, 5f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    LEFT_FOREARM("left_forearm", 3, 5f, 6f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    RIGHT_UPPER_ARM("right_upper_arm", 4, -5f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    RIGHT_FOREARM("right_forearm", 5, -5f, 6f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    LEFT_THIGH("left_thigh", 6, 1.9f, 12f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    LEFT_SHIN("left_shin", 7, 1.9f, 18f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    RIGHT_THIGH("right_thigh", 8, -1.9f, 12f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    RIGHT_SHIN("right_shin", 9, -1.9f, 18f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 6f),
    CAPE("cape", 10, 0f, 2f, 2f, 0f, 1f, 0f, 1f, 0f, 0f, 16f);

    private final String id;
    private final int channel;
    private final float ox, oy, oz;
    private final float ax, ay, az;
    private final float bx, by, bz;
    private final float length;

    PlayerRigDeformer(String id, int channel, float ox, float oy, float oz,
                      float ax, float ay, float az, float bx, float by, float bz, float length) {
        this.id = id;
        this.channel = channel;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.length = length;
    }

    public String id() { return id; }
    public int channel() { return channel; }

    RigDeformDefinition definition() {
        float unit = PlayerRigDefinition.MODEL_UNIT;
        return RigDeformDefinition.fullLength(
                channel,
                new Vector3f(ox * unit, oy * unit, oz * unit),
                new Vector3f(ax, ay, az),
                new Vector3f(bx, by, bz),
                length * unit
        );
    }
}
