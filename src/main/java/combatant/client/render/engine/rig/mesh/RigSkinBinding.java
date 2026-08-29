/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Compile-time skin assignment for a subdivided cuboid.
 * Supports rigid geometry and the first practical two-bone joint blend without constraining RigVertex to two weights.
 */
public final class RigSkinBinding {
    private final int firstBone;
    private final int secondBone;
    private final float blendStart;
    private final float blendEnd;

    private RigSkinBinding(int firstBone, int secondBone, float blendStart, float blendEnd) {
        if (firstBone < 0) throw new IllegalArgumentException("Primary rig bone index must be >= 0");
        if (secondBone < -1) throw new IllegalArgumentException("Secondary rig bone index must be >= -1");
        if (secondBone >= 0 && !(blendEnd > blendStart)) {
            throw new IllegalArgumentException("Two-bone blend end must be greater than blend start");
        }
        this.firstBone = firstBone;
        this.secondBone = secondBone;
        this.blendStart = blendStart;
        this.blendEnd = blendEnd;
    }

    public static RigSkinBinding rigid(int boneIndex) {
        return new RigSkinBinding(boneIndex, -1, 0f, 0f);
    }

    public static RigSkinBinding twoBone(int firstBone, int secondBone, float blendStart, float blendEnd) {
        if (secondBone < 0) throw new IllegalArgumentException("Secondary rig bone index must be >= 0");
        return new RigSkinBinding(firstBone, secondBone, blendStart, blendEnd);
    }

    public int firstBone() {
        return firstBone;
    }

    public int secondBone() {
        return secondBone;
    }

    float secondWeight(float longitudinal) {
        if (secondBone < 0) return 0f;
        float t = (longitudinal - blendStart) / (blendEnd - blendStart);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t;
    }
}
