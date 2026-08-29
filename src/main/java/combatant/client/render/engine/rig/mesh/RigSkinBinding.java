/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Compile-time skin assignment for a subdivided cuboid. A binding can be rigid, blend across one
 * joint, or follow a chain of up to four bones along the subdivision axis.
 */
public final class RigSkinBinding {
    private final int[] bones;
    private final float[] knots;

    private RigSkinBinding(int[] bones, float[] knots) {
        if (bones == null || knots == null || bones.length != knots.length || bones.length < 1 || bones.length > 4) {
            throw new IllegalArgumentException("Rig skin chain must contain matching 1..4 bones and knots");
        }
        for (int i = 0; i < bones.length; i++) {
            if (bones[i] < 0) throw new IllegalArgumentException("Rig skin bone index must be >= 0");
            if (!Float.isFinite(knots[i]) || knots[i] < 0f || knots[i] > 1f) {
                throw new IllegalArgumentException("Rig skin knot must be finite and inside [0,1]: " + knots[i]);
            }
            if (i > 0 && !(knots[i] > knots[i - 1])) {
                throw new IllegalArgumentException("Rig skin knots must be strictly increasing");
            }
        }
        this.bones = bones.clone();
        this.knots = knots.clone();
    }

    public static RigSkinBinding rigid(int boneIndex) {
        return new RigSkinBinding(new int[]{boneIndex}, new float[]{0f});
    }

    public static RigSkinBinding twoBone(int firstBone, int secondBone, float blendStart, float blendEnd) {
        if (secondBone < 0) throw new IllegalArgumentException("Secondary rig bone index must be >= 0");
        return new RigSkinBinding(
                new int[]{firstBone, secondBone},
                new float[]{blendStart, blendEnd}
        );
    }

    public static RigSkinBinding chain(int[] boneIndices, float[] longitudinalKnots) {
        return new RigSkinBinding(boneIndices, longitudinalKnots);
    }

    public int boneCount() {
        return bones.length;
    }

    public int firstBone() {
        return bones[0];
    }

    public int secondBone() {
        return bones.length > 1 ? bones[1] : -1;
    }

    void sample(float longitudinal, Sample destination) {
        if (destination == null) throw new IllegalArgumentException("Rig skin sample destination must not be null");
        destination.clear();
        if (bones.length == 1 || longitudinal <= knots[0]) {
            destination.add(bones[0], 1f);
            return;
        }
        int last = bones.length - 1;
        if (longitudinal >= knots[last]) {
            destination.add(bones[last], 1f);
            return;
        }
        for (int i = 0; i < last; i++) {
            if (longitudinal > knots[i + 1]) continue;
            float t = (longitudinal - knots[i]) / (knots[i + 1] - knots[i]);
            destination.add(bones[i], 1f - t);
            destination.add(bones[i + 1], t);
            return;
        }
        destination.add(bones[last], 1f);
    }

    static final class Sample {
        private final int[] bones = new int[4];
        private final float[] weights = new float[4];

        Sample() {
            clear();
        }

        int bone(int index) { return bones[index]; }
        float weight(int index) { return weights[index]; }

        /** Keeps live influences densely packed so slot zero is always the primary bone. */
        private void add(int bone, float weight) {
            if (!(weight > 0f)) return;
            for (int index = 0; index < bones.length; index++) {
                if (bones[index] != RigVertex.UNUSED_BONE) continue;
                bones[index] = bone;
                weights[index] = weight;
                return;
            }
            throw new IllegalStateException("Rig skin sample exceeds four bone influences");
        }

        private void clear() {
            java.util.Arrays.fill(bones, RigVertex.UNUSED_BONE);
            java.util.Arrays.fill(weights, 0f);
        }
    }
}
