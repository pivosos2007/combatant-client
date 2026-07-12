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
 * Fired when the centralized target changes.
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
