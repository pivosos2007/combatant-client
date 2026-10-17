/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.runtime;

import combatant.client.config.subsystem.DuplexLocalConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.config.subsystem.MapHeuristicConfig;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.map.duplex.DuplexBearingSample;
import combatant.client.features.map.duplex.DuplexEstimate;
import combatant.client.features.map.duplex.DuplexRuntime;
import combatant.client.features.map.heuristic.HeuristicEstimate;
import combatant.client.features.map.heuristic.HeuristicLifecycleEvent;
import combatant.client.features.map.heuristic.HeuristicLifecycleEventType;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import combatant.client.features.map.location.LocationSessionKey;
import combatant.client.features.map.location.PlayerLocationEvent;
import combatant.client.features.map.location.PlayerLocationEventType;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.map.location.ServerIdentity;
import combatant.client.features.map.location.WorldIdentity;
import combatant.client.features.map.locator.LocatorObservation;
import combatant.client.features.map.locator.LocatorObservationType;
import combatant.client.features.map.locator.LocatorRuntime;
import combatant.client.features.map.storage.MapBearingFrame;
import combatant.client.features.map.storage.MapEstimateFrame;
import combatant.client.features.map.storage.MapExactPointFrame;
import combatant.client.features.map.storage.MapHistorySource;
import combatant.client.features.map.storage.MapHistoryEventFrame;
import combatant.client.features.map.storage.MapHistoryEventKind;
import combatant.client.features.map.storage.MapHistoryStore;
import combatant.client.features.maplink.model.MapLinkObservation;
import combatant.client.features.maplink.model.MapLinkProfileState;
import combatant.client.features.maplink.model.MapLinkProfileStatus;
import combatant.client.features.maplink.model.MapLinkSnapshot;
import combatant.client.features.maplink.runtime.MapLinkRuntime;
import net.minecraft.client.Minecraft;
import combatant.client.runtime.error.FailureBoundary;
import combatant.client.runtime.error.FailureIsolation;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread coordinator for the location backend.
 *
 * <p>Mutable Minecraft state is captured here and converted into immutable location/history
 * records. MapLink exact points suppress single-client triangulation workload without being fed
 * into the solver. Storage/network/solver workers only receive immutable data.</p>
 */
public final class MapLocationRuntime {
    private static final MapLocationRuntime INSTANCE = new MapLocationRuntime();

    private final DuplexLocalConfig duplexConfig = DuplexLocalConfig.get();
    private final DuplexRuntime duplex = new DuplexRuntime();
    private final Map<UUID, Long> lastDuplexRevision = new HashMap<>();
    private final Map<UUID, Long> lastDuplexSentAt = new HashMap<>();
    private final Map<UUID, Long> lastLocatorSeenAt = new HashMap<>();
    private final MapHeuristicConfig heuristicConfig = MapHeuristicConfig.get();

    private String activeServerFingerprint = "";
    private String activeWorldFingerprint = "";
    private int activeDuplexConfigFingerprint;
    private long locationSessionGeneration;
    private LocationSessionKey activeLocationSession;
    private Object activeConnectionIdentity;

    private MapLocationRuntime() {
    }

    public static MapLocationRuntime get() {
        return INSTANCE;
    }

    public DuplexRuntime duplex() {
        return duplex;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!usable(mc)) {
            reset();
            return;
        }

        String server = serverFingerprint(mc);
        String world = mc.level.dimension().identifier().toString();
        LocationSessionKey session = reconcileLocationSession(server, world, mc.getConnection());
        reconcileDuplex(server, world);

        MapLinkSnapshot mapLink = MapLinkRuntime.get().snapshot();
        Set<UUID> exactTargets = new HashSet<>(exactMapLinkTargets(mapLink, world));
        exactTargets.addAll(localExactTargets(mc));
        HeuristicRuntime.get().setSuppressedTargets(Set.copyOf(exactTargets));

        LocatorRuntime.get().capture(mc, session);
        List<LocatorObservation> locator = LocatorRuntime.get().snapshot();
        PlayerTrackingRangeRuntime.get().capture(mc, session, locator);
        updateLocatorSeenTimes(locator);
        for (LocatorObservation observation : locator) {
            if (observation.targetUuid() != null && observation.type() == LocatorObservationType.EXACT_POSITION) {
                exactTargets.add(observation.targetUuid());
            }
        }
        Set<UUID> immutableExactTargets = Set.copyOf(exactTargets);
        HeuristicRuntime.get().setSuppressedTargets(immutableExactTargets);
        forwardDuplexBearings(locator, immutableExactTargets);
        duplex.tick();

