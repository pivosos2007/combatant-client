/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.map.maplink.runtime;

import combatant.client.config.subsystem.MapLinkConfig;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.map.maplink.connection.MapLinkConnection;
import combatant.client.util.map.maplink.connection.MapLinkConnectionFactory;
import combatant.client.util.map.maplink.model.MapLinkFetchContext;
import combatant.client.util.map.maplink.model.MapLinkFetchResult;
import combatant.client.util.map.maplink.model.MapLinkObservation;
import combatant.client.util.map.maplink.model.MapLinkProfile;
import combatant.client.util.map.maplink.model.MapLinkProfileState;
import combatant.client.util.map.maplink.model.MapLinkProfileStatus;
import combatant.client.util.map.maplink.model.MapLinkRawPlayer;
import combatant.client.util.map.maplink.model.MapLinkSnapshot;
import combatant.client.util.map.maplink.net.MapLinkHttpClient;
import combatant.client.util.map.maplink.net.MapLinkHttpException;
import combatant.client.util.map.maplink.net.MapLinkParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backend-only MapLink scheduler/provider owner.
 * Minecraft state is captured on the caller thread; worker code receives immutable values only.
 */
public final class MapLinkRuntime {
    private static final MapLinkRuntime INSTANCE = new MapLinkRuntime();

    private final MapLinkConfig config = MapLinkConfig.get();
    private final MapLinkHttpClient http = new MapLinkHttpClient(config);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Combatant-MapLink");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    private final Map<String, ProfileRuntime> runtimes = new LinkedHashMap<>();
    private final AtomicReference<MapLinkSnapshot> published = new AtomicReference<>(MapLinkSnapshot.empty());
    private final AtomicLong generation = new AtomicLong();
    private volatile String activeServer = "";
    private volatile MapLinkProfile legacyProfileFallback;

    private MapLinkRuntime() {
    }

    public static MapLinkRuntime get() {
        return INSTANCE;
    }

    public MapLinkSnapshot snapshot() {
        return published.get();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        touch(Minecraft.getInstance(), null);
    }

    public void shutdown() {
        worker.shutdownNow();
        resetForNoServer();
    }

    /**
     * Touches the runtime from an existing client call site. A legacy profile is used only when
     * no persisted MapLink profile matches the current server.
     */
    public MapLinkSnapshot touch(Minecraft mc, MapLinkProfile legacyFallback) {
        Capture capture = capture(mc);
        if (capture == null || !config.enabled()) {
            resetForNoServer();
            return published.get();
        }

        if (legacyFallback != null) legacyProfileFallback = legacyFallback;
        List<MapLinkProfile> profiles = matchingProfiles(capture.serverAddress, legacyFallback != null ? legacyFallback : legacyProfileFallback);
        long now = System.currentTimeMillis();
        synchronized (lock) {
            boolean changed = false;
            if (!activeServer.equalsIgnoreCase(capture.serverAddress)) {
                activeServer = capture.serverAddress;
                runtimes.clear();
                changed = true;
                if (legacyProfileFallback != null
                        && !MapLinkServerMatcher.matches(legacyProfileFallback.serverMatcher(), capture.serverAddress)) {
                    legacyProfileFallback = null;
                }
            }
            changed |= reconcileProfiles(profiles);
            for (MapLinkProfile profile : profiles) {
                ProfileRuntime runtime = runtimes.get(profile.id());
                if (runtime == null) {
                    runtime = new ProfileRuntime(profile);
                    runtimes.put(profile.id(), runtime);
                    changed = true;
                }
                if (runtime.inFlight || now < runtime.nextAttemptMs) continue;
                runtime.inFlight = true;
                runtime.state = new MapLinkProfileState(profile.id(), MapLinkProfileStatus.CONNECTING,
                        now, runtime.lastSuccessMs, "", runtime.observations.size(), runtime.providerWorlds,
                        runtime.cadenceMs, runtime.failures);
                changed = true;
                ProfileRuntime scheduledRuntime = runtime;
                worker.execute(() -> refreshProfile(scheduledRuntime, profile, capture.context));
            }
            changed |= markStaleLocked(now);
            if (changed) publishLocked(now);
        }
        return published.get();
    }

    public List<MapLinkObservation> observationsForServer(String serverAddress) {
        if (serverAddress == null || !activeServer.equalsIgnoreCase(serverAddress.trim())) return List.of();
        return published.get().observations();
    }

