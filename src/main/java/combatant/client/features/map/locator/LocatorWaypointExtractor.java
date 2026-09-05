/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.locator;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.TrackedWaypoint;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

final class LocatorWaypointExtractor {
    private static final ConcurrentHashMap<String, Field> FIELDS = new ConcurrentHashMap<>();

    private LocatorWaypointExtractor() {}

    static Extracted extract(TrackedWaypoint waypoint) {
        if (waypoint == null) return Extracted.unusable();
        String simple = waypoint.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        try {
            if (simple.contains("vec3i")) {
                Object value = read(waypoint, "vector");
                if (value instanceof Vec3i v) {
                    return new Extracted(LocatorObservationType.EXACT_POSITION,
                            v.getX(), v.getY(), v.getZ(), Double.NaN, 0.0);
                }
            }
            if (simple.contains("chunk")) {
                Object value = read(waypoint, "chunkPos");
                if (value instanceof ChunkPos c) {
                    return new Extracted(LocatorObservationType.CHUNK_POSITION,
                            c.getMinBlockX() + 8.0, Double.NaN, c.getMinBlockZ() + 8.0,
                            Double.NaN, Math.sqrt(128.0));
                }
            }
            if (simple.contains("azimuth")) {
                Object value = read(waypoint, "angle");
                if (value instanceof Number n) {
                    return new Extracted(LocatorObservationType.BEARING_ONLY,
                            Double.NaN, Double.NaN, Double.NaN,
                            normalize(Math.toRadians(n.doubleValue())), Double.NaN);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return Extracted.unusable();
    }

    private static Object read(TrackedWaypoint waypoint, String fieldName) throws ReflectiveOperationException {
        String key = waypoint.getClass().getName() + "#" + fieldName;
        Field f = FIELDS.get(key);
        if (f == null) {
            f = waypoint.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            FIELDS.put(key, f);
        }
        return f.get(waypoint);
    }

    private static double normalize(double v) {
        v %= Math.PI * 2.0;
        if (v <= -Math.PI) v += Math.PI * 2.0;
        if (v > Math.PI) v -= Math.PI * 2.0;
        return v;
    }

    record Extracted(LocatorObservationType type, double x, double y, double z,
                     double bearingRadians, double uncertaintyRadius) {
        static Extracted unusable() {
            return new Extracted(LocatorObservationType.UNUSABLE,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }
}
