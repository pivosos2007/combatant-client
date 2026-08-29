/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Immutable compiled bone definition. Bone indices are stable runtime array indices.
 */
public final class BoneDefinition {
    private final int index;
    private final int parentIndex;
    private final String name;
    private final Vector3f bindTranslation;
    private final Quaternionf bindRotation;
    private final Vector3f bindScale;
    private final Matrix4f inverseBind;

    public BoneDefinition(int index,
                          int parentIndex,
                          String name,
                          Vector3fc bindTranslation,
                          Quaternionfc bindRotation,
                          Vector3fc bindScale,
                          Matrix4fc inverseBind) {
        if (index < 0) throw new IllegalArgumentException("Bone index must be >= 0");
        if (parentIndex < -1) throw new IllegalArgumentException("Bone parent index must be >= -1");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Bone name must not be blank");
        if (inverseBind == null) throw new IllegalArgumentException("Bone inverse bind matrix must not be null");

        this.index = index;
        this.parentIndex = parentIndex;
        this.name = name;
        this.bindTranslation = bindTranslation != null ? new Vector3f(bindTranslation) : new Vector3f();
        this.bindRotation = bindRotation != null ? new Quaternionf(bindRotation) : new Quaternionf();
        this.bindScale = bindScale != null ? new Vector3f(bindScale) : new Vector3f(1f, 1f, 1f);
        this.inverseBind = new Matrix4f(inverseBind);
    }

    public int index() {
        return index;
    }

    public int parentIndex() {
        return parentIndex;
    }

    public String name() {
        return name;
    }

    public Vector3f bindTranslation() {
        return new Vector3f(bindTranslation);
    }

    public Quaternionf bindRotation() {
        return new Quaternionf(bindRotation);
    }

    public Vector3f bindScale() {
        return new Vector3f(bindScale);
    }

    public Matrix4f inverseBind() {
        return new Matrix4f(inverseBind);
    }

    Matrix4f writeBindLocal(Matrix4f destination) {
        return destination.translationRotateScale(bindTranslation, bindRotation, bindScale);
    }

    Vector3f bindTranslationRef() {
        return bindTranslation;
    }

    Quaternionf bindRotationRef() {
        return bindRotation;
    }

    Vector3f bindScaleRef() {
        return bindScale;
    }

    Matrix4f inverseBindRef() {
        return inverseBind;
    }
}
