/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

import combatant.client.runtime.loader.*;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

public enum ClientRuntime {
    ;
    private static final AtomicReference<ClientRuntimeState> STATE =
            new AtomicReference<>(ClientRuntimeState.BOOTING);
    private static final CopyOnWriteArrayList<RuntimeShutdownParticipant> PARTICIPANTS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<RuntimeResumeParticipant> RESUME_PARTICIPANTS =
            new CopyOnWriteArrayList<>();

    private static volatile LoaderBridge loaderBridge = new StandardFabricLoaderBridge();
    private static volatile RuntimeSourcePolicy sourcePolicy = new RuntimeGateSourcePolicy();
    private static volatile JarReplacementCoordinator jarReplacementCoordinator =
            new PanicOnlyJarReplacementCoordinator();
    private static volatile RuntimeCleanupReport lastCleanupReport;
    private static volatile boolean restartRequired;
    private static volatile IntSupplier callbackCounter = () -> 0;
    private static volatile IntSupplier moduleCounter = () -> 0;
    private static volatile IntSupplier addonCounter = () -> 0;

    public static ClientRuntimeState state() {
        return STATE.get();
    }

    public static void markActive(String reason) {
        while (true) {
            ClientRuntimeState current = STATE.get();
            if (current == ClientRuntimeState.JAR_REPLACEMENT_PANIC
                    || current == ClientRuntimeState.DEAD
                    || current == ClientRuntimeState.SHUTDOWN_PENDING) {
                return;
            }
            if (STATE.compareAndSet(current, ClientRuntimeState.ACTIVE)) {
                DebugLog.info("Runtime state %s -> ACTIVE (%s)", current, safeReason(reason));
                return;
            }
        }
    }

    public static boolean enterSoftPanic(String reason) {
        if (!STATE.compareAndSet(ClientRuntimeState.ACTIVE, ClientRuntimeState.SOFT_PANIC)) {
            return false;
        }
        lastCleanupReport = runParticipants(ClientRuntimeState.SOFT_PANIC, safeReason(reason));
        DebugLog.warn("Soft panic entered: %s", lastCleanupReport.summaryLine());
        return true;
    }

    public static boolean exitSoftPanic(String reason) {
        if (!STATE.compareAndSet(ClientRuntimeState.SOFT_PANIC, ClientRuntimeState.ACTIVE)) {
            return false;
        }
        runResumeParticipants(safeReason(reason));
        DebugLog.info("Soft panic exited (%s)", safeReason(reason));
        return true;
    }

    public static boolean toggleSoftPanic(String reason) {
        ClientRuntimeState current = STATE.get();
        if (current == ClientRuntimeState.ACTIVE) {
            if (loaderBridge.suspendManagedRuntime(safeReason(reason))) {
                return STATE.get() == ClientRuntimeState.SOFT_PANIC;
            }
            return enterSoftPanic(reason);
        }
        if (current == ClientRuntimeState.SOFT_PANIC) {
            if (loaderBridge.resumeManagedRuntime(safeReason(reason))) {
                return STATE.get() == ClientRuntimeState.ACTIVE;
            }
            return exitSoftPanic(reason);
        }
        return false;
    }

    public static boolean resumeSoftPanic(String reason) {
        if (STATE.get() != ClientRuntimeState.SOFT_PANIC) return false;
        if (loaderBridge.resumeManagedRuntime(safeReason(reason))) {
            return STATE.get() == ClientRuntimeState.ACTIVE;
        }
        return exitSoftPanic(reason);
    }

    public static boolean prepareJarReplacement(String reason) {
        ClientRuntimeState current = STATE.get();
        if (current == ClientRuntimeState.JAR_REPLACEMENT_PANIC || current == ClientRuntimeState.DEAD) {
            restartRequired = true;
            return false;
        }
        if (current == ClientRuntimeState.SHUTDOWN_PENDING) {
            return false;
        }
        if (!jarReplacementCoordinator.canPrepareReplacement()) {
            return false;
        }
        if (!STATE.compareAndSet(current, ClientRuntimeState.JAR_REPLACEMENT_PANIC)) {
            return prepareJarReplacement(reason);
        }

        restartRequired = true;
        String safeReason = safeReason(reason);
        jarReplacementCoordinator.prepareReplacement(safeReason);
        lastCleanupReport = runParticipants(ClientRuntimeState.JAR_REPLACEMENT_PANIC, safeReason);
        DebugLog.warn("JAR replacement panic entered: %s", lastCleanupReport.summaryLine());
        DebugLog.warn("Client is fully disabled for safe JAR replacement. Restart Minecraft to use the new JAR.");
        return true;
    }

