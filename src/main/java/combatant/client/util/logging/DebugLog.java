/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum DebugLog {
    ;

    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant");
    private static final String ROOT_PREFIX = "[Combatant][Debug] ";
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

    /**
     * General debug-level message using SLF4J-style <code>{}</code> placeholders.
     * Legacy printf-style placeholders remain accepted for existing call sites.
     */
    public static void debug(String message, Object... args) {
        logInfo(LogLevel.DEBUG, Branch.GENERAL, "", message, args);
    }

    /**
     * General info-level message using SLF4J-style <code>{}</code> placeholders.
     * Legacy printf-style placeholders remain accepted for existing call sites.
     */
    public static void info(String message, Object... args) {
        logInfo(LogLevel.INFO, Branch.GENERAL, "", message, args);
    }

    public static void config(String message, Object... args) {
        logInfo(LogLevel.INFO, Branch.CONFIG, "[CONFIG] ", message, args);
    }

    public static void renderThread(String message, Object... args) {
        logInfo(LogLevel.INFO, Branch.RENDER_THREAD, "[RENDER] ", message, args);
    }

    public static void stencil(String message, Object... args) {
        logInfo(LogLevel.INFO, Branch.STENCIL, "[STENCIL] ", message, args);
    }

    public static void server(String message, Object... args) {
        logInfo(LogLevel.INFO, Branch.SERVER, "[SERVER] ", message, args);
    }

    public static void warn(String message, Object... args) {
        if (!warnEnabled()) return;
        log(LogLevel.WARN, "[WARN] ", message, args);
    }

    /**
     * Error-level message using ordinary SLF4J argument semantics. A trailing
     * {@link Throwable} is emitted as the exception, e.g.
     * <code>error("Failed to load {}", id, throwable)</code>.
     */
    public static void error(String message, Object... args) {
        log(LogLevel.ERROR, "[ERROR] ", message, args);
    }

    /**
     * Compatibility overload for old call sites that pass the exception before
     * printf-style formatting arguments.
     */
    public static void error(String message, Throwable throwable, Object... args) {
        Object[] withThrowable = appendThrowable(args, throwable);
        log(LogLevel.ERROR, "[ERROR] ", message, withThrowable);
    }

    public static void debugOnce(String key, String message, Object... args) {
        if (once(key)) debug(message, args);
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
        if (once(key)) error(message, args);
    }

    public static void debugOnChange(String key, Object state, String message, Object... args) {
        if (changed(key, state)) debug(message, args);
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
        if (changed(key, state)) error(message, args);
    }

    private static void logInfo(LogLevel level, Branch branch, String prefix, String message, Object... args) {
        if (!branchEnabled(branch)) return;
        log(level, prefix, message, args);
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

        String pattern = ROOT_PREFIX + prefix + message;
        Object[] safeArgs = args == null ? new Object[0] : args;

        if (safeArgs.length > 0 && !containsSlf4jPlaceholder(message) && containsPrintfPlaceholder(message)) {
            LegacyMessage legacy = formatLegacy(pattern, safeArgs);
            emit(level, legacy.message(), legacy.throwable());
            return;
        }

        emit(level, pattern, safeArgs);
    }

    private static void emit(LogLevel level, String pattern, Object... args) {
        switch (level) {
            case DEBUG -> LOGGER.debug(pattern, args);
            case INFO -> LOGGER.info(pattern, args);
            case WARN -> LOGGER.warn(pattern, args);
            case ERROR -> LOGGER.error(pattern, args);
        }
    }

    private static void emit(LogLevel level, String message, Throwable throwable) {
        if (throwable == null) {
            emit(level, message);
            return;
        }
        switch (level) {
            case DEBUG -> LOGGER.debug(message, throwable);
            case INFO -> LOGGER.info(message, throwable);
            case WARN -> LOGGER.warn(message, throwable);
            case ERROR -> LOGGER.error(message, throwable);
        }
    }

    private static LegacyMessage formatLegacy(String pattern, Object[] args) {
        Throwable throwable = trailingThrowable(args);
        Object[] formatArgs = throwable == null ? args : Arrays.copyOf(args, args.length - 1);
        try {
            return new LegacyMessage(String.format(Locale.ROOT, pattern, formatArgs), throwable);
        } catch (IllegalFormatException ignored) {
            // A malformed legacy pattern should not make debug logging break the caller.
            // Fall back to SLF4J handling so the original message and arguments remain visible.
            return new LegacyMessage(pattern + " [args=" + Arrays.toString(formatArgs) + "]", throwable);
        }
    }

    private static Throwable trailingThrowable(Object[] args) {
        if (args.length == 0) return null;
        Object last = args[args.length - 1];
        return last instanceof Throwable throwable ? throwable : null;
    }

    private static Object[] appendThrowable(Object[] args, Throwable throwable) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        if (throwable == null) return safeArgs;
        Object[] result = Arrays.copyOf(safeArgs, safeArgs.length + 1);
        result[safeArgs.length] = throwable;
        return result;
    }

    private static boolean containsSlf4jPlaceholder(String message) {
        for (int i = 0; i + 1 < message.length(); i++) {
            if (message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && message.charAt(j) == '\\'; j--) backslashes++;
                if ((backslashes & 1) == 0) return true;
            }
        }
        return false;
    }

    private static boolean containsPrintfPlaceholder(String message) {
        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) != '%') continue;
            if (i + 1 >= message.length()) continue;
            if (message.charAt(i + 1) == '%') {
                i++;
                continue;
            }

            int j = i + 1;
            while (j < message.length() && Character.isDigit(message.charAt(j))) j++;
            if (j < message.length() && message.charAt(j) == '$') j++;
            while (j < message.length() && "-#+ 0,(<".indexOf(message.charAt(j)) >= 0) j++;
            while (j < message.length() && Character.isDigit(message.charAt(j))) j++;
            if (j < message.length() && message.charAt(j) == '.') {
                j++;
                while (j < message.length() && Character.isDigit(message.charAt(j))) j++;
            }
            if (j < message.length() && (message.charAt(j) == 't' || message.charAt(j) == 'T')) j++;
            if (j < message.length() && Character.isLetter(message.charAt(j))) return true;
        }
        return false;
    }

    private record LegacyMessage(String message, Throwable throwable) {
    }

    private enum Branch {
        GENERAL,
        CONFIG,
        RENDER_THREAD,
        STENCIL,
        SERVER
    }

    private enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
