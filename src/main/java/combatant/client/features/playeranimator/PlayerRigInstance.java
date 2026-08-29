/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import combatant.client.render.engine.rig.core.RigInstance;
import combatant.client.render.engine.rig.core.RigPose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Typed player-facing wrapper around the common Combatant rig instance. All body, armor and item
 * animation mutates this single pose and therefore resolves through one hierarchy solve.
 */
public final class PlayerRigInstance {
    private final RigInstance rig = new RigInstance(PlayerRigDefinition.get());
    private final Vector3f vectorScratch = new Vector3f();
    private final Quaternionf rotationScratch = new Quaternionf();
    private final Quaternionf deltaRotationScratch = new Quaternionf();

    public PlayerRigInstance() {
        for (PlayerRigDeformer deformer : PlayerRigDeformer.values()) {
            rig.deform().define(deformer.definition());
        }
    }

    public RigInstance rig() {
        return rig;
    }

    public RigPose pose() {
        return rig.pose();
    }

    public PlayerRigInstance resetFrame() {
        rig.pose().resetToBindPose();
        for (PlayerRigDeformer deformer : PlayerRigDeformer.values()) {
            rig.deform().clearDynamic(deformer.channel());
        }
        return this;
    }

    public PlayerRigInstance resetBone(int boneIndex) {
        rig.pose().resetBoneToBindPose(boneIndex);
        return this;
    }

    public PlayerRigInstance setTranslation(int boneIndex, float x, float y, float z) {
        rig.pose().setTranslation(boneIndex, x, y, z);
        return this;
    }

    public PlayerRigInstance addTranslation(int boneIndex, float x, float y, float z) {
        rig.pose().translation(boneIndex, vectorScratch).add(x, y, z);
        rig.pose().setTranslation(boneIndex, vectorScratch);
        return this;
    }

    public PlayerRigInstance setRotation(int boneIndex, float xRadians, float yRadians, float zRadians) {
        rotationScratch.rotationXYZ(xRadians, yRadians, zRadians);
        rig.pose().setRotation(boneIndex, rotationScratch);
        return this;
    }

    public PlayerRigInstance addRotation(int boneIndex, float xRadians, float yRadians, float zRadians) {
        rig.pose().rotation(boneIndex, rotationScratch);
        deltaRotationScratch.rotationXYZ(xRadians, yRadians, zRadians);
        rotationScratch.mul(deltaRotationScratch).normalize();
        rig.pose().setRotation(boneIndex, rotationScratch);
        return this;
    }

    public PlayerRigInstance setRotationQuaternion(int boneIndex, float x, float y, float z, float w) {
        rotationScratch.set(x, y, z, w).normalize();
        rig.pose().setRotation(boneIndex, rotationScratch);
        return this;
    }

    public PlayerRigInstance setScale(int boneIndex, float x, float y, float z) {
        rig.pose().setScale(boneIndex, x, y, z);
        return this;
    }

    public PlayerRigInstance setBend(int deformChannel, float angleRadians, float falloff) {
        rig.deform().setBend(deformChannel, angleRadians, falloff);
        return this;
    }

    public PlayerRigInstance setTwist(int deformChannel, float angleRadians, float falloff) {
        rig.deform().setTwist(deformChannel, angleRadians, falloff);
        return this;
    }

    public PlayerRigInstance clearDeform(int deformChannel) {
        rig.deform().clearDynamic(deformChannel);
        return this;
    }

    public Matrix4f socketMatrix(PlayerRigSocket socket, Matrix4f destination) {
        if (socket == null || destination == null) {
            throw new IllegalArgumentException("Player rig socket and destination must not be null");
        }
        return rig.sockets().modelMatrix(PlayerRigDefinition.socketIndex(socket), destination);
    }

    public Matrix4f boneMatrix(PlayerRigBone bone, Matrix4f destination) {
        if (bone == null || destination == null) {
            throw new IllegalArgumentException("Player rig bone and destination must not be null");
        }
        return rig.modelMatrix(PlayerRigDefinition.index(bone), destination);
    }

    public void solve() {
        rig.solve();
    }
}