    public static void requestRestart(String reason) {
        restartRequired = true;
        DebugLog.warn("Runtime restart requested (%s)", safeReason(reason));
    }

    public static void beginShutdown(String reason) {
        while (true) {
            ClientRuntimeState current = STATE.get();
            if (current == ClientRuntimeState.SHUTDOWN_PENDING || current == ClientRuntimeState.DEAD) {
                return;
            }
            if (STATE.compareAndSet(current, ClientRuntimeState.SHUTDOWN_PENDING)) {
                DebugLog.info("Runtime state %s -> SHUTDOWN_PENDING (%s)", current, safeReason(reason));
                return;
            }
        }
    }

    public static void markDead(String reason) {
        STATE.set(ClientRuntimeState.DEAD);
        restartRequired = true;
        DebugLog.warn("Runtime marked DEAD (%s)", safeReason(reason));
    }

    public static void registerParticipant(RuntimeShutdownParticipant participant) {
        if (participant == null) return;
        for (RuntimeShutdownParticipant existing : PARTICIPANTS) {
            if (existing.id().equals(participant.id())) return;
        }
        PARTICIPANTS.add(participant);
    }

    public static void registerResumeParticipant(RuntimeResumeParticipant participant) {
        if (participant == null) return;
        for (RuntimeResumeParticipant existing : RESUME_PARTICIPANTS) {
            if (existing.id().equals(participant.id())) return;
        }
        RESUME_PARTICIPANTS.add(participant);
    }

    public static RuntimeDiagnostics diagnostics() {
        return new RuntimeDiagnostics(
                state(),
                RuntimeGate.canRunModules(),
                RuntimeGate.canRunRender(),
                RuntimeGate.canRunHud(),
                RuntimeGate.canRunShaderBridge(),
                RuntimeGate.isJarReplacementMode(),
                restartRequired || jarReplacementCoordinator.requiresRestart(),
                safeCount(callbackCounter),
                safeCount(moduleCounter),
                safeCount(addonCounter),
                lastCleanupReport
        );
    }

    public static LoaderBridge loaderBridge() {
        return loaderBridge;
    }

    public static void setLoaderBridge(LoaderBridge bridge) {
        if (bridge != null) loaderBridge = bridge;
    }

    public static RuntimeSourcePolicy sourcePolicy() {
        return sourcePolicy;
    }

    public static void setSourcePolicy(RuntimeSourcePolicy policy) {
        if (policy != null) sourcePolicy = policy;
    }

    public static JarReplacementCoordinator jarReplacementCoordinator() {
        return jarReplacementCoordinator;
    }

    public static void setJarReplacementCoordinator(JarReplacementCoordinator coordinator) {
        if (coordinator != null) jarReplacementCoordinator = coordinator;
    }

    public static void setCallbackCounter(IntSupplier counter) {
        callbackCounter = counter != null ? counter : () -> 0;
    }

    public static void setModuleCounter(IntSupplier counter) {
        moduleCounter = counter != null ? counter : () -> 0;
    }

    public static void setAddonCounter(IntSupplier counter) {
        addonCounter = counter != null ? counter : () -> 0;
    }

    private static RuntimeCleanupReport runParticipants(ClientRuntimeState targetState, String reason) {
        RuntimeCleanupReport.Builder builder = RuntimeCleanupReport.builder(targetState, reason);
        RuntimeShutdownContext context = new RuntimeShutdownContext(targetState, reason, builder);
        List<RuntimeShutdownParticipant> participants = new ArrayList<>(PARTICIPANTS);
        for (RuntimeShutdownParticipant participant : participants) {
            try {
                participant.shutdown(context);
                context.stopped(participant.id());
            } catch (Throwable t) {
                context.failed(participant.id(), t);
                DebugLog.error("Runtime shutdown participant failed: %s", t, participant.id());
            }
        }
        return builder.build();
    }

    private static void runResumeParticipants(String reason) {
        for (RuntimeResumeParticipant participant : new ArrayList<>(RESUME_PARTICIPANTS)) {
            try {
                participant.resume(reason);
            } catch (Throwable t) {
                DebugLog.error("Runtime resume participant failed: %s", t, participant.id());
            }
        }
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    private static int safeCount(IntSupplier supplier) {
        try {
            return Math.max(0, supplier.getAsInt());
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
