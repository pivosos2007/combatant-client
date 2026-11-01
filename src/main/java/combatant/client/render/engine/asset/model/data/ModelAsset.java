/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.data;

import net.minecraft.resources.Identifier;
import combatant.client.render.engine.asset.model.animation.AnimationChannel;
import combatant.client.render.engine.asset.model.animation.AnimationClip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelAsset {
    private final Identifier id;
    private final List<ModelScene> scenes;
    private final int defaultScene;
    private final List<ModelNode> nodes;
    private final List<ModelMesh> meshes;
    private final List<ModelMaterial> materials;
    private final List<ModelTexture> textures;
    private final List<ModelSkin> skins;
    private final List<AnimationClip> animations;
    private final Map<String, AnimationClip> animationsByName;
    private final List<String> animationNames;
    private final List<String> extensionsUsed;
    private final List<String> extensionsRequired;

    public ModelAsset(Identifier id, List<ModelScene> scenes, List<ModelNode> nodes,
                     List<ModelMesh> meshes, List<ModelMaterial> materials,
                     List<ModelTexture> textures, List<ModelSkin> skins,
                     List<AnimationClip> animations, List<String> extensionsUsed,
                     List<String> extensionsRequired) {
        this(id, scenes, nodes, meshes, materials, textures, skins, animations,
            extensionsUsed, extensionsRequired, 0);
    }

    public ModelAsset(Identifier id, List<ModelScene> scenes, List<ModelNode> nodes,
                     List<ModelMesh> meshes, List<ModelMaterial> materials,
                     List<ModelTexture> textures, List<ModelSkin> skins,
                     List<AnimationClip> animations, List<String> extensionsUsed,
                     List<String> extensionsRequired, int defaultScene) {
        this.id = Objects.requireNonNull(id, "id");
        this.scenes = List.copyOf(Objects.requireNonNull(scenes, "scenes"));
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.meshes = List.copyOf(Objects.requireNonNull(meshes, "meshes"));
        this.materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        this.textures = List.copyOf(Objects.requireNonNull(textures, "textures"));
        this.skins = List.copyOf(Objects.requireNonNull(skins, "skins"));
        this.animations = List.copyOf(Objects.requireNonNull(animations, "animations"));
        this.extensionsUsed = List.copyOf(Objects.requireNonNull(extensionsUsed, "extensionsUsed"));
        this.extensionsRequired = List.copyOf(Objects.requireNonNull(extensionsRequired, "extensionsRequired"));
        if (!this.scenes.isEmpty() && (defaultScene < 0 || defaultScene >= this.scenes.size())) {
            throw new IllegalArgumentException("Default scene index is out of range");
        }
        this.defaultScene = this.scenes.isEmpty() ? -1 : defaultScene;
        validateReferences();

        Map<String, AnimationClip> byName = new LinkedHashMap<>();
        for (int i = 0; i < this.animations.size(); i++) {
            AnimationClip clip = this.animations.get(i);
            String baseName = clip.name().isBlank() ? "animation_" + i : clip.name();
            String effectiveName = baseName;
            for (int suffix = 1; byName.containsKey(effectiveName); suffix++) {
                effectiveName = baseName + "#" + suffix;
            }
            byName.put(effectiveName, clip);
        }
        this.animationsByName = Map.copyOf(byName);
        this.animationNames = List.copyOf(byName.keySet());
    }

    private void validateReferences() {
        if (materials.isEmpty()) throw new IllegalArgumentException("Asset must contain at least a default material");
        for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
            java.util.HashSet<Integer> roots = new java.util.HashSet<>();
            for (int root : scenes.get(sceneIndex).roots()) {
                requireIndex(root, nodes.size(), "scene root", sceneIndex);
                if (!roots.add(root)) throw new IllegalArgumentException("Scene " + sceneIndex + " contains duplicate root node " + root);
                if (nodes.get(root).parent() >= 0) {
                    throw new IllegalArgumentException("Scene root " + root + " is also a child node");
                }
            }
        }
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            ModelNode node = nodes.get(nodeIndex);
            if (node.parent() >= 0) requireIndex(node.parent(), nodes.size(), "parent node", nodeIndex);
            java.util.HashSet<Integer> children = new java.util.HashSet<>();
            for (int child : node.children()) {
                requireIndex(child, nodes.size(), "child node", nodeIndex);
                if (!children.add(child)) throw new IllegalArgumentException("Node " + nodeIndex + " contains duplicate child " + child);
                if (nodes.get(child).parent() != nodeIndex) {
                    throw new IllegalArgumentException("Node " + nodeIndex + " lists child " + child + " but the child's parent differs");
                }
            }
            for (int mesh : node.meshes()) requireIndex(mesh, meshes.size(), "mesh", nodeIndex);
            if (node.skin() >= 0) requireIndex(node.skin(), skins.size(), "skin", nodeIndex);
            if (node.morphWeightCount() > 0) {
                if (node.meshes().length == 0) {
                    throw new IllegalArgumentException("Node " + nodeIndex + " defines morph weights without a mesh");
                }
                for (int mesh : node.meshes()) {
                    int targetCount = meshMorphTargetCount(mesh);
                    if (targetCount != node.morphWeightCount()) {
                        throw new IllegalArgumentException("Node " + nodeIndex
                            + " morph weight count does not match mesh " + mesh);
                    }
                }
            }
        }
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            int parent = nodes.get(nodeIndex).parent();
            if (parent >= 0 && !contains(nodes.get(parent).children(), nodeIndex)) {
                throw new IllegalArgumentException("Node " + nodeIndex + " names parent " + parent + " but the parent does not list it as a child");
            }
        }
        validateNoParentCycles();
        for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            ModelMesh mesh = meshes.get(meshIndex);
            int morphTargetCount = -1;
            for (ModelPrimitive primitive : mesh.primitives()) {
                requireIndex(primitive.material(), materials.size(), "material", meshIndex);
                validateMaterialCoordinates(
                    primitive,
                    materials.get(primitive.material()),
                    meshIndex
                );
                if (morphTargetCount < 0) morphTargetCount = primitive.morphTargets().size();
                else if (primitive.morphTargets().size() != morphTargetCount) {
                    throw new IllegalArgumentException("All primitives in mesh " + meshIndex + " must have the same morph target count");
                }
            }
            float[] defaultWeights = mesh.defaultMorphWeights();
            if (defaultWeights != null && (morphTargetCount < 0 || defaultWeights.length != morphTargetCount)) {
                throw new IllegalArgumentException("Mesh " + meshIndex + " morph weight count does not match its targets");
            }
        }
        for (int materialIndex = 0; materialIndex < materials.size(); materialIndex++) {
            ModelMaterial material = materials.get(materialIndex);
            requireOptionalIndex(material.baseColorTexture(), textures.size(), "base color texture", materialIndex);
            requireOptionalIndex(
                material.metallicRoughnessTexture(),
                textures.size(),
                "metallic-roughness texture",
                materialIndex
            );
            requireOptionalIndex(material.normalTexture(), textures.size(), "normal texture", materialIndex);
            requireOptionalIndex(material.occlusionTexture(), textures.size(), "occlusion texture", materialIndex);
            requireOptionalIndex(material.emissiveTexture(), textures.size(), "emissive texture", materialIndex);
        }
        for (int skinIndex = 0; skinIndex < skins.size(); skinIndex++) {
            ModelSkin skin = skins.get(skinIndex);
            requireOptionalIndex(skin.skeletonRoot(), nodes.size(), "skeleton root", skinIndex);
            for (int joint : skin.joints()) requireIndex(joint, nodes.size(), "skin joint", skinIndex);
        }
        for (int animationIndex = 0; animationIndex < animations.size(); animationIndex++) {
            java.util.HashSet<String> animatedTargets = new java.util.HashSet<>();
            for (AnimationChannel channel : animations.get(animationIndex).channels()) {
                requireIndex(channel.node(), nodes.size(), "animation node", animationIndex);
                String animatedTarget = channel.node() + ":" + channel.path().name();
                if (!animatedTargets.add(animatedTarget)) {
                    throw new IllegalArgumentException("Animation " + animationIndex
                        + " contains duplicate channels for node " + channel.node() + " path " + channel.path());
                }
                if (channel.path() != combatant.client.render.engine.asset.model.animation.AnimationPath.WEIGHTS
                    && nodes.get(channel.node()).matrix() != null) {
                    throw new IllegalArgumentException("Animation " + animationIndex
                        + " targets TRS on matrix node " + channel.node());
                }
                if (channel.path() == combatant.client.render.engine.asset.model.animation.AnimationPath.WEIGHTS) {
                    ModelNode targetNode = nodes.get(channel.node());
                    if (targetNode.meshes().length == 0) {
                        throw new IllegalArgumentException("Morph animation targets node without a mesh: " + channel.node());
                    }
                    for (int mesh : targetNode.meshes()) {
                        if (meshMorphTargetCount(mesh) != channel.sampler().components()) {
                            throw new IllegalArgumentException("Morph animation component count does not match mesh " + mesh);
                        }
                    }
                }
            }
        }
        validateSkinnedMeshes();
    }

    private int meshMorphTargetCount(int meshIndex) {
        ModelMesh mesh = meshes.get(meshIndex);
        return mesh.primitives().isEmpty() ? 0 : mesh.primitives().get(0).morphTargets().size();
    }

    private void validateSkinnedMeshes() {
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            ModelNode node = nodes.get(nodeIndex);
            if (node.skin() < 0) continue;
            int jointCount = skins.get(node.skin()).joints().length;
            for (int meshIndex : node.meshes()) {
                for (ModelPrimitive primitive : meshes.get(meshIndex).primitives()) {
                    int[] joints = primitive.joints();
                    float[] weights = primitive.weights();
                    if (joints == null || weights == null) {
                        throw new IllegalArgumentException("Skinned node " + nodeIndex + " uses a primitive without JOINTS_0/WEIGHTS_0");
                    }
                    for (int joint : joints) {
                        if (joint >= jointCount) {
                            throw new IllegalArgumentException("Primitive joint index " + joint + " exceeds skin joint count " + jointCount);
                        }
                    }
                }
            }
        }
    }

    private void validateNoParentCycles() {
        byte[] state = new byte[nodes.size()];
        for (int start = 0; start < nodes.size(); start++) {
            int current = start;
            while (current >= 0 && state[current] == 0) {
                state[current] = 1;
                current = nodes.get(current).parent();
            }
            if (current >= 0 && state[current] == 1) throw new IllegalArgumentException("Cycle in model node hierarchy");
            current = start;
            while (current >= 0 && state[current] == 1) {
                state[current] = 2;
                current = nodes.get(current).parent();
            }
        }
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) if (value == expected) return true;
        return false;
    }

    private static void requireIndex(int index, int size, String label, int owner) {
        if (index < 0 || index >= size) throw new IllegalArgumentException(label + " index " + index + " is invalid for item " + owner);
    }

    private static void requireOptionalIndex(int index, int size, String label, int owner) {
        if (index >= 0) requireIndex(index, size, label, owner);
    }

    private static void validateMaterialCoordinates(
        ModelPrimitive primitive,
        ModelMaterial material,
        int meshIndex
    ) {
        requireCoordinates(
            primitive,
            material.baseColorTexture(),
            material.baseColorTexCoord(),
            "base color",
            meshIndex
        );
        requireCoordinates(
            primitive,
            material.metallicRoughnessTexture(),
            material.metallicRoughnessTexCoord(),
            "metallic-roughness",
            meshIndex
        );
        requireCoordinates(
            primitive,
            material.normalTexture(),
            material.normalTexCoord(),
            "normal",
            meshIndex
        );
        requireCoordinates(
            primitive,
            material.occlusionTexture(),
            material.occlusionTexCoord(),
            "occlusion",
            meshIndex
        );
        requireCoordinates(
            primitive,
            material.emissiveTexture(),
            material.emissiveTexCoord(),
            "emissive",
            meshIndex
        );
    }

    private static void requireCoordinates(
        ModelPrimitive primitive,
        int texture,
        int texCoord,
        String label,
        int meshIndex
    ) {
        if (texture < 0) return;
        boolean missing = texCoord == 0
            ? primitive.texCoords0() == null
            : primitive.texCoords1() == null;
        if (missing) {
            throw new IllegalArgumentException(
                "Mesh " + meshIndex + " material " + label
                    + " texture requires missing TEXCOORD_" + texCoord
            );
        }
    }

    public Identifier id() { return id; }
    public List<ModelScene> scenes() { return scenes; }
    public int defaultScene() { return defaultScene; }
    public List<ModelNode> nodes() { return nodes; }
    public List<ModelMesh> meshes() { return meshes; }
    public List<ModelMaterial> materials() { return materials; }
    public List<ModelTexture> textures() { return textures; }
    public List<ModelSkin> skins() { return skins; }
    public List<AnimationClip> animations() { return animations; }
    public Optional<AnimationClip> animation(String name) { return Optional.ofNullable(animationsByName.get(name)); }
    public List<String> animationNames() { return animationNames; }
    public List<String> extensionsUsed() { return extensionsUsed; }
    public List<String> extensionsRequired() { return extensionsRequired; }
}
