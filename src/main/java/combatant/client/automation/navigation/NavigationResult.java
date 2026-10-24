/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import java.util.Objects;

/** Terminal outcome of a navigation. */
public record NavigationResult(NavigationStatus status, NavigationFailure failure, String detail) {

    public static final NavigationResult SUCCESS = new NavigationResult(NavigationStatus.SUCCEEDED, NavigationFailure.NONE, "");
    public static final NavigationResult CANCELLED = new NavigationResult(NavigationStatus.CANCELLED, NavigationFailure.CANCELLED, "");

    public NavigationResult {
        Objects.requireNonNull(status, "status");
        failure = failure != null ? failure : NavigationFailure.NONE;
        detail = detail != null ? detail : "";
    }

    public static NavigationResult failed(NavigationFailure failure, String detail) {
        return new NavigationResult(NavigationStatus.FAILED, failure, detail);
    }

    public boolean isSuccess() {
        return status == NavigationStatus.SUCCEEDED;
    }
}
