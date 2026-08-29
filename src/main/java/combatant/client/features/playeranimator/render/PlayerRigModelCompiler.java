/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.render;

import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.features.playeranimator.PlayerRigDefinition;
import combatant.client.features.playeranimator.PlayerRigDeformer;
import combatant.client.mixins.accessors.ModelPartAccessor;
import combatant.client.render.engine.rig.mesh.RigAxis;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigMeshPart;
import combatant.client.render.engine.rig.mesh.RigSkinBinding;
import combatant.client.render.engine.rig.mesh.RigVertex;
import combatant.client.render.engine.rig.mesh.VanillaRigCubeCompiler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Converts vanilla player/armor cubes once, preserving baked UVs, hierarchy pivots and dilation. */
public final class PlayerRigModelCompiler {
    private static final int SECTIONS = 8;

    private PlayerRigModelCompiler() {
    }

    public static RigMeshData compile(HumanoidModel<?> model, boolean includeHiddenParts) {
        if (model == null) throw new IllegalArgumentException("Humanoid model must not be null");
        List<RigMeshPart> parts = new ArrayList<>();

        append(parts, "head", model.head, null, rigid(PlayerRigBone.HEAD), -1, includeHiddenParts);
        // In 26.2 hat is a child of head. Its own initial pose is ZERO; the head parent pivot must
        // be included or the wear cube is compiled around model origin.
        append(parts, "hat", model.hat, model.head, rigid(PlayerRigBone.HEAD), -1, includeHiddenParts);
        append(parts, "body", model.body, null, torso(), PlayerRigDeformer.SPINE.channel(), includeHiddenParts);
        append(parts, "right_arm", model.rightArm, null, rightArm(), -1, includeHiddenParts);
        append(parts, "left_arm", model.leftArm, null, leftArm(), -1, includeHiddenParts);
        append(parts, "right_leg", model.rightLeg, null, rightLeg(), -1, includeHiddenParts);
        append(parts, "left_leg", model.leftLeg, null, leftLeg(), -1, includeHiddenParts);

        if (model instanceof PlayerModel player) {
            // PlayerModel 26.2 stores every wear layer below its corresponding base part.
            append(parts, "jacket", player.jacket, model.body, torso(), PlayerRigDeformer.SPINE.channel(), includeHiddenParts);
            append(parts, "right_sleeve", player.rightSleeve, model.rightArm, rightArm(), -1, includeHiddenParts);
            append(parts, "left_sleeve", player.leftSleeve, model.leftArm, leftArm(), -1, includeHiddenParts);
            append(parts, "right_pants", player.rightPants, model.rightLeg, rightLeg(), -1, includeHiddenParts);
            append(parts, "left_pants", player.leftPants, model.leftLeg, leftLeg(), -1, includeHiddenParts);
        }

        if (parts.isEmpty()) throw new IllegalArgumentException("Humanoid model has no visible rig geometry");
        return new RigMeshData(parts);
    }

