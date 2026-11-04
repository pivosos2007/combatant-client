/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A detached Java/JS value graph safe to hand to a V8 worker. */
final class UiScriptSnapshot {
    private UiScriptSnapshot() {
    }

    static Map<String, Object> capture(Map<String, ?> props) {
        if (props == null || props.isEmpty()) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) copy(props, new IdentityHashMap<>());
        return result;
    }

    private static Object copy(Object value, IdentityHashMap<Object, Boolean> visiting) {
        // JS Number is a double: don't let Javet turn Long values into bigint.
        if (value instanceof Long number) {
            long n = number.longValue();
            return n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE ? (Object) (int) n : (double) n;
        }
        // Native Minecraft items cannot cross into the worker/V8 isolate. Capture
        // only plain, immutable JS-visible metadata on the caller (render) thread.
        // The real ItemStack is supplied to ui.item through the existing live
        // render-thread prop patches, preserving components, models and glint.
        if (value instanceof ItemStack stack) return itemStackSnapshot(stack);
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Float || value instanceof Double
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) {
            return value;
        }
        // Some Number implementations (e.g. AtomicInteger) are mutable.
        if (value instanceof Number number) return number.doubleValue();
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic JS props cannot be snapshotted.");
        }
        try {
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> out = new LinkedHashMap<>(raw.size());
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null) {
                        out.put(String.valueOf(entry.getKey()), copy(entry.getValue(), visiting));
                    }
                }
                return Collections.unmodifiableMap(out);
            }
            if (value instanceof Iterable<?> source) {
                List<Object> out = new ArrayList<>();
                for (Object item : source) out.add(copy(item, visiting));
                return Collections.unmodifiableList(out);
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> out = new ArrayList<>(length);
                for (int i = 0; i < length; i++) out.add(copy(Array.get(value, i), visiting));
                return Collections.unmodifiableList(out);
            }
            // Never expose a mutable Minecraft or UI object to an off-thread engine.
            throw new IllegalArgumentException("Unsupported async JS prop type: " + value.getClass().getName());
        } finally {
            visiting.remove(value);
        }
    }

    private static Map<String, Object> itemStackSnapshot(ItemStack stack) {
        if (stack.isEmpty()) return Map.of("empty", true);
        Map<String, Object> data = new LinkedHashMap<>(7);
        data.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.put("count", stack.getCount());
        data.put("damage", stack.getDamageValue());
        data.put("maxDamage", stack.getMaxDamage());
        data.put("componentsHash", stack.getComponents().hashCode());
        data.put("empty", false);
        return Collections.unmodifiableMap(data);
    }
}
