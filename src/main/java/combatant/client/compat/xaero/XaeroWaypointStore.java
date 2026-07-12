/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public enum XaeroWaypointStore {
    ;

    public static final String SET = "combatant";
    public static final String DEFAULT_NAME = "Combatant";
    public static final String DEFAULT_SYMBOL = "C";

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final CopyOnWriteArrayList<Marker> MARKERS = new CopyOnWriteArrayList<>();

    public static AddResult addFromCurrentDimension(int x, int y, int z, String name, boolean dualDefault) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return new AddResult(false, 0, "No client world is loaded.");
        }

        String safeName = sanitizeName(name);
        try {
            RuntimeAddResult runtime = XaeroWaypointRuntime.addFromCurrentDimension(x, y, z, safeName, dualDefault);
            if (!runtime.success()) {
                return new AddResult(false, 0, runtime.message());
            }
            MARKERS.addAll(runtime.markers());
            return new AddResult(true, runtime.markers().size(), runtime.message());
        } catch (NoClassDefFoundError | ExceptionInInitializerError error) {
            return new AddResult(false, 0, "Xaero waypoint runtime is not available.");
        } catch (Throwable throwable) {
            return new AddResult(false, 0, "Failed to add Xaero marker: " + throwable.getClass().getSimpleName() + ".");
        }
    }

    public static List<Marker> currentDimensionMarkers() {
        pruneMissing();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || MARKERS.isEmpty()) return List.of();
        String dim = dimensionId(mc.level.dimension().identifier());
        List<Marker> out = new ArrayList<>();
        for (Marker marker : MARKERS) {
            if (marker.dimensionId().equals(dim)) {
                out.add(marker);
            }
        }
        return out;
    }

    public static List<Marker> markers() {
        pruneMissing();
        return List.copyOf(MARKERS);
    }

    public static RemoveResult remove(String selector) {
        pruneMissing();
        if (MARKERS.isEmpty()) {
            return new RemoveResult(true, 0, "No Combatant Xaero markers to remove.");
        }

        List<Marker> targets = select(selector);
        if (targets.isEmpty()) {
            return new RemoveResult(false, 0, "No matching Combatant Xaero marker: " + selector + ".");
        }

        int removed = 0;
        for (Marker marker : targets) {
            if (removeMarker(marker)) {
                removed++;
            }
        }
        if (removed == 0) {
            return new RemoveResult(false, 0, "No Combatant Xaero markers were removed.");
        }
        return new RemoveResult(true, removed, "Removed Xaero markers: " + removed + ".");
    }

    public static int clear() {
        return remove(null).count();
    }

    static Marker createMarker(String dimensionId,
                               int x,
                               int y,
                               int z,
                               String name,
                               String symbol,
                               String set,
                               String kind,
                               Object waypointSetHandle,
                               Object waypointHandle) {
        return new Marker(
                NEXT_ID.getAndIncrement(),
                dimensionId,
                x,
                y,
                z,
                name,
                symbol,
                set,
                kind,
                System.currentTimeMillis(),
                waypointSetHandle,
                waypointHandle
        );
    }

    private static List<Marker> select(String selector) {
        String normalized = selector == null ? "" : selector.trim();
        if (normalized.isEmpty()
                || "all".equalsIgnoreCase(normalized)
                || "*".equals(normalized)) {
            return new ArrayList<>(MARKERS);
        }

        Integer index = parsePositiveInt(normalized);
        if (index != null) {
            List<Marker> snapshot = markers();
            int zeroIndex = index - 1;
            if (zeroIndex >= 0 && zeroIndex < snapshot.size()) {
                return List.of(snapshot.get(zeroIndex));
            }
            return List.of();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        List<Marker> out = new ArrayList<>();
        for (Marker marker : MARKERS) {
            if (marker.name().toLowerCase(Locale.ROOT).contains(lower)
                    || marker.kind().toLowerCase(Locale.ROOT).contains(lower)
                    || marker.dimensionId().toLowerCase(Locale.ROOT).contains(lower)) {
                out.add(marker);
            }
        }
        return out;
    }

    private static boolean removeMarker(Marker marker) {
        boolean removedFromXaero = true;
        try {
            removedFromXaero = XaeroWaypointRuntime.remove(marker);
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            removedFromXaero = true;
        } catch (Throwable ignored) {
            removedFromXaero = false;
        }
        boolean removedFromStore = MARKERS.remove(marker);
        return removedFromStore && removedFromXaero;
    }

    private static void pruneMissing() {
        if (MARKERS.isEmpty()) return;
        try {
            Iterator<Marker> iterator = MARKERS.iterator();
            while (iterator.hasNext()) {
                Marker marker = iterator.next();
                if (!XaeroWaypointRuntime.exists(marker)) {
                    MARKERS.remove(marker);
                }
            }
        } catch (NoClassDefFoundError | ExceptionInInitializerError ignored) {
            MARKERS.clear();
        } catch (Throwable ignored) {
            // Keep the in-memory index if Xaero is mid-session reload or unavailable for a tick.
        }
    }

    static int netherCoord(int overworldCoord) {
        return (int) Math.round(overworldCoord / 8.0);
    }

    static String dimensionId(Identifier id) {
        return id == null ? "" : id.toString();
    }

    static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return DEFAULT_NAME;
        String trimmed = name.trim();
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
    }

    static String displayName(String name, String kind) {
        String safeName = sanitizeName(name);
        if (kind == null || kind.isBlank() || kind.contains(":")) return safeName;
        return safeName + " " + kind;
    }

    static String format(int x, int y, int z) {
        return String.format(Locale.ROOT, "%d %d %d", x, y, z);
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed <= 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isOverworld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    static boolean isNether() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
    }

    public record Marker(long id,
                         String dimensionId,
                         int x,
                         int y,
                         int z,
                         String name,
                         String symbol,
                         String set,
                         String kind,
                         long createdAtMs,
                         Object waypointSetHandle,
                         Object waypointHandle) {
    }

    public record AddResult(boolean success, int count, String message) {
    }

    public record RemoveResult(boolean success, int count, String message) {
    }

    static record RuntimeAddResult(boolean success, String message, List<Marker> markers) {
    }
}