    private static void append(List<RigMeshPart> output, String name, ModelPart part, ModelPart parent,
                               RigSkinBinding skin, int deformId, boolean includeHidden) {
        if (part == null || (!includeHidden && (!part.visible || part.skipDraw))) return;
        List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) part).combatant$getCubes();
        if (cubes == null || cubes.isEmpty()) return;

        Matrix4f bindTransform = new Matrix4f();
        if (parent != null) bindTransform.mul(poseMatrix(parent.getInitialPose()));
        bindTransform.mul(poseMatrix(part.getInitialPose()));

        for (int i = 0; i < cubes.size(); i++) {
            RigMeshPart compiled = VanillaRigCubeCompiler.compile(
                    name + '_' + i, cubes.get(i), RigAxis.Y, SECTIONS, skin, deformId
            );
            output.add(transform(compiled, bindTransform));
        }
    }

    private static Matrix4f poseMatrix(PartPose pose) {
        return new Matrix4f().translationRotateScale(
                new Vector3f(pose.x() / 16f, pose.y() / 16f, pose.z() / 16f),
                new Quaternionf().rotationXYZ(pose.xRot(), pose.yRot(), pose.zRot()),
                new Vector3f(pose.xScale(), pose.yScale(), pose.zScale())
        );
    }

    private static RigMeshPart transform(RigMeshPart part, Matrix4f transform) {
        RigVertex[] vertices = new RigVertex[part.vertexCount()];
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < vertices.length; i++) {
            RigVertex source = part.vertex(i);
            transform.transformPosition(source.x(), source.y(), source.z(), position);
            transform.transformDirection(source.normalX(), source.normalY(), source.normalZ(), normal).normalize();
            vertices[i] = new RigVertex(
                    position.x, position.y, position.z,
                    source.u(), source.v(), normal.x, normal.y, normal.z, source.colorArgb(),
                    source.bone0(), source.bone1(), source.bone2(), source.bone3(),
                    source.weight0(), source.weight1(), source.weight2(), source.weight3(),
                    source.deformU(), source.deformLateral(), source.deformDepth(), source.deformAux(),
                    source.deformId(), source.deformFlags()
            );
        }
        int[] indices = new int[part.indexCount()];
        for (int i = 0; i < indices.length; i++) indices[i] = part.index(i);
        return new RigMeshPart(part.name(), vertices, indices);
    }

    private static RigSkinBinding rigid(PlayerRigBone bone) {
        return RigSkinBinding.rigid(PlayerRigDefinition.index(bone));
    }

    private static RigSkinBinding torso() {
        return RigSkinBinding.chain(
                indices(PlayerRigBone.CHEST, PlayerRigBone.SPINE_UPPER, PlayerRigBone.SPINE_LOWER, PlayerRigBone.PELVIS),
                new float[]{0f, 0.22f, 0.62f, 1f}
        );
    }

    /** Upper arm -> forearm -> hand, with only narrow soft bands around elbow and wrist. */
    private static RigSkinBinding leftArm() {
        return RigSkinBinding.banded(
                indices(PlayerRigBone.LEFT_UPPER_ARM, PlayerRigBone.LEFT_FOREARM, PlayerRigBone.LEFT_HAND),
                new float[]{0.50f, 0.91f},
                new float[]{0.055f, 0.035f}
        );
    }

    private static RigSkinBinding rightArm() {
        return RigSkinBinding.banded(
                indices(PlayerRigBone.RIGHT_UPPER_ARM, PlayerRigBone.RIGHT_FOREARM, PlayerRigBone.RIGHT_HAND),
                new float[]{0.50f, 0.91f},
                new float[]{0.055f, 0.035f}
        );
    }

    /**
     * Pelvis -> thigh -> shin -> foot. A narrow pelvis/thigh blend at the very top keeps the butt
     * seam attached while the hip rotates; the old fully-thigh-rigid top produced a visible break.
     * Knees/ankles remain narrow bands so the Minecraft limb still reads as articulated, not rubber.
     */
    private static RigSkinBinding leftLeg() {
        return RigSkinBinding.banded(
                indices(PlayerRigBone.PELVIS, PlayerRigBone.LEFT_THIGH, PlayerRigBone.LEFT_SHIN, PlayerRigBone.LEFT_FOOT),
                new float[]{0.10f, 0.50f, 0.94f},
                new float[]{0.10f, 0.060f, 0.025f}
        );
    }

    private static RigSkinBinding rightLeg() {
        return RigSkinBinding.banded(
                indices(PlayerRigBone.PELVIS, PlayerRigBone.RIGHT_THIGH, PlayerRigBone.RIGHT_SHIN, PlayerRigBone.RIGHT_FOOT),
                new float[]{0.10f, 0.50f, 0.94f},
                new float[]{0.10f, 0.060f, 0.025f}
        );
    }

    private static int[] indices(PlayerRigBone... bones) {
        int[] indices = new int[bones.length];
        for (int i = 0; i < bones.length; i++) indices[i] = PlayerRigDefinition.index(bones[i]);
        return indices;
    }
}
