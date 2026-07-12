/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.util.Map;

public enum ProxyBackend {
    ;

    public static final String DEFAULT_ACCOUNT_KEY = "";

    private static final ProxyConfig CONFIG = new ProxyConfig();
    private static ProxyEntry lastUsedProxy = ProxyEntry.empty();

    public static void init() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
    }

    public static ProxyConfig config() {
        init();
        return CONFIG;
    }

    public static boolean isEnabled() {
        init();
        return CONFIG.proxyEnabled();
    }

    public static void setEnabled(boolean enabled) {
        init();
        CONFIG.proxyEnabled(enabled);
        CONFIG.save();
    }

    public static ProxyEntry getDefaultProxy() {
        init();
        return CONFIG.defaultProxy();
    }

    public static void setDefaultProxy(ProxyEntry proxy) {
        init();
        CONFIG.defaultProxy(proxy);
        CONFIG.save();
    }

    public static ProxyEntry getAccountProxy(String accountName) {
        init();
        return CONFIG.proxyForAccount(accountName);
    }

    public static void setAccountProxy(String accountName, ProxyEntry proxy) {
        init();
        CONFIG.proxyForAccount(accountName, proxy);
        CONFIG.save();
    }

    public static void removeAccountProxy(String accountName) {
        init();
        CONFIG.removeAccountProxy(accountName);
        CONFIG.save();
    }

    public static Map<String, ProxyEntry> accountProxies() {
        init();
        return CONFIG.accountsSnapshot();
    }

    public static ProxyEntry activeProxy() {
        init();
        if (!CONFIG.proxyEnabled()) return ProxyEntry.empty();
        String name = currentProfileName();
        ProxyEntry proxy = CONFIG.proxyForAccount(name);
        return proxy.isConfigured() ? proxy : ProxyEntry.empty();
    }

    public static ProxyEntry lastUsedProxy() {
        return lastUsedProxy == null ? ProxyEntry.empty() : lastUsedProxy.copy();
    }

    static void markLastUsed(ProxyEntry proxy) {
        lastUsedProxy = proxy == null ? ProxyEntry.empty() : proxy.copy();
    }

    public static String lastUsedProxyIp() {
        ProxyEntry proxy = lastUsedProxy();
        return proxy.isConfigured() ? proxy.host() : "none";
    }

    private static String currentProfileName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return "";
        try {
            GameProfile profile = minecraft.getGameProfile();
            if (profile != null && profile.name() != null && !profile.name().isBlank()) {
                return profile.name();
            }
        } catch (Throwable ignored) {
        }
        try {
            return minecraft.getUser().getName();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
