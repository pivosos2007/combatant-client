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
 * Snapshot of one bone's local animated TRS.
 */
public final class BonePose {
    private final Vector3f translation;
    private final Quaternionf rotation;
    private final Vector3f scale;

    public BonePose(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        this.translation = translation != null ? new Vector3f(translation) : new Vector3f();
        this.rotation = rotation != null ? new Quaternionf(rotation) : new Quaternionf();
        this.scale = scale != null ? new Vector3f(scale) : new Vector3f(1f, 1f, 1f);
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
