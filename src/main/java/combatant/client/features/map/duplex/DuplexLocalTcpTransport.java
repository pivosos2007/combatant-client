/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Single-peer local TCP transport for DUPLEX.
 *
 * <p>PRIMARY listens on 127.0.0.1 only. SECONDARY connects to the same loopback endpoint.
 * No wildcard bind, configurable host, DNS, TLS or external networking path exists here.</p>
 */
public final class DuplexLocalTcpTransport implements AutoCloseable {
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int MAX_FRAME_BYTES = 8192;
    private static final int READ_BUFFER_BYTES = 32768;
    private static final int OUTBOUND_CAPACITY = 256;
    private static final long IDLE_PARK_MS = 2L;

    private final DuplexRole role;
    private final int port;
    private final int reconnectMs;
    private final ArrayBlockingQueue<byte[]> outbound = new ArrayBlockingQueue<>(OUTBOUND_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();

    private volatile Consumer<byte[]> messageSink = ignored -> {};
    private volatile SocketChannel channel;
    private volatile ServerSocketChannel server;
    private volatile boolean connected;
    private volatile String lastError = "";
    private Thread ioThread;

    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_BYTES).order(ByteOrder.BIG_ENDIAN);
    private ByteBuffer pendingWrite;

    public DuplexLocalTcpTransport(DuplexRole role, int port, int reconnectMs) {
        this.role = role == null ? DuplexRole.PRIMARY : role;
        this.port = Math.max(1024, Math.min(65535, port));
        this.reconnectMs = Math.max(100, reconnectMs);
    }

    public void setMessageSink(Consumer<byte[]> sink) {
        this.messageSink = sink == null ? ignored -> {} : sink;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        ioThread = new Thread(this::runLoop, "Combatant-Duplex-LocalTCP-" + role.name());
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public boolean sendPayload(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_FRAME_BYTES) return false;
        if (!connected || !running.get()) return false;
        return outbound.offer(payload.clone());
    }

    public boolean connected() { return connected; }
    public boolean running() { return running.get(); }
    public long connectionGeneration() { return connectionGeneration.get(); }
    public String endpoint() { return LOOPBACK_HOST + ":" + port; }
    public String lastError() { return lastError; }

    private void runLoop() {
        try {
            if (role == DuplexRole.PRIMARY) runPrimary();
            else runSecondary();
        } finally {
            disconnectPeer();
            closeServer();
            running.set(false);
        }
    }

    private void runPrimary() {
        try {
            ServerSocketChannel listener = ServerSocketChannel.open();
            listener.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            listener.bind(new InetSocketAddress(LOOPBACK_HOST, port), 1);
            listener.configureBlocking(false);
            server = listener;
            lastError = "";
        } catch (IOException e) {
            lastError = "bind: " + messageOf(e);
            return;
        }

        while (running.get()) {
            try {
                if (channel == null) {
                    SocketChannel accepted = server.accept();
                    if (accepted != null) attach(accepted);
                }
                if (channel != null) pumpPeer();
                idle();
            } catch (IOException e) {
                lastError = "peer: " + messageOf(e);
                disconnectPeer();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runSecondary() {
        long nextAttemptAt = 0L;
        while (running.get()) {
            try {
                if (channel == null) {
                    long now = System.currentTimeMillis();
                    if (now >= nextAttemptAt) {
                        nextAttemptAt = now + reconnectMs;
                        tryConnect();
                    }
                }
                if (channel != null) pumpPeer();
                idle();
            } catch (IOException e) {
                lastError = "peer: " + messageOf(e);
                disconnectPeer();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void tryConnect() {
        SocketChannel candidate = null;
        try {
            candidate = SocketChannel.open();
            candidate.setOption(StandardSocketOptions.TCP_NODELAY, true);
            candidate.configureBlocking(true);
            candidate.connect(new InetSocketAddress(LOOPBACK_HOST, port));
            attach(candidate);
            lastError = "";
        } catch (IOException e) {
            lastError = "connect: " + messageOf(e);
            if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
        }
    }

    private void attach(SocketChannel peer) throws IOException {
        if (peer == null) return;
        if (channel != null) {
            peer.close();
            return;
        }
        peer.setOption(StandardSocketOptions.TCP_NODELAY, true);
        peer.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        peer.configureBlocking(false);
        readBuffer.clear();
        pendingWrite = null;
        outbound.clear();
        channel = peer;
        connected = true;
        connectionGeneration.incrementAndGet();
        lastError = "";
    }

    private void pumpPeer() throws IOException {
        SocketChannel peer = channel;
        if (peer == null) return;

        int read;
        do {
            read = peer.read(readBuffer);
            if (read < 0) {
                disconnectPeer();
                return;
            }
            if (read > 0) decodeAvailableFrames();
        } while (read > 0 && channel != null);

        if (pendingWrite == null) {
            byte[] payload = outbound.poll();
            if (payload != null) pendingWrite = framed(payload);
        }
        if (pendingWrite != null) {
            peer.write(pendingWrite);
            if (!pendingWrite.hasRemaining()) pendingWrite = null;
        }
    }

    private void decodeAvailableFrames() throws IOException {
        readBuffer.flip();
        try {
            while (readBuffer.remaining() >= Integer.BYTES) {
                readBuffer.mark();
                int length = readBuffer.getInt();
                if (length <= 0 || length > MAX_FRAME_BYTES) {
                    throw new IOException("invalid frame length " + length);
                }
                if (readBuffer.remaining() < length) {
                    readBuffer.reset();
                    break;
                }
                byte[] payload = new byte[length];
                readBuffer.get(payload);
                try {
                    messageSink.accept(payload);
                } catch (RuntimeException ignored) {
                    // Domain decode/validation failure must not kill the transport loop.
                }
            }
        } finally {
            readBuffer.compact();
        }
        if (!readBuffer.hasRemaining()) throw new IOException("receive buffer exhausted");
    }

    private static ByteBuffer framed(byte[] payload) {
        ByteBuffer out = ByteBuffer.allocate(Integer.BYTES + payload.length).order(ByteOrder.BIG_ENDIAN);
        out.putInt(payload.length).put(payload).flip();
        return out;
    }

    private void disconnectPeer() {
        connected = false;
        SocketChannel peer = channel;
        channel = null;
        pendingWrite = null;
        outbound.clear();
        readBuffer.clear();
        if (peer != null) try { peer.close(); } catch (IOException ignored) {}
    }

    private void closeServer() {
        ServerSocketChannel listener = server;
        server = null;
        if (listener != null) try { listener.close(); } catch (IOException ignored) {}
    }

    private static void idle() throws InterruptedException {
        Thread.sleep(IDLE_PARK_MS);
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @Override
    public synchronized void close() {
        running.set(false);
        Thread thread = ioThread;
        if (thread != null) thread.interrupt();

        // Only close channels here. The I/O thread owns readBuffer/pendingWrite and performs
        // their final reset from runLoop/finally, avoiding cross-thread mutation of NIO buffers.
        SocketChannel peer = channel;
        if (peer != null) try { peer.close(); } catch (IOException ignored) {}
        ServerSocketChannel listener = server;
        if (listener != null) try { listener.close(); } catch (IOException ignored) {}
        ioThread = null;
    }
}