    private void refreshProfile(ProfileRuntime runtime, MapLinkProfile profile, MapLinkFetchContext context) {
        long attempt = System.currentTimeMillis();
        try {
            MapLinkConnection connection;
            synchronized (lock) {
                if (!isCurrentLocked(runtime, profile)) {
                    runtime.inFlight = false;
                    return;
                }
                if (runtime.connection == null) {
                    runtime.connection = MapLinkConnectionFactory.create(profile, http);
                }
                connection = runtime.connection;
            }

            MapLinkFetchResult result = connection.fetchPlayers(context);
            long success = System.currentTimeMillis();
            List<MapLinkObservation> observations = mapObservations(profile, result, context, success);
            boolean unmapped = profile.hasExplicitWorldMappings()
                    && result.players().stream().anyMatch(player -> !profile.hasMappingFor(player.providerWorld()));
            long cadence = profile.refreshIntervalMs() > 0 ? profile.refreshIntervalMs() : result.suggestedPollIntervalMs();

            synchronized (lock) {
                // A server/profile switch may happen while HTTP is in flight. Never publish a stale result
                // into a newly reconciled runtime that happens to reuse the same profile id.
                if (!isCurrentLocked(runtime, profile)) {
                    runtime.inFlight = false;
                    return;
                }
                runtime.observations = observations;
                runtime.providerWorlds = result.providerWorlds();
                runtime.lastSuccessMs = success;
                runtime.failures = 0;
                runtime.cadenceMs = cadence;
                runtime.nextAttemptMs = success + cadence;
                runtime.inFlight = false;
                runtime.state = new MapLinkProfileState(profile.id(),
                        unmapped ? MapLinkProfileStatus.WORLD_UNMAPPED : MapLinkProfileStatus.LIVE,
                        attempt, success,
                        unmapped ? "One or more provider worlds have no explicit mapping" : "",
                        observations.size(), result.providerWorlds(), cadence, 0);
                publishLocked(success);
            }
            DebugLog.server("MapLink %s/%s: %d player(s), next %d ms",
                    profile.id(), profile.providerType(), observations.size(), cadence);
        } catch (Exception e) {
            long failedAt = System.currentTimeMillis();
            synchronized (lock) {
                if (!isCurrentLocked(runtime, profile)) {
                    runtime.inFlight = false;
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    return;
                }
                runtime.failures++;
                long previous = runtime.cadenceMs > 0 ? runtime.cadenceMs : 1_000L;
                long backoff = Math.min(config.maxBackoffMs(), Math.max(1_000L, previous << Math.min(runtime.failures, 8)));
                runtime.nextAttemptMs = failedAt + backoff;
                runtime.inFlight = false;
                MapLinkProfileStatus status = classify(e);
                runtime.state = new MapLinkProfileState(profile.id(), status, attempt, runtime.lastSuccessMs,
                        compactError(e), runtime.observations.size(), runtime.providerWorlds, backoff, runtime.failures);
                publishLocked(failedAt);
            }
            DebugLog.server("MapLink %s/%s failed: %s", profile.id(), profile.providerType(), compactError(e));
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private boolean isCurrentLocked(ProfileRuntime runtime, MapLinkProfile profile) {
        return runtimes.get(profile.id()) == runtime && runtime.profile.equals(profile);
    }

    private List<MapLinkObservation> mapObservations(MapLinkProfile profile,
                                                     MapLinkFetchResult result,
                                                     MapLinkFetchContext context,
                                                     long fetchTime) {
        List<MapLinkObservation> out = new ArrayList<>(result.players().size());
        for (MapLinkRawPlayer player : result.players()) {
            if (player == null || player.name().isBlank()) continue;
            UUID resolved = player.uuid() != null ? player.uuid() : context.resolveKnownUuid(player.name());
            String mapped = profile.mapWorld(player.providerWorld());
            out.add(new MapLinkObservation(profile.id(), profile.providerType(), player.name(), resolved,
                    player.providerWorld(), mapped, player.x(), player.y(), player.z(), fetchTime,
                    player.providerTimestamp(), player.revision(), profile.sourcePriority()));
        }
        return List.copyOf(out);
    }

    private Capture capture(Minecraft mc) {
        if (mc == null || mc.hasSingleplayerServer() || mc.getCurrentServer() == null || mc.player == null || mc.level == null) {
            return null;
        }
        ServerData server = mc.getCurrentServer();
        String address = server.ip == null ? "" : server.ip.trim().toLowerCase(Locale.ROOT);
        if (address.isBlank()) return null;

        String localName = mc.player.getGameProfile() == null ? "" : mc.player.getGameProfile().name();
        String world = mc.level.dimension().identifier().toString();
        Map<String, UUID> known = new HashMap<>();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
                known.put(info.getProfile().name().toLowerCase(Locale.ROOT), info.getProfile().id());
            }
        }
        return new Capture(address, new MapLinkFetchContext(localName, world, known));
    }

