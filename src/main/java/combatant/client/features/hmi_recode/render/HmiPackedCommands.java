/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.hmi_recode.render;

import com.mojang.blaze3d.vertex.PoseStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Packed HMI geometry protocol: opcode, from, to, argc, four Float64 args.
 * Copying once at the JNI boundary is essential: deferred quads/replay outlive the next V8 call.
 */
public final class HmiPackedCommands {
    public static final int STAGES = 4;
    public static final int TYPES = 2;
    public static final int CAPACITY = 256;
    public static final int STRIDE = 8;
    public static final int VALUE_COUNT = STAGES * TYPES * CAPACITY * STRIDE;
    public static final int BYTE_COUNT = VALUE_COUNT * Double.BYTES;
    private static final String[] OPS = {
            "", "moveX", "moveY", "moveZ", "translate", "scale", "rotateX", "rotateY", "rotateZ", "shear", "push", "pop"
    };

    private HmiPackedCommands() { }

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(new double[0], 0, false);
        private final double[] commands;
        private final int count;
        private final boolean model;

        private Snapshot(double[] commands, int count, boolean model) {
            this.commands = commands;
            this.count = count;
            this.model = model;
        }

        public boolean isEmpty() { return count == 0; }
        public int size() { return count; }

        public void applyTransforms(PoseStack stack) {
            if (count == 0) return;
            if (model) throw new IllegalStateException("Expected transform snapshot");
            for (int i = 0; i < count; i++) apply(i, stack);
        }

        public void applyModels(int quadIndex, PoseStack stack) {
            if (count == 0) return;
            if (!model) throw new IllegalStateException("Expected model snapshot");
            // ModelPartAnimator order is newest first, including deferred render/foil passes.
            for (int i = count - 1; i >= 0; i--) {
                int base = i * STRIDE;
                if (quadIndex >= (int) commands[base + 1] && quadIndex <= Math.max((int) commands[base + 1], (int) commands[base + 2])) {
                    apply(i, stack);
                }
            }
        }

        private void apply(int i, PoseStack stack) {
            int base = i * STRIDE;
            HmiTransformCommand.applyOpcode(stack, (int) commands[base], (int) commands[base + 3],
                    commands[base + 4], commands[base + 5], commands[base + 6], commands[base + 7]);
        }

        public List<HmiTransformCommand> toTransforms() {
            if (model) throw new IllegalStateException("Expected transform snapshot");
            if (count == 0) return List.of();
            List<HmiTransformCommand> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int base = i * STRIDE;
                double[] args = args(base);
                result.add(new HmiTransformCommand(OPS[(int)commands[base]], args));
            }
            return result;
        }

        public List<HmiModelCommand> toModels() {
            if (!model) throw new IllegalStateException("Expected model snapshot");
            if (count == 0) return List.of();
            List<HmiModelCommand> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int base = i * STRIDE;
                result.add(new HmiModelCommand((int) commands[base + 1],
                        Math.max((int) commands[base + 1], (int) commands[base + 2]),
                        new HmiTransformCommand(OPS[(int)commands[base]], args(base))));
            }
            return result;
        }

        private double[] args(int base) {
            int size = (int) commands[base + 3];
            double[] result = new double[size];
            System.arraycopy(commands, base + 4, result, 0, size);
            return result;
        }
    }

    /** Absolute reads are bounded, independent of the caller's ByteBuffer position. */
    public static Snapshot copy(ByteBuffer source, int stage, boolean model, int count) {
        if (source == null || source.capacity() < BYTE_COUNT || stage < 0 || stage >= STAGES
                || count < 0 || count > CAPACITY) {
            throw new IllegalArgumentException("Invalid HMI packed command buffer");
        }
        if (count == 0) return Snapshot.EMPTY;
        int offset = (stage * TYPES + (model ? 1 : 0)) * CAPACITY * STRIDE * Double.BYTES;
        double[] data = new double[count * STRIDE];
        for (int i = 0; i < data.length; i++) data[i] = source.getDouble(offset + i * Double.BYTES);
        for (int i = 0; i < count; i++) {
            int base = i * STRIDE;
            int opcode = (int) data[base];
            int argc = (int) data[base + 3];
            if (!Double.isFinite(data[base]) || opcode < 1 || opcode >= OPS.length
                    || !Double.isFinite(data[base + 3]) || argc < 0 || argc > 4) {
                throw new IllegalArgumentException("Corrupted HMI geometry at entry " + i);
            }
        }
        return new Snapshot(data, count, model);
    }
}
