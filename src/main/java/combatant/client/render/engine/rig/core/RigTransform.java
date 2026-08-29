/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Immutable local TRS used by rig definitions and sockets.
 */
public final class RigTransform {
    public static final RigTransform IDENTITY = new RigTransform(
            new Vector3f(),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f)
    );

    private final Vector3f translation;
    private final Quaternionf rotation;
    private final Vector3f scale;

    public RigTransform(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        this.translation = translation != null ? new Vector3f(translation) : new Vector3f();
        this.rotation = rotation != null ? new Quaternionf(rotation) : new Quaternionf();
        this.scale = scale != null ? new Vector3f(scale) : new Vector3f(1f, 1f, 1f);
    }

    public static RigTransform identity() {
        return IDENTITY;
    }

    public static RigTransform translation(float x, float y, float z) {
        return new RigTransform(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(1f, 1f, 1f));
    }

    public Vector3f translation() {
        return new Vector3f(translation);
    }

    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    public Vector3f scale() {
        return new Vector3f(scale);
    }

    public Matrix4f matrix(Matrix4f destination) {
        return destination.translationRotateScale(translation, rotation, scale);
    }

    Vector3f translationRef() {
        return translation;
    }

    Quaternionf rotationRef() {
        return rotation;
    }

    Vector3f scaleRef() {
        return scale;
    }
}
