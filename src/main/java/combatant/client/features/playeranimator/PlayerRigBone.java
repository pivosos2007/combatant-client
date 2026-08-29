/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

/**
 * Stable semantic skeleton contract shared by meshes, animation code and JavaScript.
 * Bind translations use vanilla model pixels and are converted to model units by
 * {@link PlayerRigDefinition}.
 */
public enum PlayerRigBone {
    ROOT("root", null, 0f, 0f, 0f),
    MOTION("motion", ROOT, 0f, 0f, 0f),
    PELVIS("pelvis", MOTION, 0f, 12f, 0f),

    SPINE_LOWER("spine_lower", PELVIS, 0f, -4f, 0f),
    SPINE_MID("spine_mid", SPINE_LOWER, 0f, -3f, 0f),
    SPINE_UPPER("spine_upper", SPINE_MID, 0f, -3f, 0f),
    CHEST("chest", SPINE_UPPER, 0f, -2f, 0f),
    // Vanilla's head cube rotates around y=0. Extra anatomical neck joints share that bind pivot;
    // separating them vertically would make the complete head orbit above the shoulders.
    NECK_LOWER("neck_lower", CHEST, 0f, 0f, 0f),
    NECK_UPPER("neck_upper", NECK_LOWER, 0f, 0f, 0f),
    HEAD("head", NECK_UPPER, 0f, 0f, 0f),
    JAW("jaw", HEAD, 0f, 4f, -3f),

    LEFT_SCAPULA("left_scapula", CHEST, 1.5f, 0f, 1f),
    LEFT_CLAVICLE("left_clavicle", LEFT_SCAPULA, 1.5f, 0f, -1f),
    LEFT_SHOULDER("left_shoulder", LEFT_CLAVICLE, 2f, 0f, 0f),
    LEFT_UPPER_ARM("left_upper_arm", LEFT_SHOULDER, 0f, 0f, 0f),
    LEFT_UPPER_ARM_TWIST("left_upper_arm_twist", LEFT_UPPER_ARM, 0f, 3f, 0f),
    LEFT_ELBOW("left_elbow", LEFT_UPPER_ARM_TWIST, 0f, 3f, 0f),
    LEFT_FOREARM("left_forearm", LEFT_ELBOW, 0f, 0f, 0f),
    LEFT_FOREARM_TWIST("left_forearm_twist", LEFT_FOREARM, 0f, 3f, 0f),
    LEFT_WRIST("left_wrist", LEFT_FOREARM_TWIST, 0f, 2f, 0f),
    LEFT_HAND("left_hand", LEFT_WRIST, 0f, 1f, 0f),
    LEFT_ITEM_CONTROL("left_item_control", LEFT_HAND, 0f, 1f, -1f),
    LEFT_THUMB_METACARPAL("left_thumb_metacarpal", LEFT_HAND, -1.5f, 1f, -1f),
    LEFT_THUMB("left_thumb", LEFT_THUMB_METACARPAL, -1f, 1f, 0f),
    LEFT_FINGERS("left_fingers", LEFT_HAND, 0f, 2f, -1f),

    RIGHT_SCAPULA("right_scapula", CHEST, -1.5f, 0f, 1f),
    RIGHT_CLAVICLE("right_clavicle", RIGHT_SCAPULA, -1.5f, 0f, -1f),
    RIGHT_SHOULDER("right_shoulder", RIGHT_CLAVICLE, -2f, 0f, 0f),
    RIGHT_UPPER_ARM("right_upper_arm", RIGHT_SHOULDER, 0f, 0f, 0f),
    RIGHT_UPPER_ARM_TWIST("right_upper_arm_twist", RIGHT_UPPER_ARM, 0f, 3f, 0f),
    RIGHT_ELBOW("right_elbow", RIGHT_UPPER_ARM_TWIST, 0f, 3f, 0f),
    RIGHT_FOREARM("right_forearm", RIGHT_ELBOW, 0f, 0f, 0f),
    RIGHT_FOREARM_TWIST("right_forearm_twist", RIGHT_FOREARM, 0f, 3f, 0f),
    RIGHT_WRIST("right_wrist", RIGHT_FOREARM_TWIST, 0f, 2f, 0f),
    RIGHT_HAND("right_hand", RIGHT_WRIST, 0f, 1f, 0f),
    RIGHT_ITEM_CONTROL("right_item_control", RIGHT_HAND, 0f, 1f, -1f),
    RIGHT_THUMB_METACARPAL("right_thumb_metacarpal", RIGHT_HAND, 1.5f, 1f, -1f),
    RIGHT_THUMB("right_thumb", RIGHT_THUMB_METACARPAL, 1f, 1f, 0f),
    RIGHT_FINGERS("right_fingers", RIGHT_HAND, 0f, 2f, -1f),

    LEFT_HIP("left_hip", PELVIS, 1.9f, 0f, 0f),
    LEFT_THIGH("left_thigh", LEFT_HIP, 0f, 0f, 0f),
    LEFT_THIGH_TWIST("left_thigh_twist", LEFT_THIGH, 0f, 3f, 0f),
    LEFT_KNEE("left_knee", LEFT_THIGH_TWIST, 0f, 3f, 0f),
    LEFT_SHIN("left_shin", LEFT_KNEE, 0f, 0f, 0f),
    LEFT_SHIN_TWIST("left_shin_twist", LEFT_SHIN, 0f, 3f, 0f),
    LEFT_ANKLE("left_ankle", LEFT_SHIN_TWIST, 0f, 3f, 0f),
    LEFT_FOOT("left_foot", LEFT_ANKLE, 0f, 0f, 0f),
    LEFT_TOE("left_toe", LEFT_FOOT, 0f, 0f, -3f),

    RIGHT_HIP("right_hip", PELVIS, -1.9f, 0f, 0f),
    RIGHT_THIGH("right_thigh", RIGHT_HIP, 0f, 0f, 0f),
    RIGHT_THIGH_TWIST("right_thigh_twist", RIGHT_THIGH, 0f, 3f, 0f),
    RIGHT_KNEE("right_knee", RIGHT_THIGH_TWIST, 0f, 3f, 0f),
    RIGHT_SHIN("right_shin", RIGHT_KNEE, 0f, 0f, 0f),
    RIGHT_SHIN_TWIST("right_shin_twist", RIGHT_SHIN, 0f, 3f, 0f),
    RIGHT_ANKLE("right_ankle", RIGHT_SHIN_TWIST, 0f, 3f, 0f),
    RIGHT_FOOT("right_foot", RIGHT_ANKLE, 0f, 0f, 0f),
    RIGHT_TOE("right_toe", RIGHT_FOOT, 0f, 0f, -3f);

    private final String id;
    private final PlayerRigBone parent;
    private final float xPixels;
    private final float yPixels;
    private final float zPixels;

    PlayerRigBone(String id, PlayerRigBone parent, float xPixels, float yPixels, float zPixels) {
        this.id = id;
        this.parent = parent;
        this.xPixels = xPixels;
        this.yPixels = yPixels;
        this.zPixels = zPixels;
    }

    public String id() { return id; }
    public PlayerRigBone parent() { return parent; }
    public float xPixels() { return xPixels; }
    public float yPixels() { return yPixels; }
    public float zPixels() { return zPixels; }
}
