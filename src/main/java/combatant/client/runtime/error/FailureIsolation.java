/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.RuntimeGate;
import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.runtime.error.FailureRegistry.Scope;
import combatant.client.runtime.error.FailureRegistry.State;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Client-thread lifecycle coordinator. The module owns its cleanup; this class owns the
 * transaction and prevents a second cleanup or retry after cleanup has failed.
 * No chat, UI or renderer dependency. All module callbacks are explicit ISOLATE boundaries.
 */
public final class FailureIsolation {
    public enum Cleanup { PENDING, SUCCEEDED, FAILED }
    private record CleanupRecord(long incident, Cleanup state) { }
    private static final IdentityHashMap<Module, CleanupRecord> CLEANUP = new IdentityHashMap<>();
    private static final IdentityHashMap<Module, Object> CLEANUP_KEYS = new IdentityHashMap<>();
    private static final Queue<Runnable> MAIN_QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean DRAIN_SCHEDULED = new AtomicBoolean();
    private static final AtomicBoolean DRAINING = new AtomicBoolean();
    private FailureIsolation() { }

    public static void reportModule(Module module, String phase, Throwable cause) {
        reportModule(module, phase, cause, false);
    }
    /** cleanupAlreadyFailed is supplied by the lifecycle caller, never inferred from an exception. */
    public static void reportModule(Module module, String phase, Throwable cause, boolean cleanupAlreadyFailed) {
        if (module == null) throw FailureBoundary.propagate(cause);
        FailureBoundary.requireRecoverable(cause);
        FailureRegistry.Report report = ErrorHandler.reportIncident(module, Scope.MODULE, module.name(),
                module.name(), phase, cause, FailureBoundary.ISOLATE);
        Failure result = report.failure();
        if (!report.created()) return;
        synchronized (CLEANUP) {
            CleanupRecord existing = CLEANUP.get(module);
            if (existing != null && existing.incident() == result.id()) return;
            CLEANUP.put(module, new CleanupRecord(result.id(),
                    cleanupAlreadyFailed ? Cleanup.FAILED : Cleanup.PENDING));
        }
        enqueue(() -> cleanup(module, result.id(), cleanupAlreadyFailed));
    }

