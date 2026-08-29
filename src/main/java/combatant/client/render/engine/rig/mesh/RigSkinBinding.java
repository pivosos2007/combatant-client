/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Compile-time skin assignment for a subdivided cuboid.
 *
 * <p>Chain bindings interpolate continuously between knots and are useful for genuinely soft
 * deformations. Banded bindings keep most of a limb rigid and blend only inside narrow regions
 * around anatomical joints. The latter avoids the "rubber sausage" look on Minecraft limbs.</p>
 */
public final class RigSkinBinding {
    private enum Mode { CHAIN, BANDED }

    private final int[] bones;
    private final float[] knots;
    private final float[] blendHalfWidths;
    private final Mode mode;

    private RigSkinBinding(int[] bones, float[] knots, float[] blendHalfWidths, Mode mode) {
        if (bones == null || knots == null || bones.length < 1 || bones.length > 4) {
            throw new IllegalArgumentException("Rig skin chain must contain 1..4 bones");
        }
        if (mode == Mode.CHAIN && bones.length != knots.length) {
            throw new IllegalArgumentException("Continuous rig skin chain needs one knot per bone");
        }
        if (mode == Mode.BANDED && knots.length != Math.max(0, bones.length - 1)) {
            throw new IllegalArgumentException("Banded rig skin needs one joint position between adjacent bones");
        }
        if (mode == Mode.BANDED && (blendHalfWidths == null || blendHalfWidths.length != knots.length)) {
            throw new IllegalArgumentException("Banded rig skin needs one blend width per joint");
        }
        for (int bone : bones) {
            if (bone < 0) throw new IllegalArgumentException("Rig skin bone index must be >= 0");
        }
        float previous = -1f;
        for (int i = 0; i < knots.length; i++) {
            float knot = knots[i];
            if (!Float.isFinite(knot) || knot < 0f || knot > 1f || knot <= previous) {
                throw new IllegalArgumentException("Rig skin knots must be finite, strictly increasing and inside [0,1]");
            }
            previous = knot;
            if (mode == Mode.BANDED) {
                float halfWidth = blendHalfWidths[i];
                if (!Float.isFinite(halfWidth) || halfWidth < 0f || halfWidth > 0.5f) {
                    throw new IllegalArgumentException("Rig skin blend half-width must be finite and inside [0,0.5]");
                }
            }
        }
        this.bones = bones.clone();
        this.knots = knots.clone();
        this.blendHalfWidths = blendHalfWidths != null ? blendHalfWidths.clone() : null;
        this.mode = mode;
    }

    public static RigSkinBinding rigid(int boneIndex) {
        return new RigSkinBinding(new int[]{boneIndex}, new float[]{0f}, null, Mode.CHAIN);
    }

    public static RigSkinBinding twoBone(int firstBone, int secondBone, float blendStart, float blendEnd) {
        if (secondBone < 0) throw new IllegalArgumentException("Secondary rig bone index must be >= 0");
        return new RigSkinBinding(new int[]{firstBone, secondBone}, new float[]{blendStart, blendEnd}, null, Mode.CHAIN);
    }

    public static RigSkinBinding chain(int[] boneIndices, float[] longitudinalKnots) {
        return new RigSkinBinding(boneIndices, longitudinalKnots, null, Mode.CHAIN);
    }

    /**
     * Keeps vertices rigid to the nearest segment and blends only inside a narrow band centered on
     * each joint. Joint positions and widths are normalized to the deformation axis.
     */
    public static RigSkinBinding banded(int[] boneIndices, float[] jointPositions, float[] blendHalfWidths) {
        return new RigSkinBinding(boneIndices, jointPositions, blendHalfWidths, Mode.BANDED);
    }

    public int boneCount() { return bones.length; }
    public int firstBone() { return bones[0]; }
    public int secondBone() { return bones.length > 1 ? bones[1] : -1; }

    void sample(float longitudinal, Sample destination) {
        if (destination == null) throw new IllegalArgumentException("Rig skin sample destination must not be null");
        destination.clear();
        if (bones.length == 1) {
            destination.add(bones[0], 1f);
            return;
        }
        if (mode == Mode.BANDED) {
            sampleBanded(longitudinal, destination);
            return;
        }
        sampleChain(longitudinal, destination);
    }

    private void sampleChain(float longitudinal, Sample destination) {
        if (longitudinal <= knots[0]) {
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

    private void sampleBanded(float longitudinal, Sample destination) {
        int lastJoint = knots.length - 1;
        for (int joint = 0; joint <= lastJoint; joint++) {
            float center = knots[joint];
            float half = blendHalfWidths[joint];
            float start = center - half;
            float end = center + half;
            if (longitudinal < start) {
                destination.add(bones[joint], 1f);
                return;
            }
            if (longitudinal <= end) {
                if (half <= 1.0e-6f) {
                    destination.add(longitudinal < center ? bones[joint] : bones[joint + 1], 1f);
                    return;
                }
                float t = (longitudinal - start) / (end - start);
                // Smoothstep reduces a visible crease at the edge of a deliberately narrow band.
                t = t * t * (3f - 2f * t);
                destination.add(bones[joint], 1f - t);
                destination.add(bones[joint + 1], t);
                return;
            }
        }
        destination.add(bones[bones.length - 1], 1f);
    }

    static final class Sample {
        private final int[] bones = new int[4];
        private final float[] weights = new float[4];

        Sample() { clear(); }
        int bone(int index) { return bones[index]; }
        float weight(int index) { return weights[index]; }

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
