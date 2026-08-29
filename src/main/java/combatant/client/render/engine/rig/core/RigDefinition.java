/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable compiled rig definition. Hierarchy order is precomputed once; frame evaluation is iterative.
 */
public final class RigDefinition {
    private final BoneDefinition[] bones;
    private final RigSocket[] sockets;
    private final List<BoneDefinition> boneView;
    private final List<RigSocket> socketView;
    private final int[] topologicalOrder;
    private final Object2IntMap<String> boneIndices;
    private final Object2IntMap<String> socketIndices;

    public RigDefinition(List<BoneDefinition> bones, List<RigSocket> sockets) {
        if (bones == null || bones.isEmpty()) throw new IllegalArgumentException("Rig must contain at least one bone");

        BoneDefinition[] byIndex = new BoneDefinition[bones.size()];
        Object2IntOpenHashMap<String> compiledBoneIndices = new Object2IntOpenHashMap<>(bones.size());
        compiledBoneIndices.defaultReturnValue(-1);
        for (BoneDefinition bone : bones) {
            if (bone == null) throw new IllegalArgumentException("Rig contains a null bone");
            int index = bone.index();
            if (index >= byIndex.length) {
                throw new IllegalArgumentException("Bone index " + index + " is outside dense range [0, " + (byIndex.length - 1) + "]");
            }
            if (byIndex[index] != null) throw new IllegalArgumentException("Duplicate bone index: " + index);
            if (compiledBoneIndices.putIfAbsent(bone.name(), index) != -1) {
                throw new IllegalArgumentException("Duplicate bone name: " + bone.name());
            }
            byIndex[index] = bone;
        }
        for (int i = 0; i < byIndex.length; i++) {
            if (byIndex[i] == null) throw new IllegalArgumentException("Missing bone index in dense rig: " + i);
        }

        int[] order = compileTopologicalOrder(byIndex);
        RigSocket[] compiledSockets = sockets != null ? sockets.toArray(new RigSocket[0]) : new RigSocket[0];
        Object2IntOpenHashMap<String> compiledSocketIndices = new Object2IntOpenHashMap<>(compiledSockets.length);
        compiledSocketIndices.defaultReturnValue(-1);
        for (int i = 0; i < compiledSockets.length; i++) {
            RigSocket socket = compiledSockets[i];
            if (socket == null) throw new IllegalArgumentException("Rig contains a null socket");
            if (socket.boneIndex() >= byIndex.length) {
                throw new IllegalArgumentException("Socket '" + socket.name() + "' references missing bone " + socket.boneIndex());
            }
            if (compiledSocketIndices.putIfAbsent(socket.name(), i) != -1) {
                throw new IllegalArgumentException("Duplicate socket name: " + socket.name());
            }
        }

        this.bones = byIndex.clone();
        this.sockets = compiledSockets.clone();
        this.boneView = Collections.unmodifiableList(Arrays.asList(this.bones));
        this.socketView = Collections.unmodifiableList(Arrays.asList(this.sockets));
        this.topologicalOrder = order;
        this.boneIndices = compiledBoneIndices;
        this.socketIndices = compiledSocketIndices;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int boneCount() {
        return bones.length;
    }

    public BoneDefinition bone(int index) {
        return bones[index];
    }

    public BoneDefinition bone(String name) {
        return bone(requireBoneIndex(name));
    }

    public List<BoneDefinition> bones() {
        return boneView;
    }

    public int boneIndex(String name) {
        return name != null ? boneIndices.getInt(name) : -1;
    }

    public int requireBoneIndex(String name) {
        int index = boneIndex(name);
        if (index < 0) throw new IllegalArgumentException("Unknown rig bone: " + name);
        return index;
    }

    public int socketCount() {
        return sockets.length;
    }

    public RigSocket socket(int index) {
        return sockets[index];
    }

    public RigSocket socket(String name) {
        return socket(requireSocketIndex(name));
    }

    public List<RigSocket> sockets() {
        return socketView;
    }

    public int socketIndex(String name) {
        return name != null ? socketIndices.getInt(name) : -1;
    }

    public int requireSocketIndex(String name) {
        int index = socketIndex(name);
        if (index < 0) throw new IllegalArgumentException("Unknown rig socket: " + name);
        return index;
    }

    int[] topologicalOrderRef() {
        return topologicalOrder;
    }

    BoneDefinition boneRef(int index) {
        return bones[index];
    }

    RigSocket socketRef(int index) {
        return sockets[index];
    }

    private static int[] compileTopologicalOrder(BoneDefinition[] bones) {
        int count = bones.length;
        int[] indegree = new int[count];
        IntArrayList[] children = new IntArrayList[count];

        for (BoneDefinition bone : bones) {
            int parent = bone.parentIndex();
            if (parent >= count) {
                throw new IllegalArgumentException("Bone '" + bone.name() + "' references missing parent " + parent);
            }
            if (parent == bone.index()) {
                throw new IllegalArgumentException("Bone '" + bone.name() + "' cannot parent itself");
            }
            if (parent >= 0) {
                indegree[bone.index()] = 1;
                IntArrayList list = children[parent];
                if (list == null) children[parent] = list = new IntArrayList(2);
                list.add(bone.index());
            }
        }

        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int i = 0; i < count; i++) {
            if (indegree[i] == 0) queue.enqueue(i);
        }

        int[] order = new int[count];
        int cursor = 0;
        while (!queue.isEmpty()) {
            int current = queue.dequeueInt();
            order[cursor++] = current;
            IntArrayList list = children[current];
            if (list == null) continue;
            for (int i = 0, size = list.size(); i < size; i++) {
                int child = list.getInt(i);
                if (--indegree[child] == 0) queue.enqueue(child);
            }
        }

        if (cursor != count) throw new IllegalArgumentException("Rig hierarchy contains a parent cycle");
        return order;
    }