        Map<UUID, HeuristicEstimate> heuristic = rejectLocallyImpossibleEstimates(mc, locator, HeuristicRuntime.get().snapshot());
        Map<UUID, DuplexEstimate> duplexEstimates = duplex.estimates();

        Captured captured = captureLocations(mc, server, world, mapLink, locator, heuristic, duplexEstimates);
        List<PlayerLocationEvent> locationEvents = PlayerLocationService.get().publish(captured.locations);
        persist(server, world, captured, heuristic, duplexEstimates);
        persistLocationEvents(server, locationEvents);
        persistHeuristicEvents(HeuristicRuntime.get().drainLifecycleEvents());
    }

    public void shutdown() {
        isolateTeardown("reset", this::reset);
        isolateTeardown("historyStore", () -> MapHistoryStore.get().shutdown());
    }

    /** Stops live locator/IPC state while keeping persistent storage resumable after soft panic. */
    public void suspend() {
        reset();
    }

    private Captured captureLocations(Minecraft mc,
                                      String server,
                                      String currentWorld,
                                      MapLinkSnapshot mapLink,
                                      List<LocatorObservation> locator,
                                      Map<UUID, HeuristicEstimate> heuristic,
                                      Map<UUID, DuplexEstimate> duplexEstimates) {
        long now = System.currentTimeMillis();
        ServerIdentity serverIdentity = ServerIdentity.endpoint(server);
        WorldIdentity currentWorldIdentity = WorldIdentity.dimension(currentWorld);
        List<PlayerLocationSnapshot> locations = new ArrayList<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<String, UUID> idsByName = new HashMap<>();
        Map<String, UUID> localIdsByName = new HashMap<>();
        Set<UUID> localPlayerIds = new HashSet<>();

        UUID self = mc.player.getUUID();
        String selfName = mc.player.getGameProfile() == null ? "" : clean(mc.player.getGameProfile().name());
        rememberIdentity(names, idsByName, self, selfName);
        if (!selfName.isBlank()) localIdsByName.put(selfName.toLowerCase(Locale.ROOT), self);
        localPlayerIds.add(self);
        for (Player player : mc.level.players()) {
            if (player == null || player.getUUID() == null) continue;
            UUID id = player.getUUID();
            String name = player.getName() == null ? "" : player.getName().getString();
            rememberIdentity(names, idsByName, id, name);
            if (!clean(name).isBlank()) localIdsByName.put(clean(name).toLowerCase(Locale.ROOT), id);
            localPlayerIds.add(id);
            if (id.equals(self)) continue;
            locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                    PlayerLocationSource.LOCAL_ENTITY_EXACT, now, now,
                    player.getX(), player.getY(), player.getZ(), Double.NaN,
                    0.0, 0.0, 0.0, 1.0, "local_entity", now, Map.of()));
        }

        if (mapLink != null) {
            for (MapLinkObservation observation : mapLink.observations()) {
                UUID id = observation.resolvedUuid();
                if (id == null || !observation.worldMapped()) continue;
                MapLinkProfileState state = mapLink.profileStates().get(observation.profileId());
                if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
                String name = observation.rawPlayerName();
                UUID localByName = clean(name).isBlank()
                        ? null : localIdsByName.get(clean(name).toLowerCase(Locale.ROOT));
                // MapLink is a remote/exact source. Never publish a second marker for ourselves or
                // for a player the client is already receiving as a real local entity. Name-based
                // suppression intentionally covers providers that resolved an offline/wrong UUID
                // for a player whose authoritative UUID is already present in the current level.
                if (localPlayerIds.contains(id) || (localByName != null && localPlayerIds.contains(localByName))) {
                    continue;
                }
                rememberIdentity(names, idsByName, id, name);
                long effective = observation.providerTimestamp() > 0L
                        ? observation.providerTimestamp() : observation.fetchTimestamp();
                locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, WorldIdentity.dimension(observation.mappedWorld()),
                        PlayerLocationSource.MAPLINK_EXACT, observation.fetchTimestamp(), effective,
                        observation.x(), observation.y(), observation.z(), Double.NaN,
                        0.0, 0.0, 0.0, 1.0, observation.profileId(),
                        revisionHash(observation.profileId(), observation.revision(), effective),
                        Map.of("provider", observation.providerType().name(),
                                "providerWorld", clean(observation.providerWorld()))));
            }
        }

        if (locator != null) {
            for (LocatorObservation observation : locator) {
                String cleanTargetName = clean(observation.targetName());
                UUID localByName = cleanTargetName.isBlank()
                        ? null : localIdsByName.get(cleanTargetName.toLowerCase(Locale.ROOT));
                UUID id = localByName != null
                        ? localByName : resolve(observation.targetUuid(), observation.targetName(), idsByName);
                if (id == null) continue;
                if (id.equals(self)) continue;
                String name = !clean(observation.targetName()).isBlank()
                        ? clean(observation.targetName()) : names.getOrDefault(id, "");
                rememberIdentity(names, idsByName, id, name);

                if (observation.type() == LocatorObservationType.EXACT_POSITION
                        || observation.type() == LocatorObservationType.CHUNK_POSITION) {
                    boolean exact = observation.type() == LocatorObservationType.EXACT_POSITION;
                    locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                            exact ? PlayerLocationSource.LOCATOR_EXACT : PlayerLocationSource.LOCATOR_APPROXIMATE,
                            observation.observedAtMs(), observation.observedAtMs(),
                            observation.x(), observation.y(), observation.z(), Double.NaN,
                            Math.max(0.0, observation.uncertaintyRadius()),
                            Math.max(0.0, observation.uncertaintyRadius()), 0.0,
                            exact ? 1.0 : 0.75, "locator", observation.sourceRevision(),
                            Map.of("type", observation.type().name(),
                                    "sourceGeneration", Long.toString(observation.sourceGeneration()))));
                } else if (observation.type() == LocatorObservationType.BEARING_ONLY) {
                    locations.add(new PlayerLocationSnapshot(id, name, serverIdentity, currentWorldIdentity,
                            PlayerLocationSource.LOCATOR_BEARING,
                            observation.observedAtMs(), observation.observedAtMs(),
                            Double.NaN, Double.NaN, Double.NaN, observation.bearingRadians(),
                            0.0, 0.0, 0.0, 0.25, "locator", observation.sourceRevision(),
                            Map.of("type", observation.type().name(),
                                    "observerX", Double.toString(observation.observerX()),
                                    "observerZ", Double.toString(observation.observerZ()),
                                    "sourceGeneration", Long.toString(observation.sourceGeneration()))));
                }
            }
        }

        if (heuristic != null) {
            Set<UUID> locatorPresent = locatorTargetIds(locator);
            for (HeuristicEstimate estimate : heuristic.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                UUID target = estimate.targetUuid();
                String name = names.getOrDefault(target, MapTriangulationConfig.get().nameForTarget(target));
                UUID localByName = clean(name).isBlank()
                        ? null : localIdsByName.get(clean(name).toLowerCase(Locale.ROOT));
                UUID publishedTarget = localByName != null ? localByName : target;
                if (publishedTarget.equals(self)) continue;
                boolean liveInput = locatorPresent.contains(target);
                long lastSeen = liveInput ? now : lastLocatorSeenAt.getOrDefault(target, estimate.updatedAtMs());
                long sourceLostAge = Math.max(0L, now - lastSeen);
                if (!liveInput && sourceLostAge > heuristicConfig.staleEstimateDisplayMs()) continue;
                boolean historical = !liveInput && sourceLostAge > heuristicConfig.liveSourceGraceMs();
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("samples", Integer.toString(estimate.sampleCount()));
                metadata.put("inliers", Integer.toString(estimate.inlierCount()));
                metadata.put("segment", Long.toString(estimate.segmentId()));
                metadata.put("liveInput", Boolean.toString(liveInput));
                if (!liveInput) {
                    metadata.put("lastLocatorSeenAt", Long.toString(lastSeen));
                    metadata.put("sourceLostAgeMs", Long.toString(sourceLostAge));
                }
                locations.add(new PlayerLocationSnapshot(publishedTarget, name, serverIdentity, currentWorldIdentity,
                        historical ? PlayerLocationSource.HISTORICAL : PlayerLocationSource.TRIANGULATED,
                        estimate.updatedAtMs(), estimate.updatedAtMs(),
                        estimate.x(), Double.NaN, estimate.z(), Double.NaN,
                        estimate.uncertaintyMajor(), estimate.uncertaintyMinor(),
                        estimate.uncertaintyAngleRadians(), estimate.confidence(),
                        historical ? "single_client_stale" : "single_client", estimate.updatedAtMs(),
                        Map.copyOf(metadata)));
            }
        }

        if (duplexEstimates != null) {
            for (DuplexEstimate estimate : duplexEstimates.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                UUID target = estimate.targetUuid();
                String name = names.getOrDefault(target, MapTriangulationConfig.get().nameForTarget(target));
                UUID localByName = clean(name).isBlank()
                        ? null : localIdsByName.get(clean(name).toLowerCase(Locale.ROOT));
                UUID publishedTarget = localByName != null ? localByName : target;
                if (publishedTarget.equals(self)) continue;
                locations.add(new PlayerLocationSnapshot(publishedTarget, name, serverIdentity, currentWorldIdentity,
                        PlayerLocationSource.DUPLEX_TRIANGULATED,
                        estimate.observedAtMs(), estimate.observedAtMs(),
                        estimate.x(), Double.NaN, estimate.z(), Double.NaN,
                        estimate.uncertaintyRadius(), estimate.uncertaintyRadius(), 0.0,
                        estimate.confidence(), "duplex", estimate.sourceRevision(),
                        Map.of("crossingAngle", Double.toString(estimate.crossingAngleRadians()))));
            }
        }

        return new Captured(List.copyOf(locations), Map.copyOf(names), Map.copyOf(idsByName),
                Set.copyOf(localPlayerIds), mapLink, locator);
    }

    private void persist(String server,
                         String currentWorld,
                         Captured captured,
                         Map<UUID, HeuristicEstimate> heuristic,
                         Map<UUID, DuplexEstimate> duplexEstimates) {
        MapHistoryStore history = MapHistoryStore.get();

        for (PlayerLocationSnapshot snapshot : captured.locations) {
            if (snapshot.source() != PlayerLocationSource.LOCAL_ENTITY_EXACT) continue;
            if (!persistableIdentity(snapshot.playerUuid(), snapshot.playerName())) continue;
            if (!finite3(snapshot.x(), snapshot.y(), snapshot.z())) continue;
            history.offer(server, new MapExactPointFrame(snapshot.playerUuid(), snapshot.playerName(),
                    snapshot.worldKey(), MapHistorySource.LOCAL_ENTITY_EXACT, "local_entity",
                    snapshot.observedAtMs(), snapshot.sourceRevision(),
                    snapshot.x(), snapshot.y(), snapshot.z(), 0.0));
        }

        if (captured.mapLink != null) {
            for (MapLinkObservation observation : captured.mapLink.observations()) {
                UUID id = observation.resolvedUuid();
                if (id == null || !observation.worldMapped()) continue;
                UUID localByName = clean(observation.rawPlayerName()).isBlank()
                        ? null : captured.idsByName.get(clean(observation.rawPlayerName()).toLowerCase(Locale.ROOT));
                if (captured.localPlayerIds.contains(id)
                        || (localByName != null && captured.localPlayerIds.contains(localByName))) continue;
                if (!persistableIdentity(id, observation.rawPlayerName())) continue;
                MapLinkProfileState state = captured.mapLink.profileStates().get(observation.profileId());
                if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
                if (!finite3(observation.x(), observation.y(), observation.z())) continue;
                long effective = observation.providerTimestamp() > 0L
                        ? observation.providerTimestamp() : observation.fetchTimestamp();
                history.offer(server, new MapExactPointFrame(id, observation.rawPlayerName(),
                        observation.mappedWorld(), MapHistorySource.MAPLINK_EXACT, observation.profileId(),
                        effective,
                        revisionHash(observation.profileId(), observation.revision(), effective),
                        observation.x(), observation.y(), observation.z(), 0.0));
            }
        }

        if (captured.locator != null) {
            for (LocatorObservation observation : captured.locator) {
                UUID id = resolve(observation.targetUuid(), observation.targetName(), captured.idsByName);
                if (id == null) continue;
                String name = !clean(observation.targetName()).isBlank()
                        ? clean(observation.targetName()) : captured.names.getOrDefault(id, "");
                if (!persistableIdentity(id, name)) continue;
                switch (observation.type()) {
                    case EXACT_POSITION, CHUNK_POSITION -> {
                        if (!finite3(observation.x(), observation.y(), observation.z())) continue;
                        history.offer(server, new MapExactPointFrame(id, name, currentWorld,
                                    observation.type() == LocatorObservationType.EXACT_POSITION
                                            ? MapHistorySource.LOCATOR_EXACT
                                            : MapHistorySource.LOCATOR_APPROXIMATE,
                                    "locator", observation.observedAtMs(), observation.sourceRevision(),
                                    observation.x(), observation.y(), observation.z(),
                                    Math.max(0.0, observation.uncertaintyRadius())));
                    }
                    case BEARING_ONLY -> {
                        if (!finite3(observation.observerX(), observation.bearingRadians(), observation.observerZ())) continue;
                        history.offer(server, new MapBearingFrame(id, name, currentWorld, MapHistorySource.LOCATOR_BEARING,
                                    "locator", observation.observedAtMs(), observation.sourceRevision(),
                                    observation.observerX(), observation.observerZ(),
                                    observation.bearingRadians(), 1.0));
                    }
                    default -> {
                    }
                }
            }
        }

        if (heuristic != null) {
            for (HeuristicEstimate estimate : heuristic.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                String estimateName = captured.names.getOrDefault(estimate.targetUuid(), "");
                if (!persistableIdentity(estimate.targetUuid(), estimateName)) continue;
                if (!Double.isFinite(estimate.x()) || !Double.isFinite(estimate.z())) continue;
                history.offer(server, new MapEstimateFrame(estimate.targetUuid(),
                        estimateName, currentWorld,
                        MapHistorySource.HEURISTIC_TRIANGULATED, "single_client",
                        estimate.updatedAtMs(), estimate.updatedAtMs(),
                        estimate.x(), estimate.z(), estimate.uncertaintyMajor(), estimate.uncertaintyMinor(),
                        estimate.uncertaintyAngleRadians(), estimate.residualRms(), estimate.confidence(),
                        estimate.sampleCount(), estimate.inlierCount(), estimate.segmentId()));
            }
        }

        if (duplexEstimates != null) {
            for (DuplexEstimate estimate : duplexEstimates.values()) {
                if (estimate == null || estimate.targetUuid() == null) continue;
                String estimateName = captured.names.getOrDefault(estimate.targetUuid(), "");
                if (!persistableIdentity(estimate.targetUuid(), estimateName)) continue;
                if (!Double.isFinite(estimate.x()) || !Double.isFinite(estimate.z())) continue;
                history.offer(server, new MapEstimateFrame(estimate.targetUuid(),
                        estimateName, currentWorld,
                        MapHistorySource.DUPLEX_TRIANGULATED, "duplex",
                        estimate.observedAtMs(), estimate.sourceRevision(),
                        estimate.x(), estimate.z(), estimate.uncertaintyRadius(), estimate.uncertaintyRadius(),
                        0.0, 0.0, estimate.confidence(), 2, 2, estimate.sourceRevision()));
            }
        }
    }

    private static boolean finite3(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }

    private void forwardDuplexBearings(List<LocatorObservation> observations, Set<UUID> exactTargets) {
        Set<UUID> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        for (LocatorObservation observation : observations) {
            UUID target = observation.targetUuid();
            if (target == null || observation.type() != LocatorObservationType.BEARING_ONLY
                    || exactTargets.contains(target)) {
                continue;
            }
            seen.add(target);
            long revision = observation.sourceRevision();
            Long previous = lastDuplexRevision.get(target);
            long lastSent = lastDuplexSentAt.getOrDefault(target, 0L);
            // Refresh unchanged rays at a bounded rate. Pairing uses observation time, so retaining
            // only the first timestamp makes a newly connected courier impossible to pair with a
            // perfectly valid, still-present local bearing.
            if (previous != null && previous == revision && now - lastSent < 250L) continue;

            if (duplex.publishBearing(new DuplexBearingSample(
                    target,
                    observation.observerX(),
                    observation.observerZ(),
                    observation.bearingRadians(),
                    observation.observedAtMs(),
                    revision))) {
                lastDuplexRevision.put(target, revision);
                lastDuplexSentAt.put(target, now);
            }
        }
        lastDuplexRevision.keySet().removeIf(id -> !seen.contains(id));
        lastDuplexSentAt.keySet().removeIf(id -> !seen.contains(id));
    }


    private void updateLocatorSeenTimes(List<LocatorObservation> observations) {
        long now = System.currentTimeMillis();
        if (observations != null) {
            for (LocatorObservation observation : observations) {
                if (observation != null && observation.targetUuid() != null) {
                    lastLocatorSeenAt.put(observation.targetUuid(), now);
                }
            }
        }
        long keep = Math.max(heuristicConfig.maxSampleAgeMs(), heuristicConfig.staleEstimateDisplayMs()) * 2L;
        lastLocatorSeenAt.entrySet().removeIf(entry -> now - entry.getValue() > keep);
    }

    private Map<UUID, HeuristicEstimate> rejectLocallyImpossibleEstimates(Minecraft mc,
                                                                           List<LocatorObservation> locator,
                                                                           Map<UUID, HeuristicEstimate> estimates) {
        if (estimates == null || estimates.isEmpty()) return estimates == null ? Map.of() : estimates;
        Set<UUID> locatorPresent = locatorTargetIds(locator);
        Set<UUID> local = localExactTargets(mc);
        Map<UUID, HeuristicEstimate> filtered = null;
        for (Map.Entry<UUID, HeuristicEstimate> entry : estimates.entrySet()) {
            UUID id = entry.getKey();
            HeuristicEstimate estimate = entry.getValue();
            if (id == null || estimate == null || local.contains(id) || !locatorPresent.contains(id)) continue;
            if (!PlayerTrackingRangeRuntime.get().contradictsLocalAbsence(mc, estimate.x(), estimate.z(), estimate.uncertaintyMajor())) continue;
            // Negative local visibility is strong evidence: the whole estimate lies well inside a
            // radius where this session has already received player entities, yet this target is not
            // present locally while locator still says it exists. Drop the segment instead of
            // painting a convincing-but-impossible marker next to the observer.
            HeuristicRuntime.get().clear(id);
            if (filtered == null) filtered = new LinkedHashMap<>(estimates);
            filtered.remove(id);
        }
        return filtered == null ? estimates : Map.copyOf(filtered);
    }

    private static Set<UUID> locatorTargetIds(List<LocatorObservation> observations) {
        if (observations == null || observations.isEmpty()) return Set.of();
        Set<UUID> ids = new HashSet<>();
        for (LocatorObservation observation : observations) {
            if (observation != null && observation.targetUuid() != null) ids.add(observation.targetUuid());
        }
        return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
    }

    private static Set<UUID> localExactTargets(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) return Set.of();
        UUID self = mc.player.getUUID();
        Set<UUID> result = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == null || player.getUUID() == null || player.getUUID().equals(self)) continue;
            result.add(player.getUUID());
        }
        return result;
    }

    private Set<UUID> exactMapLinkTargets(MapLinkSnapshot snapshot, String world) {
        if (snapshot == null || snapshot.observations().isEmpty()) return Set.of();
        Set<UUID> result = new HashSet<>();
        for (MapLinkObservation observation : snapshot.observations()) {
            UUID target = observation.resolvedUuid();
            if (target == null || !observation.worldMapped() || !world.equals(observation.mappedWorld())) continue;
            MapLinkProfileState state = snapshot.profileStates().get(observation.profileId());
            if (state == null || state.status() != MapLinkProfileStatus.LIVE) continue;
            result.add(target);
        }
        return Set.copyOf(result);
    }

    private LocationSessionKey reconcileLocationSession(String server, String world, Object connectionIdentity) {
        if (activeLocationSession != null
                && activeLocationSession.serverKey().equals(server)
                && activeLocationSession.worldKey().equals(world)
                && activeConnectionIdentity == connectionIdentity) {
            return activeLocationSession;
        }
        closeLocationSession();
        LocationSessionKey next = new LocationSessionKey(server, world, ++locationSessionGeneration);
        activeLocationSession = next;
        activeConnectionIdentity = connectionIdentity;
        HeuristicRuntime.get().beginSession(next);
        return next;
    }

    private void closeLocationSession() {
        LocationSessionKey previous = activeLocationSession;
        if (previous == null) return;
        try {
            List<PlayerLocationEvent> events = PlayerLocationService.get().clear();
            isolateTeardown("persistLocationEvents", () -> persistLocationEvents(previous.serverKey(), events));
            isolateTeardown("heuristicEndSession", () -> HeuristicRuntime.get().endSession());
            isolateTeardown("persistHeuristicEvents", () -> persistHeuristicEvents(HeuristicRuntime.get().drainLifecycleEvents()));
            isolateTeardown("locatorClear", () -> LocatorRuntime.get().clear());
            isolateTeardown("trackingRangeClear", () -> PlayerTrackingRangeRuntime.get().clear());
        } finally {
            lastLocatorSeenAt.clear();
            activeLocationSession = null;
            activeConnectionIdentity = null;
        }
    }

    private void persistLocationEvents(String server, List<PlayerLocationEvent> events) {
        if (server == null || server.isBlank() || events == null || events.isEmpty()) return;
        MapHistoryStore history = MapHistoryStore.get();
        for (PlayerLocationEvent event : events) {
            if (event == null || event.playerUuid() == null) continue;
            if (event.type() != PlayerLocationEventType.SOURCE_APPEARED
                    && event.type() != PlayerLocationEventType.SOURCE_LOST) continue;
            PlayerLocationSnapshot snapshot = event.type() == PlayerLocationEventType.SOURCE_APPEARED
                    ? event.current() : event.previous();
            if (snapshot == null || !persistableIdentity(event.playerUuid(), event.playerName())) continue;
            MapHistorySource source = historySource(snapshot.source());
            if (source == null) continue;
            MapHistoryEventKind kind = event.type() == PlayerLocationEventType.SOURCE_APPEARED
                    ? MapHistoryEventKind.SOURCE_APPEARED : MapHistoryEventKind.SOURCE_LOST;
            history.offer(server, new MapHistoryEventFrame(event.playerUuid(), event.playerName(),
                    snapshot.worldKey(), source, snapshot.sourceKey(), event.timestampMs(),
                    snapshot.sourceRevision(), kind, event.generation(), parseLong(snapshot.metadata().get("segment"))));
        }
    }

    private void persistHeuristicEvents(List<HeuristicLifecycleEvent> events) {
        if (events == null || events.isEmpty()) return;
        MapHistoryStore history = MapHistoryStore.get();
        for (HeuristicLifecycleEvent event : events) {
            if (event == null || event.targetUuid() == null || event.session() == null) continue;
            if (!persistableIdentity(event.targetUuid(), event.targetName())) continue;
            MapHistoryEventKind kind = switch (event.type()) {
                case TELEPORT_SEGMENT_BREAK -> MapHistoryEventKind.TELEPORT_SEGMENT_BREAK;
                case EXACT_SOURCE_ACQUIRED -> MapHistoryEventKind.EXACT_SOURCE_ACQUIRED;
                case SESSION_ENDED -> MapHistoryEventKind.SESSION_ENDED;
            };
            history.offer(event.session().serverKey(), new MapHistoryEventFrame(event.targetUuid(),
                    event.targetName(), event.session().worldKey(), MapHistorySource.HEURISTIC_TRIANGULATED,
                    "single_client", event.timestampMs(), event.timestampMs(), kind,
                    event.session().generation(), event.segmentId()));
        }
    }

    private static MapHistorySource historySource(PlayerLocationSource source) {
        if (source == null || source == PlayerLocationSource.HISTORICAL) return null;
        return switch (source) {
            case LOCAL_ENTITY_EXACT -> MapHistorySource.LOCAL_ENTITY_EXACT;
            case MAPLINK_EXACT -> MapHistorySource.MAPLINK_EXACT;
            case LOCATOR_EXACT -> MapHistorySource.LOCATOR_EXACT;
            case LOCATOR_APPROXIMATE -> MapHistorySource.LOCATOR_APPROXIMATE;
            case LOCATOR_BEARING -> MapHistorySource.LOCATOR_BEARING;
            case TRIANGULATED -> MapHistorySource.HEURISTIC_TRIANGULATED;
            case DUPLEX_TRIANGULATED -> MapHistorySource.DUPLEX_TRIANGULATED;
            case HISTORICAL -> null;
        };
    }

    private static boolean persistableIdentity(UUID id, String name) {
        if (id == null) return false;
        if (MapTriangulationConfig.isOfflineTargetUuid(id, name)) return false;
        return !MapTriangulationConfig.get().isOfflineTargetUuid(id);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
    }

    private void reconcileDuplex(String server, String world) {
        if (!duplexConfig.enabled()) {
            if (!activeServerFingerprint.isEmpty() || !activeWorldFingerprint.isEmpty()) duplex.close();
            activeServerFingerprint = "";
            activeWorldFingerprint = "";
            activeDuplexConfigFingerprint = 0;
            lastDuplexRevision.clear();
            lastDuplexSentAt.clear();
            return;
        }
        int configFingerprint = duplexConfig.runtimeFingerprint();
        if (server.equals(activeServerFingerprint) && world.equals(activeWorldFingerprint)
                && configFingerprint == activeDuplexConfigFingerprint) return;
        activeServerFingerprint = server;
        activeWorldFingerprint = world;
        activeDuplexConfigFingerprint = configFingerprint;
        lastDuplexRevision.clear();
        lastDuplexSentAt.clear();
        duplex.start(server, world);
    }

    private void reset() {
        isolateTeardown("locationSession", this::closeLocationSession);
        isolateTeardown("heuristicSuppressedTargets", () -> HeuristicRuntime.get().setSuppressedTargets(Set.of()));
        isolateTeardown("duplex", duplex::close);
        activeServerFingerprint = "";
        activeWorldFingerprint = "";
        activeDuplexConfigFingerprint = 0;
        lastDuplexRevision.clear();
        lastDuplexSentAt.clear();
    }

    private void isolateTeardown(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Throwable unwrapped = FailureBoundary.unwrap(t);
            if (unwrapped instanceof VirtualMachineError error) {
                throw error;
            }
            if (unwrapped instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Teardown interrupted: " + step, unwrapped);
            }
            DebugLog.error("MapLocationRuntime teardown step '{}' failed", step, unwrapped);
            try {
                FailureIsolation.reportComponent(this, "MapLocationRuntime", step, unwrapped);
            } catch (Throwable ignored) {
                // FailureIsolation/ErrorHandler re-throws non-recoverable Errors; suppress during teardown
                // so remaining teardown steps continue to run.
            }
        }
    }

    private static boolean usable(Minecraft mc) {
        return mc != null && mc.player != null && mc.level != null && mc.getConnection() != null
                && !mc.hasSingleplayerServer() && mc.getCurrentServer() != null;
    }

    private static String serverFingerprint(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        String address = server == null || server.ip == null ? "" : server.ip.trim();
        return address.toLowerCase(Locale.ROOT);
    }

    private static void rememberIdentity(Map<UUID, String> names, Map<String, UUID> idsByName,
                                         UUID id, String name) {
        if (id == null) return;
        String cleaned = clean(name);
        if (!cleaned.isBlank()) {
            names.put(id, cleaned);
            idsByName.put(cleaned.toLowerCase(Locale.ROOT), id);
        } else {
            names.putIfAbsent(id, "");
        }
    }

    private static UUID resolve(UUID direct, String name, Map<String, UUID> idsByName) {
        if (direct != null) return direct;
        String cleaned = clean(name);
        return cleaned.isBlank() ? null : idsByName.get(cleaned.toLowerCase(Locale.ROOT));
    }

    private static long revisionHash(String profile, String revision, long effectiveTimestamp) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, clean(profile).hashCode());
        h = mix(h, clean(revision).hashCode());
        h = mix(h, effectiveTimestamp);
        return h;
    }

    private static long mix(long h, long value) {
        return (h ^ value) * 0x100000001b3L;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record Captured(
            List<PlayerLocationSnapshot> locations,
            Map<UUID, String> names,
            Map<String, UUID> idsByName,
            Set<UUID> localPlayerIds,
            MapLinkSnapshot mapLink,
            List<LocatorObservation> locator
    ) {
    }
}
