/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import net.minecraft.world.entity.LivingEntity;
import combatant.client.events.Event;
import combatant.client.util.target.TargetManager;

/**
 * Reports a change in the selected target or its arbitration source.
 * Previous/current entities and sources form a snapshot of the transition;
 * this event does not itself change the selected target.
 */
public class EventTargetChanged extends Event {
    public final LivingEntity previous;
    public final TargetManager.Source previousSource;
    public final LivingEntity current;
    public final TargetManager.Source currentSource;

    public EventTargetChanged(LivingEntity previous,
                              TargetManager.Source previousSource,
                              LivingEntity current,
                              TargetManager.Source currentSource) {
        this.previous = previous;
        this.previousSource = previousSource;
        this.current = current;
        this.currentSource = currentSource;
    }
}
