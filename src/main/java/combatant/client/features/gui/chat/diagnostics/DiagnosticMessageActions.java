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
import combatant.client.runtime.error.FailureIsolation;
import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.runtime.error.FailureRegistry.Scope;
import combatant.client.runtime.error.FailureRegistry.State;
import combatant.client.util.text.ClipboardUtil;
import java.util.ArrayList;
import java.util.List;

/** Diagnostic action provider. Other features may register additional providers independently. */
public final class DiagnosticMessageActions implements MessageActionRegistry.Provider {
    public static final String TYPE = "combatant:error";
    public static final String OWNER = "combatant.error";
    private static MessageActionRegistry.Registration registration;
    private DiagnosticMessageActions() { }
    static synchronized void initialize() {
        if (registration == null) registration = ChatMessageActions.register(OWNER, new DiagnosticMessageActions());
    }
    static synchronized void shutdown() {
        if (registration != null) { registration.close(); registration = null; }
    }
    private static Failure resolve(MessageActionRegistry.Context context) {
        if (!TYPE.equals(context.type())) return null;
        try { return ErrorHandler.byId(Long.parseLong(context.data("failureId"))); }
        catch (NumberFormatException | NullPointerException e) { return null; }
    }
    private static Module currentModule(Failure failure) {
        if (failure == null || failure.scope() != Scope.MODULE) return null;
        Module module = ModuleManager.get(failure.component());
        Failure current = ErrorHandler.failure(module);
        return current != null && current.id() == failure.id() ? module : null;
    }
    @Override public List<MessageActionRegistry.Action> actions(MessageActionRegistry.Context context) {
        Failure failure = resolve(context);
        if (failure == null) return List.of();
        ArrayList<MessageActionRegistry.Action> actions = new ArrayList<>();
        actions.add(new MessageActionRegistry.Action("show", FailureText.tr("action.show"), MessageActionRegistry.Tone.NORMAL, true));
        actions.add(new MessageActionRegistry.Action("copy", FailureText.tr("action.copy"), MessageActionRegistry.Tone.NORMAL, true));
        Module module = currentModule(failure);
        if (module != null) actions.add(new MessageActionRegistry.Action("retry", FailureText.tr("action.retry_module"),
                MessageActionRegistry.Tone.WARNING, failure.state() == State.QUARANTINED
                        && FailureIsolation.canRetryModule(module)));
        return List.copyOf(actions);
    }
    @Override public void execute(MessageActionRegistry.Context context, String actionId) {
        Failure failure = resolve(context);
        if (failure == null) return;
        switch (actionId) {
            case "show" -> FailureDiagnostics.show(failure);
            case "copy" -> ClipboardUtil.copy(failure.stackTrace());
            case "retry" -> {
                Module module = currentModule(failure);
                if (module != null && FailureIsolation.retryModule(module))
                    CommandOutput.success(FailureText.tr("recovery.success", module.getDisplayName()));
                else CommandOutput.warning(FailureText.tr("recovery.unavailable"));
            }
            default -> { }
        }
    }
}
