/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import combatant.client.render.engine.rig.deform.RigDeformState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Per-instance solved rig state. Owns CPU matrices only.
 */
public final class RigInstance {
    private final RigDefinition definition;
    private final RigPose pose;
    private final Matrix4f[] localMatrices;
    private final Matrix4f[] modelMatrices;
    private final Matrix4f[] skinMatrices;
    private final RigSocketState sockets;
    private final RigDeformState deformState;
    private boolean poseDirty = true;

    public RigInstance(RigDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Rig definition must not be null");
        this.definition = definition;
        this.pose = new RigPose(definition, this::markPoseDirty);
        this.localMatrices = allocateMatrices(definition.boneCount());
        this.modelMatrices = allocateMatrices(definition.boneCount());
        this.skinMatrices = allocateMatrices(definition.boneCount());
        this.sockets = new RigSocketState(definition);
        this.deformState = new RigDeformState();
    }

    public RigDefinition definition() {
        return definition;
    }

    public RigPose pose() {
        return pose;
    }

    public RigSocketState sockets() {
        solve();
        return sockets;
    }

    public RigDeformState deform() {
        return deformState;
    }

    public boolean poseDirty() {
        return poseDirty;
    }

    public void markPoseDirty() {
        poseDirty = true;
    }

    public void solve() {
        if (!poseDirty) return;

        for (int index : definition.topologicalOrderRef()) {
            localMatrices[index].translationRotateScale(
                    pose.translationRef(index),
                    pose.rotationRef(index),
                    pose.scaleRef(index)
            );

            BoneDefinition bone = definition.boneRef(index);
            int parent = bone.parentIndex();
            if (parent >= 0) {
                modelMatrices[index].set(modelMatrices[parent]).mul(localMatrices[index]);
            } else {
                modelMatrices[index].set(localMatrices[index]);
            }

            skinMatrices[index].set(modelMatrices[index]).mul(bone.inverseBindRef());
        }

        sockets.solve(modelMatrices);
        poseDirty = false;
    }

    public Matrix4f localMatrix(int boneIndex, Matrix4f destination) {
        solve();
        checkBoneIndex(boneIndex);
        return destination.set(localMatrices[boneIndex]);
    }

    public Matrix4f modelMatrix(int boneIndex, Matrix4f destination) {
        solve();
        checkBoneIndex(boneIndex);
        return destination.set(modelMatrices[boneIndex]);
    }

    public Matrix4f skinMatrix(int boneIndex, Matrix4f destination) {
        solve();
        checkBoneIndex(boneIndex);
        return destination.set(skinMatrices[boneIndex]);
    }

    /** Read-only view used by uniform upload to avoid a Matrix4f copy per bone. */
    public Matrix4fc skinMatrixRef(int boneIndex) {
        solve();
        checkBoneIndex(boneIndex);
        return skinMatrices[boneIndex];
    }

    public Matrix4f modelMatrix(String boneName, Matrix4f destination) {
        return modelMatrix(definition.requireBoneIndex(boneName), destination);
    }

    public Matrix4f skinMatrix(String boneName, Matrix4f destination) {
        return skinMatrix(definition.requireBoneIndex(boneName), destination);
    }

    private void checkBoneIndex(int boneIndex) {
        if (boneIndex < 0 || boneIndex >= definition.boneCount()) {
            throw new IndexOutOfBoundsException("Bone index " + boneIndex + " outside [0, " + (definition.boneCount() - 1) + "]");
        }
    }

    private static Matrix4f[] allocateMatrices(int count) {
        Matrix4f[] matrices = new Matrix4f[count];
        for (int i = 0; i < count; i++) matrices[i] = new Matrix4f();
        return matrices;
    }
}
