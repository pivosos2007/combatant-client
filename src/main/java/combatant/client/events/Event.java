/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

import lombok.Getter;
import lombok.Setter;

/**
 * Base for events dispatched synchronously through {@link EventBus}.
 * Cancellation records a request for the publisher; dispatch continues to other subscribers,
 * and only publishers that inspect {@link #isCancelled()} act on it.
 */
@Setter
@Getter
public class Event {
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }
}
