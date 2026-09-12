/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat.diagnostics;

import combatant.client.features.gui.diagnostics.FailureText;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.actions.ChatMessageActions;
import combatant.client.features.gui.chat.actions.MessageActionRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureEvents;
import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.runtime.error.FailureRegistry.Scope;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Chat/log presentation adapter. The failure state machine never invokes this class. */
public final class FailureDiagnostics {
    public static final int ERROR_ACCENT = 0xFF7777;
    private static final Queue<Failure> PENDING = new ConcurrentLinkedQueue<>();
    private static final LinkedHashSet<String> ANNOUNCED = new LinkedHashSet<>();
    private static AutoCloseable subscription;
    private static final AtomicBoolean DRAINING = new AtomicBoolean();
    private FailureDiagnostics() { }
    public static synchronized void initialize() {
        if (subscription != null) return;
        DiagnosticMessageActions.initialize();
        subscription = FailureEvents.subscribe(FailureDiagnostics::onChange);
    }
    private static void onChange(FailureEvents.Change change) {
        if (change.kind() != FailureEvents.Kind.REPORTED) return;
        Failure failure = change.failure();
        if (!firstOccurrence(change.cause(), failure)) return;
        DebugLog.error("[ErrorHandler] {} #{} failed in {}: {}",
                change.cause(), failure.component(), failure.id(), failure.phase(), failure.summary());
        PENDING.add(failure);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            try { mc.execute(FailureDiagnostics::drain); }
            catch (RuntimeException e) { DebugLog.error("Could not schedule failure diagnostics", e); }
        }
    }
    private static boolean firstOccurrence(Throwable cause, Failure failure) {
        Throwable root = cause;
        while (root != null && root.getCause() != null) root = root.getCause();
        String type = root == null ? "" : root.getClass().getName();
        String detail = root == null || root.getMessage() == null
                ? failure == null ? "" : failure.summary()
                : root.getMessage();
        String signature = type + '|' + (detail == null ? "" : detail.trim());
        synchronized (ANNOUNCED) {
            if (!ANNOUNCED.add(signature)) return false;
            while (ANNOUNCED.size() > 256) ANNOUNCED.removeFirst();
            return true;
        }
    }
    public static void drain() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread() || !DRAINING.compareAndSet(false, true)) return;
        try {
            for (int i = 0; i < 32; i++) {
                Failure failure = PENDING.poll();
                if (failure == null) break;
                try { announce(failure); }
                catch (RuntimeException e) { DebugLog.error("Could not publish diagnostic #{}", e, failure.id()); }
            }
        } finally { DRAINING.set(false); }
    }
    public static void showSummaryFor(Object owner) { announceLater(ErrorHandler.failure(owner)); }
    private static void announceLater(Failure failure) {
        if (failure == null) return;
        PENDING.add(failure);
        drain();
    }
    public static void announce(Failure failure) {
        if (failure == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) { announceLater(failure); return; }
        Failure latest = ErrorHandler.byId(failure.id());
        if (latest != null) failure = latest;
        MessageActionRegistry.Context context = ChatMessageActions.publish(DiagnosticMessageActions.TYPE,
                Map.of("failureId", Long.toString(failure.id())));
        String description;
        if (failure.scope() == Scope.SETTING) {
            description = FailureText.tr("target.setting", FailureText.scope(failure.scope()),
                    failure.owner(), failure.component());
        } else if (failure.scope() == Scope.MODULE) {
            Module module = ModuleManager.get(failure.component());
            description = module == null ? failure.component() : module.getDisplayName();
        } else {
            description = failure.component();
        }
        MutableComponent message = Component.literal(
                FailureText.tr("card.title", failure.id(), FailureText.state(failure.state()))
                        + " · " + description);
        message.append(Component.literal("  "));
        message.append(ChatMessageActions.button(context.token(), DiagnosticMessageActions.OWNER + ":show",
                "[" + FailureText.tr("action.details") + "]", ERROR_ACCENT));
        CommandOutput.send(message, CommandOutput.Tone.ERROR, context);
    }
    /** Full diagnostic is emitted only following an explicit local user action. */
    public static void show(Failure failure) {
        if (failure == null) { CommandOutput.warning(FailureText.tr("unavailable")); return; }
        CommandOutput.error(FailureText.tr("details.title", failure.id(), failure.component(), FailureText.phase(failure.phase())));
        for (String line : failure.stackTrace().split("\\R", -1)) {
            if (line.isEmpty()) continue;
            if (line.length() <= 900) CommandOutput.send(line, CommandOutput.Tone.ERROR);
            else for (int i = 0; i < line.length(); i += 900)
                CommandOutput.send(line.substring(i, Math.min(i + 900, line.length())), CommandOutput.Tone.ERROR);
        }
    }
    public static void showFor(Object owner) { show(ErrorHandler.failure(owner)); }
    public static void clearSession() {
        PENDING.clear();
        synchronized (ANNOUNCED) { ANNOUNCED.clear(); }
        ChatMessageActions.clearSession();
    }
    public static synchronized void shutdown() {
        PENDING.clear();
        synchronized (ANNOUNCED) { ANNOUNCED.clear(); }
        if (subscription != null) {
            try { subscription.close(); }
            catch (Exception e) { DebugLog.error("Could not unregister diagnostic observer", e); }
            subscription = null;
        }
        DiagnosticMessageActions.shutdown();
        ChatMessageActions.shutdown();
    }
}
