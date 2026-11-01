package combatant.client.compat.seedcracker;

import combatant.client.config.ConfigPaths;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.config.subsystem.SeedCrackerConfig;
import combatant.client.util.logging.DebugLog;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.Finder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

/** The original command settings, persisted per Combatant server/dimension, not in seedcracker.json. */
final class SeedCrackerNativeState {
    private static final String FORMAT = "1";
    private static String activeScope;
    private static Values defaults;
    private static Values current;

    private SeedCrackerNativeState() { }

    private record Values(Config.RenderType render, boolean debug, Map<Finder.Type, Boolean> finders) {
        static Values readCurrent() {
            EnumMap<Finder.Type, Boolean> map = new EnumMap<>(Finder.Type.class);
            for (Finder.Type type : Finder.Type.values()) map.put(type, type.enabled.get());
            return new Values(Config.get().render, Config.get().debug, map);
        }

        void apply() {
            Config config = Config.get();
            config.debug = debug;
            config.render = SeedCrackerConfig.get().showNativeOverlay()
                    ? (render == Config.RenderType.OFF ? Config.RenderType.XRAY : render)
                    : Config.RenderType.OFF;
            boolean guard = SeedCrackerConfig.get().ignoreServerHash();
            for (var e : finders.entrySet()) {
                Finder.Type type = e.getKey();
                boolean enabled = e.getValue();
                if (guard) {
                    if (type == Finder.Type.END_CITY || type == Finder.Type.END_PILLARS
                            || type == Finder.Type.END_GATEWAY || type == Finder.Type.BIOME) enabled = false;
                    if (type == Finder.Type.DUNGEON) enabled = true;
                }
                type.enabled.set(enabled);
            }
        }
    }

    /** Capture pristine upstream defaults before our hash guard alters finder flags. */
    static void captureDefaults() {
        if (defaults == null) defaults = Values.readCurrent();
    }

    private static String scope() {
        return SeedCrackerConfig.get().contextDescription();
    }

    private static Path path(String scope) {
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return ConfigPaths.subsystemsRoot().resolve("seedcracker/contexts/upstream-"
                    + HexFormat.of().formatHex(hash) + ".properties");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void sync() {
        String next = scope();
        if (defaults == null) defaults = Values.readCurrent();
        if (!next.equals(activeScope)) {
            activeScope = next;
            current = load(next);
        }
        // Integration.sync() also handles hash guard and overlay. Apply the
        // per-context native options last so a previous server cannot leak state.
        current.apply();
    }

    static void capture() {
        if (activeScope == null) sync();
        // Hash guard forces some detector values temporarily; don't replace the
        // user's saved preferences with the guard's transient suppression.
        Values actual = Values.readCurrent();
        if (SeedCrackerConfig.get().ignoreServerHash() && current != null) {
            EnumMap<Finder.Type, Boolean> chosen = new EnumMap<>(actual.finders());
            for (Finder.Type type : Finder.Type.values()) {
                if (type == Finder.Type.END_CITY || type == Finder.Type.END_PILLARS
                        || type == Finder.Type.END_GATEWAY || type == Finder.Type.BIOME
                        || type == Finder.Type.DUNGEON) {
                    chosen.put(type, current.finders().get(type));
                }
            }
            actual = new Values(actual.render(), actual.debug(), chosen);
        }
        current = actual;
        save(activeScope, current);
    }

    private static Values load(String context) {
        var result = AtomicFileStore.readRecovering(path(context), b -> valid(b, context));
        if (!result.found()) return defaults;
        try {
            Properties p = new Properties();
            p.load(new ByteArrayInputStream(result.bytes()));
            EnumMap<Finder.Type, Boolean> flags = new EnumMap<>(Finder.Type.class);
            for (Finder.Type type : Finder.Type.values()) {
                flags.put(type, Boolean.parseBoolean(p.getProperty("finder." + type.name())));
            }
            return new Values(Config.RenderType.valueOf(p.getProperty("render")),
                    Boolean.parseBoolean(p.getProperty("debug")), flags);
        } catch (Exception e) {
            DebugLog.error("SeedCrackerX native command settings failed to load", e);
            return defaults;
        }
    }

    private static boolean valid(byte[] bytes, String context) {
        try {
            Properties p = new Properties();
            p.load(new ByteArrayInputStream(bytes));
            if (!FORMAT.equals(p.getProperty("format")) || !context.equals(p.getProperty("scope"))) return false;
            Config.RenderType.valueOf(p.getProperty("render"));
            if (!isBoolean(p.getProperty("debug"))) return false;
            for (Finder.Type type : Finder.Type.values()) {
                if (!isBoolean(p.getProperty("finder." + type.name()))) return false;
            }
            return true;
        } catch (RuntimeException | IOException e) {
            return false;
        }
    }

    private static boolean isBoolean(String s) {
        return "true".equals(s) || "false".equals(s);
    }

    private static void save(String context, Values state) {
        try {
            Properties p = new Properties();
            p.setProperty("format", FORMAT);
            p.setProperty("scope", context);
            p.setProperty("render", state.render().name());
            p.setProperty("debug", Boolean.toString(state.debug()));
            for (var e : state.finders().entrySet()) {
                p.setProperty("finder." + e.getKey().name(), Boolean.toString(e.getValue()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            p.store(out, "Combatant SeedCrackerX native options: per-server and dimension");
            AtomicFileStore.write(path(context), out.toByteArray(), bytes -> valid(bytes, context));
        } catch (IOException e) {
            DebugLog.error("SeedCrackerX native command settings failed to save", e);
        }
    }
}
