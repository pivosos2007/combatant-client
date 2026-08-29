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

    // Allocation-free two-bone reach scratch. Used by mouth/item and two-handed weapon poses so
    // the hand follows the actual animated target bone instead of relying on guessed Euler angles.
    private final Matrix4f ikParentMatrix = new Matrix4f();
    private final Matrix4f ikInverseParentMatrix = new Matrix4f();
    private final Matrix4f ikTargetMatrix = new Matrix4f();
    private final Matrix4f ikUpperMatrix = new Matrix4f();
    private final Vector3f ikTargetPoint = new Vector3f();
    private final Vector3f ikShoulderPoint = new Vector3f();
    private final Vector3f ikTargetDirection = new Vector3f();
    private final Vector3f ikHint = new Vector3f();
    private final Vector3f ikPerpendicular = new Vector3f();
    private final Vector3f ikElbowPoint = new Vector3f();
    private final Vector3f ikUpperDirection = new Vector3f();
    private final Vector3f ikLowerDirection = new Vector3f();
    private final Vector3f ikLowerLocalDirection = new Vector3f();
    private final Quaternionf ikUpperRotation = new Quaternionf();
    private final Quaternionf ikElbowRotation = new Quaternionf();
    private final Quaternionf ikInverseUpperRotation = new Quaternionf();
    private final Quaternionf ikBlendedRotation = new Quaternionf();

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

    /**
     * Solves the anatomical upper-arm/elbow chain toward a point expressed in another rig bone's
     * local space. The target therefore follows an animated head/hand exactly (mouth, crossbow,
     * etc.) instead of being approximated by a fixed Euler pose.
     *
     * The hint is expressed in the upper arm parent's local space and selects the elbow side of the
     * two-bone solution. This method is intentionally player-specific: the split arm lengths are
     * fixed by {@link PlayerRigBone} and are both six model pixels.
     */
    public PlayerRigInstance reachHandToBone(int upperArmIndex, int targetBoneIndex,
                                             float targetX, float targetY, float targetZ,
                                             float hintX, float hintY, float hintZ,
                                             float weight) {
        int leftUpper = PlayerRigDefinition.index(PlayerRigBone.LEFT_UPPER_ARM);
        int rightUpper = PlayerRigDefinition.index(PlayerRigBone.RIGHT_UPPER_ARM);
        final int elbowIndex;
        if (upperArmIndex == leftUpper) {
            elbowIndex = PlayerRigDefinition.index(PlayerRigBone.LEFT_ELBOW);
        } else if (upperArmIndex == rightUpper) {
            elbowIndex = PlayerRigDefinition.index(PlayerRigBone.RIGHT_ELBOW);
        } else {
            return this;
        }
        if (targetBoneIndex < 0 || targetBoneIndex >= rig.definition().boneCount()) return this;

        int parentIndex = rig.definition().bone(upperArmIndex).parentIndex();
        if (parentIndex < 0) return this;

        // Resolve all pose commands that preceded this reach command. This is what makes a mouth
        // target follow the already-curved neck/head and makes the support hand follow the already
        // aimed crossbow hand in the same JS evaluation.
        rig.solve();
        rig.modelMatrix(parentIndex, ikParentMatrix);
        ikInverseParentMatrix.set(ikParentMatrix).invert();

        ikTargetPoint.set(targetX, targetY, targetZ);
        rig.modelMatrix(targetBoneIndex, ikTargetMatrix).transformPosition(ikTargetPoint);
        ikInverseParentMatrix.transformPosition(ikTargetPoint);

        rig.modelMatrix(upperArmIndex, ikUpperMatrix).getTranslation(ikShoulderPoint);
        ikInverseParentMatrix.transformPosition(ikShoulderPoint);

        ikTargetDirection.set(ikTargetPoint).sub(ikShoulderPoint);
        float rawDistance = ikTargetDirection.length();
        if (!(rawDistance > 1.0e-6f) || !Float.isFinite(rawDistance)) return this;
        ikTargetDirection.mul(1.0f / rawDistance);

        final float upperLength = 6.0f * PlayerRigDefinition.MODEL_UNIT;
        final float lowerLength = 6.0f * PlayerRigDefinition.MODEL_UNIT;
        final float maxReach = upperLength + lowerLength - 1.0e-4f;
        final float minReach = Math.abs(upperLength - lowerLength) + 1.0e-4f;
        float distance = Math.max(minReach, Math.min(maxReach, rawDistance));
        // Use the clamped point for the actual triangle as well; otherwise an unreachable target
        // would feed the raw far-away point into the lower-segment direction and distort the elbow.
        ikTargetPoint.set(ikShoulderPoint).fma(distance, ikTargetDirection);

        // Project the elbow hint onto the plane perpendicular to the target direction. If an addon
        // supplies a degenerate hint, select a stable side rather than allowing a frame flip.
        ikHint.set(hintX, hintY, hintZ);
        if (ikHint.lengthSquared() < 1.0e-8f) ikHint.set(1f, 0.35f, 0.15f);
        ikPerpendicular.set(ikHint)
                .fma(-ikHint.dot(ikTargetDirection), ikTargetDirection);
        if (ikPerpendicular.lengthSquared() < 1.0e-8f) {
            ikPerpendicular.set(0f, 0f, 1f)
                    .fma(-ikTargetDirection.z, ikTargetDirection);
            if (ikPerpendicular.lengthSquared() < 1.0e-8f) {
                ikPerpendicular.set(1f, 0f, 0f)
                        .fma(-ikTargetDirection.x, ikTargetDirection);
            }
        }
        ikPerpendicular.normalize();

        float along = (upperLength * upperLength - lowerLength * lowerLength + distance * distance)
                / (2.0f * distance);
        float heightSq = Math.max(0f, upperLength * upperLength - along * along);
        float height = (float) Math.sqrt(heightSq);

        ikElbowPoint.set(ikShoulderPoint)
                .fma(along, ikTargetDirection)
                .fma(height, ikPerpendicular);
        ikUpperDirection.set(ikElbowPoint).sub(ikShoulderPoint).normalize();
        ikLowerDirection.set(ikTargetPoint).sub(ikElbowPoint).normalize();

        ikUpperRotation.rotationTo(
                0f, 1f, 0f,
                ikUpperDirection.x, ikUpperDirection.y, ikUpperDirection.z
        ).normalize();
        ikInverseUpperRotation.set(ikUpperRotation).conjugate();
        ikInverseUpperRotation.transform(ikLowerDirection, ikLowerLocalDirection).normalize();
        ikElbowRotation.rotationTo(
                0f, 1f, 0f,
                ikLowerLocalDirection.x, ikLowerLocalDirection.y, ikLowerLocalDirection.z
        ).normalize();

        float blend = Math.max(0f, Math.min(1f, weight));
        if (blend <= 1.0e-5f) return this;
        if (blend < 0.99999f) {
            rig.pose().rotation(upperArmIndex, ikBlendedRotation).slerp(ikUpperRotation, blend).normalize();
            rig.pose().setRotation(upperArmIndex, ikBlendedRotation);
            rig.pose().rotation(elbowIndex, ikBlendedRotation).slerp(ikElbowRotation, blend).normalize();
            rig.pose().setRotation(elbowIndex, ikBlendedRotation);
        } else {
            rig.pose().setRotation(upperArmIndex, ikUpperRotation);
            rig.pose().setRotation(elbowIndex, ikElbowRotation);
        }
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
