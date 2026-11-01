/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.geometry;

import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.data.PrimitiveMode;

import java.util.Arrays;
import java.util.Objects;

/** Normalizes triangle-family primitive topology into indexed triangles for Combatant RHI. */
public final class ModelTriangleIndices {
    private ModelTriangleIndices() {}

    public static boolean supports(PrimitiveMode mode) {
        return mode == PrimitiveMode.TRIANGLES || mode == PrimitiveMode.TRIANGLE_STRIP || mode == PrimitiveMode.TRIANGLE_FAN;
    }

    public static int[] build(ModelPrimitive primitive) {
        Objects.requireNonNull(primitive, "primitive");
        if (!supports(primitive.mode())) {
            throw new IllegalArgumentException("Primitive mode " + primitive.mode() + " is not a surface triangle topology");
        }
        int[] source = primitive.indices();
        int count = source == null ? primitive.vertexCount() : source.length;
        return switch (primitive.mode()) {
            case TRIANGLES -> source == null ? sequential(count) : source;
            case TRIANGLE_STRIP -> strip(source, count);
            case TRIANGLE_FAN -> fan(source, count);
            default -> throw new IllegalStateException("Unexpected triangle topology " + primitive.mode());
        };
    }

    private static int[] sequential(int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = i;
        return result;
    }

    private static int[] strip(int[] source, int count) {
        int[] result = new int[Math.max(0, count - 2) * 3];
        int out = 0;
        for (int i = 2; i < count; i++) {
            int a = index(source, i - 2);
            int b = index(source, i - 1);
            int c = index(source, i);
            if ((i & 1) != 0) {
                int swap = a; a = b; b = swap;
            }
            if (a == b || b == c || a == c) continue;
            result[out++] = a;
            result[out++] = b;
            result[out++] = c;
        }
        return out == result.length ? result : Arrays.copyOf(result, out);
    }

    private static int[] fan(int[] source, int count) {
        int root = index(source, 0);
        int[] result = new int[Math.max(0, count - 2) * 3];
        int out = 0;
        for (int i = 2; i < count; i++) {
            int b = index(source, i - 1);
            int c = index(source, i);
            if (root == b || b == c || root == c) continue;
            result[out++] = root;
            result[out++] = b;
            result[out++] = c;
        }
        return out == result.length ? result : Arrays.copyOf(result, out);
    }

    private static int index(int[] source, int element) {
        return source == null ? element : source[element];
    }
}
