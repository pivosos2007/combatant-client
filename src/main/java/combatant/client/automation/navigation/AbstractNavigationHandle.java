/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/** Terminal-state bookkeeping shared by handles: first terminal call wins, {@link #onFinished} runs once. */
public abstract class AbstractNavigationHandle implements NavigationHandle {

    protected final NavigationRequest request;
    protected final NavigationIntent intent = new NavigationIntent();
    private NavigationResult result;
    private int ticks;

    protected AbstractNavigationHandle(NavigationRequest request) {
        this.request = request;
    }

    @Override
    public final NavigationStatus status() {
        return result == null ? NavigationStatus.RUNNING : result.status();
    }

    @Override
    public final NavigationResult result() {
        return result;
    }

    @Override
    public final NavigationIntent intent() {
        return intent;
    }

    /** Ticks driven so far, including the current one. */
    protected final int ticks() {
        return ticks;
    }

    @Override
    public final void tick(PlayerSample player) {
        if (result != null) return;
        ticks++;
        intent.clear();
        try {
            int timeout = request.options().timeoutTicks();
            if (timeout > 0 && ticks > timeout) {
                fail(NavigationFailure.TIMEOUT, "no arrival after " + timeout + " ticks");
            } else {
                step(player);
            }
        } catch (RuntimeException e) {
            fail(NavigationFailure.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (result != null) intent.clear();
    }

    /** One tick of work; call {@link #succeed} or {@link #fail} to end. Only invoked while running. */
    protected abstract void step(PlayerSample player);

    /** Releases whatever the handle holds; invoked exactly once, whichever way it ended. */
    protected void onFinished(NavigationResult result) {
    }

    protected final void succeed() {
        finish(NavigationResult.SUCCESS);
    }

    protected final void fail(NavigationFailure failure, String detail) {
        finish(NavigationResult.failed(failure, detail));
    }

    @Override
    public final void cancel() {
        finish(NavigationResult.CANCELLED);
    }

    private void finish(NavigationResult outcome) {
        if (result != null) return;
        result = outcome;
        intent.clear();
        try {
            onFinished(outcome);
        } catch (RuntimeException ignored) {
            // The outcome is already recorded; cleanup failure must not change it.
        }
    }
}
