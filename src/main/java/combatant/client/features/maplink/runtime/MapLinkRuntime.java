/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.runtime;

import combatant.client.config.subsystem.MapLinkConfig;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.features.maplink.connection.MapLinkConnection;
import combatant.client.features.maplink.connection.MapLinkConnectionFactory;
import combatant.client.features.maplink.model.MapLinkFetchContext;
import combatant.client.features.maplink.model.MapLinkFetchResult;
import combatant.client.features.maplink.model.MapLinkObservation;
import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.model.MapLinkProfileState;
import combatant.client.features.maplink.model.MapLinkProfileStatus;
import combatant.client.features.maplink.model.MapLinkRawPlayer;
import combatant.client.features.maplink.model.MapLinkSnapshot;
import combatant.client.features.maplink.net.MapLinkHttpClient;
import combatant.client.features.maplink.net.MapLinkHttpException;
import combatant.client.features.maplink.net.MapLinkParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Owns MapLink provider scheduling and immutable snapshot publication. */
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

    /** Uses the fallback profile only when no persisted profile matches the current server. */
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
            Map<String, String> worldMappings;
            Map<String, String> learnedMappings;
            synchronized (lock) {
                if (!isCurrentLocked(runtime, profile)) {
                    runtime.inFlight = false;
                    return;
                }
                learnedMappings = learnWorldMappingsLocked(runtime, profile, result, context);
                worldMappings = Map.copyOf(runtime.worldMappings);
            }
            List<MapLinkObservation> observations = mapObservations(profile, result, context, success, worldMappings);
            long cadence = result.suggestedPollIntervalMs();

            synchronized (lock) {
                // Reject responses captured under a different server/profile generation.
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
                runtime.state = new MapLinkProfileState(profile.id(), MapLinkProfileStatus.LIVE,
                        attempt, success, "", observations.size(), result.providerWorlds(), cadence, 0);
                publishLocked(success);
            }
            if (!learnedMappings.isEmpty()) {
                Map<String, String> learnedCopy = Map.copyOf(learnedMappings);
                Minecraft.getInstance().execute(() -> config.rememberDimensionMappings(profile.id(), learnedCopy));
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
                                                     long fetchTime,
                                                     Map<String, String> worldMappings) {
        List<MapLinkObservation> out = new ArrayList<>(result.players().size());
        for (MapLinkRawPlayer player : result.players()) {
            if (player == null || player.name().isBlank()) continue;
            UUID resolved = player.uuid() != null ? player.uuid() : context.resolveKnownUuid(player.name());
            String mapped = mapProviderWorld(profile, player.providerWorld(), context.currentWorldId(), worldMappings);
            out.add(new MapLinkObservation(profile.id(), profile.providerType(), player.name(), resolved,
                    player.providerWorld(), mapped, player.x(), player.y(), player.z(), fetchTime,
                    player.providerTimestamp(), player.revision()));
        }
        return List.copyOf(out);
    }

    private static Map<String, String> learnWorldMappingsLocked(ProfileRuntime runtime,
                                                        MapLinkProfile profile,
                                                        MapLinkFetchResult result,
                                                        MapLinkFetchContext context) {
        if (runtime == null || result == null || context == null) return Map.of();
        // Older configs are recovery hints. New aliases are learned from our own web-map position.
        Map<String, String> persistedMappings = profile.dimensionMappings();
        runtime.worldMappings.putAll(persistedMappings);
        String localName = context.localPlayerName();
        String currentWorld = MapLinkProfile.normalizeWorld(context.currentWorldId());
        if (localName == null || localName.isBlank() || currentWorld.isBlank()) return Map.of();
        Map<String, String> learned = new LinkedHashMap<>();
        for (MapLinkRawPlayer player : result.players()) {
            if (player == null || !localName.equalsIgnoreCase(player.name())) continue;
            String providerWorld = MapLinkProfile.normalizeWorld(player.providerWorld());
            if (providerWorld.isBlank()) continue;
            runtime.worldMappings.put(providerWorld, currentWorld);
            if (!currentWorld.equals(persistedMappings.get(providerWorld))) {
                learned.put(providerWorld, currentWorld);
            }
        }
        return learned.isEmpty() ? Map.of() : Map.copyOf(learned);
    }

    private static String mapProviderWorld(MapLinkProfile profile,
                                           String providerWorld,
                                           String currentWorld,
                                           Map<String, String> worldMappings) {
        String normalized = MapLinkProfile.normalizeWorld(providerWorld);
        if (normalized.isBlank()) return MapLinkProfile.normalizeWorld(currentWorld);
        if (worldMappings != null) {
            String learned = worldMappings.get(normalized);
            if (learned != null && !learned.isBlank()) return MapLinkProfile.normalizeWorld(learned);
        }
        String normalizedCurrent = MapLinkProfile.normalizeWorld(currentWorld);
        if (normalized.equals(normalizedCurrent)) return normalized;
        return profile.mapWorld(normalized);
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
        if (legacyFallback != null
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
                boolean reconnect = requiresConnectionRefresh(runtime.profile, profile);
                runtime.profile = profile;
                runtime.worldMappings.putAll(profile.dimensionMappings());
                if (reconnect) {
                    runtime.connection = null;
                    runtime.nextAttemptMs = 0L;
                    runtime.failures = 0;
                }
                changed = true;
            }
        }
        return changed;
    }

    private static boolean requiresConnectionRefresh(MapLinkProfile previous, MapLinkProfile next) {
        if (previous == null || next == null) return true;
        return previous.providerType() != next.providerType()
                || !Objects.equals(previous.baseUrl(), next.baseUrl())
                || previous.maxUpdateDelayMs() != next.maxUpdateDelayMs()
                || previous.defaultY() != next.defaultY()
                || !Objects.equals(previous.requestHeaders(), next.requestHeaders());
    }

    private boolean markStaleLocked(long now) {
        boolean changed = false;
        for (ProfileRuntime runtime : runtimes.values()) {
            if (runtime.lastSuccessMs <= 0 || runtime.inFlight) continue;
            long staleThreshold = Math.max(config.staleAfterMs(), Math.max(1_000L, runtime.cadenceMs) * 2L);
            if (now - runtime.lastSuccessMs > staleThreshold
                    && runtime.state.status() == MapLinkProfileStatus.LIVE) {
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
        private final Map<String, String> worldMappings = new LinkedHashMap<>();
        private MapLinkProfileState state;

        private ProfileRuntime(MapLinkProfile profile) {
            this.profile = profile;
            this.worldMappings.putAll(profile.dimensionMappings());
            this.state = new MapLinkProfileState(profile.id(), MapLinkProfileStatus.IDLE,
                    0L, 0L, "", 0, Set.of(), 0L, 0);
        }
    }
}
