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

/**
 * Offers consumers a chance to claim the current use-key input before vanilla item use.
 * {@code clickCount > 0} denotes a queued press; {@code held} is the current key state.
 * Calling {@link #claim(InteractionHand)} stores the chosen hand and cancels the
 * original use action; claiming does not itself perform an interaction.
 */
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