    private List<MapLinkProfile> matchingProfiles(String serverAddress, MapLinkProfile legacyFallback) {
        List<MapLinkProfile> matched = config.profiles().stream()
                .filter(profile -> MapLinkServerMatcher.matches(profile.serverMatcher(), serverAddress))
                .toList();
        if (!matched.isEmpty()) return matched;
        if (legacyFallback != null && legacyFallback.enabled()
                && MapLinkServerMatcher.matches(legacyFallback.serverMatcher(), serverAddress)) {
            return List.of(legacyFallback);
        }
        return List.of();
    }

    private boolean reconcileProfiles(List<MapLinkProfile> profiles) {
        boolean changed = false;
        Set<String> ids = profiles.stream().map(MapLinkProfile::id).collect(java.util.stream.Collectors.toSet());
        java.util.Iterator<Map.Entry<String, ProfileRuntime>> iterator = runtimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ProfileRuntime> entry = iterator.next();
            if (!ids.contains(entry.getKey())) {
                iterator.remove();
                changed = true;
            }
        }
        for (MapLinkProfile profile : profiles) {
            ProfileRuntime runtime = runtimes.get(profile.id());
            if (runtime != null && !runtime.profile.equals(profile)) {
                runtime.profile = profile;
                runtime.connection = null;
                runtime.nextAttemptMs = 0L;
                runtime.failures = 0;
                changed = true;
            }
        }
        return changed;
    }

    private boolean markStaleLocked(long now) {
        boolean changed = false;
        for (ProfileRuntime runtime : runtimes.values()) {
            if (runtime.lastSuccessMs <= 0 || runtime.inFlight) continue;
            long staleThreshold = Math.max(config.staleAfterMs(), Math.max(1_000L, runtime.cadenceMs) * 2L);
            if (now - runtime.lastSuccessMs > staleThreshold
                    && (runtime.state.status() == MapLinkProfileStatus.LIVE
                    || runtime.state.status() == MapLinkProfileStatus.WORLD_UNMAPPED)) {
                runtime.state = new MapLinkProfileState(runtime.profile.id(), MapLinkProfileStatus.STALE,
                        runtime.state.lastAttemptMs(), runtime.lastSuccessMs,
                        runtime.state.detail(), runtime.observations.size(), runtime.providerWorlds,
                        runtime.cadenceMs, runtime.failures);
                changed = true;
            }
        }
        return changed;
    }

    private void publishLocked(long now) {
        List<MapLinkObservation> observations = new ArrayList<>();
        Map<String, MapLinkProfileState> states = new LinkedHashMap<>();
        for (ProfileRuntime runtime : runtimes.values()) {
            observations.addAll(runtime.observations);
            states.put(runtime.profile.id(), runtime.state);
        }
        observations.sort((a, b) -> Integer.compare(b.sourcePriority(), a.sourcePriority()));
        published.set(new MapLinkSnapshot(generation.incrementAndGet(), now, observations, states));
    }

    private void resetForNoServer() {
        synchronized (lock) {
            if (runtimes.isEmpty() && activeServer.isEmpty() && legacyProfileFallback == null
                    && published.get().observations().isEmpty() && published.get().profileStates().isEmpty()) {
                return;
            }
            runtimes.clear();
            activeServer = "";
            legacyProfileFallback = null;
            published.set(new MapLinkSnapshot(generation.incrementAndGet(), System.currentTimeMillis(), List.of(), Map.of()));
        }
    }

    private static MapLinkProfileStatus classify(Exception error) {
        if (error instanceof MapLinkParseException) return MapLinkProfileStatus.PARSE_ERROR;
        if (error instanceof MapLinkHttpException http) {
            if (http.statusCode() == 401 || http.statusCode() == 403) return MapLinkProfileStatus.AUTH_ERROR;
            return MapLinkProfileStatus.HTTP_ERROR;
        }
        return MapLinkProfileStatus.HTTP_ERROR;
    }

    private static String compactError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private record Capture(String serverAddress, MapLinkFetchContext context) {
    }

    private static final class ProfileRuntime {
        private MapLinkProfile profile;
        private MapLinkConnection connection;
        private boolean inFlight;
        private int failures;
        private long nextAttemptMs;
        private long lastSuccessMs;
        private long cadenceMs = 1_000L;
        private List<MapLinkObservation> observations = List.of();
        private Set<String> providerWorlds = Set.of();
        private MapLinkProfileState state;

        private ProfileRuntime(MapLinkProfile profile) {
            this.profile = profile;
            this.state = new MapLinkProfileState(profile.id(), profile.enabled() ? MapLinkProfileStatus.IDLE : MapLinkProfileStatus.DISABLED,
                    0L, 0L, "", 0, Set.of(), 0L, 0);
        }
    }
}
