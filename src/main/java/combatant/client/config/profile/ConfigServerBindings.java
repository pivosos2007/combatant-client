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
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Tracks loaded profiles and their bindings to multiplayer servers or singleplayer save folders.
 *
 * <p>One profile per (type, world key) is stored in
 * {@code profiles/server-bindings.properties} as {@code <type>|<world key>=<profile id>}.
 * Local worlds use {@code local:<folder>} keys. Bound profiles are saved after a quiet period
 * when their settings change, and synchronously before leaving or switching worlds.
 */
public final class ConfigServerBindings {
    public static final ConfigServerBindings INSTANCE = new ConfigServerBindings();

    private static final String FILE_NAME = "server-bindings.properties";
    private static final int DEFAULT_PORT = 25565;
    private static final int AUTO_SAVE_POLL_TICKS = 20;
    private static final long AUTO_SAVE_QUIET_NANOS = 1_000_000_000L;
    private static final long AUTO_SAVE_MAX_NANOS = 10_000_000_000L;

    private final ConfigProfileCollector collector = new ConfigProfileCollector();
    private final Map<String, String> bindings = new LinkedHashMap<>();
    private final Path bindingsFile;
    private final EnumMap<ConfigProfileType, ActiveProfile> active = new EnumMap<>(ConfigProfileType.class);
    private boolean loaded;
    private int autoSaveTicks;
    /** World key last processed for automatic loading; reset on disconnect. */
    private String appliedFor = "";

    private ConfigServerBindings() {
        this(null);
    }

    ConfigServerBindings(Path bindingsFile) {
        this.bindingsFile = bindingsFile;
    }

