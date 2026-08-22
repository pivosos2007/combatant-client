/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.event;

import combatant.client.events.Event;
import combatant.client.util.sound.SoundDefinition;

public final class SoundErrorEvent extends Event {
    public enum Operation { LOAD, PLAY, CONTROL, RESET }

    private final Operation operation;
    private final SoundDefinition definition;
    private final Throwable error;

    public SoundErrorEvent(Operation operation, SoundDefinition definition, Throwable error) {
        this.operation = operation;
        this.definition = definition;
        this.error = error;
    }

    public Operation operation() { return operation; }
    public SoundDefinition definition() { return definition; }
    public Throwable error() { return error; }
}
