/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

public enum ActionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isDone() {
        return this != RUNNING;
    }
}
