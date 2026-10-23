/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import combatant.client.config.ConfigPaths;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.module.Notifier;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Binds profiles to server addresses and loads them when that server is joined.
 *
 * <p>One profile per (profile type, server): joining a server applies the bound modules, hud and themes
 * profiles. The binding is stored in {@code config/combatant/profiles/server-bindings.properties}
 * ({@code <type>|<address>=<profile id>}). Auto-load runs once per connection, not on every dimension
 * change, and only touches profiles that still exist.
 */
public final class ConfigServerBindings {
    public static final ConfigServerBindings INSTANCE = new ConfigServerBindings();

    private static final String FILE_NAME = "server-bindings.properties";
    private static final int DEFAULT_PORT = 25565;

    private final Map<String, String> bindings = new LinkedHashMap<>();
    private boolean loaded;
    /** Address the bound profiles were last applied for; cleared on disconnect so a rejoin re-applies. */
    private String appliedFor = "";

    private ConfigServerBindings() {
    }

    /** Normalized address of the server the client is connected to, or empty (singleplayer / menu). */
    public static String currentAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.hasSingleplayerServer()) return "";
        ServerData server = mc.getCurrentServer();
        return server == null ? "" : normalize(server.ip);
    }

    /** Lower-cases, trims, drops a trailing dot and the default port so {@code Host:25565} equals {@code host}. */
    public static String normalize(String address) {
        if (address == null) return "";
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(":" + DEFAULT_PORT)) value = value.substring(0, value.length() - (":" + DEFAULT_PORT).length());
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public synchronized String boundProfile(ConfigProfileType type, String address) {
        ensureLoaded();
        return bindings.get(key(type, address));
    }

    public boolean isBound(ConfigProfileType type, String profileId, String address) {
        if (profileId == null || address == null || address.isEmpty()) return false;
        return profileId.equals(boundProfile(type, address));
    }

    /** Binds the profile to the server, or removes the binding if that exact profile is already bound. */
    public synchronized boolean toggle(ConfigProfileType type, String profileId, String address) throws IOException {
        ensureLoaded();
        String key = key(type, address);
        boolean nowBound;
        if (profileId.equals(bindings.get(key))) {
            bindings.remove(key);
            nowBound = false;
        } else {
            bindings.put(key, profileId);
            nowBound = true;
        }
        save();
        return nowBound;
    }

    /** Keeps bindings valid when a profile is renamed. */
    public synchronized void renamed(ConfigProfileType type, String oldId, String newId) {
        ensureLoaded();
        boolean changed = false;
        String prefix = type.folderName() + "|";
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().equals(oldId)) {
                entry.setValue(newId);
                changed = true;
            }
        }
        if (changed) saveQuietly();
    }

    /** Drops every binding to a deleted profile. */
    public synchronized void removed(ConfigProfileType type, String profileId) {
        ensureLoaded();
        String prefix = type.folderName() + "|";
        boolean changed = bindings.entrySet().removeIf(e -> e.getKey().startsWith(prefix) && e.getValue().equals(profileId));
        if (changed) saveQuietly();
    }

    @EventHandler
    public void onWorldChange(WorldChangeEvent event) {
        if (event.reason() == WorldChangeEvent.Reason.DISCONNECT) {
            appliedFor = "";
            return;
        }
        String address = currentAddress();
        if (address.isEmpty() || address.equals(appliedFor)) return;
        appliedFor = address;
        Minecraft mc = Minecraft.getInstance();
        // The level is replaced after this event; apply once the join has settled.
        mc.execute(() -> applyFor(address));
    }

    private void applyFor(String address) {
        if (!address.equals(currentAddress())) return;
        StringBuilder names = new StringBuilder();
        for (ConfigProfileType type : ConfigProfileType.values()) {
            String id = boundProfile(type, address);
            if (id == null) continue;
            try {
                ConfigProfileService.INSTANCE.apply(type, id);
                if (!names.isEmpty()) names.append(", ");
                names.append(id);
            } catch (IOException | RuntimeException e) {
                DebugLog.error("Server auto-load failed for %s (%s)", e, address, id);
            }
        }
        if (!names.isEmpty()) Notifier.info(I18n.get("notification.config.server_autoload", names.toString(), address));
    }

    private static String key(ConfigProfileType type, String address) {
        return type.folderName() + "|" + normalize(address);
    }

    private static Path file() {
        return ConfigPaths.profilesRoot().resolve(FILE_NAME);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try (InputStream in = new ByteArrayInputStream(Files.readAllBytes(file))) {
            Properties props = new Properties();
            props.load(in);
            for (String name : props.stringPropertyNames()) bindings.put(name, props.getProperty(name));
        } catch (IOException | RuntimeException e) {
            DebugLog.error("Failed to read server profile bindings %s", e, file.toAbsolutePath());
        }
    }

    private void save() throws IOException {
        Properties props = new Properties();
        // Sorted so the file is stable between saves.
        new TreeMap<>(bindings).forEach(props::setProperty);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.store(out, "Combatant: profile -> server auto-load bindings");
        AtomicFileStore.write(file(), out.toByteArray(), null);
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException e) {
            DebugLog.error("Failed to save server profile bindings", e);
        }
    }
}
