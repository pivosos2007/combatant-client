/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Text share codes for profiles and single-module settings.
 *
 * <p>A code is {@code <prefix><base64url>}, where the body is exactly the {@code .cbcfg} encoding
 * ({@link ConfigProfileBinaryCodec}: deflated payload + CRC). The prefix carries the kind and the code
 * format revision, so a code never silently decodes as something it is not and old builds reject
 * newer revisions explicitly. Because the body is the file encoding, a code decodes to the same snapshot
 * a {@code .cbcfg} file would.
 */
public final class ConfigShareCodec {
    public static final String PROFILE_PREFIX = "CBCFG1:";
    public static final String MODULE_PREFIX = "CBMOD1:";
    /** Upper bound for pasted text; far above any real profile, stops pathological clipboards. */
    private static final int MAX_CODE_CHARS = 2 * 1024 * 1024;
    private static final String FAMILY_PROFILE = "CBCFG";
    private static final String FAMILY_MODULE = "CBMOD";

    public enum Kind {PROFILE, MODULE}

    public enum Failure {
        /** Clipboard text is not a Combatant share code. */
        NOT_A_CODE,
        /** A Combatant code, but written by a newer, unknown revision. */
        UNSUPPORTED_VERSION,
        /** Prefix is fine but the body is truncated, altered or fails its checksum. */
        CORRUPT,
        /** Decoded fine, but holds nothing importable. */
        EMPTY
    }

    public static final class ShareCodeException extends Exception {
        private final Failure failure;

        ShareCodeException(Failure failure, Throwable cause) {
            super(failure.name(), cause);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    public record Decoded(Kind kind, ConfigProfileSnapshot snapshot) {
    }

    private final ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();

    public String encodeProfile(ConfigProfileSnapshot snapshot) throws IOException {
        return PROFILE_PREFIX + body(snapshot);
    }

    /** Wraps one module's entry into a single-entry modules snapshot and encodes it. */
    public String encodeModule(ConfigProfileEntry entry, String author) throws IOException {
        long now = System.currentTimeMillis();
        ConfigProfileMeta meta = new ConfigProfileMeta(
                ConfigProfileStorage.sanitizeFileName(entry.displayName()),
                entry.displayName(),
                ConfigProfileType.MODULES,
                author,
                now,
                now,
                ConfigProfileBinaryCodec.VERSION,
                currentVersion()
        );
        return MODULE_PREFIX + body(new ConfigProfileSnapshot(meta, List.of(entry)));
    }

    public Decoded decode(String text) throws ShareCodeException {
        String code = clean(text);
        int colon = code.indexOf(':');
        if (colon <= 0 || colon > 12) throw new ShareCodeException(Failure.NOT_A_CODE, null);
        String family = code.substring(0, colon).toUpperCase(Locale.ROOT);
        String body = code.substring(colon + 1);

        Kind kind;
        if (family.startsWith(FAMILY_PROFILE)) {
            kind = Kind.PROFILE;
            requireRevision(family, FAMILY_PROFILE);
        } else if (family.startsWith(FAMILY_MODULE)) {
            kind = Kind.MODULE;
            requireRevision(family, FAMILY_MODULE);
        } else {
            throw new ShareCodeException(Failure.NOT_A_CODE, null);
        }
        if (body.isEmpty()) throw new ShareCodeException(Failure.CORRUPT, null);

        ConfigProfileSnapshot snapshot;
        try {
            snapshot = codec.read(Base64.getUrlDecoder().decode(body));
        } catch (IOException | RuntimeException e) {
            throw new ShareCodeException(Failure.CORRUPT, e);
        }
        if (snapshot.entries().isEmpty()) throw new ShareCodeException(Failure.EMPTY, null);
        if (kind == Kind.MODULE) {
            if (snapshot.meta().type() != ConfigProfileType.MODULES || snapshot.entries().size() != 1) {
                throw new ShareCodeException(Failure.CORRUPT, null);
            }
        }
        return new Decoded(kind, snapshot);
    }

    /** True when the text looks like one of our codes (cheap pre-check, no decoding). */
    public static boolean looksLikeCode(String text) {
        String code = clean(text);
        return code.regionMatches(true, 0, FAMILY_PROFILE, 0, FAMILY_PROFILE.length())
                || code.regionMatches(true, 0, FAMILY_MODULE, 0, FAMILY_MODULE.length());
    }

    private static void requireRevision(String family, String base) throws ShareCodeException {
        String revision = family.substring(base.length());
        if (!revision.equals("1")) {
            boolean digits = !revision.isEmpty() && revision.chars().allMatch(Character::isDigit);
            throw new ShareCodeException(digits ? Failure.UNSUPPORTED_VERSION : Failure.NOT_A_CODE, null);
        }
    }

    private String body(ConfigProfileSnapshot snapshot) throws IOException {
        return new String(Base64.getUrlEncoder().withoutPadding().encode(codec.write(snapshot)), StandardCharsets.US_ASCII);
    }

    /** Strips whitespace/newlines a chat or paste site may have inserted, and bounds the length. */
    private static String clean(String text) {
        if (text == null) return "";
        String trimmed = text.strip();
        if (trimmed.length() > MAX_CODE_CHARS) return "";
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isWhitespace(c)) out.append(c);
        }
        return out.toString();
    }

    private static String currentVersion() {
        try {
            return combatant.client.runtime.CombatantBuild.version();
        } catch (Throwable t) {
            return "";
        }
    }
}
