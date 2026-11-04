/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import combatant.client.features.playeranimator.PlayerRigBone;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.geometry.ModelCompiledPrimitive;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigMeshPart;
import combatant.client.render.engine.rig.mesh.RigVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PlayerModelCompiler {
    public record Surface(RigMeshData mesh, boolean translucent) {}
    private PlayerModelCompiler() {}

    public static List<Surface> compile(ModelRuntimeAsset asset, PlayerModelProfile profile) {
        return compile(asset, profile, List.of());
    }

    /** Only selected OBJ source groups are used in a skin-backed armor overlay.
     *  The skin itself stays owned by Minecraft's live AvatarRenderState. */
    public static List<Surface> compile(ModelRuntimeAsset asset, PlayerModelProfile profile,
                                        List<String> renderGroups) {
        // Keep source-node boundaries for selective hand drawing, but submit the complete
        // opaque and translucent batches through only two collector nodes.
        List<RigMeshPart> solidParts = new ArrayList<>();
        List<RigMeshPart> glassParts = new ArrayList<>();
        java.util.Set<PlayerRigBone> bodyBones = java.util.EnumSet.noneOf(PlayerRigBone.class);
        int totalVertices = 0;
        for (var placement : asset.defaultSceneLayout().primitives()) {
            String name = asset.asset().nodes().get(placement.nodeIndex()).name();
            String group = name.split("/", 2)[0];
            if (!renderGroups.isEmpty() && !renderGroups.contains(group)) continue;
            PlayerRigBone bone = profile.bone(group);
            bodyBones.add(bone);
            ModelCompiledPrimitive primitive = asset.compilePrimitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) throw new IllegalArgumentException("Player model profile requires rigid source groups");
            totalVertices = Math.addExact(totalVertices, primitive.vertexCount());
            if (totalVertices > 500_000) throw new IllegalArgumentException("Player model exceeds 500000 vertices");
            float[] positions = primitive.positions(), normals = primitive.normals(), uv = primitive.texCoords0();
            if (uv == null) throw new IllegalArgumentException("Missing model UVs: " + name);
            Matrix4f transform = placement.localToAsset();
            Matrix3f normalTransform = transform.normal(new Matrix3f());
            boolean explicitBone = profile.bones().containsKey(group);
            Articulation articulation = explicitBone ? null : Articulation.forGroup(group);
            boolean torso = !explicitBone && group.equals("bodyModel");
            boolean hasLinks = profile.links().stream().anyMatch(link -> link.group().equals(group));
            boolean hasAttachments = profile.attachments().stream().anyMatch(attachment -> attachment.group().equals(group));
            Components components = (articulation != null || torso || hasLinks || hasAttachments)
                    ? new Components(positions, primitive.indices()) : null;
            RigVertex[] vertices = new RigVertex[primitive.vertexCount()];
            for (int i = 0; i < vertices.length; i++) {
                Vector3f p = transform.transformPosition(new Vector3f(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]));
                Vector3f n = normalTransform.transform(new Vector3f(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])).normalize();
                if (!p.isFinite() || !n.isFinite()) throw new IllegalArgumentException("Nonfinite model geometry");
                int first = bone.ordinal(), second = -1;
                float weight = 0;
                if (torso) {
                    // The torso is NOT a rigid CHEST attachment: its lower edge must follow
                    // the pelvis while the upper edge follows the spine/chest. The previous
                    // bind made the waist slide across the upper legs in flight and crouch.
                    float y = p.y;
                    if (y <= 0f) first = PlayerRigBone.CHEST.ordinal();
                    else if (y < 2.64f) {
                        first = PlayerRigBone.CHEST.ordinal();
                        second = PlayerRigBone.SPINE_UPPER.ordinal();
                        weight = smooth(y / 2.64f);
                    } else if (y < 7.44f) {
                        first = PlayerRigBone.SPINE_UPPER.ordinal();
                        second = PlayerRigBone.SPINE_LOWER.ordinal();
                        weight = smooth((y - 2.64f) / 4.8f);
                    } else if (y < 12f) {
                        first = PlayerRigBone.SPINE_LOWER.ordinal();
                        second = PlayerRigBone.PELVIS.ordinal();
                        weight = smooth((y - 7.44f) / 4.56f);
                    } else first = PlayerRigBone.PELVIS.ordinal();
                } else if (articulation != null) {
                    // Anatomical elbow is y=6, NOT y=8. Keep detached metal plates rigid;
                    // interpolate the material of a connected component across a hinge.
                    float low = components.minimum(i), high = components.maximum(i);
                    if (articulation.leg && p.y < 13.5f) {
                        first = PlayerRigBone.PELVIS.ordinal();
                        second = bone.ordinal();
                        weight = smooth((p.y - 11.4f) / 2.1f);
                    } else if (p.y >= (articulation.leg ? 22.4f : 10.4f)) {
                        // Preserve actual ankle/wrist motion in the imported boots and gloves.
                        // Previously all pixels below the knee or elbow were rigidly weighted
                        // to SHIN/FOREARM, so foot and hand rotations ignored the clothing.
                        first = articulation.lower.ordinal();
                        second = articulation.end.ordinal();
                        weight = low >= (articulation.leg ? 22.8f : 10.9f) ? 1f
                                : smooth((p.y - (articulation.leg ? 22.4f : 10.4f))
                                        / (articulation.leg ? 1.4f : 1.2f));
                    } else {
                        second = articulation.lower.ordinal();
                        weight = low < articulation.hinge - 1 && high > articulation.hinge + 1
                                ? smooth((p.y - articulation.hinge + 1) / 2)
                                : (low + high) * 0.5f >= articulation.hinge ? 1f : 0f;
                    }
                }
                // One compact rigid attachment wins over automatic body/limb weighting.
                // This handles plates and oxygen tanks which otherwise flex like clothing.
                boolean rigidAttachment = false;
                if (hasAttachments) {
                    float[] low = components.minBounds(i), high = components.maxBounds(i);
                    for (PlayerModelProfile.Attachment attachment : profile.attachments()) {
                        if (!attachment.matches(group, low, high)) continue;
                        first = PlayerRigBone.valueOf(attachment.bone()).ordinal();
                        second = -1;
                        weight = 0f;
                        rigidAttachment = true;
                        break;
                    }
                }
                if (!rigidAttachment && hasLinks) {
                    float[] low = components.minBounds(i), high = components.maxBounds(i);
                    for (PlayerModelProfile.Link link : profile.links()) {
                        if (!link.matches(group, low, high)) continue;
                        first = PlayerRigBone.valueOf(link.fromBone()).ordinal();
                        second = PlayerRigBone.valueOf(link.toBone()).ordinal();
                        weight = link.weight(p);
                        break;
                    }
                }
                // Clothing blends override automatic anatomy or soft links, but NEVER
                // override an explicitly rigid plate/pouch matched by source island.
                if (!rigidAttachment) {
                    for (PlayerModelProfile.Blend blend : profile.blends()) {
                        if (!blend.contains(group, p)) continue;
                        first = PlayerRigBone.valueOf(blend.fromBone()).ordinal();
                        second = PlayerRigBone.valueOf(blend.toBone()).ordinal();
                        weight = blend.weight(p);
                        break;
                    }
                }
                vertices[i] = new RigVertex(p.x / 16f, p.y / 16f, p.z / 16f,
                        uv[i * 2], uv[i * 2 + 1], n.x, n.y, n.z,
                        first, second, -1, -1, 1f - weight, weight, 0f, 0f,
                        0f, 0f, 0f, 0f, -1, 0);
            }
            int[] indices = primitive.indices();
            if (placement.mirroredWinding()) {
                for (int i = 0; i < indices.length; i += 3) {
                    int swap = indices[i + 1]; indices[i + 1] = indices[i + 2]; indices[i + 2] = swap;
                }
            }
            RigMeshPart part = new RigMeshPart(name, vertices, indices);
            String material = asset.asset().materials().get(primitive.material()).name();
            (material.toLowerCase(java.util.Locale.ROOT).contains("glass") ? glassParts : solidParts).add(part);
        }
        if (solidParts.isEmpty() && glassParts.isEmpty()) throw new IllegalArgumentException("Empty replacement model");
        if (renderGroups.isEmpty() && !bodyBones.containsAll(java.util.Set.of(PlayerRigBone.HEAD, PlayerRigBone.CHEST,
                PlayerRigBone.LEFT_UPPER_ARM, PlayerRigBone.RIGHT_UPPER_ARM, PlayerRigBone.LEFT_THIGH, PlayerRigBone.RIGHT_THIGH))) throw new IllegalArgumentException("Replacement is missing body geometry");
        List<Surface> surfaces = new ArrayList<>(2);
        if (!solidParts.isEmpty()) surfaces.add(new Surface(new RigMeshData(solidParts), false));
        if (!glassParts.isEmpty()) surfaces.add(new Surface(new RigMeshData(glassParts), true));
        return List.copyOf(surfaces);
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private record Articulation(PlayerRigBone lower, PlayerRigBone end, float hinge, boolean leg) {
        static Articulation forGroup(String group) {
            return switch (group) {
                case "leftArmModel" -> new Articulation(PlayerRigBone.LEFT_FOREARM, PlayerRigBone.LEFT_HAND, 6f, false);
                case "rightArmModel" -> new Articulation(PlayerRigBone.RIGHT_FOREARM, PlayerRigBone.RIGHT_HAND, 6f, false);
                case "leftLegModel" -> new Articulation(PlayerRigBone.LEFT_SHIN, PlayerRigBone.LEFT_FOOT, 18f, true);
                case "rightLegModel" -> new Articulation(PlayerRigBone.RIGHT_SHIN, PlayerRigBone.RIGHT_FOOT, 18f, true);
                default -> null;
            };
        }
    }

    private static final class Components {
        private record Point(float x, float y, float z) {}
        private final int[] parent;
        private final float[] min, max;
        private final float[][] boundsMin, boundsMax;
        Components(float[] positions, int[] indices) {
            int count = positions.length / 3;
            parent = new int[count]; min = new float[count]; max = new float[count];
            boundsMin = new float[count][3]; boundsMax = new float[count][3];
            java.util.Arrays.fill(min, Float.POSITIVE_INFINITY);
            java.util.Arrays.fill(max, Float.NEGATIVE_INFINITY);
            for (int i = 0; i < count; i++) {
                java.util.Arrays.fill(boundsMin[i], Float.POSITIVE_INFINITY);
                java.util.Arrays.fill(boundsMax[i], Float.NEGATIVE_INFINITY);
            }
            var welded = new java.util.HashMap<Point, Integer>();
            for (int i = 0; i < count; i++) {
                parent[i] = i;
                Point point = new Point(positions[3 * i], positions[3 * i + 1], positions[3 * i + 2]);
                Integer previous = welded.putIfAbsent(point, i);
                if (previous != null) join(i, previous);
            }
            for (int i = 0; i < indices.length; i += 3) {
                join(indices[i], indices[i + 1]); join(indices[i], indices[i + 2]);
            }
            for (int i = 0; i < count; i++) {
                int root = find(i);
                min[root] = Math.min(min[root], positions[3 * i + 1]);
                max[root] = Math.max(max[root], positions[3 * i + 1]);
                for (int axis = 0; axis < 3; axis++) {
                    float value = positions[3 * i + axis];
                    boundsMin[root][axis] = Math.min(boundsMin[root][axis], value);
                    boundsMax[root][axis] = Math.max(boundsMax[root][axis], value);
                }
            }
        }
        int find(int i) {
            while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
            return i;
        }
        void join(int a, int b) { parent[find(a)] = find(b); }
        float minimum(int vertex) { return min[find(vertex)]; }
        float maximum(int vertex) { return max[find(vertex)]; }
        float[] minBounds(int vertex) { return boundsMin[find(vertex)]; }
        float[] maxBounds(int vertex) { return boundsMax[find(vertex)]; }
    }
}
