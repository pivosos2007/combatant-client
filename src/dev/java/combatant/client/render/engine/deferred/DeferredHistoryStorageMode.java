/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Physical ownership policy. Existing histories overwrite their persistent snapshot only after all
 * consumers have read it; future consumers may register a true ping-pong pair without changing the
 * descriptor semantics.
 */
public enum DeferredHistoryStorageMode {
    STORE_AFTER_CONSUME,
    PING_PONG,
    /** One persistent state buffer updated in-place after its previous value has been consumed. */
    IN_PLACE
}
