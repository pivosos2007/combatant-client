/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import combatant.client.config.subsystem.DuplexLocalConfig;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates a local Duplex peer session, frame sequencing and exchanged bearing observations.
 * Incoming frames are checked against peer/session state before their contents update estimates.
 */
public final class DuplexRuntime implements AutoCloseable {
    private final DuplexLocalConfig config;
    private final UUID senderId = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final DuplexPeerWindow replayWindow = new DuplexPeerWindow();
    private final ConcurrentHashMap<UUID, DuplexBearingSample> localBearings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DuplexBearingSample> remoteBearings = new ConcurrentHashMap<>();
    private final AtomicReference<Map<UUID, DuplexEstimate>> estimates = new AtomicReference<>(Map.of());

    private volatile DuplexLocalTcpTransport transport;
    private volatile DuplexState state = DuplexState.DISCONNECTED;
    private volatile UUID sessionId;
    private volatile String serverFingerprint = "";
    private volatile String worldFingerprint = "";
    private volatile UUID peerId;
    private volatile long lastPeerAt;
    private volatile long lastHeartbeatAt;
    private volatile long lastConnectionGeneration;

    public DuplexRuntime() {
        this(DuplexLocalConfig.get());
    }

    DuplexRuntime(DuplexLocalConfig config) {
        this.config = config;
    }

