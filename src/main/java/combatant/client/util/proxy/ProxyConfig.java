/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import combatant.client.config.ConfigPaths;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File-backed proxy backend config.
 */
public final class ProxyConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type ACCOUNTS_TYPE = new TypeToken<LinkedHashMap<String, ProxyEntry>>() {
    }
    .getType();

    private final Path file;
    private boolean loaded;
    private boolean enabled;
    private ProxyEntry defaultProxy = ProxyEntry.empty();
    private final LinkedHashMap<String, ProxyEntry> accounts = new LinkedHashMap<>();

    public ProxyConfig() {
        this(ConfigPaths.root().resolve("proxy").resolve("proxy.json"));
    }

    public ProxyConfig(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                loaded = true;
                save();
                return;
            }

            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text == null || text.isBlank()) {
                loaded = true;
                save();
                return;
            }

            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }

            if (json.has("default") && json.get("default").isJsonObject()) {
                ProxyEntry parsed = GSON.fromJson(json.get("default"), ProxyEntry.class);
                defaultProxy = parsed == null ? ProxyEntry.empty() : parsed;
            }

            accounts.clear();
            if (json.has("accounts") && json.get("accounts").isJsonObject()) {
                LinkedHashMap<String, ProxyEntry> loadedAccounts = GSON.fromJson(json.get("accounts"), ACCOUNTS_TYPE);
                if (loadedAccounts != null) {
                    accounts.putAll(loadedAccounts);
                }
            }
            normalize();
            loaded = true;
        } catch (Exception e) {
            loaded = true;
            DebugLog.warn("Failed to load proxy config: %s", file.toAbsolutePath());
            DebugLog.error("Proxy config load error", e);
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            normalize();

            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.add("default", GSON.toJsonTree(defaultProxy, ProxyEntry.class));
            JsonElement accountsJson = GSON.toJsonTree(accounts, ACCOUNTS_TYPE);
            json.add("accounts", accountsJson);

            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DebugLog.warn("Failed to save proxy config: %s", file.toAbsolutePath());
            DebugLog.error("Proxy config save error", e);
        }
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized boolean proxyEnabled() {
        return enabled;
    }

    public synchronized void proxyEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized ProxyEntry defaultProxy() {
        return defaultProxy == null ? ProxyEntry.empty() : defaultProxy.copy();
    }

    public synchronized void defaultProxy(ProxyEntry proxy) {
        defaultProxy = proxy == null ? ProxyEntry.empty() : proxy.copy();
        normalize();
    }

    public synchronized ProxyEntry proxyForAccount(String accountName) {
        String key = accountKey(accountName);
        if (!key.isEmpty() && accounts.containsKey(key)) {
            ProxyEntry accountProxy = accounts.get(key);
            return accountProxy == null ? ProxyEntry.empty() : accountProxy.copy();
        }
        return defaultProxy();
    }

    public synchronized void proxyForAccount(String accountName, ProxyEntry proxy) {
        String key = accountKey(accountName);
        if (key.isEmpty()) {
            defaultProxy(proxy);
            return;
        }
        accounts.put(key, proxy == null ? ProxyEntry.empty() : proxy.copy());
        normalize();
    }

    public synchronized void removeAccountProxy(String accountName) {
        String key = accountKey(accountName);
        if (key.isEmpty()) {
            defaultProxy = ProxyEntry.empty();
        } else {
            accounts.remove(key);
        }
    }

    public synchronized Map<String, ProxyEntry> accountsSnapshot() {
        LinkedHashMap<String, ProxyEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, ProxyEntry> entry : accounts.entrySet()) {
            out.put(entry.getKey(), entry.getValue() == null ? ProxyEntry.empty() : entry.getValue().copy());
        }
        return out;
    }

    public synchronized Path file() {
        return file;
    }

    private void normalize() {
        if (defaultProxy == null) {
            defaultProxy = ProxyEntry.empty();
        }
        defaultProxy.normalize();
        accounts.replaceAll((ignored, proxy) -> {
            ProxyEntry safe = proxy == null ? ProxyEntry.empty() : proxy;
            safe.normalize();
            return safe;
        });
    }

    private static String accountKey(String accountName) {
        return accountName == null ? "" : accountName.trim();
    }
}
