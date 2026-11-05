/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.locator;

import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.map.heuristic.HeuristicObservation;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import combatant.client.features.map.location.LocationSessionKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.waypoints.ClientWaypointManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class LocatorRuntime {
    private static final LocatorRuntime INSTANCE = new LocatorRuntime();
    private final AtomicReference<List<LocatorObservation>> published = new AtomicReference<>(List.of());
    private final Map<String, Revision> revisions = new HashMap<>();
    private final Map<String, Long> sourceGenerations = new HashMap<>();
    private final Set<String> presentKeys = new HashSet<>();
    private LocationSessionKey activeSession;

    private LocatorRuntime() {}
    public static LocatorRuntime get() { return INSTANCE; }
    public List<LocatorObservation> snapshot() { return published.get(); }

    public void capture(Minecraft mc, LocationSessionKey session) {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null || session == null) {
            clear();
            return;
        }
        synchronized (this) {
            if (!session.equals(activeSession)) resetForSession(session);
        }

        ClientWaypointManager manager = mc.getConnection().getWaypointManager();
        if (manager == null || !manager.hasWaypoints()) {
            markAllAbsent();
            published.set(List.of());
            return;
        }

        double ox = mc.player.getX(), oy = mc.player.getY(), oz = mc.player.getZ();
        long now = System.currentTimeMillis();
        List<LocatorObservation> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, UUID> onlineByName = new HashMap<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            onlineByName.put(info.getProfile().name().toLowerCase(Locale.ROOT), info.getProfile().id());
        }

        manager.forEachWaypoint(mc.player, wp -> {
            UUID uuid = wp.id().left().orElse(null);
            String name = wp.id().right().orElse("");
            UUID onlineResolved = null;
            if (uuid == null && !name.isBlank()) {
                onlineResolved = onlineByName.get(name.toLowerCase(Locale.ROOT));
                uuid = onlineResolved;
            }
            if (uuid == null && !name.isBlank() && MapTriangulationConfig.get().isTargetedName(name)) {
                uuid = MapTriangulationConfig.offlineTargetUuid(name);
            }
            if (uuid != null && uuid.equals(mc.player.getUUID())) return;
            LocatorWaypointExtractor.Extracted e = LocatorWaypointExtractor.extract(wp);
            if (e.type() == LocatorObservationType.UNUSABLE) return;

            // Prefer the stable waypoint name for name-only targets. This also keeps the locator
            // generation continuous when the same named target later resolves to its real UUID.
            String key = !name.isBlank()
                    ? "name:" + name.toLowerCase(Locale.ROOT)
                    : uuid != null ? "uuid:" + uuid : "unknown";
            boolean reappeared = wasAbsent(key);
            long generation = sourceGeneration(key);
            long fp = fingerprint(key, e, ox, oz);
            long rev = revision(key, fp, reappeared);
            seen.add(key);

            if (onlineResolved != null && !name.isBlank()) {
                UUID synthetic = MapTriangulationConfig.offlineTargetUuid(name);
                if (!synthetic.equals(onlineResolved)) HeuristicRuntime.get().clear(synthetic);
            }

            double minimumHorizontalRange = e.type() == LocatorObservationType.BEARING_ONLY
                    ? safeHorizontalRangeFloor(mc, oy, LocatorWaypointExtractor.AZIMUTH_MIN_DISTANCE_3D)
                    : 0.0;
            LocatorObservation observation = new LocatorObservation(uuid, name, e.type(), ox, oz,
                    e.x(), e.y(), e.z(), e.bearingRadians(), e.uncertaintyRadius(), minimumHorizontalRange,
                    now, rev, generation);
            out.add(observation);
            if (uuid != null && e.type() == LocatorObservationType.BEARING_ONLY) {
                HeuristicRuntime.get().offer(new HeuristicObservation(uuid, name, session, generation,
                        ox, oz, e.bearingRadians(), minimumHorizontalRange, now, rev, 1.0));
            }
        });

        synchronized (this) {
            presentKeys.clear();
            presentKeys.addAll(seen);
        }
        published.set(List.copyOf(out));
    }

    public synchronized void clear() {
        activeSession = null;
        published.set(List.of());
        revisions.clear();
        sourceGenerations.clear();
        presentKeys.clear();
    }

    private synchronized void resetForSession(LocationSessionKey session) {
        activeSession = session;
        published.set(List.of());
        revisions.clear();
        sourceGenerations.clear();
        presentKeys.clear();
    }

    private synchronized void markAllAbsent() {
        presentKeys.clear();
    }

    private synchronized boolean wasAbsent(String key) {
        return !presentKeys.contains(key);
    }

    private synchronized long sourceGeneration(String key) {
        if (presentKeys.contains(key)) return sourceGenerations.getOrDefault(key, 1L);
        long next = sourceGenerations.getOrDefault(key, 0L) + 1L;
        sourceGenerations.put(key, next);
        return next;
    }

    private synchronized long revision(String key, long fp, boolean newPresence) {
        Revision r = revisions.get(key);
        if (r == null) {
            revisions.put(key, new Revision(fp, 1));
            return 1;
        }
        if (newPresence || r.fingerprint() != fp) {
            long n = r.revision() + 1;
            revisions.put(key, new Revision(fp, n));
            return n;
        }
        return r.revision();
    }

    /**
     * Converts the vanilla 3D azimuth threshold into a conservative X/Z-only lower bound.
     * The solver does not know target Y, so we subtract the largest vertical separation that is
     * physically possible in the current dimension. If height alone could explain the 332-block
     * distance, the safe horizontal floor is zero rather than making up a stronger constraint.
     */
    private static double safeHorizontalRangeFloor(Minecraft mc, double observerY, double minDistance3d) {
        if (mc == null || mc.level == null || !(minDistance3d > 0.0) || !Double.isFinite(observerY)) return 0.0;
        double minY = mc.level.getMinY();
        double maxY = mc.level.getMaxY();
        double maxVertical = Math.max(Math.abs(observerY - minY), Math.abs(maxY - observerY));
        double squared = minDistance3d * minDistance3d - maxVertical * maxVertical;
        return squared > 0.0 ? Math.sqrt(squared) : 0.0;
    }

    private static long fingerprint(String key, LocatorWaypointExtractor.Extracted e,
                                    double observerX, double observerZ) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, key.hashCode()); h = mix(h, e.type().ordinal());
        h = mix(h, Double.doubleToLongBits(e.x())); h = mix(h, Double.doubleToLongBits(e.y()));
        h = mix(h, Double.doubleToLongBits(e.z())); h = mix(h, Double.doubleToLongBits(e.bearingRadians()));
        if (e.type() == LocatorObservationType.BEARING_ONLY) {
            h = mix(h, (long) Math.floor(observerX)); h = mix(h, (long) Math.floor(observerZ));
        }
        return h;
    }

    private static long mix(long h, long v) { return (h ^ v) * 0x100000001b3L; }
    private record Revision(long fingerprint, long revision) {}
}