    public DuplexState state() { return state; }
    public UUID senderId() { return senderId; }
    public boolean transportConnected() {
        DuplexLocalTcpTransport current = transport;
        return current != null && current.connected();
    }
    public String transportEndpoint() {
        DuplexLocalTcpTransport current = transport;
        return current == null ? "127.0.0.1:" + config.port() : current.endpoint();
    }
    public String transportLastError() {
        DuplexLocalTcpTransport current = transport;
        return current == null ? "" : current.lastError();
    }
    public boolean peerVerified() { return state == DuplexState.READY && peerId != null; }
    public long peerAgeMs() {
        long seen = lastPeerAt;
        return seen <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - seen);
    }
    public int localBearingCount() { return localBearings.size(); }
    public int remoteBearingCount() { return remoteBearings.size(); }
    public int estimateCount() { return estimates().size(); }

    /**
     * Returns an immutable estimate map with entries older than the configured
     * staleness threshold excluded; the result is safe to retain as a snapshot.
     */
    public Map<UUID, DuplexEstimate> estimates() {
        Map<UUID, DuplexEstimate> current = estimates.get();
        if (current.isEmpty()) return current;
        long now = System.currentTimeMillis();
        Map<UUID, DuplexEstimate> filtered = new LinkedHashMap<>();
        for (Map.Entry<UUID, DuplexEstimate> entry : current.entrySet()) {
            DuplexEstimate estimate = entry.getValue();
            if (estimate != null && now - estimate.observedAtMs() <= config.estimateStaleMs()) {
                filtered.put(entry.getKey(), estimate);
            }
        }
        return filtered.size() == current.size() ? current : Map.copyOf(filtered);
    }

    /**
     * Replaces the existing session and derives its identity from the configured
     * token, port and normalized server/world fingerprints. A disabled configuration
     * leaves the runtime disconnected.
     */
    public synchronized void start(String serverFingerprint, String worldFingerprint) {
        close();
        if (!config.enabled()) {
            state = DuplexState.DISCONNECTED;
            return;
        }

        this.serverFingerprint = normalize(serverFingerprint);
        this.worldFingerprint = normalize(worldFingerprint);
        this.sessionId = deriveSessionId(config.sessionToken(), this.serverFingerprint, this.worldFingerprint,
                config.port());
        this.state = DuplexState.HANDSHAKING;
        this.lastConnectionGeneration = 0L;

        DuplexLocalTcpTransport next = new DuplexLocalTcpTransport(config.role(), config.port(), config.reconnectMs());
        next.setMessageSink(this::onPayload);
        this.transport = next;
        next.start();
    }

    public void tick() {
        DuplexLocalTcpTransport current = transport;
        if (current == null || state == DuplexState.DISCONNECTED) return;

        long now = System.currentTimeMillis();
        if (!current.connected()) {
            if (!current.running() || lastConnectionGeneration > 0L) state = DuplexState.DEGRADED;
            pruneEstimates(now);
            return;
        }

        long generation = current.connectionGeneration();
        if (generation != lastConnectionGeneration) {
            onTransportConnected(generation);
        }

        if (now - lastHeartbeatAt >= config.heartbeatMs()) {
            send(DuplexMessageType.HEARTBEAT, DuplexPayloads.text(Long.toString(now)));
            lastHeartbeatAt = now;
        }
        if (peerId != null && now - lastPeerAt > Math.max(2000L, config.heartbeatMs() * 3L)) {
            state = DuplexState.DEGRADED;
        }
        pruneEstimates(now);
    }

    /**
     * Records the local observation and updates any matching estimate before sending.
     * With an active transport, the sample is retained even when no verified peer is available.
     *
     * @return {@code true} only when the encoded sample was accepted by the transport queue
     */
    public boolean publishBearing(DuplexBearingSample sample) {
        if (sample == null || sample.targetUuid() == null || transport == null) return false;
        localBearings.put(sample.targetUuid(), sample);
        tryPair(sample.targetUuid());
        if (state != DuplexState.READY) return false;
        return send(DuplexMessageType.BEARING_SAMPLE, DuplexPayloads.bearing(sample));
    }

    private void onTransportConnected(long generation) {
        lastConnectionGeneration = generation;
        peerId = null;
        lastPeerAt = 0L;
        lastHeartbeatAt = 0L;
        remoteBearings.clear();
        replayWindow.clear();
        state = DuplexState.HANDSHAKING;
        if (sendHello()) state = DuplexState.SESSION_VERIFY;
    }

    private boolean sendHello() {
        return send(DuplexMessageType.HELLO, DuplexPayloads.text(
                config.role().name(), serverFingerprint, worldFingerprint));
    }

    private boolean send(DuplexMessageType type, byte[] payload) {
        DuplexLocalTcpTransport current = transport;
        UUID session = sessionId;
        if (current == null || session == null || !current.connected()) return false;
        DuplexFrame frame = new DuplexFrame(type, sequence.incrementAndGet(), session, senderId, payload);
        return current.sendPayload(DuplexCodec.encodeBinary(frame, config.sessionToken()));
    }

    private void onPayload(byte[] payload) {
        DuplexFrame frame;
        try {
            frame = DuplexCodec.decodeBinary(payload, config.sessionToken());
        } catch (RuntimeException ignored) {
            return;
        }
        if (frame.senderId().equals(senderId) || sessionId == null || !sessionId.equals(frame.sessionId())) return;
        if (!replayWindow.accept(frame)) return;
        lastPeerAt = System.currentTimeMillis();

        switch (frame.type()) {
            case HELLO -> handleHello(frame);
            case HEARTBEAT -> {
                if (peerId == null) return;
                if (!peerId.equals(frame.senderId())) return;
                if (state == DuplexState.DEGRADED) state = DuplexState.READY;
            }
            case BEARING_SAMPLE -> {
                if (state != DuplexState.READY || peerId == null || !peerId.equals(frame.senderId())) return;
                DuplexBearingSample sample;
                try {
                    sample = DuplexPayloads.readBearing(frame.payload());
                } catch (RuntimeException ignored) {
                    return;
                }
                remoteBearings.put(sample.targetUuid(), sample);
                tryPair(sample.targetUuid());
            }
            case GOODBYE -> {
                if (frame.senderId().equals(peerId)) {
                    peerId = null;
                    remoteBearings.clear();
                    state = DuplexState.DEGRADED;
                }
            }
            default -> {
            }
        }
    }

    private void handleHello(DuplexFrame frame) {
        String[] fields;
        try {
            fields = DuplexPayloads.readText(frame.payload());
        } catch (RuntimeException ignored) {
            return;
        }
        if (fields.length != 3) return;
        if (!serverFingerprint.equals(normalize(fields[1])) || !worldFingerprint.equals(normalize(fields[2]))) {
            state = DuplexState.DEGRADED;
            return;
        }

        DuplexRole remoteRole;
        try {
            remoteRole = DuplexRole.valueOf(fields[0]);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (remoteRole == config.role()) {
            state = DuplexState.DEGRADED;
            return;
        }

        boolean newlyReady = state != DuplexState.READY || peerId == null || !peerId.equals(frame.senderId());
        peerId = frame.senderId();
        state = DuplexState.READY;
        if (newlyReady) {
            // Ack once. A peer already READY does not answer again, avoiding HELLO ping-pong.
            sendHello();
            resendLocalBearings();
        }
    }

    private void resendLocalBearings() {
        if (state != DuplexState.READY) return;
        for (DuplexBearingSample sample : localBearings.values()) {
            if (sample != null) send(DuplexMessageType.BEARING_SAMPLE, DuplexPayloads.bearing(sample));
        }
    }

    private void tryPair(UUID target) {
        DuplexBearingSample local = localBearings.get(target);
        DuplexBearingSample remote = remoteBearings.get(target);
        if (local == null || remote == null) return;
        if (Math.abs(local.observedAtMs() - remote.observedAtMs()) > config.samplePairToleranceMs()) return;
        DuplexEstimate estimate = DuplexPairSolver.solve(local, remote,
                config.minCrossingAngleRadians(), config.bearingNoiseRadians());
        if (estimate == null) return;
        while (true) {
            Map<UUID, DuplexEstimate> current = estimates.get();
            Map<UUID, DuplexEstimate> next = new LinkedHashMap<>(current);
            next.put(target, estimate);
            if (estimates.compareAndSet(current, Map.copyOf(next))) return;
        }
    }

    private void pruneEstimates(long now) {
        while (true) {
            Map<UUID, DuplexEstimate> current = estimates.get();
            if (current.isEmpty()) return;
            Map<UUID, DuplexEstimate> next = new LinkedHashMap<>(current);
            next.entrySet().removeIf(entry -> entry.getValue() == null
                    || now - entry.getValue().observedAtMs() > config.estimateStaleMs());
            if (next.size() == current.size()) return;
            if (estimates.compareAndSet(current, Map.copyOf(next))) return;
        }
    }

    @Override
    public synchronized void close() {
        DuplexLocalTcpTransport current = transport;
        if (current != null && sessionId != null && current.connected()) {
            try { send(DuplexMessageType.GOODBYE, new byte[0]); } catch (RuntimeException ignored) {}
        }
        if (current != null) current.close();
        transport = null;
        state = DuplexState.DISCONNECTED;
        peerId = null;
        sessionId = null;
        lastConnectionGeneration = 0L;
        lastPeerAt = 0L;
        lastHeartbeatAt = 0L;
        localBearings.clear();
        remoteBearings.clear();
        estimates.set(Map.of());
        replayWindow.clear();
    }

    private static UUID deriveSessionId(String token, String server, String world, int port) {
        String key = "combatant-duplex-local\n" + (token == null ? "" : token) + "\n"
                + port + "\n" + server + "\n" + world;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