    public static final class Builder {
        private final List<BoneDraft> bones = new ArrayList<>();
        private final List<SocketDraft> sockets = new ArrayList<>();
        private final Object2IntOpenHashMap<String> boneIndices = new Object2IntOpenHashMap<>();

        private Builder() {
            boneIndices.defaultReturnValue(-1);
        }

        public int bone(String name, int parentIndex, Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Bone name must not be blank");
            if (boneIndices.containsKey(name)) throw new IllegalArgumentException("Duplicate bone name: " + name);
            int index = bones.size();
            bones.add(new BoneDraft(index, parentIndex, name,
                    translation != null ? new Vector3f(translation) : new Vector3f(),
                    rotation != null ? new Quaternionf(rotation) : new Quaternionf(),
                    scale != null ? new Vector3f(scale) : new Vector3f(1f, 1f, 1f)));
            boneIndices.put(name, index);
            return index;
        }

        public int bone(String name, int parentIndex, RigTransform bindLocal) {
            RigTransform transform = bindLocal != null ? bindLocal : RigTransform.identity();
            return bone(name, parentIndex, transform.translationRef(), transform.rotationRef(), transform.scaleRef());
        }

        public int bone(String name, String parentName, RigTransform bindLocal) {
            int parentIndex = parentName != null ? requireDraftBoneIndex(parentName) : -1;
            return bone(name, parentIndex, bindLocal);
        }

        public Builder socket(String name, int boneIndex, RigTransform local) {
            sockets.add(new SocketDraft(name, boneIndex, local != null ? local : RigTransform.identity()));
            return this;
        }

        public Builder socket(String name, String boneName, RigTransform local) {
            return socket(name, requireDraftBoneIndex(boneName), local);
        }

        public RigDefinition build() {
            if (bones.isEmpty()) throw new IllegalArgumentException("Rig must contain at least one bone");

            BoneDefinition[] temporary = new BoneDefinition[bones.size()];
            for (BoneDraft bone : bones) {
                temporary[bone.index] = new BoneDefinition(
                        bone.index,
                        bone.parentIndex,
                        bone.name,
                        bone.translation,
                        bone.rotation,
                        bone.scale,
                        new Matrix4f()
                );
            }
            int[] order = compileTopologicalOrder(temporary);

            Matrix4f[] bindModels = new Matrix4f[bones.size()];
            for (int i = 0; i < bindModels.length; i++) bindModels[i] = new Matrix4f();

            List<BoneDefinition> compiled = new ArrayList<>(bones.size());
            for (int i = 0; i < bones.size(); i++) compiled.add(null);
            Matrix4f local = new Matrix4f();
            for (int index : order) {
                BoneDraft bone = bones.get(index);
                local.translationRotateScale(bone.translation, bone.rotation, bone.scale);
                if (bone.parentIndex >= 0) {
                    bindModels[index].set(bindModels[bone.parentIndex]).mul(local);
                } else {
                    bindModels[index].set(local);
                }
                Matrix4f inverseBind = new Matrix4f(bindModels[index]).invert();
                compiled.set(index, new BoneDefinition(
                        index,
                        bone.parentIndex,
                        bone.name,
                        bone.translation,
                        bone.rotation,
                        bone.scale,
                        inverseBind
                ));
            }

            List<RigSocket> compiledSockets = new ArrayList<>(sockets.size());
            for (SocketDraft socket : sockets) {
                compiledSockets.add(new RigSocket(socket.name, socket.boneIndex, socket.local));
            }
            return new RigDefinition(compiled, compiledSockets);
        }

        private int requireDraftBoneIndex(String name) {
            int index = name != null ? boneIndices.getInt(name) : -1;
            if (index < 0) throw new IllegalArgumentException("Unknown parent/socket bone in rig builder: " + name);
            return index;
        }
    }

    private record BoneDraft(int index,
                             int parentIndex,
                             String name,
                             Vector3f translation,
                             Quaternionf rotation,
                             Vector3f scale) {
    }

    private record SocketDraft(String name, int boneIndex, RigTransform local) {
    }
}
