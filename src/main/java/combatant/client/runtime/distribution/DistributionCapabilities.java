/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.distribution;

import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Build-time distribution metadata exposed to runtime code.
 *
 * <p>Release artifacts embed {@code META-INF/combatant/distribution.properties}.
 * Development and ordinary non-release builds deliberately fall back to a permissive
 * universal state so IDE/native dependency layouts keep working.</p>
 */
public final class DistributionCapabilities {
    public static final String METADATA_RESOURCE = "/META-INF/combatant/distribution.properties";

    private static final State STATE = load();

    private DistributionCapabilities() {
    }

    public static String profile() {
        return STATE.profile;
    }

    public static String target() {
        return STATE.target;
    }

    public static String fontProfile() {
        return STATE.fontProfile;
    }

    public static boolean isReleaseArtifact() {
        return STATE.release;
    }

    public static String currentTargetId() {
        return targetId(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static boolean isJavetAvailable() {
        return matchesCurrentTarget(STATE.javetTargets);
    }

    public static String javetUnavailableReason() {
        if (isJavetAvailable()) return "";
        if (!STATE.release) return "Javet V8 native runtime is unavailable in the current development runtime.";
        String current = currentTargetId();
        if (targetSetContains(STATE.javetOmittedTargets, current)) {
            return "Javet V8 native runtime was intentionally omitted from distribution profile '"
                    + STATE.profile + "' for target '" + current + "'.";
        }
        return "Javet V8 native runtime is not packaged for target '" + current
                + "' in distribution profile '" + STATE.profile + "'.";
    }

    public static boolean isMediaSessionAvailable() {
        String current = currentTargetId();
        if (!isMediaSupportedOperatingSystem(current)) return false;
        return targetSetContains(STATE.mediaTargets, current);
    }

    public static boolean isMediaSessionIntentionallyOmitted() {
        String current = currentTargetId();
        return STATE.release && isMediaSupportedOperatingSystem(current)
                && targetSetContains(STATE.mediaOmittedTargets, current);
    }

    public static String mediaSessionUnavailableReason() {
        if (isMediaSessionAvailable()) return "";
        String current = currentTargetId();
        if (!isMediaSupportedOperatingSystem(current)) {
            return "Media session integration is not supported on target '" + current + "'.";
        }
        if (STATE.release && targetSetContains(STATE.mediaOmittedTargets, current)) {
            return "Media session integration was intentionally omitted from distribution profile '"
                    + STATE.profile + "' for target '" + current + "'.";
        }
        if (STATE.release) {
            return "Media session integration is not packaged for target '" + current
                    + "' in distribution profile '" + STATE.profile + "'.";
        }
        return "Media session integration failed to initialize in the current development runtime.";
    }

    public static boolean isOptionalFontCoverageAvailable(String coverageId) {
        if (coverageId == null || coverageId.isBlank()) return false;
        return STATE.fontCoverage.contains(normalizeToken(coverageId));
    }

    /**
     * Returns true when the build intentionally removed the source TTF/OTF because a
     * validated MSDF atlas is authoritative for that builtin face.
     */
    public static boolean isAtlasOnlyBuiltinFont(Identifier resource) {
        if (resource == null || !"combatant".equals(resource.getNamespace())) return false;
        String path = resource.getPath();
        if (!path.startsWith("font/")) return false;
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        return STATE.atlasOnlyFontFiles.contains(fileName.toLowerCase(Locale.ROOT));
    }

    public static Set<String> atlasOnlyFontFiles() {
        return STATE.atlasOnlyFontFiles;
    }

    public static String displaySuffix() {
        if (!STATE.release || "full".equalsIgnoreCase(STATE.profile)) return "";
        return STATE.profile + "/" + STATE.target + "/" + STATE.fontProfile;
    }

    static String targetId(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);

        String platform;
        if (os.startsWith("windows")) {
            platform = "windows";
        } else if (os.equals("linux") || os.contains("linux")) {
            platform = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "macos";
        } else {
            platform = normalizeToken(os.isBlank() ? "unknown" : os);
        }

        String normalizedArch;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) {
            normalizedArch = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            normalizedArch = "arm64";
        } else {
            normalizedArch = normalizeToken(arch.isBlank() ? "unknown" : arch);
        }
        return platform + '-' + normalizedArch;
    }

    private static boolean matchesCurrentTarget(Set<String> targets) {
        return targetSetContains(targets, currentTargetId());
    }

    private static boolean targetSetContains(Set<String> targets, String current) {
        if (targets.contains("*")) return true;
        if (targets.contains(current)) return true;
        int dash = current.indexOf('-');
        String platform = dash > 0 ? current.substring(0, dash) : current;
        return targets.contains(platform + "-*") || targets.contains(platform);
    }

    private static boolean isMediaSupportedOperatingSystem(String current) {
        return current.startsWith("windows-") || current.startsWith("linux-");
    }

    private static State load() {
        Properties properties = new Properties();
        try (InputStream input = DistributionCapabilities.class.getResourceAsStream(METADATA_RESOURCE)) {
            if (input == null) return State.development();
            properties.load(input);
        } catch (IOException ignored) {
            return State.development();
        }

        return new State(
                Boolean.parseBoolean(properties.getProperty("release", "true")),
                properties.getProperty("profile", "full").trim(),
                properties.getProperty("target", "universal").trim(),
                properties.getProperty("fontProfile", "full").trim(),
                csv(properties.getProperty("javet.targets", "")),
                csv(properties.getProperty("javet.omittedTargets", "")),
                csv(properties.getProperty("media.targets", "")),
                csv(properties.getProperty("media.omittedTargets", "")),
                csv(properties.getProperty("font.coverage", "base")),
                lowerCsv(properties.getProperty("font.atlasOnlyFiles", ""))
        );
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(DistributionCapabilities::normalizeToken)
                .forEach(out::add);
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> lowerCsv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> token.toLowerCase(Locale.ROOT))
                .forEach(out::add);
        return Collections.unmodifiableSet(out);
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private record State(boolean release,
                         String profile,
                         String target,
                         String fontProfile,
                         Set<String> javetTargets,
                         Set<String> javetOmittedTargets,
                         Set<String> mediaTargets,
                         Set<String> mediaOmittedTargets,
                         Set<String> fontCoverage,
                         Set<String> atlasOnlyFontFiles) {
        private static State development() {
            return new State(false, "development", "universal", "full",
                    Set.of("*"), Set.of(), Set.of("windows-*", "linux-*"), Set.of(),
                    Set.of("base", "cjk", "full"), Set.of());
        }
    }
}
