/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.render;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record HmiModelCommand(int fromInclusive, int toInclusive, HmiTransformCommand transform) {
    public boolean appliesTo(int quadIndex) {
        return quadIndex >= fromInclusive && quadIndex <= toInclusive;
    }

    public static List<HmiModelCommand> decode(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        List<HmiModelCommand> out = new ArrayList<>();
        for (Object entry : iterable) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            int from = number(map.get("from"), 0);
            int to = Math.max(from, number(map.get("to"), from));
            Object op = map.get("op");
            Object args = map.get("args");
            if (!(op instanceof String name) || !(args instanceof Iterable<?> values)) continue;
            List<Double> numbers = new ArrayList<>();
            for (Object value : values) if (value instanceof Number n) numbers.add(n.doubleValue());
            double[] packed = new double[numbers.size()];
            for (int i = 0; i < numbers.size(); i++) packed[i] = numbers.get(i);
            out.add(new HmiModelCommand(from, to, new HmiTransformCommand(name, packed)));
        }
        return out;
    }

    public static void apply(List<HmiModelCommand> commands, int quadIndex, PoseStack stack) {
        // HoldMyItems' ModelPartAnimator applies the newest pose first.
        for (int i = commands.size() - 1; i >= 0; i--) {
            HmiModelCommand command = commands.get(i);
            if (command.appliesTo(quadIndex)) command.transform.apply(stack);
        }
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