    /** Stable binding key for a multiplayer server or local save folder; empty in the menu. */
    public static String currentAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "";
        if (mc.getSingleplayerServer() != null) {
            var server = mc.getSingleplayerServer();
            Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return normalize("local:" + world.getFileName());
        }
        return currentServerAddress();
    }

    /** Multiplayer-only address for systems that must not act on local worlds. */
    public static String currentServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSingleplayerServer() != null) return "";
        ServerData server = mc.getCurrentServer();
        return server == null ? "" : normalize(server.ip);
    }

    public static String currentDisplayName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return currentAddress();
    }

    /** Lower-cases, trims, drops a trailing dot and the default port so {@code Host:25565} equals {@code host}. */
    public static String normalize(String address) {
        if (address == null) return "";
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("local:")) return value;
        if (value.endsWith(":" + DEFAULT_PORT)) value = value.substring(0, value.length() - (":" + DEFAULT_PORT).length());
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    /** Snapshot of editable bindings for a particular profile type, including local worlds. */
    public synchronized Map<String, String> bindingsFor(ConfigProfileType type) {
        ensureLoaded();
        Map<String, String> result = new TreeMap<>();
        String prefix = type.folderName() + "|";
        bindings.forEach((key, value) -> {
            if (key.startsWith(prefix)) result.put(key.substring(prefix.length()), value);
        });
        return result;
    }

    public synchronized String activeProfile(ConfigProfileType type) {
        ActiveProfile profile = active.get(type);
        return profile == null ? null : profile.id;
    }

    public synchronized String activeContext(ConfigProfileType type) {
        ActiveProfile profile = active.get(type);
        return profile == null ? "" : profile.context;
    }

    /** Saves edits to the previous live profile before applying a different one. */
    public synchronized void beforeApply(ConfigProfileType type, String nextId) throws IOException {
        ActiveProfile previous = active.get(type);
        if (previous != null && !previous.id.equals(nextId)) saveActive(type, previous);
    }

    /** Called after the profile has been applied, so the baseline reflects the actual runtime values. */
    public synchronized void applied(ConfigProfileType type, ConfigProfileSnapshot snapshot) {
        active.put(type, new ActiveProfile(snapshot.meta().getId(), currentAddress(),
                collector.collectEntries(type)));
    }

    /** Aligns dirty tracking with an explicit manual overwrite. */
    public synchronized void saved(ConfigProfileType type, String id, List<ConfigProfileEntry> entries) {
        ActiveProfile profile = active.get(type);
        if (profile != null && profile.id.equals(id)) profile.acceptSaved(entries);
    }

    @EventHandler
    public synchronized void onGameTick(GameTickEvent event) {
        if (++autoSaveTicks < AUTO_SAVE_POLL_TICKS) return;
        autoSaveTicks = 0;
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        String address = currentAddress();
        if (address.isEmpty()) return;
        long now = System.nanoTime();
        for (var entry : new ArrayList<>(active.entrySet())) {
            ConfigProfileType type = entry.getKey();
            ActiveProfile profile = entry.getValue();
            if (!address.equals(profile.context) || !profile.id.equals(boundProfile(type, address))) continue;
            try {
                List<ConfigProfileEntry> current = collector.collectEntries(type);
                if (!ConfigProfileChangeSet.hasChanges(profile.baseline, current)) {
                    profile.resetPending();
                    continue;
                }
                if (profile.firstDirtyAt == 0L) {
                    profile.firstDirtyAt = now;
                    profile.lastChangedAt = now;
                }
                if (!current.equals(profile.lastObserved)) {
                    profile.lastObserved = current;
                    profile.lastChangedAt = now;
                }
                if (now - profile.lastChangedAt < AUTO_SAVE_QUIET_NANOS
                        && now - profile.firstDirtyAt < AUTO_SAVE_MAX_NANOS) continue;
                saveActive(type, profile, current);
            } catch (IOException | RuntimeException e) {
                DebugLog.error("Failed to auto-save bound profile %s", e, profile.id);
            }
        }
    }

    public synchronized void flushActive() {
        for (var entry : new ArrayList<>(active.entrySet())) {
            try {
                saveActive(entry.getKey(), entry.getValue());
            } catch (IOException | RuntimeException e) {
                DebugLog.error("Failed to auto-save active profile %s", e, entry.getValue().id);
            }
        }
    }

    private void saveActive(ConfigProfileType type, ActiveProfile profile) throws IOException {
        saveActive(type, profile, collector.collectEntries(type));
    }

    private void saveActive(ConfigProfileType type, ActiveProfile profile,
                            List<ConfigProfileEntry> current) throws IOException {
        if (!ConfigProfileChangeSet.hasChanges(profile.baseline, current)) {
            profile.resetPending();
            return;
        }
        if (!ConfigProfileService.INSTANCE.storage().exists(type, profile.id)) return;
        ConfigProfileSnapshot stored = ConfigProfileService.INSTANCE.read(type, profile.id);
        List<ConfigProfileEntry> merged = ConfigProfileChangeSet.merge(stored.entries(), profile.baseline, current);
        if (!merged.equals(stored.entries())) {
            ConfigProfileService.INSTANCE.storage().save(new ConfigProfileSnapshot(
                    stored.meta().withUpdatedAt(System.currentTimeMillis()), merged));
        }
        profile.acceptSaved(current);
    }

    public synchronized void set(ConfigProfileType type, String id, String context) throws IOException {
        ensureLoaded();
        String key = checkedKey(type, context);
        String previous = bindings.put(key, id);
        try { save(); } catch (IOException e) {
            if (previous == null) bindings.remove(key);
            else bindings.put(key, previous);
            throw e;
        }
    }

    /** Move an existing profile-owned assignment (or create a new one) in a single file write.
     *  The new target always replaces its previous profile for this category.
     */
    public synchronized void replace(ConfigProfileType type, String id,
                                     String previousContext, String nextContext) throws IOException {
        ensureLoaded();
        String nextKey = checkedKey(type, nextContext);
        String oldKey = previousContext == null || previousContext.isEmpty()
                ? null : checkedKey(type, previousContext);
        if (oldKey != null && !id.equals(bindings.get(oldKey)))
            throw new IllegalStateException("profile binding changed while editing");
        String replaced = bindings.get(nextKey);
        if (oldKey != null && !oldKey.equals(nextKey)) bindings.remove(oldKey);
        bindings.put(nextKey, id);
        try {
            save();
        } catch (IOException e) {
            if (replaced == null) bindings.remove(nextKey);
            else bindings.put(nextKey, replaced);
            if (oldKey != null && !oldKey.equals(nextKey)) bindings.put(oldKey, id);
            throw e;
        }
    }

    public synchronized void unbind(ConfigProfileType type, String context) throws IOException {
        ensureLoaded();
        String key = checkedKey(type, context);
        String previous = bindings.remove(key);
        try { save(); } catch (IOException e) {
            if (previous != null) bindings.put(key, previous);
            throw e;
        }
    }

    /** Reload manually edited properties without clearing the active runtime profile. */
    public synchronized void reload() {
        bindings.clear();
        loaded = false;
        ensureLoaded();
    }

    private static String checkedKey(ConfigProfileType type, String context) {
        String normalized = normalize(context);
        if (normalized.isBlank() || normalized.equals("local:") || normalized.indexOf('|') >= 0
                || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0)
            throw new IllegalArgumentException("invalid profile binding context");
        return key(type, normalized);
    }

    private static final class ActiveProfile {
        private String id;
        private final String context;
        private List<ConfigProfileEntry> baseline;
        private List<ConfigProfileEntry> lastObserved;
        private long firstDirtyAt;
        private long lastChangedAt;

        private ActiveProfile(String id, String context, List<ConfigProfileEntry> baseline) {
            this.id = id;
            this.context = context;
            acceptSaved(baseline);
        }

        private void acceptSaved(List<ConfigProfileEntry> entries) {
            baseline = entries;
            lastObserved = entries;
            resetPending();
        }

        private void resetPending() {
            firstDirtyAt = 0L;
            lastChangedAt = 0L;
            lastObserved = baseline;
        }
    }

    public synchronized String boundProfile(ConfigProfileType type, String address) {
        ensureLoaded();
        return bindings.get(key(type, address));
    }

    public boolean isBound(ConfigProfileType type, String profileId, String address) {
        if (profileId == null || address == null || address.isEmpty()) return false;
        return profileId.equals(boundProfile(type, address));
    }

    /** Toggles the profile binding for the normalized world key. */
    public synchronized boolean toggle(ConfigProfileType type, String profileId, String address) throws IOException {
        ensureLoaded();
        String key = checkedKey(type, address);
        String previous = bindings.get(key);
        boolean nowBound;
        if (profileId.equals(previous)) {
            bindings.remove(key);
            nowBound = false;
        } else {
            bindings.put(key, profileId);
            nowBound = true;
        }
        try {
            save();
        } catch (IOException e) {
            // A failed disk write must not leave an ephemeral binding visible in the GUI.
            if (previous == null) bindings.remove(key);
            else bindings.put(key, previous);
            throw e;
        }
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
        ActiveProfile live = active.get(type);
        if (live != null && live.id.equals(oldId)) live.id = newId;
    }

    /** Drops every binding to a deleted profile. */
    public synchronized void removed(ConfigProfileType type, String profileId) {
        ensureLoaded();
        String prefix = type.folderName() + "|";
        boolean changed = bindings.entrySet().removeIf(e -> e.getKey().startsWith(prefix) && e.getValue().equals(profileId));
        if (changed) saveQuietly();
        ActiveProfile live = active.get(type);
        if (live != null && live.id.equals(profileId)) active.remove(type);
    }

    @EventHandler(priority = 1000)
    public synchronized void onWorldChange(WorldChangeEvent event) {
        if (event.reason() == WorldChangeEvent.Reason.DISCONNECT) {
            flushActive();
            active.clear();
            appliedFor = "";
            return;
        }
        // This event fires before setLevel replaces the old world. Preserve that world's edits
        // while the old runtime state is still available.
        flushActive();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // The event fires at setLevel HEAD. Resolve the local world path only after setLevel returns.
        mc.schedule(() -> {
            if (mc.level == null) return;
            String address = currentAddress();
            if (address.isEmpty() || address.equals(appliedFor)) return;
            active.clear();
            appliedFor = address;
            applyFor(address);
        });
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
        if (!names.isEmpty()) CommandOutput.send(I18n.get("notification.config.server_autoload", names.toString(), address));
    }

    private static String key(ConfigProfileType type, String address) {
        return type.folderName() + "|" + normalize(address);
    }

    private Path file() {
        if (bindingsFile != null) return bindingsFile;
        return ConfigPaths.profilesRoot().resolve(FILE_NAME);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = file();
        AtomicFileStore.ReadResult read = AtomicFileStore.readRecovering(path, this::validBindings);
        if (!read.found()) return;
        try (InputStream in = new ByteArrayInputStream(read.bytes())) {
            Properties props = new Properties();
            props.load(in);
            for (String name : new java.util.TreeSet<>(props.stringPropertyNames())) {
                String value = props.getProperty(name);
                if (!name.contains("|") || value == null || value.isBlank()) continue;
                int separator = name.indexOf('|');
                String prefix = name.substring(0, separator);
                for (ConfigProfileType type : ConfigProfileType.values()) {
                    if (!type.folderName().equals(prefix)) continue;
                    try {
                        String key = checkedKey(type, name.substring(separator + 1));
                        // A canonical entry wins over aliases from manually edited files.
                        if (name.equals(key) || !props.containsKey(key)) bindings.put(key, value);
                    } catch (IllegalArgumentException ignored) {
                    }
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            DebugLog.error("Failed to read profile bindings %s", e, path.toAbsolutePath());
        }
    }

    private boolean validBindings(byte[] bytes) {
        try {
            Properties props = new Properties();
            props.load(new ByteArrayInputStream(bytes));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private void save() throws IOException {
        Properties props = new Properties();
        // Sorted so the file is stable between saves.
        new TreeMap<>(bindings).forEach(props::setProperty);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.store(out, "Combatant: profile -> world auto-load bindings");
        AtomicFileStore.write(file(), out.toByteArray(), this::validBindings);
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException e) {
            DebugLog.error("Failed to save server profile bindings", e);
        }
    }
}
