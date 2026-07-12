/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum DebugLog {
    ;

    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant");
    private static final Set<String> ONCE_KEYS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, String> LAST_STATES = new ConcurrentHashMap<>();
    private static volatile DebugMode mode = DebugMode.OFF;

    public static void setMode(DebugMode mode) {
        DebugLog.mode = mode == null ? DebugMode.OFF : mode;
    }

    public static DebugMode mode() {
        return mode;
    }

    public static boolean isEnabled() {
        return mode != DebugMode.OFF;
    }

    public static boolean serverOnly() {
        return mode == DebugMode.SERVERDEBUG;
    }

    public static boolean isStencilDebugEnabled() {
        return branchEnabled(Branch.STENCIL);
    }

    public static boolean isRenderThreadDebugEnabled() {
        return branchEnabled(Branch.RENDER_THREAD);
    }

    public static boolean isConfigDebugEnabled() {
        return branchEnabled(Branch.CONFIG);
    }

    public static void info(String message, Object... args) {
        logInfo(Branch.GENERAL, "", message, args);
    }

    public static void config(String message, Object... args) {
        logInfo(Branch.CONFIG, "[CONFIG] ", message, args);
    }

    public static void renderThread(String message, Object... args) {
        logInfo(Branch.RENDER_THREAD, "[RENDER] ", message, args);
    }

    public static void stencil(String message, Object... args) {
        logInfo(Branch.STENCIL, "[STENCIL] ", message, args);
    }

    public static void server(String message, Object... args) {
        logInfo(Branch.SERVER, "[SERVER] ", message, args);
    }

    public static void warn(String message, Object... args) {
        if (!warnEnabled()) return;
        log(LogLevel.WARN, "[WARN] ", message, args);
    }

    public static void infoOnce(String key, String message, Object... args) {
        if (once(key)) info(message, args);
    }

    public static void configOnce(String key, String message, Object... args) {
        if (once(key)) config(message, args);
    }

    public static void renderThreadOnce(String key, String message, Object... args) {
        if (once(key)) renderThread(message, args);
    }

    public static void stencilOnce(String key, String message, Object... args) {
        if (once(key)) stencil(message, args);
    }

    public static void warnOnce(String key, String message, Object... args) {
        if (once(key)) warn(message, args);
    }

    public static void errorOnce(String key, String message, Object... args) {
        if (once(key)) error(message, null, args);
    }

    public static void infoOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) info(message, args);
    }

    public static void configOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) config(message, args);
    }

    public static void renderThreadOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) renderThread(message, args);
    }

    public static void stencilOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) stencil(message, args);
    }

    public static void warnOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) warn(message, args);
    }

    public static void errorOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) error(message, null, args);
    }

    public static void error(String message) {
        error(message, null);
    }

    public static void error(String message, Throwable t) {
        log(LogLevel.ERROR, "[ERROR] ", message);
        if (isEnabled() && t != null) {
            LOGGER.error("[Combatant][Debug] exception", t);
        }
    }

    public static void error(String message, Throwable t, Object... args) {
        log(LogLevel.ERROR, "[ERROR] ", message, args);
        if (isEnabled() && t != null) {
            LOGGER.error("[Combatant][Debug] exception", t);
        }
    }

    private static void logInfo(Branch branch, String prefix, String message, Object... args) {
        if (!branchEnabled(branch)) return;
        log(LogLevel.INFO, prefix, message, args);
    }

    private static boolean warnEnabled() {
        return mode != DebugMode.OFF && mode != DebugMode.ERROR_ONLY;
    }

    private static boolean branchEnabled(Branch branch) {
        return switch (mode) {
            case OFF, ERROR_ONLY, ERROR_AND_WARNINGS -> false;
            case INFO -> branch == Branch.GENERAL;
            case CONFIG -> branch == Branch.CONFIG;
            case RENDER_THREAD -> branch == Branch.RENDER_THREAD;
            case STENCIL -> branch == Branch.STENCIL;
            case SERVERDEBUG -> branch == Branch.SERVER;
            case ALL -> true;
        };
    }

    private static boolean once(String key) {
        String safeKey = key == null ? "<null>" : key;
        return ONCE_KEYS.add(safeKey);
    }

    private static boolean changed(String key, Object state) {
        String safeKey = key == null ? "<null>" : key;
        String next = String.valueOf(state);
        String previous = LAST_STATES.put(safeKey, next);
        return !next.equals(previous);
    }

    private static void log(LogLevel level, String prefix, String message, Object... args) {
        if (!isEnabled()) return;
        Objects.requireNonNull(message, "message");

        String formatted = (args == null || args.length == 0)
                ? message
                : String.format(message, args);

        String out = "[Combatant][Debug] " + prefix + formatted;
        switch (level) {
            case INFO -> LOGGER.info(out);
            case WARN -> LOGGER.warn(out);
            case ERROR -> LOGGER.error(out);
        }
    }

    private enum Branch {
        GENERAL,
        CONFIG,
        RENDER_THREAD,
        STENCIL,
        SERVER
    }

    private enum LogLevel {
        INFO,
        WARN,
        ERROR
    }
}
