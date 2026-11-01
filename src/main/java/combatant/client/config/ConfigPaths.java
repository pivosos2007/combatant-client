/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Locale;

public enum ConfigPaths {
    ;

    // The root is fixed for this process; pending migrations run before any caller can cache a path.
    private static final class Storage {
        private static final ConfigurationRootManager MANAGER = open();

        private static ConfigurationRootManager open() {
            try {
                // OS process teardown releases the locks after all concurrent writer shutdown hooks finish.
                return ConfigurationRootManager.open(FabricLoader.getInstance().getGameDir());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Combatant configuration storage unavailable; recover the bootstrap metadata before restarting", e);
            }
        }
    }

    public static ConfigurationRootManager manager() {
        return Storage.MANAGER;
    }

    public static Path root() {
        return manager().root();
    }

    public static Path profilesRoot() {
        return root().resolve("profiles");
    }

    public static Path subsystemsRoot() {
        return root().resolve("subsystems");
    }

    /** Resolves a slash-separated subsystem path below config/combatant/subsystems. */
    public static Path subsystem(String relativePath) {
        Path out = subsystemsRoot();
        if (relativePath == null || relativePath.isBlank()) return out.resolve("config");

        String normalized = relativePath.trim().replace('\\', '/');
        for (String rawSegment : normalized.split("/+")) {
            if (rawSegment == null || rawSegment.isBlank()) continue;
            out = out.resolve(sanitizeSegment(rawSegment));
        }
        return out.equals(subsystemsRoot()) ? out.resolve("config") : out;
    }

    public static Path subsystemFile(String relativePath, String extension) {
        String ext = normalizeExtension(extension);
        Path path = subsystem(relativePath);
        Path fileName = path.getFileName();
        if (fileName == null) return path.resolve("config" + ext);
        return path.resolveSibling(fileName + ext);
    }

    public static String legacyNamespaceName() {
        return new String(new char[]{'c', 'o', 'm', 'b', 'a', 't', 'a', 'n', 't'});
    }

    private static String sanitizeSegment(String value) {
        String source = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty() || source.equals(".") || source.equals("..")) return "config";

        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }

        String result = out.toString();
        return result.isEmpty() || result.equals(".") || result.equals("..") ? "config" : result;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) return "";
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (!ext.startsWith(".")) ext = "." + ext;
        return ext.replaceAll("[^a-z0-9._-]", "_");
    }
}
