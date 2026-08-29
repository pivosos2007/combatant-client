/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

/** Stable attachment points used by held items, armor layers and feature renderers. */
public enum PlayerRigSocket {
    HEAD("head", PlayerRigBone.HEAD),
    FACE("face", PlayerRigBone.HEAD),
    HELMET("armor_helmet", PlayerRigBone.HEAD),
    CHEST("chest", PlayerRigBone.CHEST),
    BACK("back", PlayerRigBone.CHEST),
    CAPE_ROOT("cape_root", PlayerRigBone.CHEST),
    ELYTRA_LEFT("elytra_left", PlayerRigBone.LEFT_SCAPULA),
    ELYTRA_RIGHT("elytra_right", PlayerRigBone.RIGHT_SCAPULA),
    WAIST("waist", PlayerRigBone.PELVIS),
    LEFT_SHOULDER_ARMOR("armor_left_shoulder", PlayerRigBone.LEFT_SHOULDER),
    RIGHT_SHOULDER_ARMOR("armor_right_shoulder", PlayerRigBone.RIGHT_SHOULDER),
    LEFT_FOREARM_ARMOR("armor_left_forearm", PlayerRigBone.LEFT_FOREARM_TWIST),
    RIGHT_FOREARM_ARMOR("armor_right_forearm", PlayerRigBone.RIGHT_FOREARM_TWIST),
    LEFT_HAND("left_hand", PlayerRigBone.LEFT_HAND),
    RIGHT_HAND("right_hand", PlayerRigBone.RIGHT_HAND),
    LEFT_ITEM("left_item", PlayerRigBone.LEFT_ITEM_CONTROL),
    RIGHT_ITEM("right_item", PlayerRigBone.RIGHT_ITEM_CONTROL),
    LEFT_THIGH_ARMOR("armor_left_thigh", PlayerRigBone.LEFT_THIGH),
    RIGHT_THIGH_ARMOR("armor_right_thigh", PlayerRigBone.RIGHT_THIGH),
    LEFT_SHIN_ARMOR("armor_left_shin", PlayerRigBone.LEFT_SHIN),
    RIGHT_SHIN_ARMOR("armor_right_shin", PlayerRigBone.RIGHT_SHIN),
    LEFT_FOOT_ARMOR("armor_left_foot", PlayerRigBone.LEFT_FOOT),
    RIGHT_FOOT_ARMOR("armor_right_foot", PlayerRigBone.RIGHT_FOOT);

    private final String id;
    private final PlayerRigBone bone;

    PlayerRigSocket(String id, PlayerRigBone bone) {
        this.id = id;
        this.bone = bone;
    }

    public String id() { return id; }
    public PlayerRigBone bone() { return bone; }
}
