/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * One step of an {@link AutomationTask}. Actions are single-use: once done they keep returning
 * their final result. Composites and {@code RetryAction} build fresh instances for new attempts.
 * Extend {@link AbstractAction} rather than implementing this directly.
 */
public interface Action {

    String name();

    /** Advances the action by one client tick. */
    ActionResult tick(ActionContext ctx);

    /** Stops the action and releases everything it holds. No-op once done. */
    void cancel(ActionContext ctx, String reason);

    /** RUNNING until the action finishes, including before its first tick. */
    ActionStatus status();
}
