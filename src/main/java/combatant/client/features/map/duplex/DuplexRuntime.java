/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import combatant.client.config.subsystem.DuplexIrcConfig;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class DuplexRuntime implements AutoCloseable {
    private final DuplexIrcConfig config;
    private final UUID senderId = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final DuplexPeerWindow replayWindow = new DuplexPeerWindow();
    private final ConcurrentHashMap<UUID, DuplexBearingSample> localBearings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DuplexBearingSample> remoteBearings = new ConcurrentHashMap<>();
    private final AtomicReference<Map<UUID, DuplexEstimate>> estimates = new AtomicReference<>(Map.of());

    private volatile DuplexIrcTransport transport;
    private volatile DuplexState state = DuplexState.DISCONNECTED;
    private volatile UUID sessionId;
    private volatile String serverFingerprint = "";
    private volatile String worldFingerprint = "";
    private volatile UUID peerId;
    private volatile long lastPeerAt;
    private volatile long lastHeartbeatAt;

    public DuplexRuntime() {
        this(DuplexIrcConfig.get());
    }

    DuplexRuntime(DuplexIrcConfig config) {
        this.config = config;
    }

    public DuplexState state() { return state; }
    public Map<UUID, DuplexEstimate> estimates() { return estimates.get(); }
    public UUID senderId() { return senderId; }

    public synchronized void start(String serverFingerprint, String worldFingerprint) {
        close();
        if (!config.enabled() || config.host().isBlank() || config.channel().isBlank()
                || config.sessionToken().isBlank()) {
            state = DuplexState.DISCONNECTED;
            return;
        }
        this.serverFingerprint = normalize(serverFingerprint);
        this.worldFingerprint = normalize(worldFingerprint);
        this.sessionId = deriveSessionId(config.sessionToken(), this.serverFingerprint, this.worldFingerprint);
        this.state = DuplexState.HANDSHAKING;
        DuplexIrcTransport next = new DuplexIrcTransport(config);
        next.setMessageSink(this::onPayload);
        this.transport = next;
        next.start();
        sendHello();
        state = DuplexState.SESSION_VERIFY;
    }

    public void tick() {
        DuplexIrcTransport current = transport;
        if (current == null || state == DuplexState.DISCONNECTED) return;
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatAt >= config.heartbeatMs()) {
            send(DuplexMessageType.HEARTBEAT, DuplexPayloads.text(Long.toString(now)));
            lastHeartbeatAt = now;
        }
        if (peerId != null && now - lastPeerAt > Math.max(5000L, config.heartbeatMs() * 3L)) {
            state = DuplexState.DEGRADED;
        }
    }

    public boolean publishBearing(DuplexBearingSample sample) {
        if (sample == null || transport == null) return false;
        localBearings.put(sample.targetUuid(), sample);
        boolean sent = send(DuplexMessageType.BEARING_SAMPLE, DuplexPayloads.bearing(sample));
        tryPair(sample.targetUuid());
        return sent;
    }

    private void sendHello() {
        send(DuplexMessageType.HELLO, DuplexPayloads.text(
                config.role().name(), serverFingerprint, worldFingerprint));
    }

    private boolean send(DuplexMessageType type, byte[] payload) {
        DuplexIrcTransport current = transport;
        UUID session = sessionId;
        if (current == null || session == null) return false;
        DuplexFrame frame = new DuplexFrame(type, sequence.incrementAndGet(), session, senderId, payload);
        return current.sendPayload(DuplexCodec.encode(frame, config.sessionToken()));
    }

    private void onPayload(String payload) {
        DuplexFrame frame;
        try {
            frame = DuplexCodec.decode(payload, config.sessionToken());
        } catch (RuntimeException ignored) {
            return;
        }
        if (frame.senderId().equals(senderId) || sessionId == null || !sessionId.equals(frame.sessionId())) return;
        if (!replayWindow.accept(frame)) return;
        lastPeerAt = System.currentTimeMillis();

        switch (frame.type()) {
            case HELLO -> handleHello(frame);
            case HEARTBEAT -> {
                peerId = frame.senderId();
                if (state == DuplexState.DEGRADED) state = DuplexState.READY;
            }
            case BEARING_SAMPLE -> {
                if (state != DuplexState.READY) return;
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
        peerId = frame.senderId();
        state = DuplexState.READY;
        sendHello();
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

    @Override
    public synchronized void close() {
        DuplexIrcTransport current = transport;
        if (current != null && sessionId != null) {
            try { send(DuplexMessageType.GOODBYE, new byte[0]); } catch (RuntimeException ignored) {}
            current.close();
        }
        transport = null;
        state = DuplexState.DISCONNECTED;
        peerId = null;
        localBearings.clear();
        remoteBearings.clear();
        estimates.set(Map.of());
        replayWindow.clear();
    }

    private static UUID deriveSessionId(String token, String server, String world) {
        String key = "combatant-duplex-irc\n" + token + "\n" + server + "\n" + world;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
