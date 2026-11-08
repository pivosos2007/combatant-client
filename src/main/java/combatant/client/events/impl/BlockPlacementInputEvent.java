/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

@Getter
public final class BlockPlacementInputEvent extends Event {
    private final int clickCount;
    private final boolean held;
    private final HitResult target;
    private final Object replayOwner;
    private InteractionHand hand;

    public BlockPlacementInputEvent(int clickCount, boolean held, HitResult target, Object replayOwner) {
        this.clickCount = clickCount;
        this.held = held;
        this.target = target;
        this.replayOwner = replayOwner;
    }

    public boolean isFreshPress() { return clickCount > 0; }

    public void claim(InteractionHand hand) {
        this.hand = hand;
        cancel();
    }
}
