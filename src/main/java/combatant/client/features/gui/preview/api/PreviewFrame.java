/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview.api;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable data source for a preview frame. No Minecraft world, inventory or module
 * state is modified. A producer resolves its normal inputs through this context,
 * then calls the same presentation code used by live rendering.
 */
public record PreviewFrame(Mode mode, boolean worldAvailable, String scenario,
                           Map<Key<?>, ?> samples) {
    private static final ThreadLocal<PreviewFrame> CURRENT = new ThreadLocal<>();

    public enum Mode { LIVE, SAMPLE }

    public record Key<T>(String id, Class<T> type) {
        public Key {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            Objects.requireNonNull(type, "type");
        }
    }

    public PreviewFrame {
        Objects.requireNonNull(mode, "mode");
        scenario = scenario == null ? "default" : scenario;
        samples = samples == null ? Map.of() : Map.copyOf(samples);
    }

    public static PreviewFrame current() {
        return CURRENT.get();
    }

    public static boolean isSample() {
        PreviewFrame frame = current();
        return frame != null && frame.mode == Mode.SAMPLE;
    }

    /** Returns null when a sample is unavailable; never invokes live data collectors offline. */
    public static <T> T resolve(Key<T> key, Supplier<? extends T> live) {
        Objects.requireNonNull(key, "key");
        PreviewFrame frame = current();
        if (frame != null && frame.mode == Mode.SAMPLE) {
            Object sample = frame.samples.get(key);
            return sample == null ? null : key.type.cast(sample);
        }
        return live == null ? null : live.get();
    }

    /** Nested previews always restore the prior frame, even after render failures. */
    public static Scope enter(PreviewFrame frame) {
        Objects.requireNonNull(frame, "frame");
        PreviewFrame previous = CURRENT.get();
        CURRENT.set(frame);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final PreviewFrame previous;
        private boolean closed;

        private Scope(PreviewFrame previous) { this.previous = previous; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
