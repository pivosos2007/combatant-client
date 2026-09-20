/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Numerical/quality controls for the foundational HDR post stages. */
public final class DeferredPostConfig {
    public static final int HISTOGRAM_BINS = 256;
    private static final AtomicReference<Snapshot> CURRENT =
            new AtomicReference<>(Snapshot.fromSystemProperties());

    private DeferredPostConfig() {
    }

    /** Immutable numerical policy sampled by post passes/resource sizing. */
    public static Snapshot current() {
        return CURRENT.get();
    }

    public static void apply(Snapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    /** Re-reads developer defaults; the future quality/profile layer can publish via {@link #apply}. */
    public static void reloadSystemDefaults() {
        apply(Snapshot.fromSystemProperties());
    }

    public record Snapshot(
            float histogramMinLogLuminance,
            float histogramMaxLogLuminance,
            int histogramMaxSamples,
            float bloomThreshold,
            float bloomSoftKnee,
            float bloomIntensity,
            float bloomInitialScale,
            int bloomMaxMipCount,
            boolean exposureEnabled,
            boolean bloomEnabled
    ) {
        public Snapshot {
            histogramMinLogLuminance = finite(histogramMinLogLuminance, -16.0f);
            histogramMaxLogLuminance = finite(histogramMaxLogLuminance, 16.0f);
            if (histogramMaxLogLuminance <= histogramMinLogLuminance + 1.0f) {
                histogramMaxLogLuminance = histogramMinLogLuminance + 1.0f;
            }
            histogramMaxSamples = clamp(histogramMaxSamples, 4096, 1_048_576);
            bloomThreshold = Math.max(0.0f, finite(bloomThreshold, 1.0f));
            bloomSoftKnee = clamp(finite(bloomSoftKnee, 0.5f), 0.0f, 1.0f);
            bloomIntensity = Math.max(0.0f, finite(bloomIntensity, 0.05f));
            bloomInitialScale = clamp(finite(bloomInitialScale, 0.5f), 0.125f, 1.0f);
            bloomMaxMipCount = clamp(bloomMaxMipCount, 1, 12);
        }

        static Snapshot fromSystemProperties() {
            Snapshot defaults = defaults();
            return new Snapshot(
                    floatProperty("combatant.render.post.exposure.histogramMinLog", defaults.histogramMinLogLuminance),
                    floatProperty("combatant.render.post.exposure.histogramMaxLog", defaults.histogramMaxLogLuminance),
                    intProperty("combatant.render.post.exposure.histogramMaxSamples", defaults.histogramMaxSamples),
                    floatProperty("combatant.render.post.bloom.threshold", defaults.bloomThreshold),
                    floatProperty("combatant.render.post.bloom.softKnee", defaults.bloomSoftKnee),
                    floatProperty("combatant.render.post.bloom.intensity", defaults.bloomIntensity),
                    floatProperty("combatant.render.post.bloom.initialScale", defaults.bloomInitialScale),
                    intProperty("combatant.render.post.bloom.maxMips", defaults.bloomMaxMipCount),
                    booleanProperty("combatant.render.post.exposure.enabled", defaults.exposureEnabled),
                    booleanProperty("combatant.render.post.bloom.enabled", defaults.bloomEnabled)
            );
        }

        public static Snapshot defaults() {
            return new Snapshot(-16.0f, 16.0f, 262_144,
                    1.0f, 0.5f, 0.05f, 0.5f, 8, true, true);
        }


        private static boolean booleanProperty(String key, boolean fallback) {
            String value = System.getProperty(key);
            return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
        }

        private static int intProperty(String key, int fallback) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static float floatProperty(String key, float fallback) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            try {
                return Float.parseFloat(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float finite(float value, float fallback) {
            return Float.isFinite(value) ? value : fallback;
        }
    }
}
