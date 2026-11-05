/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dependencies observed while evaluating render(ctx) in the V8 worker.
 * The worker returns a compact list of paths; comparison runs on the owner
 * thread against the last detached input, without copying all props per frame.
 *
 * A null dependency list means that tracking was unavailable or saturated;
 * correctness then requires comparing the full input graph. An empty list
 * means the output is independent of the render context.
 */
public final class UiScriptDependencies {
    private static final Object MISSING = new Object();
    private static final int MAX_PATHS = 2048;
    private static final int MAX_DEPTH = 64;

    private UiScriptDependencies() {}

    public record Dependency(String kind, List<String> path) {}

    record Inputs(long frame, double time, double delta, float width, float height, Map<String, ?> props) {
        static Inputs from(UiScriptRenderContext ctx) {
            return new Inputs(ctx.frame(), ctx.time(), ctx.delta(), ctx.width(), ctx.height(), ctx.props().values());
        }
    }

    static List<Dependency> parse(Object source) {
        if (source == null) return null;
        if (!(source instanceof Collection<?> raw) || raw.size() > MAX_PATHS) return null;
        ArrayList<Dependency> result = new ArrayList<>(raw.size());
        for (Object record : raw) {
            if (!(record instanceof List<?> pair) || pair.size() != 2 || !(pair.get(0) instanceof String kind)
                    || !(pair.get(1) instanceof List<?> path) || path.size() > MAX_DEPTH
                    || !(kind.equals("value") || kind.equals("keys") || kind.equals("has"))) return null;
            ArrayList<String> segments = new ArrayList<>(path.size());
            for (Object segment : path) {
                if (!(segment instanceof String s)) return null;
                segments.add(s);
            }
            result.add(new Dependency(kind, List.copyOf(segments)));
        }
        return List.copyOf(result);
    }

    /** Same explicitly supplied input on a failed evaluation (ignores the JS clock). */
    static boolean samePayload(Inputs previous, Inputs current) {
        return previous != null && current != null
                && Float.floatToIntBits(previous.width()) == Float.floatToIntBits(current.width())
                && Float.floatToIntBits(previous.height()) == Float.floatToIntBits(current.height())
                && equalValues(previous.props(), current.props(), 0);
    }

    static boolean equivalent(List<Dependency> deps, Inputs previous, Inputs current) {
        if (previous == null || current == null) return false;
        if (deps == null) {
            return previous.frame == current.frame
                    && Double.doubleToLongBits(previous.time) == Double.doubleToLongBits(current.time)
                    && Double.doubleToLongBits(previous.delta) == Double.doubleToLongBits(current.delta)
                    && Float.floatToIntBits(previous.width) == Float.floatToIntBits(current.width)
                    && Float.floatToIntBits(previous.height) == Float.floatToIntBits(current.height)
                    && equalValues(previous.props, current.props, 0);
        }
        for (Dependency dep : deps) {
            Object a = read(previous, dep.path);
            Object b = read(current, dep.path);
            boolean same = switch (dep.kind) {
                case "has" -> (a != MISSING) == (b != MISSING);
                case "keys" -> sameKeys(a, b);
                default -> equalValues(a, b, 0);
            };
            if (!same) return false;
        }
        return true;
    }

    private static Object read(Inputs source, List<String> path) {
        if (path.isEmpty()) return source;
        Object current = switch (path.get(0)) {
            case "frame" -> source.frame;
            case "time" -> source.time;
            case "delta" -> source.delta;
            case "width" -> source.width;
            case "height" -> source.height;
            case "props" -> source.props;
            default -> MISSING;
        };
        for (int i = 1; i < path.size() && current != MISSING; i++) {
            String part = path.get(i);
            if (current instanceof Map<?, ?> map) {
                current = map.containsKey(part) ? map.get(part) : MISSING;
            } else if (current instanceof List<?> list) {
                if (part.equals("length")) current = list.size();
                else {
                    int index = arrayIndex(part, list.size());
                    current = index < 0 ? MISSING : list.get(index);
                }
            } else if (current != null && current.getClass().isArray()) {
                int length = Array.getLength(current);
                if (part.equals("length")) current = length;
                else {
                    int index = arrayIndex(part, length);
                    current = index < 0 ? MISSING : Array.get(current, index);
                }
            } else if (current instanceof String text && part.equals("length")) {
                current = text.length();
            } else current = MISSING;
        }
        return current;
    }

    private static int arrayIndex(String raw, int length) {
        if (raw.isEmpty() || raw.length() > 10) return -1;
        try {
            int index = Integer.parseInt(raw);
            return index >= 0 && index < length && raw.equals(Integer.toString(index)) ? index : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean sameKeys(Object a, Object b) {
        if (a == MISSING || b == MISSING) return a == b;
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) return x.keySet().equals(y.keySet());
        if (a instanceof List<?> x && b instanceof List<?> y) return x.size() == y.size();
        if (a != null && b != null && a.getClass().isArray() && b.getClass().isArray()) {
            return Array.getLength(a) == Array.getLength(b);
        }
        return Objects.equals(a, b);
    }

    private static boolean equalValues(Object a, Object b, int depth) {
        if (a == MISSING || b == MISSING) return a == b;
        if (a == b) return true;
        if (a == null || b == null || depth >= MAX_DEPTH) return false;
        if (a instanceof Number x && b instanceof Number y) {
            return Double.doubleToLongBits(x.doubleValue()) == Double.doubleToLongBits(y.doubleValue());
        }
        if (a instanceof Map<?, ?> left && b instanceof Map<?, ?> right) {
            if (left.size() != right.size() || !left.keySet().equals(right.keySet())) return false;
            for (Map.Entry<?, ?> entry : left.entrySet()) {
                if (!equalValues(entry.getValue(), right.get(entry.getKey()), depth + 1)) return false;
            }
            return true;
        }
        if (a instanceof List<?> list) return equalSequence(list, b, depth);
        if (a.getClass().isArray()) return equalSequence(a, b, depth);
        if (a instanceof Collection<?> collection) return equalSequence(collection, b, depth);
        // Unknown mutable Java objects are never considered stable across
        // snapshots. The caller must explicitly convert these on the owner thread.
        if (!(a instanceof String || a instanceof Boolean || a instanceof Character || a instanceof Enum<?>)) {
            return false;
        }
        return Objects.equals(a, b);
    }

    private static boolean equalSequence(Object left, Object right, int depth) {
        List<?> a = asList(left);
        List<?> b = asList(right);
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!equalValues(a.get(i), b.get(i), depth + 1)) return false;
        }
        return true;
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) return list;
        if (value != null && value.getClass().isArray()) {
            int size = Array.getLength(value);
            return new java.util.AbstractList<>() {
                @Override public Object get(int index) { return Array.get(value, index); }
                @Override public int size() { return size; }
            };
        }
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        return null;
    }
}
