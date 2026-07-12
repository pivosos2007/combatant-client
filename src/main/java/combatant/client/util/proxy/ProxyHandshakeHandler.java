/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

final class ProxyHandshakeHandler extends ChannelDuplexHandler {

    private final ProxyEntry proxy;

    private ChannelPromise originalConnectPromise;
    private InetSocketAddress targetAddress;
    private ByteBuf inbound;
    private State state = State.INIT;

    ProxyHandshakeHandler(ProxyEntry proxy) {
        this.proxy = proxy == null ? ProxyEntry.empty() : proxy.copy();
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (!proxy.isConfigured() || !(remoteAddress instanceof InetSocketAddress target)) {
            ctx.connect(remoteAddress, localAddress, promise);
            return;
        }

        this.targetAddress = target;
        this.originalConnectPromise = promise;

        ChannelPromise proxyConnectPromise = ctx.newPromise();
        proxyConnectPromise.addListener(future -> {
            if (!future.isSuccess()) {
                fail(ctx, future.cause() != null ? future.cause() : new IllegalStateException("Proxy connect failed"));
                return;
            }
            startHandshake(ctx);
        });

        ctx.connect(proxy.socketAddress(), localAddress, proxyConnectPromise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.DONE || state == State.INIT) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!(msg instanceof ByteBuf buf)) {
            ReferenceCountUtil.release(msg);
            fail(ctx, new IllegalStateException("Unexpected proxy handshake payload: " + msg));
            return;
        }

        try {
            if (inbound == null) {
                inbound = ctx.alloc().buffer(buf.readableBytes());
            }
            inbound.writeBytes(buf);
        } finally {
            buf.release();
        }

        processInbound(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (state != State.DONE && originalConnectPromise != null && !originalConnectPromise.isDone()) {
            originalConnectPromise.tryFailure(new IllegalStateException("Proxy connection closed during handshake"));
        }
        releaseInbound();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (state != State.DONE && originalConnectPromise != null && !originalConnectPromise.isDone()) {
            fail(ctx, cause);
            return;
        }
        super.exceptionCaught(ctx, cause);
    }

    private void startHandshake(ChannelHandlerContext ctx) {
        try {
            switch (proxy.type()) {
                case SOCKS4 -> {
                    state = State.SOCKS4_RESPONSE;
                    ctx.writeAndFlush(createSocks4ConnectRequest(ctx));
                }
                case SOCKS5 -> {
                    state = State.SOCKS5_METHOD;
                    ctx.writeAndFlush(createSocks5Greeting(ctx));
                }
            }
        } catch (Throwable t) {
            fail(ctx, t);
        }
    }

    private void processInbound(ChannelHandlerContext ctx) {
        try {
            boolean again;
            do {
                again = false;
                switch (state) {
                    case SOCKS4_RESPONSE -> {
                        if (inbound.readableBytes() < 8) return;
                        inbound.readByte();
                        int status = inbound.readUnsignedByte();
                        inbound.skipBytes(6);
                        if (status != 90) {
                            throw new IllegalStateException("SOCKS4 proxy rejected connect request: " + status);
                        }
                        finish(ctx);
                    }
                    case SOCKS5_METHOD -> {
                        if (inbound.readableBytes() < 2) return;
                        int version = inbound.readUnsignedByte();
                        int method = inbound.readUnsignedByte();
                        if (version != 5) {
                            throw new IllegalStateException("Invalid SOCKS5 method response version: " + version);
                        }
                        if (method == 0xFF) {
                            throw new IllegalStateException("SOCKS5 proxy rejected all authentication methods");
                        }
                        if (method == 0x02) {
                            state = State.SOCKS5_AUTH;
                            ctx.writeAndFlush(createSocks5AuthRequest(ctx));
                        } else if (method == 0x00) {
                            state = State.SOCKS5_CONNECT;
                            ctx.writeAndFlush(createSocks5ConnectRequest(ctx));
                        } else {
                            throw new IllegalStateException("Unsupported SOCKS5 auth method: " + method);
                        }
                        again = inbound.isReadable();
                    }
                    case SOCKS5_AUTH -> {
                        if (inbound.readableBytes() < 2) return;
                        int version = inbound.readUnsignedByte();
                        int status = inbound.readUnsignedByte();
                        if (version != 1 || status != 0) {
                            throw new IllegalStateException("SOCKS5 authentication failed: " + status);
                        }
                        state = State.SOCKS5_CONNECT;
                        ctx.writeAndFlush(createSocks5ConnectRequest(ctx));
                        again = inbound.isReadable();
                    }
                    case SOCKS5_CONNECT -> {
                        if (!tryReadSocks5ConnectResponse()) return;
                        finish(ctx);
                    }
                    default -> {
                    }
                }
            } while (again && state != State.DONE);
        } catch (Throwable t) {
            fail(ctx, t);
        }
    }

