/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.duplexcourier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.world.waypoints.TrackedWaypoint;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DuplexCourier implements ClientModInitializer {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 28464;
    private static final int RECONNECT_MS = 1000;
    private static final int HEARTBEAT_MS = 2000;
    private static final int SEND_INTERVAL_MS = 250;
    private static final String TOKEN = "";

    private final UUID senderId = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, Long> lastSentAt = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private final Map<UUID, Long> fingerprints = new HashMap<>();

    private volatile SessionState state = SessionState.DISCONNECTED;
    private volatile LocalTransport transport;
    private volatile UUID sessionId;
    private volatile UUID primaryId;
    private volatile String serverFingerprint = "";
    private volatile String worldFingerprint = "";
    private volatile long connectionGeneration;
    private volatile long lastHeartbeatAt;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (!usable(mc)) {
            closeSession();
            return;
        }

        String server = normalize(mc.getCurrentServer().ip);
        String world = normalize(mc.level.dimension().identifier().toString());
        if (!server.equals(serverFingerprint) || !world.equals(worldFingerprint) || transport == null) {
            openSession(server, world);
        }

        LocalTransport current = transport;
        if (current == null) return;
        if (!current.connected()) {
            state = connectionGeneration > 0L ? SessionState.DEGRADED : SessionState.CONNECTING;
            return;
        }

        long generation = current.connectionGeneration();
        if (generation != connectionGeneration) {
            connectionGeneration = generation;
            primaryId = null;
            state = SessionState.VERIFYING;
            sendHello();
        }

        long now = System.currentTimeMillis();
        if (state == SessionState.READY) {
            if (now - lastHeartbeatAt >= HEARTBEAT_MS) {
                send(MessageType.HEARTBEAT, text(Long.toString(now)));
                lastHeartbeatAt = now;
            }
            captureAndSend(mc, now);
        }
    }

    private synchronized void openSession(String server, String world) {
        closeSession();
        serverFingerprint = server;
        worldFingerprint = world;
        sessionId = deriveSessionId(TOKEN, server, world, PORT);
        state = SessionState.CONNECTING;
        LocalTransport next = new LocalTransport(PORT, RECONNECT_MS);
        next.setMessageSink(this::onPayload);
        transport = next;
        next.start();
    }

    private void captureAndSend(Minecraft mc, long now) {
        ClientWaypointManager manager = mc.getConnection().getWaypointManager();
        if (manager == null || !manager.hasWaypoints()) return;

        Map<String, UUID> onlineByName = new HashMap<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            onlineByName.put(info.getProfile().name().toLowerCase(Locale.ROOT), info.getProfile().id());
        }

        double observerX = mc.player.getX();
        double observerZ = mc.player.getZ();
        UUID self = mc.player.getUUID();
        manager.forEachWaypoint(mc.player, waypoint -> {
            Double bearing = readAzimuth(waypoint);
            if (bearing == null || !Double.isFinite(bearing)) return;
            UUID target = waypoint.id().left().orElse(null);
            String targetName = waypoint.id().right().orElse("");
            if (target == null && !targetName.isBlank()) {
                target = onlineByName.get(targetName.toLowerCase(Locale.ROOT));
            }
            if (target == null || target.equals(self)) return;

            long fingerprint = bearingFingerprint(observerX, observerZ, bearing);
            Long previousFingerprint = fingerprints.put(target, fingerprint);
            long last = lastSentAt.getOrDefault(target, 0L);
            if (previousFingerprint != null && previousFingerprint == fingerprint
                    && now - last < SEND_INTERVAL_MS) return;

            long revision = revisions.merge(target, 1L, Long::sum);
            if (send(MessageType.BEARING_SAMPLE,
                    bearingPayload(target, observerX, observerZ, bearing, now, revision))) {
                lastSentAt.put(target, now);
            }
        });
    }

    private synchronized void onPayload(byte[] payload) {
        Frame frame;
        try {
            frame = decode(payload, TOKEN);
        } catch (RuntimeException ignored) {
            return;
        }
        UUID session = sessionId;
        if (session == null || !session.equals(frame.sessionId) || senderId.equals(frame.senderId)) return;

        if (frame.type == MessageType.HELLO) {
            String[] fields;
            try {
                fields = readText(frame.payload);
            } catch (RuntimeException ignored) {
                return;
            }
            if (fields.length != 3 || !"PRIMARY".equals(fields[0])
                    || !serverFingerprint.equals(normalize(fields[1]))
                    || !worldFingerprint.equals(normalize(fields[2]))) {
                state = SessionState.DEGRADED;
                return;
            }
            boolean newlyReady = state != SessionState.READY || !frame.senderId.equals(primaryId);
            primaryId = frame.senderId;
            state = SessionState.READY;
            if (newlyReady) sendHello();
            return;
        }

        if (primaryId == null || !primaryId.equals(frame.senderId)) return;
        if (frame.type == MessageType.GOODBYE) {
            primaryId = null;
            state = SessionState.DEGRADED;
        }
    }

    private boolean sendHello() {
        return send(MessageType.HELLO, text("SECONDARY", serverFingerprint, worldFingerprint));
    }

    private boolean send(MessageType type, byte[] payload) {
        LocalTransport current = transport;
        UUID session = sessionId;
        if (current == null || session == null || !current.connected()) return false;
        return current.sendPayload(encode(new Frame(type, sequence.incrementAndGet(), session, senderId, payload), TOKEN));
    }

    private synchronized void closeSession() {
        LocalTransport current = transport;
        if (current != null && current.connected() && sessionId != null) {
            try { send(MessageType.GOODBYE, new byte[0]); } catch (RuntimeException ignored) {}
        }
        if (current != null) current.close();
        transport = null;
        state = SessionState.DISCONNECTED;
        sessionId = null;
        primaryId = null;
        serverFingerprint = "";
        worldFingerprint = "";
        connectionGeneration = 0L;
        lastHeartbeatAt = 0L;
        lastSentAt.clear();
        revisions.clear();
        fingerprints.clear();
    }

    private static boolean usable(Minecraft mc) {
        return mc != null && mc.player != null && mc.level != null && mc.getConnection() != null
                && !mc.hasSingleplayerServer() && mc.getCurrentServer() != null
                && mc.getCurrentServer().ip != null;
    }

    private static final ConcurrentHashMap<String, Field> WAYPOINT_FIELDS = new ConcurrentHashMap<>();

    private static Double readAzimuth(TrackedWaypoint waypoint) {
        if (waypoint == null || !waypoint.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("azimuth")) {
            return null;
        }
        try {
            String key = waypoint.getClass().getName() + "#angle";
            Field field = WAYPOINT_FIELDS.get(key);
            if (field == null) {
                field = waypoint.getClass().getDeclaredField("angle");
                field.setAccessible(true);
                WAYPOINT_FIELDS.put(key, field);
            }
            Object value = field.get(waypoint);
            return value instanceof Number number ? normalizeAngle(number.doubleValue()) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static long bearingFingerprint(double x, double z, double bearing) {
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ (long) Math.floor(x)) * 0x100000001b3L;
        hash = (hash ^ (long) Math.floor(z)) * 0x100000001b3L;
        return (hash ^ Double.doubleToLongBits(bearing)) * 0x100000001b3L;
    }

    private static double normalizeAngle(double value) {
        value %= Math.PI * 2.0;
        if (value <= -Math.PI) value += Math.PI * 2.0;
        if (value > Math.PI) value -= Math.PI * 2.0;
        return value;
    }

    private static UUID deriveSessionId(String token, String server, String world, int port) {
        String key = "combatant-duplex-local\n" + token + "\n" + port + "\n" + server + "\n" + world;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static byte[] bearingPayload(UUID target, double x, double z, double bearing,
                                         long observedAt, long revision) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bytes);
            writeUuid(out, target);
            out.writeDouble(x);
            out.writeDouble(z);
            out.writeDouble(bearing);
            out.writeLong(observedAt);
            out.writeLong(revision);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] text(String... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(values.length);
            for (String value : values) {
                byte[] raw = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                out.writeShort(raw.length);
                out.write(raw);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String[] readText(byte[] payload) {
        ByteBuffer input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int count = Byte.toUnsignedInt(input.get());
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            int length = Short.toUnsignedInt(input.getShort());
            if (length > input.remaining()) throw new IllegalArgumentException("truncated text");
            byte[] raw = new byte[length];
            input.get(raw);
            values[i] = new String(raw, StandardCharsets.UTF_8);
        }
        if (input.hasRemaining()) throw new IllegalArgumentException("trailing text");
        return values;
    }

    private static byte[] encode(Frame frame, String token) {
        byte[] payload = frame.payload;
        ByteBuffer body = ByteBuffer.allocate(4 + 1 + 1 + 8 + 16 + 16 + 2 + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        body.putInt(0x43424458).put((byte) 1).put((byte) frame.type.ordinal()).putLong(frame.sequence);
        putUuid(body, frame.sessionId);
        putUuid(body, frame.senderId);
        body.putShort((short) payload.length).put(payload);
        byte[] unsigned = body.array();
        byte[] mac = hmac(unsigned, token);
        return ByteBuffer.allocate(unsigned.length + mac.length).put(unsigned).put(mac).array();
    }

    private static Frame decode(byte[] bytes, String token) {
        if (bytes == null || bytes.length < 80) throw new IllegalArgumentException("truncated frame");
        int bodyLength = bytes.length - 32;
        byte[] body = java.util.Arrays.copyOf(bytes, bodyLength);
        byte[] actualMac = java.util.Arrays.copyOfRange(bytes, bodyLength, bytes.length);
        if (!MessageDigest.isEqual(actualMac, hmac(body, token))) throw new SecurityException("invalid MAC");
        ByteBuffer input = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != 0x43424458 || Byte.toUnsignedInt(input.get()) != 1) {
            throw new IllegalArgumentException("invalid frame header");
        }
        int typeIndex = Byte.toUnsignedInt(input.get());
        if (typeIndex >= MessageType.values().length) throw new IllegalArgumentException("invalid frame type");
        long sequence = input.getLong();
        UUID session = readUuid(input);
        UUID sender = readUuid(input);
        int length = Short.toUnsignedInt(input.getShort());
        if (length != input.remaining() || length > 4096) throw new IllegalArgumentException("invalid payload");
        byte[] payload = new byte[length];
        input.get(payload);
        return new Frame(MessageType.values()[typeIndex], sequence, session, sender, payload);
    }

    private static byte[] hmac(byte[] body, String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String material = token == null || token.isBlank() ? "combatant-local-duplex" : token;
            mac.init(new SecretKeySpec(material.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (Exception error) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", error);
        }
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static void putUuid(ByteBuffer out, UUID id) {
        out.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer input) {
        return new UUID(input.getLong(), input.getLong());
    }

    private enum SessionState { DISCONNECTED, CONNECTING, VERIFYING, READY, DEGRADED }

    // Ordinals are the wire contract shared with Combatant's DuplexMessageType.
    private enum MessageType {
        HELLO, SESSION_STATE, CLOCK_SYNC, BEARING_SAMPLE, HEARTBEAT, ACK, ERROR, GOODBYE
    }

    private record Frame(MessageType type, long sequence, UUID sessionId, UUID senderId, byte[] payload) {}

    /** Single-peer non-blocking loopback client with bounded queues. */
    private static final class LocalTransport implements AutoCloseable {
        private static final int MAX_FRAME_BYTES = 8192;
        private final int port;
        private final int reconnectMs;
        private final ArrayBlockingQueue<byte[]> outbound = new ArrayBlockingQueue<>(128);
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicLong generation = new AtomicLong();
        private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(32768).order(ByteOrder.BIG_ENDIAN);
        private volatile Consumer<byte[]> sink = ignored -> {};
        private volatile SocketChannel channel;
        private volatile boolean connected;
        private Thread thread;
        private ByteBuffer pendingWrite;

        private LocalTransport(int port, int reconnectMs) {
            this.port = port;
            this.reconnectMs = reconnectMs;
        }

        void setMessageSink(Consumer<byte[]> sink) { this.sink = sink == null ? ignored -> {} : sink; }

        synchronized void start() {
            if (!running.compareAndSet(false, true)) return;
            thread = new Thread(this::runLoop, "Combatant-Duplex-Courier");
            thread.setDaemon(true);
            thread.start();
        }

        boolean sendPayload(byte[] payload) {
            return payload != null && payload.length > 0 && payload.length <= MAX_FRAME_BYTES
                    && connected && running.get() && outbound.offer(payload.clone());
        }

        boolean connected() { return connected; }
        boolean running() { return running.get(); }
        long connectionGeneration() { return generation.get(); }

        private void runLoop() {
            long nextAttemptAt = 0L;
            try {
                while (running.get()) {
                    try {
                        if (channel == null && System.currentTimeMillis() >= nextAttemptAt) {
                            nextAttemptAt = System.currentTimeMillis() + reconnectMs;
                            connect();
                        }
                        if (channel != null) pump();
                        Thread.sleep(2L);
                    } catch (IOException ignored) {
                        disconnect();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                disconnect();
                running.set(false);
            }
        }

        private void connect() {
            SocketChannel candidate = null;
            try {
                candidate = SocketChannel.open();
                candidate.setOption(StandardSocketOptions.TCP_NODELAY, true);
                candidate.configureBlocking(true);
                candidate.connect(new InetSocketAddress(HOST, port));
                candidate.configureBlocking(false);
                readBuffer.clear();
                pendingWrite = null;
                outbound.clear();
                channel = candidate;
                connected = true;
                generation.incrementAndGet();
            } catch (IOException ignored) {
                if (candidate != null) try { candidate.close(); } catch (IOException ignoredClose) {}
            }
        }

        private void pump() throws IOException {
            SocketChannel peer = channel;
            int read;
            do {
                read = peer.read(readBuffer);
                if (read < 0) {
                    disconnect();
                    return;
                }
                if (read > 0) decodeFrames();
            } while (read > 0 && channel != null);

            if (pendingWrite == null) {
                byte[] payload = outbound.poll();
                if (payload != null) pendingWrite = frame(payload);
            }
            if (pendingWrite != null) {
                peer.write(pendingWrite);
                if (!pendingWrite.hasRemaining()) pendingWrite = null;
            }
        }

        private void decodeFrames() throws IOException {
            readBuffer.flip();
            try {
                while (readBuffer.remaining() >= Integer.BYTES) {
                    readBuffer.mark();
                    int length = readBuffer.getInt();
                    if (length <= 0 || length > MAX_FRAME_BYTES) throw new IOException("invalid frame length");
                    if (readBuffer.remaining() < length) {
                        readBuffer.reset();
                        break;
                    }
                    byte[] payload = new byte[length];
                    readBuffer.get(payload);
                    try { sink.accept(payload); } catch (RuntimeException ignored) {}
                }
            } finally {
                readBuffer.compact();
                if (!readBuffer.hasRemaining()) throw new IOException("receive buffer exhausted");
            }
        }

        private static ByteBuffer frame(byte[] payload) {
            return ByteBuffer.allocate(Integer.BYTES + payload.length).order(ByteOrder.BIG_ENDIAN)
                    .putInt(payload.length).put(payload).flip();
        }

        private void disconnect() {
            connected = false;
            SocketChannel peer = channel;
            channel = null;
            pendingWrite = null;
            outbound.clear();
            readBuffer.clear();
            if (peer != null) try { peer.close(); } catch (IOException ignored) {}
        }

        @Override
        public synchronized void close() {
            running.set(false);
            Thread current = thread;
            if (current != null) current.interrupt();
            SocketChannel peer = channel;
            if (peer != null) try { peer.close(); } catch (IOException ignored) {}
            thread = null;
        }
    }
}
