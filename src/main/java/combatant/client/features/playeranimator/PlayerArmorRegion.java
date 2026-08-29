/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.render.engine.rig.mesh.RigSkinBinding;

/**
 * Canonical skin assignments for body and armor mesh compilers. Armor is skinned by the same
 * skeleton as the body; these are not detached vanilla ModelPart pivots.
 */
public enum PlayerArmorRegion {
    HEAD(PlayerRigBone.NECK_UPPER, PlayerRigBone.HEAD),
    TORSO(PlayerRigBone.PELVIS, PlayerRigBone.SPINE_LOWER, PlayerRigBone.SPINE_UPPER, PlayerRigBone.CHEST),
    LEFT_UPPER_ARM(PlayerRigBone.LEFT_UPPER_ARM, PlayerRigBone.LEFT_UPPER_ARM_TWIST, PlayerRigBone.LEFT_ELBOW),
    LEFT_FOREARM(PlayerRigBone.LEFT_FOREARM, PlayerRigBone.LEFT_FOREARM_TWIST, PlayerRigBone.LEFT_WRIST),
    RIGHT_UPPER_ARM(PlayerRigBone.RIGHT_UPPER_ARM, PlayerRigBone.RIGHT_UPPER_ARM_TWIST, PlayerRigBone.RIGHT_ELBOW),
    RIGHT_FOREARM(PlayerRigBone.RIGHT_FOREARM, PlayerRigBone.RIGHT_FOREARM_TWIST, PlayerRigBone.RIGHT_WRIST),
    LEFT_THIGH(PlayerRigBone.LEFT_THIGH, PlayerRigBone.LEFT_THIGH_TWIST, PlayerRigBone.LEFT_KNEE),
    LEFT_SHIN(PlayerRigBone.LEFT_SHIN, PlayerRigBone.LEFT_SHIN_TWIST, PlayerRigBone.LEFT_ANKLE),
    RIGHT_THIGH(PlayerRigBone.RIGHT_THIGH, PlayerRigBone.RIGHT_THIGH_TWIST, PlayerRigBone.RIGHT_KNEE),
    RIGHT_SHIN(PlayerRigBone.RIGHT_SHIN, PlayerRigBone.RIGHT_SHIN_TWIST, PlayerRigBone.RIGHT_ANKLE),
    LEFT_FOOT(PlayerRigBone.LEFT_FOOT, PlayerRigBone.LEFT_TOE),
    RIGHT_FOOT(PlayerRigBone.RIGHT_FOOT, PlayerRigBone.RIGHT_TOE);

    private final PlayerRigBone[] chain;

    PlayerArmorRegion(PlayerRigBone... chain) {
        this.chain = chain;
    }

    public PlayerRigBone proximal() { return chain[0]; }
    public PlayerRigBone distal() { return chain[chain.length - 1]; }
    public PlayerRigBone[] bones() { return chain.clone(); }

    public RigSkinBinding skin(float blendStart, float blendEnd) {
        return RigSkinBinding.twoBone(
                PlayerRigDefinition.index(proximal()),
                PlayerRigDefinition.index(distal()),
                blendStart,
                blendEnd
        );
    }

    public RigSkinBinding rigidProximal() {
        return RigSkinBinding.rigid(PlayerRigDefinition.index(proximal()));
    }

    public RigSkinBinding defaultSkin() {
        int[] indices = new int[chain.length];
        float[] knots = new float[chain.length];
        for (int i = 0; i < chain.length; i++) {
            indices[i] = PlayerRigDefinition.index(chain[i]);
            knots[i] = chain.length == 1 ? 0f : (float) i / (chain.length - 1);
        }
        return RigSkinBinding.chain(indices, knots);
    }
}