    private boolean tryReadSocks5ConnectResponse() {
        if (inbound.readableBytes() < 5) return false;

        inbound.markReaderIndex();
        int version = inbound.readUnsignedByte();
        int status = inbound.readUnsignedByte();
        inbound.readUnsignedByte();
        int addressType = inbound.readUnsignedByte();

        if (version != 5) {
            throw new IllegalStateException("Invalid SOCKS5 connect response version: " + version);
        }

        int addressBytes;
        switch (addressType) {
            case 1 -> addressBytes = 4;
            case 4 -> addressBytes = 16;
            case 3 -> {
                if (inbound.readableBytes() < 1) {
                    inbound.resetReaderIndex();
                    return false;
                }
                addressBytes = inbound.readUnsignedByte();
            }
            default -> throw new IllegalStateException("Invalid SOCKS5 address type: " + addressType);
        }

        if (inbound.readableBytes() < addressBytes + 2) {
            inbound.resetReaderIndex();
            return false;
        }

        inbound.skipBytes(addressBytes + 2);
        if (status != 0) {
            throw new IllegalStateException("SOCKS5 proxy rejected connect request: " + status);
        }
        return true;
    }

    private ByteBuf createSocks5Greeting(ChannelHandlerContext ctx) {
        ByteBuf out = ctx.alloc().buffer(3);
        out.writeByte(5);
        out.writeByte(1);
        out.writeByte(proxy.hasCredentials() ? 0x02 : 0x00);
        return out;
    }

    private ByteBuf createSocks5AuthRequest(ChannelHandlerContext ctx) {
        byte[] username = ascii(proxy.username());
        byte[] password = ascii(proxy.password());
        if (username.length > 255 || password.length > 255) {
            throw new IllegalArgumentException("SOCKS5 username/password is too long");
        }

        ByteBuf out = ctx.alloc().buffer(3 + username.length + password.length);
        out.writeByte(1);
        out.writeByte(username.length);
        out.writeBytes(username);
        out.writeByte(password.length);
        out.writeBytes(password);
        return out;
    }

    private ByteBuf createSocks5ConnectRequest(ChannelHandlerContext ctx) {
        ByteBuf out = ctx.alloc().buffer(32);
        out.writeByte(5);
        out.writeByte(1);
        out.writeByte(0);
        writeSocks5Address(out, targetAddress);
        out.writeShort(targetAddress.getPort());
        return out;
    }

    private ByteBuf createSocks4ConnectRequest(ChannelHandlerContext ctx) {
        ByteBuf out = ctx.alloc().buffer(64);
        out.writeByte(4);
        out.writeByte(1);
        out.writeShort(targetAddress.getPort());

        InetAddress address = targetAddress.getAddress();
        boolean ipv4 = address instanceof Inet4Address;
        if (ipv4) {
            out.writeBytes(address.getAddress());
        } else {
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(1);
        }

        if (proxy.hasUsername()) {
            out.writeBytes(ascii(proxy.username()));
        }
        out.writeByte(0);

        if (!ipv4) {
            out.writeBytes(ascii(targetAddress.getHostString()));
            out.writeByte(0);
        }
        return out;
    }

    private static void writeSocks5Address(ByteBuf out, InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        if (inetAddress instanceof Inet4Address) {
            out.writeByte(1);
            out.writeBytes(inetAddress.getAddress());
            return;
        }
        if (inetAddress instanceof Inet6Address) {
            out.writeByte(4);
            out.writeBytes(inetAddress.getAddress());
            return;
        }

        byte[] host = ascii(address.getHostString());
        if (host.length > 255) {
            throw new IllegalArgumentException("SOCKS5 host is too long: " + address.getHostString());
        }
        out.writeByte(3);
        out.writeByte(host.length);
        out.writeBytes(host);
    }

    private void finish(ChannelHandlerContext ctx) {
        state = State.DONE;

        ByteBuf leftover = null;
        if (inbound != null && inbound.isReadable()) {
            leftover = inbound.readRetainedSlice(inbound.readableBytes());
        }
        releaseInbound();

        if (ctx.pipeline().get(this.getClass()) != null) {
            ctx.pipeline().remove(this);
        } else {
            try {
                ctx.pipeline().remove(this);
            } catch (Throwable ignored) {
            }
        }

        if (originalConnectPromise != null) {
            originalConnectPromise.trySuccess();
        }
        if (leftover != null) {
            ctx.fireChannelRead(leftover);
        }
    }

    private void fail(ChannelHandlerContext ctx, Throwable throwable) {
        releaseInbound();
        if (originalConnectPromise != null) {
            originalConnectPromise.tryFailure(throwable);
        }
        ctx.close();
    }

    private void releaseInbound() {
        if (inbound != null) {
            inbound.release();
            inbound = null;
        }
    }

    private static byte[] ascii(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.US_ASCII);
    }

    private enum State {
        INIT,
        SOCKS4_RESPONSE,
        SOCKS5_METHOD,
        SOCKS5_AUTH,
        SOCKS5_CONNECT,
        DONE
    }
}
