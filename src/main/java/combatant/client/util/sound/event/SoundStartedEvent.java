/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.event;

import combatant.client.events.Event;
import combatant.client.util.sound.SoundDefinition;
import combatant.client.util.sound.SoundInstance;

public final class SoundStartedEvent extends Event {
    private final SoundDefinition definition;
    private final SoundInstance instance;

    public SoundStartedEvent(SoundDefinition definition, SoundInstance instance) {
        this.definition = definition;
        this.instance = instance;
    }

    public SoundDefinition definition() { return definition; }
    public SoundInstance instance() { return instance; }
}
