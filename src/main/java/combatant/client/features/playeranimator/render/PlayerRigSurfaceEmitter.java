/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.render;

import combatant.client.features.playeranimator.PlayerAnimator;
import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.features.playeranimator.PlayerRigDefinition;
import combatant.client.features.playeranimator.PlayerRigInstance;
import combatant.client.render.effects.emitter.RigSurfaceEmitter;
import combatant.client.render.effects.emitter.SurfaceEmitterSample;
import combatant.client.render.engine.rig.mesh.RigAxis;
import combatant.client.render.engine.rig.mesh.RigCuboid;
import combatant.client.render.engine.rig.mesh.RigCuboidSubdivision;
import combatant.client.render.engine.rig.mesh.RigFaceUv;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigSkinBinding;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/** Current-backend player surface source backed by the anatomical rig. */
public final class PlayerRigSurfaceEmitter {
    public static final String SOURCE_ID = "combatant:player_anatomical_surface";
    private static final float MODEL_HEIGHT = 1.501f;
    private static final RigFaceUv UV = RigFaceUv.rectangle(0f, 0f, 1f, 1f);
    private static final RigMeshData SURFACE_MESH = buildSurfaceMesh();

    private PlayerRigSurfaceEmitter() {
    }

    public static SurfaceEmitterSample sample(AbstractClientPlayer player, long seed, int sampleIndex) {
        if (player == null) return new SurfaceEmitterSample(Vec3.ZERO, Vec3.ZERO, -1, -1);
        PlayerRigInstance playerRig = PlayerAnimator.instance(player);
        playerRig.solve();
        SurfaceEmitterSample local = RigSurfaceEmitter.sample(SURFACE_MESH, playerRig.rig(), seed, sampleIndex);
        return new SurfaceEmitterSample(
                toWorld(player, local.position()),
                normalToWorld(player, local.normal()),
                local.partIndex(),
                local.triangleIndex()
        );
    }

    private static Vec3 toWorld(AbstractClientPlayer player, Vec3 local) {
        double lx = -local.x;
        double ly = MODEL_HEIGHT - local.y;
        double lz = local.z;
        double yaw = Math.toRadians(180.0 - player.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double x = lx * cos + lz * sin;
        double z = lz * cos - lx * sin;
        return new Vec3(player.getX() + x, player.getY() + ly, player.getZ() + z);
    }

    private static Vec3 normalToWorld(AbstractClientPlayer player, Vec3 local) {
        double lx = -local.x;
        double ly = -local.y;
        double lz = local.z;
        double yaw = Math.toRadians(180.0 - player.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        Vec3 world = new Vec3(lx * cos + lz * sin, ly, lz * cos - lx * sin);
        return world.lengthSqr() > 1.0e-8 ? world.normalize() : Vec3.ZERO;
    }

    private static RigMeshData buildSurfaceMesh() {
        RigMeshData.Builder mesh = RigMeshData.builder();
        mesh.part(cuboid("head", -0.25f, -0.50f, -0.25f, 0.25f, 0.00f, 0.25f,
                rigid(PlayerRigBone.HEAD)));
        mesh.part(cuboid("body", -0.25f, 0.00f, -0.125f, 0.25f, 0.75f, 0.125f,
                RigSkinBinding.chain(indices(PlayerRigBone.CHEST, PlayerRigBone.SPINE_UPPER,
                        PlayerRigBone.SPINE_LOWER, PlayerRigBone.PELVIS),
                        new float[]{0f, 0.22f, 0.62f, 1f})));
        mesh.part(cuboid("left_arm", 0.25f, 0.125f, -0.125f, 0.50f, 0.875f, 0.125f,
                RigSkinBinding.banded(indices(PlayerRigBone.LEFT_UPPER_ARM, PlayerRigBone.LEFT_FOREARM,
                                PlayerRigBone.LEFT_HAND),
                        new float[]{0.50f, 0.91f}, new float[]{0.055f, 0.035f})));
        mesh.part(cuboid("right_arm", -0.50f, 0.125f, -0.125f, -0.25f, 0.875f, 0.125f,
                RigSkinBinding.banded(indices(PlayerRigBone.RIGHT_UPPER_ARM, PlayerRigBone.RIGHT_FOREARM,
                                PlayerRigBone.RIGHT_HAND),
                        new float[]{0.50f, 0.91f}, new float[]{0.055f, 0.035f})));
        mesh.part(cuboid("left_leg", 0.00f, 0.75f, -0.125f, 0.25f, 1.50f, 0.125f,
                RigSkinBinding.banded(indices(PlayerRigBone.PELVIS, PlayerRigBone.LEFT_THIGH,
                                PlayerRigBone.LEFT_SHIN, PlayerRigBone.LEFT_FOOT),
                        new float[]{0.10f, 0.50f, 0.94f}, new float[]{0.10f, 0.060f, 0.025f})));
        mesh.part(cuboid("right_leg", -0.25f, 0.75f, -0.125f, 0.00f, 1.50f, 0.125f,
                RigSkinBinding.banded(indices(PlayerRigBone.PELVIS, PlayerRigBone.RIGHT_THIGH,
                                PlayerRigBone.RIGHT_SHIN, PlayerRigBone.RIGHT_FOOT),
                        new float[]{0.10f, 0.50f, 0.94f}, new float[]{0.10f, 0.060f, 0.025f})));
        return mesh.build();
    }

    private static combatant.client.render.engine.rig.mesh.RigMeshPart cuboid(
            String name,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            RigSkinBinding skin
    ) {
        RigCuboid box = RigCuboid.builder(minX, minY, minZ, maxX, maxY, maxZ)
                .allFaces(UV)
                .build();
        return RigCuboidSubdivision.compile(name, box, RigAxis.Y, 6, skin, -1);
    }

    private static RigSkinBinding rigid(PlayerRigBone bone) {
        return RigSkinBinding.rigid(PlayerRigDefinition.index(bone));
    }

    private static int[] indices(PlayerRigBone... bones) {
        int[] out = new int[bones.length];
        for (int i = 0; i < bones.length; i++) out[i] = PlayerRigDefinition.index(bones[i]);
        return out;
    }
}
