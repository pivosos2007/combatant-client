/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import java.util.Objects;

/**
 * Outcome of one action tick. {@link #RUNNING} and {@link #SUCCESS} are shared instances so the
 * steady-state tick path does not allocate.
 */
public record ActionResult(ActionStatus status, FailureReason reason, String detail) {

    public static final ActionResult RUNNING = new ActionResult(ActionStatus.RUNNING, FailureReason.NONE, "");
    public static final ActionResult SUCCESS = new ActionResult(ActionStatus.SUCCEEDED, FailureReason.NONE, "");

    public ActionResult {
        Objects.requireNonNull(status, "status");
        reason = reason != null ? reason : FailureReason.NONE;
        detail = detail != null ? detail : "";
    }

    public static ActionResult failure(FailureReason reason, String detail) {
        return new ActionResult(ActionStatus.FAILED, reason, detail);
    }

    public static ActionResult cancelled(String detail) {
        return new ActionResult(ActionStatus.CANCELLED, FailureReason.CANCELLED, detail);
    }

    public boolean isRunning() {
        return status == ActionStatus.RUNNING;
    }

    public boolean isSuccess() {
        return status == ActionStatus.SUCCEEDED;
    }

    public boolean isFailure() {
        return status == ActionStatus.FAILED;
    }

    public boolean isDone() {
        return status.isDone();
    }

    @Override
    public String toString() {
        if (status != ActionStatus.FAILED && status != ActionStatus.CANCELLED) return status.name();
        return detail.isEmpty() ? status + "(" + reason + ")" : status + "(" + reason + ": " + detail + ")";
    }
}
