/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Dense mutable local pose. Setters are allocation-free and notify the owning instance when changed.
 */
public final class RigPose {
    private final RigDefinition definition;
    private final Vector3f[] translations;
    private final Quaternionf[] rotations;
    private final Vector3f[] scales;
    private final Runnable dirtyListener;

    public RigPose(RigDefinition definition) {
        this(definition, null);
    }

    RigPose(RigDefinition definition, Runnable dirtyListener) {
        if (definition == null) throw new IllegalArgumentException("Rig definition must not be null");
        this.definition = definition;
        this.dirtyListener = dirtyListener;
        this.translations = new Vector3f[definition.boneCount()];
        this.rotations = new Quaternionf[definition.boneCount()];
        this.scales = new Vector3f[definition.boneCount()];

        for (int i = 0; i < definition.boneCount(); i++) {
            BoneDefinition bone = definition.bone(i);
            translations[i] = new Vector3f(bone.bindTranslationRef());
            rotations[i] = new Quaternionf(bone.bindRotationRef());
            scales[i] = new Vector3f(bone.bindScaleRef());
        }
    }

    public RigDefinition definition() {
        return definition;
    }

    public int boneCount() {
        return translations.length;
    }

    public BonePose bone(int boneIndex) {
        checkBoneIndex(boneIndex);
        return new BonePose(translations[boneIndex], rotations[boneIndex], scales[boneIndex]);
    }

    public BonePose bone(String boneName) {
        return bone(definition.requireBoneIndex(boneName));
    }

    public Vector3f translation(int boneIndex, Vector3f destination) {
        checkBoneIndex(boneIndex);
        return destination.set(translations[boneIndex]);
    }

    public Quaternionf rotation(int boneIndex, Quaternionf destination) {
        checkBoneIndex(boneIndex);
        return destination.set(rotations[boneIndex]);
    }

    public Vector3f scale(int boneIndex, Vector3f destination) {
        checkBoneIndex(boneIndex);
        return destination.set(scales[boneIndex]);
    }

    public RigPose set(int boneIndex, BonePose pose) {
        if (pose == null) throw new IllegalArgumentException("Bone pose must not be null");
        return set(boneIndex, pose.translationRef(), pose.rotationRef(), pose.scaleRef());
    }

    public RigPose set(int boneIndex, Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        checkBoneIndex(boneIndex);
        if (translation != null) translations[boneIndex].set(translation);
        if (rotation != null) rotations[boneIndex].set(rotation);
        if (scale != null) scales[boneIndex].set(scale);
        markDirty();
        return this;
    }

    public RigPose setTranslation(int boneIndex, float x, float y, float z) {
        checkBoneIndex(boneIndex);
        translations[boneIndex].set(x, y, z);
        markDirty();
        return this;
    }

    public RigPose setTranslation(int boneIndex, Vector3fc value) {
        if (value == null) return this;
        checkBoneIndex(boneIndex);
        translations[boneIndex].set(value);
        markDirty();
        return this;
    }

    public RigPose setRotation(int boneIndex, Quaternionfc value) {
        if (value == null) return this;
        checkBoneIndex(boneIndex);
        rotations[boneIndex].set(value);
        markDirty();
        return this;
    }

    public RigPose setScale(int boneIndex, float x, float y, float z) {
        checkBoneIndex(boneIndex);
        scales[boneIndex].set(x, y, z);
        markDirty();
        return this;
    }

    public RigPose setScale(int boneIndex, Vector3fc value) {
        if (value == null) return this;
        checkBoneIndex(boneIndex);
        scales[boneIndex].set(value);
        markDirty();
        return this;
    }

    public RigPose resetBoneToBindPose(int boneIndex) {
        checkBoneIndex(boneIndex);
        BoneDefinition bone = definition.bone(boneIndex);
        translations[boneIndex].set(bone.bindTranslationRef());
        rotations[boneIndex].set(bone.bindRotationRef());
        scales[boneIndex].set(bone.bindScaleRef());
        markDirty();
        return this;
    }

    public RigPose resetToBindPose() {
        for (int i = 0; i < definition.boneCount(); i++) {
            BoneDefinition bone = definition.bone(i);
            translations[i].set(bone.bindTranslationRef());
            rotations[i].set(bone.bindRotationRef());
            scales[i].set(bone.bindScaleRef());
        }
        markDirty();
        return this;
    }

    Vector3f translationRef(int index) {
        return translations[index];
    }

    Quaternionf rotationRef(int index) {
        return rotations[index];
    }

    Vector3f scaleRef(int index) {
        return scales[index];
    }

    private void checkBoneIndex(int boneIndex) {
        if (boneIndex < 0 || boneIndex >= translations.length) {
            throw new IndexOutOfBoundsException("Bone index " + boneIndex + " outside [0, " + (translations.length - 1) + "]");
        }
    }

    private void markDirty() {
        if (dirtyListener != null) dirtyListener.run();
    }
}