    private static void cleanup(Module module, long incident, boolean alreadyFailed) {
        Failure current = ErrorHandler.failure(module);
        if (current == null || current.id() != incident) return;
        try {
            // Even a failed onDisable must finish the non-callback portion of quarantine.
            module.quarantineAfterFailure();
            synchronized (CLEANUP) {
                CleanupRecord record = CLEANUP.get(module);
                if (record != null && record.incident() == incident)
                    CLEANUP.put(module, new CleanupRecord(incident,
                            alreadyFailed ? Cleanup.FAILED : Cleanup.SUCCEEDED));
            }
        } catch (RuntimeException e) {
            FailureBoundary.requireRecoverable(e);
            synchronized (CLEANUP) {
                CleanupRecord record = CLEANUP.get(module);
                if (record != null && record.incident() == incident)
                    CLEANUP.put(module, new CleanupRecord(incident, Cleanup.FAILED));
            }
            Object key;
            synchronized (CLEANUP) { key = CLEANUP_KEYS.computeIfAbsent(module, ignored -> new Object()); }
            FailureExecution.reportComponent(key, module.name() + " cleanup", "cleanup", e, FailureBoundary.ISOLATE);
        } finally {
            Failure latest = ErrorHandler.failure(module);
            if (latest != null && latest.id() == incident) ErrorHandler.quarantine(module);
        }
    }
    public static Cleanup cleanupState(Module module) {
        if (module == null) return null;
        synchronized (CLEANUP) {
            CleanupRecord record = CLEANUP.get(module);
            Failure current = ErrorHandler.failure(module);
            return record != null && current != null && record.incident() == current.id() ? record.state() : null;
        }
    }
    public static boolean canRetryModule(Module module) {
        if (module == null) return false;
        Failure failure = ErrorHandler.failure(module);
        return failure != null && failure.state() == State.QUARANTINED
                && cleanupState(module) == Cleanup.SUCCEEDED
                && ModuleManager.get(module.getClass()) == module;
    }
    /** A retry is an explicit transaction; the failure is cleared only after successful activation. */
    public static boolean retryModule(Module module) {
        if (!canRetryModule(module)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread() || !RuntimeGate.canRunModules()) return false;
        if (ErrorHandler.beginRecovery(module) == null) return false;
        try (AutoCloseable scope = ErrorHandler.recoveryScope(module)) {
            boolean ready = module.recoverAfterFailure();
            if (ready && module.isEnabled() && ErrorHandler.failure(module) != null
                    && ErrorHandler.failure(module).state() == State.RECOVERING) {
                module.setEnabledManual(true);
                ready = module.isEnabled() && ErrorHandler.failure(module) != null
                        && ErrorHandler.failure(module).state() == State.RECOVERING;
            } else ready = false;
            if (ready) return ErrorHandler.recovered(module);
            Failure current = ErrorHandler.failure(module);
            if (current != null && current.state() == State.RECOVERING) {
                ErrorHandler.recoveryFailed(module);
                // A vetoed enable has no active resources; quarantine performs the state reset.
                try { module.quarantineAfterFailure(); }
                catch (RuntimeException e) { reportModule(module, "recovery cleanup", e, true); }
            }
            return false;
        } catch (RuntimeException e) {
            reportModule(module, "recovery", e);
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Could not close recovery transaction", e);
        }
    }
    public static void reportSetting(Setting setting, String phase, Throwable cause) {
        if (setting == null) throw FailureBoundary.propagate(cause);
        ErrorHandler.report(setting, Scope.SETTING,
                setting.getParent() instanceof Module module ? module.name() : "settings",
                setting.getName(), phase, cause, FailureBoundary.ISOLATE);
        ErrorHandler.quarantine(setting);
    }
    public static boolean runSetting(Setting setting, String phase, Runnable action) {
        if (!ErrorHandler.canRun(setting)) return false;
        try { action.run(); return true; }
        catch (RuntimeException e) { reportSetting(setting, phase, e); return false; }
    }
    public static <T> T callSetting(Setting setting, String phase, Supplier<T> action, T fallback) {
        if (!ErrorHandler.canRun(setting)) return fallback;
        try { return action.get(); }
        catch (RuntimeException e) { reportSetting(setting, phase, e); return fallback; }
    }
    /** Clears the setting quarantine; the next actual evaluation is its validation. */
    public static boolean retrySetting(Setting setting) {
        Minecraft mc = Minecraft.getInstance();
        if (setting == null || mc == null || !mc.isSameThread()) return false;
        if (ErrorHandler.beginRecovery(setting) == null) return false;
        return ErrorHandler.recovered(setting);
    }
    public static Failure reportComponent(Object key, String name, String phase, Throwable cause) {
        return FailureExecution.reportComponent(key, name, phase, cause, FailureBoundary.ISOLATE);
    }
    public static Failure settingFailure(Module module) {
        if (module == null) return null;
        for (Setting setting : module.getSettings()) {
            Failure f = ErrorHandler.failure(setting);
            if (f != null) return f;
        }
        return null;
    }
    public static Failure moduleOrSettingFailure(Module module) {
        Failure direct = ErrorHandler.failure(module);
        return direct != null ? direct : settingFailure(module);
    }
    public static void unregister(Object owner) {
        if (owner == null) return;
        if (owner instanceof Module module) {
            if (module.isLifecycleEnabled()) throw new IllegalStateException("Disable the module before unregistering its failure");
            if (cleanupState(module) == Cleanup.PENDING) throw new IllegalStateException("Failure cleanup is still pending");
            synchronized (CLEANUP) {
                CLEANUP.remove(module);
                Object cleanupKey = CLEANUP_KEYS.remove(module);
                if (cleanupKey != null) ErrorHandler.unregister(cleanupKey);
            }
        }
        ErrorHandler.unregister(owner);
    }
    private static void enqueue(Runnable task) {
        MAIN_QUEUE.add(task);
        scheduleDrain();
    }
    private static void scheduleDrain() {
        if (DRAINING.get() || !DRAIN_SCHEDULED.compareAndSet(false, true)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) { DRAIN_SCHEDULED.set(false); return; }
        try { mc.execute(FailureIsolation::drain); }
        catch (RuntimeException e) {
            DRAIN_SCHEDULED.set(false);
            DebugLog.error("Could not schedule failure cleanup", e);
        }
    }
    public static void drain() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!mc.isSameThread()) { scheduleDrain(); return; }
        if (!DRAINING.compareAndSet(false, true)) return;
        DRAIN_SCHEDULED.set(false);
        try {
            for (int n = 0; n < 64; n++) {
                Runnable task = MAIN_QUEUE.poll();
                if (task == null) break;
                // Cleanup transactions handle their own local exceptions. Unknown failures propagate.
                task.run();
            }
        } finally {
            DRAINING.set(false);
            if (!MAIN_QUEUE.isEmpty()) scheduleDrain();
        }
    }
    public static void clearSession() {
        MAIN_QUEUE.clear();
        synchronized (CLEANUP) { CLEANUP.clear(); CLEANUP_KEYS.clear(); }
        DRAIN_SCHEDULED.set(false);
        ErrorHandler.clearSession();
    }
}
