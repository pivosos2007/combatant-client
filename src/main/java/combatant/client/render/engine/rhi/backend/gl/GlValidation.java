/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl;

import java.util.Locale;

/**
 * Expensive driver-query validation for Combatant-owned native GL paths.
 *
 * <p>Production defaults to {@link Mode#OFF}. Use
 * {@code -Dcombatant.rhi.gl.validation=errors} to drain/report GL errors at explicit
 * native RHI boundaries, or {@code full} to additionally enable expensive framebuffer
 * preflight/completeness checks. The legacy boolean debug flag still maps to ERRORS.</p>
 */
public final class GlValidation {
    public enum Mode {
        OFF,
        ERRORS,
        FULL
    }

    private static final Mode MODE = resolveMode();

    private GlValidation() {
    }

    private static Mode resolveMode() {
        String raw = System.getProperty("combatant.rhi.gl.validation", "").trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "full" -> Mode.FULL;
            case "errors", "error", "on", "true", "1" -> Mode.ERRORS;
            case "off", "false", "0" -> Mode.OFF;
            default -> Boolean.getBoolean("combatant.render.debug.gl") ? Mode.ERRORS : Mode.OFF;
        };
    }

    public static Mode mode() {
        return MODE;
    }

    public static boolean errorsEnabled() {
        return MODE != Mode.OFF;
    }

    public static boolean fullEnabled() {
        return MODE == Mode.FULL;
    }
}
