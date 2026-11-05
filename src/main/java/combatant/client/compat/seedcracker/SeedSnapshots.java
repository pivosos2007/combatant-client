/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import net.fabricmc.loader.api.FabricLoader;
import combatant.client.config.profile.ConfigServerBindings;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * Persistent seed snapshots indexed by (server, dimension, worldgen profile).
 * A session-only snapshot is used solely when automatic disk saving is disabled.
 * No dependency on SeedCrackerX is needed to read or use saved seeds.
 */
public final class SeedSnapshots {
    private static final Map<String, SeedSnapshot> SAVED = new HashMap<>();
    private static final Map<String, SeedSnapshot> SESSION = new HashMap<>();
    private static boolean loaded;
    private SeedSnapshots() { }

    /** Seed available to consumers; the use_saved setting governs only automatic consumption. */
    public static Optional<SeedSnapshot> current() {
        return SeedCrackerConfig.get().useSaved()
                ? currentStored().filter(snapshot -> snapshot.origin() != SeedSnapshot.Origin.CANDIDATE)
                : Optional.empty();
    }

    /** For commands/diagnostics, even when automatic consumption is disabled. */
    public static Optional<SeedSnapshot> currentStored() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return Optional.empty();
        return lookup(server(mc), dimension(mc), profile());
    }

    public static boolean currentIsPersisted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        synchronized (SAVED) {
            load();
            SeedSnapshot visible = currentStored().orElse(null);
            SeedSnapshot disk = SAVED.get(key(server(mc), dimension(mc), profile()));
            return visible != null && visible.equals(disk);
        }
    }

    /** Explicitly commits a session-only recovery to disk. */
    public static boolean persistCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.isSameThread()) return false;
        synchronized (SAVED) {
            load();
            String id = key(server(mc), dimension(mc), profile());
            SeedSnapshot snapshot = SESSION.get(id);
            if (snapshot == null) return SAVED.containsKey(id);
            SeedSnapshot previous = SAVED.put(id, snapshot);
            if (persist()) return true;
            if (previous == null) SAVED.remove(id);
            else SAVED.put(id, previous);
            return false;
        }
    }

    public static OptionalLong worldSeed() {
        Optional<SeedSnapshot> result = current();
        return result.isPresent() ? OptionalLong.of(result.get().worldSeed()) : OptionalLong.empty();
    }

    /** The current OreSimulation implementation assumes vanilla 26.3 worldgen. */
    public static OptionalLong forNativeOreSimulation() {
        return "26.3".equals(profile()) ? worldSeed() : OptionalLong.empty();
    }

    public static String profile() { return SeedCrackerRuntime.profile(); }

    public static String dimension(Minecraft mc) {
        return mc == null || mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    public static String server(Minecraft mc) {
        if (mc == null) return "";
        if (mc.hasSingleplayerServer()) {
            return "local:" + (mc.getSingleplayerServer() == null ? "unknown" : mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        return ConfigServerBindings.currentAddress();
    }

    public static Optional<SeedSnapshot> lookup(String server, String dimension, String profile) {
        if (server == null || server.isBlank() || dimension == null || dimension.isBlank()
                || profile == null || profile.isBlank()) return Optional.empty();
        synchronized (SAVED) {
            load();
            String id = key(server, dimension, profile);
            SeedSnapshot live = SESSION.get(id);
            return Optional.ofNullable(live != null ? live : SAVED.get(id));
        }
    }

    public static boolean save(long seed, SeedSnapshot.Origin origin) { return save(seed, origin, true); }

    public static boolean save(long seed, SeedSnapshot.Origin origin, boolean persistToDisk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.isSameThread()) return false;
        String host = server(mc);
        String dim = dimension(mc);
        if (host.isBlank() || dim.isBlank()) return false;
        SeedSnapshot snapshot = new SeedSnapshot(seed, host, dim, profile(), origin, System.currentTimeMillis());
        String id = key(host, dim, snapshot.worldgen());
        synchronized (SAVED) {
            load();
            SESSION.put(id, snapshot);
            if (!persistToDisk) return true;
            SeedSnapshot previous = SAVED.put(id, snapshot);
            if (persist()) return true;
            if (previous == null) SAVED.remove(id);
            else SAVED.put(id, previous);
            return false;
        }
    }

    public static boolean forget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.isSameThread()) return false;
        synchronized (SAVED) {
            load();
            String id = key(server(mc), dimension(mc), profile());
            SeedSnapshot previousSession = SESSION.remove(id);
            SeedSnapshot previous = SAVED.remove(id);
            if (previous == null) return previousSession != null;
            if (persist()) return true;
            SAVED.put(id, previous);
            if (previousSession != null) SESSION.put(id, previousSession);
            return false;
        }
    }

    public static void clearSession() {
        synchronized (SAVED) { SESSION.clear(); }
    }

    private static String key(String server, String dimension, String profile) {
        return server.trim().toLowerCase(Locale.ROOT) + "|" + dimension.trim().toLowerCase(Locale.ROOT) + "|" + profile;
    }

    private static String legacyKey(String server, String profile) {
        return server.trim().toLowerCase(Locale.ROOT) + "|" + profile;
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("combatant/seedcracker/snapshots.properties"); }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static boolean validFile(byte[] bytes) {
        Properties p = new Properties();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            p.load(in);
            // An empty database is valid after forgetting the last snapshot.
            return p.stringPropertyNames().stream().allMatch(s -> s.matches("[a-f0-9]{64}\\.(seed|server|dimension|worldgen|origin|time)"));
        } catch (IOException | IllegalArgumentException e) { return false; }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        AtomicFileStore.ReadResult read = AtomicFileStore.readRecovering(file(), SeedSnapshots::validFile);
        if (!read.found()) return;
        Properties props = new Properties();
        boolean legacyFound = false;
        try (ByteArrayInputStream in = new ByteArrayInputStream(read.bytes())) {
            props.load(in);
            for (String id : props.stringPropertyNames()) {
                if (!id.endsWith(".seed")) continue;
                String prefix = id.substring(0, id.length() - 5);
                try {
                    String host = props.getProperty(prefix + ".server");
                    String dim = props.getProperty(prefix + ".dimension");
                    String worldgen = props.getProperty(prefix + ".worldgen");
                    if (host == null || host.isBlank() || dim == null || dim.isBlank()
                            || worldgen == null || worldgen.isBlank()) continue;
                    String currentHash = digest(key(host, dim, worldgen));
                    boolean legacy = digest(legacyKey(host, worldgen)).equals(prefix);
                    if (!currentHash.equals(prefix) && !legacy) continue;
                    SeedSnapshot snapshot = new SeedSnapshot(Long.parseLong(props.getProperty(id)), host,
                            dim, worldgen, SeedSnapshot.Origin.valueOf(props.getProperty(prefix + ".origin", "MANUAL")),
                            Long.parseLong(props.getProperty(prefix + ".time", "0")));
                    SAVED.put(key(host, dim, worldgen), snapshot);
                    legacyFound |= legacy;
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException ex) { DebugLog.error("SeedCracker snapshot load failed", ex); }
        if (legacyFound && !persist()) DebugLog.warn("SeedCracker snapshot migration write failed; retry on next save");
    }

    private static boolean persist() {
        Properties props = new Properties();
        for (SeedSnapshot entry : SAVED.values()) {
            String id = digest(key(entry.server(), entry.dimension(), entry.worldgen()));
            props.setProperty(id + ".seed", Long.toString(entry.worldSeed()));
            props.setProperty(id + ".server", entry.server());
            props.setProperty(id + ".dimension", entry.dimension());
            props.setProperty(id + ".worldgen", entry.worldgen());
            props.setProperty(id + ".origin", entry.origin().name());
            props.setProperty(id + ".time", Long.toString(entry.savedAtMillis()));
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            props.store(out, "Combatant local seeds by server/dimension/worldgen");
            AtomicFileStore.write(file(), out.toByteArray(), SeedSnapshots::validFile);
            return true;
        } catch (IOException e) {
            DebugLog.error("SeedCracker snapshot write failed", e);
            return false;
        }
    }
}
