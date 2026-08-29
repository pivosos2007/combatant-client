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

/** Converts vanilla player/armor cubes once, preserving their baked UVs and dilation. */
public final class PlayerRigModelCompiler {
    private static final int SECTIONS = 6;

    private PlayerRigModelCompiler() {
    }

    public static RigMeshData compile(HumanoidModel<?> model, boolean includeHiddenParts) {
        if (model == null) throw new IllegalArgumentException("Humanoid model must not be null");
        List<RigMeshPart> parts = new ArrayList<>();

        append(parts, "head", model.head, rigid(PlayerRigBone.HEAD), -1, includeHiddenParts);
        append(parts, "hat", model.hat, rigid(PlayerRigBone.HEAD), -1, includeHiddenParts);
        append(parts, "body", model.body, torso(), PlayerRigDeformer.SPINE.channel(), includeHiddenParts);
        append(parts, "right_arm", model.rightArm, rightArm(), PlayerRigDeformer.RIGHT_UPPER_ARM.channel(), includeHiddenParts);
        append(parts, "left_arm", model.leftArm, leftArm(), PlayerRigDeformer.LEFT_UPPER_ARM.channel(), includeHiddenParts);
        append(parts, "right_leg", model.rightLeg, rightLeg(), PlayerRigDeformer.RIGHT_THIGH.channel(), includeHiddenParts);
        append(parts, "left_leg", model.leftLeg, leftLeg(), PlayerRigDeformer.LEFT_THIGH.channel(), includeHiddenParts);

        if (model instanceof PlayerModel player) {
            append(parts, "jacket", player.jacket, torso(), PlayerRigDeformer.SPINE.channel(), includeHiddenParts);
            append(parts, "right_sleeve", player.rightSleeve, rightArm(), PlayerRigDeformer.RIGHT_UPPER_ARM.channel(), includeHiddenParts);
            append(parts, "left_sleeve", player.leftSleeve, leftArm(), PlayerRigDeformer.LEFT_UPPER_ARM.channel(), includeHiddenParts);
            append(parts, "right_pants", player.rightPants, rightLeg(), PlayerRigDeformer.RIGHT_THIGH.channel(), includeHiddenParts);
            append(parts, "left_pants", player.leftPants, leftLeg(), PlayerRigDeformer.LEFT_THIGH.channel(), includeHiddenParts);
        }

        if (parts.isEmpty()) throw new IllegalArgumentException("Humanoid model has no visible rig geometry");
        return new RigMeshData(parts);
    }

    private static void append(List<RigMeshPart> output, String name, ModelPart part,
                               RigSkinBinding skin, int deformId, boolean includeHidden) {
        if (part == null || (!includeHidden && (!part.visible || part.skipDraw))) return;
        List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) part).combatant$getCubes();
        if (cubes == null || cubes.isEmpty()) return;

        PartPose bind = part.getInitialPose();
        Matrix4f bindTransform = new Matrix4f().translationRotateScale(
                new Vector3f(bind.x() / 16f, bind.y() / 16f, bind.z() / 16f),
                new Quaternionf().rotationXYZ(bind.xRot(), bind.yRot(), bind.zRot()),
                new Vector3f(bind.xScale(), bind.yScale(), bind.zScale())
        );
        for (int i = 0; i < cubes.size(); i++) {
            RigMeshPart compiled = VanillaRigCubeCompiler.compile(
                    name + '_' + i, cubes.get(i), RigAxis.Y, SECTIONS, skin, deformId
            );
            output.add(transform(compiled, bindTransform));
        }
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
        return chain(
                new PlayerRigBone[]{PlayerRigBone.CHEST, PlayerRigBone.SPINE_UPPER, PlayerRigBone.SPINE_LOWER, PlayerRigBone.PELVIS},
                new float[]{0f, 0.25f, 0.67f, 1f}
        );
    }

    private static RigSkinBinding leftArm() {
        return limb(PlayerRigBone.LEFT_UPPER_ARM, PlayerRigBone.LEFT_ELBOW,
                PlayerRigBone.LEFT_FOREARM_TWIST, PlayerRigBone.LEFT_HAND);
    }

    private static RigSkinBinding rightArm() {
        return limb(PlayerRigBone.RIGHT_UPPER_ARM, PlayerRigBone.RIGHT_ELBOW,
                PlayerRigBone.RIGHT_FOREARM_TWIST, PlayerRigBone.RIGHT_HAND);
    }

    private static RigSkinBinding leftLeg() {
        return limb(PlayerRigBone.LEFT_THIGH, PlayerRigBone.LEFT_KNEE,
                PlayerRigBone.LEFT_SHIN_TWIST, PlayerRigBone.LEFT_FOOT);
    }

    private static RigSkinBinding rightLeg() {
        return limb(PlayerRigBone.RIGHT_THIGH, PlayerRigBone.RIGHT_KNEE,
                PlayerRigBone.RIGHT_SHIN_TWIST, PlayerRigBone.RIGHT_FOOT);
    }

    private static RigSkinBinding limb(PlayerRigBone a, PlayerRigBone b, PlayerRigBone c, PlayerRigBone d) {
        return chain(new PlayerRigBone[]{a, b, c, d}, new float[]{0f, 0.48f, 0.76f, 1f});
    }

    private static RigSkinBinding chain(PlayerRigBone[] bones, float[] knots) {
        int[] indices = new int[bones.length];
        for (int i = 0; i < bones.length; i++) indices[i] = PlayerRigDefinition.index(bones[i]);
        return RigSkinBinding.chain(indices, knots);
    }
}
