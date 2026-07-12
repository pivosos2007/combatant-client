/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Event {
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }
}
