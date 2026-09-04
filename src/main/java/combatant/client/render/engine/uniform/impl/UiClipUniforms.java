/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiClipStack;
import combatant.client.render.engine.renderer.ui.draw.UiChamferRadii;
import combatant.client.render.engine.renderer.ui.draw.UiCornerMode;
import combatant.client.render.engine.renderer.ui.draw.UiCornerRadii;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import combatant.client.render.engine.renderer.ui.draw.UiShapeKind;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Std140 writer for the fixed-size analytic UI clip intersection. */
public enum UiClipUniforms {
    ;
    private static final int MAX_CLIPS = UiClipStack.MAX_ANALYTIC_PRIMITIVES;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4() // header
            .putVec4() // kinds
            .putVec4().putVec4().putVec4().putVec4() // bounds
            .putVec4().putVec4().putVec4().putVec4() // params0
            .putVec4().putVec4().putVec4().putVec4() // params1
            .get();
    private static final String UNIFORM_NAME = "Combatant - UI Analytic Clip UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 128;
    private static final Data DATA = new Data();

    public static GpuBufferSlice write(UiClipSnapshot snapshot) {
        if (snapshot == null || !snapshot.usesAnalyticPipeline()) {
            throw new IllegalArgumentException("Analytic clip uniform requires an active analytic snapshot");
        }
        DATA.set(snapshot);
        return CombatantRenderSystem.uniforms().write(
                UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA
        );
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] header = new float[4];
        private final float[] kinds = new float[MAX_CLIPS];
        private final float[][] bounds = new float[MAX_CLIPS][4];
        private final float[][] params0 = new float[MAX_CLIPS][4];
        private final float[][] params1 = new float[MAX_CLIPS][4];

        void set(UiClipSnapshot snapshot) {
            clear();
            int count = Math.min(MAX_CLIPS, snapshot.primitives().size());
            header[0] = count;
            for (int i = 0; i < count; i++) encode(i, snapshot.primitives().get(i));
        }

        private void encode(int index, UiShape shape) {
            UiRect b = shape.bounds();
            bounds[index][0] = b.x();
            bounds[index][1] = b.y();
            bounds[index][2] = b.width();
            bounds[index][3] = b.height();

            if (shape.kind() == UiShapeKind.CIRCLE) {
                kinds[index] = 3f;
                return;
            }
            if (shape.kind() != UiShapeKind.RECT) {
                throw new IllegalArgumentException("Unsupported analytic clip shape: " + shape.kind());
            }
            if (shape.cornerMode() == UiCornerMode.ROUNDED) {
                kinds[index] = 1f;
                UiCornerRadii r = shape.roundedRadii();
                set4(params0[index], r.topLeft(), r.topRight(), r.bottomRight(), r.bottomLeft());
            } else if (shape.cornerMode() == UiCornerMode.CHAMFERED) {
                kinds[index] = 2f;
                UiChamferRadii r = shape.chamferRadii();
                set4(params0[index], r.topLeftX(), r.topRightX(), r.bottomRightX(), r.bottomLeftX());
                set4(params1[index], r.topLeftY(), r.topRightY(), r.bottomRightY(), r.bottomLeftY());
            } else {
                kinds[index] = 0f;
            }
        }

        private void clear() {
            Arrays.fill(header, 0f);
            Arrays.fill(kinds, 0f);
            for (int i = 0; i < MAX_CLIPS; i++) {
                Arrays.fill(bounds[i], 0f);
                Arrays.fill(params0[i], 0f);
                Arrays.fill(params1[i], 0f);
            }
        }

        private static void set4(float[] target, float x, float y, float z, float w) {
            target[0] = x;
            target[1] = y;
            target[2] = z;
            target[3] = w;
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder writer = Std140Builder.intoBuffer(buffer)
                    .putFloat(header[0]).putFloat(header[1]).putFloat(header[2]).putFloat(header[3])
                    .putFloat(kinds[0]).putFloat(kinds[1]).putFloat(kinds[2]).putFloat(kinds[3]);
            writeArray(writer, bounds);
            writeArray(writer, params0);
            writeArray(writer, params1);
        }

        private static void writeArray(Std140Builder writer, float[][] values) {
            for (float[] value : values) {
                writer.putFloat(value[0]).putFloat(value[1]).putFloat(value[2]).putFloat(value[3]);
            }
        }
    }
}
