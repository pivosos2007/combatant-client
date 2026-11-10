/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

/**
 * Shared entry point to the client event bus. The bus is process-wide;
 * registered owners must unregister when their subscriptions are no longer needed.
 */
public enum Events {
    ;
    public static final EventBus BUS = new EventBus();

}
