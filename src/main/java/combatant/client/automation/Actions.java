/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import combatant.client.automation.actions.RunAction;
import combatant.client.automation.actions.WaitTicksAction;
import combatant.client.automation.actions.WaitUntilAction;
import combatant.client.automation.composite.GuardAction;
import combatant.client.automation.composite.ParallelAction;
import combatant.client.automation.composite.DeferAction;
import combatant.client.automation.composite.RetryAction;
import combatant.client.automation.composite.SelectorAction;
import combatant.client.automation.composite.SequenceAction;
import combatant.client.automation.composite.TimeoutAction;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Short factories for composing action trees. */
public final class Actions {
    private Actions() {
    }

    public static Action sequence(String name, Action... children) {
        return new SequenceAction(name, children);
    }

    public static Action selector(String name, Action... children) {
        return new SelectorAction(name, children);
    }

    public static Action all(String name, Action... children) {
        return new ParallelAction(name, ParallelAction.Policy.REQUIRE_ALL, children);
    }

    public static Action any(String name, Action... children) {
        return new ParallelAction(name, ParallelAction.Policy.REQUIRE_ONE, children);
    }

    public static Action retry(String name, Supplier<? extends Action> factory, int maxAttempts, int backoffTicks) {
        return new RetryAction(name, factory, maxAttempts, backoffTicks);
    }

    public static Action timeout(Action child, int ticks) {
        return new TimeoutAction(null, child, ticks);
    }

    public static Action guard(String name, BooleanSupplier precondition, Action child) {
        return new GuardAction(name, precondition, child);
    }

    /** Builds the action when it first runs; a null result skips the step. */
    public static Action defer(String name, Supplier<? extends Action> factory) {
        return new DeferAction(name, factory);
    }

    public static Action waitTicks(int ticks) {
        return new WaitTicksAction(ticks);
    }

    public static Action waitUntil(String name, BooleanSupplier condition) {
        return new WaitUntilAction(name, condition);
    }

    public static Action run(String name, Runnable work) {
        return RunAction.once(name, work);
    }

    public static Action step(String name, Supplier<ActionResult> step) {
        return new RunAction(name, step);
    }
}
