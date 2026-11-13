/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.gauges;

import net.minecraft.client.resources.language.I18n;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GaugeRegistry {
    public enum Readiness { FULL, THRESHOLD, CONTINUOUS }
    public enum Flow { FILL, DRAIN }
    public enum Kind { BAR, MESSAGE }

    public record Gauge(String id, String label, float fill, Readiness readiness,
                        float threshold, boolean ready, Flow flow, String detail, Kind kind) {
        public Gauge(String id, String label, float fill, Readiness readiness,
                     float threshold, boolean ready, Flow flow, String detail) {
            this(id, label, fill, readiness, threshold, ready, flow, detail, Kind.BAR);
        }
        public Gauge {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Gauge id is required");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("Gauge label is required");
            readiness = readiness == null ? Readiness.CONTINUOUS : readiness;
            flow = flow == null ? Flow.FILL : flow;
            kind = kind == null ? Kind.BAR : kind;
            fill = clamp(fill);
            threshold = readiness == Readiness.FULL ? 1f : clamp(threshold);
            detail = detail == null ? "" : detail;
        }
        public float target() { return readiness == Readiness.CONTINUOUS ? 0f : threshold; }
    }

    /**
     * A provider owns the resource and the decision to consume it. A finite threshold
     * overrides @GaugeConnect.threshold for this sample, so variable costs and limits
     * can be displayed from the very same calculation used by the consuming module.
     * NaN means 'use the annotation default'. All values are fractions of the bar.
     */
    public record Sample(float fill, boolean ready, boolean visible, Flow flow, String detail,
                         float threshold) {
        public Sample {
            fill = clamp(fill);
            detail = detail == null ? "" : detail;
            threshold = Float.isFinite(threshold) ? clamp(threshold) : Float.NaN;
        }
        public Sample(float fill, boolean ready, boolean visible, Flow flow, String detail) {
            this(fill, ready, visible, flow, detail, Float.NaN);
        }
        public Sample(float fill, boolean ready, boolean visible) {
            this(fill, ready, visible, null, "", Float.NaN);
        }
    }

    /** Minimum remaining fraction before another fixed-cost use can be attempted. */
    public static float consumptionThreshold(double capacity, double usableLimit, double cost) {
        if (!Double.isFinite(capacity) || !Double.isFinite(usableLimit) || !Double.isFinite(cost)
                || capacity <= 0.0D || cost < 0.0D) return 1f;
        return clamp((float) ((capacity - usableLimit + cost) / capacity));
    }

    /** Actual resource budget gate; caller also checks its non-resource prerequisites. */
    public static boolean canConsume(double used, double usableLimit, double cost) {
        return Double.isFinite(used) && Double.isFinite(usableLimit) && Double.isFinite(cost)
                && cost >= 0.0D && used + cost <= usableLimit;
    }

    private record Connection(GaugeConnect meta, MethodHandle getter) {}
    private static final ClassValue<List<Connection>> CONNECTIONS = new ClassValue<>() {
        @Override protected List<Connection> computeValue(Class<?> type) {
            List<Connection> found = new ArrayList<>();
            for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
                for (Method method : cursor.getDeclaredMethods()) {
                    GaugeConnect meta = method.getAnnotation(GaugeConnect.class);
                    if (meta == null) continue;
                    if (method.getParameterCount() != 0 || method.getReturnType() != Sample.class
                            || meta.id().isBlank() || meta.labelKey().isBlank()) {
                        throw new IllegalStateException("Invalid @GaugeConnect method: " + method);
                    }
                    try {
                        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cursor, MethodHandles.lookup());
                        found.add(new Connection(meta, lookup.unreflect(method)));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Cannot access @GaugeConnect method: " + method, e);
                    }
                }
            }
            return List.copyOf(found);
        }
    };

    private record Live(Gauge gauge, long updatedAt, long visibleUntil) {}
    private static final long EXPIRATION_NANOS = 2_500_000_000L;
    private static final int MAX_GAUGES = 64;
    private static final Map<String, Live> LIVE = new LinkedHashMap<>();

    private GaugeRegistry() {}

    /** Updates all annotated providers on a module; metadata and method handles are cached per class. */
    public static void updateAnnotated(String owner, Object source) {
        if (source == null || owner == null || owner.isBlank()) return;
        for (Connection connection : CONNECTIONS.get(source.getClass())) {
            GaugeConnect meta = connection.meta();
            Sample sample;
            try {
                sample = (Sample) connection.getter().invoke(source);
            } catch (Throwable e) {
                remove(owner, meta.id());
                continue;
            }
            if (sample == null) {
                remove(owner, meta.id());
                continue;
            }
            float threshold = Float.isFinite(sample.threshold()) ? sample.threshold() : meta.threshold();
            Gauge gauge = new Gauge(meta.id(), I18n.get(meta.labelKey()), sample.fill(),
                    meta.readiness(), threshold, sample.ready(),
                    sample.flow() != null ? sample.flow() : meta.flow(), sample.detail(), meta.kind());
            publishSample(owner, gauge, sample.visible(), meta.hideAfterMillis());
        }
    }

    /** Direct publication remains available for dynamically created gauges. */
    public static synchronized void publish(String owner, Gauge gauge) {
        publishSample(owner, gauge, true, 0L);
    }

    public static synchronized void publishSample(String owner, Gauge gauge, boolean visible, long hideAfterMillis) {
        if (owner == null || owner.isBlank() || gauge == null) return;
        long now = System.nanoTime();
        String key = owner + ':' + gauge.id();
        Live previous = LIVE.get(key);
        if (!visible && previous == null) return;
        long until = visible ? Long.MAX_VALUE : previous.visibleUntil() == Long.MAX_VALUE
                ? now + Math.max(0L, hideAfterMillis) * 1_000_000L : previous.visibleUntil();
        if (!visible && now >= until) {
            LIVE.remove(key);
            return;
        }
        if (previous == null && LIVE.size() >= MAX_GAUGES) prune(now);
        if (previous == null && LIVE.size() >= MAX_GAUGES) return;
        LIVE.put(key, new Live(!visible && previous != null ? previous.gauge() : gauge, now, until));
    }

    public static synchronized void remove(String owner, String id) {
        if (owner != null && id != null) LIVE.remove(owner + ':' + id);
    }

    public static synchronized void removeOwner(String owner) {
        if (owner != null) LIVE.keySet().removeIf(key -> key.startsWith(owner + ':'));
    }

    public static synchronized List<Gauge> snapshot() {
        prune(System.nanoTime());
        List<Gauge> out = new ArrayList<>(LIVE.size());
        for (Map.Entry<String, Live> entry : LIVE.entrySet()) {
            Gauge gauge = entry.getValue().gauge();
            out.add(new Gauge(entry.getKey(), gauge.label(), gauge.fill(),
                    gauge.readiness(), gauge.threshold(), gauge.ready(), gauge.flow(), gauge.detail(), gauge.kind()));
        }
        return List.copyOf(out);
    }

    private static void prune(long now) {
        LIVE.values().removeIf(item -> now - item.updatedAt() > EXPIRATION_NANOS
                || now >= item.visibleUntil());
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
