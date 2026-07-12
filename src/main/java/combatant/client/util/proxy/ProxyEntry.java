/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Objects;

/**
 * Backend proxy data model.
 */
public final class ProxyEntry {

    private String address = "";
    private ProxyType type = ProxyType.SOCKS5;
    private String username = "";
    private String password = "";

    public ProxyEntry() {
    }

    public ProxyEntry(ProxyType type, String ipPort, String username, String password) {
        this.type = ProxyType.safe(type);
        this.address = clean(ipPort);
        this.username = clean(username);
        this.password = clean(password);
    }

    public ProxyEntry(boolean socks4, String ipPort, String username, String password) {
        this(socks4 ? ProxyType.SOCKS4 : ProxyType.SOCKS5, ipPort, username, password);
    }

    public static ProxyEntry socks5(String host, int port) {
        return new ProxyEntry(ProxyType.SOCKS5, join(host, port), "", "");
    }

    public static ProxyEntry socks5(String host, int port, String username, String password) {
        return new ProxyEntry(ProxyType.SOCKS5, join(host, port), username, password);
    }

    public static ProxyEntry socks4(String host, int port) {
        return new ProxyEntry(ProxyType.SOCKS4, join(host, port), "", "");
    }

    public static ProxyEntry socks4(String host, int port, String username) {
        return new ProxyEntry(ProxyType.SOCKS4, join(host, port), username, "");
    }

    public static ProxyEntry empty() {
        return new ProxyEntry();
    }

    public ProxyEntry copy() {
        return new ProxyEntry(type, address, username, password);
    }

    public boolean isConfigured() {
        return isValidIpPort(address);
    }

    public String ipPort() {
        return address == null ? "" : address;
    }

    public void ipPort(String ipPort) {
        this.address = clean(ipPort);
    }

    public ProxyType type() {
        return ProxyType.safe(type);
    }

    public void type(ProxyType type) {
        this.type = ProxyType.safe(type);
    }

    public String username() {
        return username == null ? "" : username;
    }

    public void username(String username) {
        this.username = clean(username);
    }

    public String password() {
        return password == null ? "" : password;
    }

    public void password(String password) {
        this.password = clean(password);
    }

    public boolean hasUsername() {
        return !username().isBlank();
    }

    public boolean hasPassword() {
        return !password().isBlank();
    }

    public boolean hasCredentials() {
        return hasUsername() || hasPassword();
    }

    public String host() {
        if (address == null || address.isBlank()) return "";
        int separator = findPortSeparator(address);
        if (separator < 0) return "";
        String host = address.substring(0, separator).trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host;
    }

    public int port() {
        if (address == null || address.isBlank()) return 0;
        int separator = findPortSeparator(address);
        if (separator < 0 || separator + 1 >= address.length()) return 0;
        try {
            int port = Integer.parseInt(address.substring(separator + 1).trim());
            return port >= 0 && port <= 0xFFFF ? port : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public InetSocketAddress socketAddress() {
        String host = host();
        int port = port();
        if (host.isBlank() || port <= 0) {
            throw new IllegalStateException("Proxy entry is not configured: " + ipPort());
        }
        return new InetSocketAddress(host, port);
    }

    public static boolean isValidIpPort(String ipPort) {
        if (ipPort == null || ipPort.isBlank()) return false;
        int separator = findPortSeparator(ipPort.trim());
        if (separator <= 0 || separator + 1 >= ipPort.trim().length()) return false;
        String host = ipPort.trim().substring(0, separator).trim();
        if (host.isBlank()) return false;
        try {
            int port = Integer.parseInt(ipPort.trim().substring(separator + 1).trim());
            return port >= 1 && port <= 0xFFFF;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int findPortSeparator(String value) {
        if (value == null) return -1;
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            int endBracket = trimmed.indexOf(']');
            if (endBracket < 0) return -1;
            return endBracket + 1 < trimmed.length() && trimmed.charAt(endBracket + 1) == ':' ? endBracket + 1 : -1;
        }
        return trimmed.lastIndexOf(':');
    }

    private static String join(String host, int port) {
        String cleanHost = clean(host);
        if (cleanHost.contains(":") && !cleanHost.startsWith("[")) {
            cleanHost = '[' + cleanHost + ']';
        }
        return cleanHost + ':' + port;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public void normalize() {
        address = clean(address);
        type = ProxyType.safe(type);
        username = clean(username);
        password = clean(password);
    }

    @Override
    public String toString() {
        return type().name().toLowerCase(Locale.ROOT) + "://" + ipPort();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxyEntry that)) return false;
        return Objects.equals(ipPort(), that.ipPort())
                && type() == that.type()
                && Objects.equals(username(), that.username())
                && Objects.equals(password(), that.password());
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipPort(), type(), username(), password());
    }
}
