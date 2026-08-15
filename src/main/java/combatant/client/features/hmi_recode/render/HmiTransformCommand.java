/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record HmiTransformCommand(String op, double[] args) {
    public static List<HmiTransformCommand> decode(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        List<HmiTransformCommand> out = new ArrayList<>();
        for (Object entry : iterable) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object op = map.get("op");
            Object args = map.get("args");
            if (!(op instanceof String name) || !(args instanceof Iterable<?> values)) continue;
            List<Double> numbers = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Number n) numbers.add(n.doubleValue());
            }
            double[] packed = new double[numbers.size()];
            for (int i = 0; i < numbers.size(); i++) packed[i] = numbers.get(i);
            out.add(new HmiTransformCommand(name, packed));
        }
        return out;
    }

    public void apply(PoseStack stack) {
        if (stack == null) return;
        switch (op) {
            case "moveX" -> stack.translate(arg(0), 0.0, 0.0);
            case "moveY" -> stack.translate(0.0, arg(0), 0.0);
            case "moveZ" -> stack.translate(0.0, 0.0, arg(0));
            case "translate" -> stack.translate(arg(0), arg(1), arg(2));
            case "scale" -> stack.scale((float) arg(0), (float) arg(1), (float) arg(2));
            case "rotateX" -> rotate(stack, Axis.XP, args);
            case "rotateY" -> rotate(stack, Axis.YP, args);
            case "rotateZ" -> rotate(stack, Axis.ZP, args);
            case "shear" -> shear(stack, arg(0), arg(1), arg(2));
            case "push" -> stack.pushPose();
            case "pop" -> {
                if (!stack.isEmpty()) stack.popPose();
            }
            default -> {
            }
        }
    }

    private static void rotate(PoseStack stack, Axis axis, double[] args) {
        if (args.length >= 4) {
            stack.rotateAround(axis.rotationDegrees((float) args[0]), (float) args[1], (float) args[2], (float) args[3]);
        } else if (args.length >= 1) {
            stack.mulPose(axis.rotationDegrees((float) args[0]));
        }
    }

    private static void shear(PoseStack stack, double x, double y, double z) {
        Matrix4f matrix = new Matrix4f(
                1.0f, (float) x, (float) x, 0.0f,
                (float) y, 1.0f, (float) y, 0.0f,
                (float) z, (float) z, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
        stack.last().pose().mul(matrix);
    }

    private double arg(int index) {
        return index >= 0 && index < args.length ? args[index] : 0.0;
    }
}
