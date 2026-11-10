/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Reports the result of a local item or block interaction after the game-mode call returns.
 * {@code hit} is null for item use; {@code blockItemAtStart} records the held item
 * before a block interaction may consume or replace it. This is observational:
 * cancelling the event does not undo an already completed interaction.
 */
@Getter
public final class BlockUseAttemptEvent extends Event {
    private final LocalPlayer player;
    private final InteractionHand hand;
    private final BlockHitResult hit;
    private final InteractionResult result;
    private final boolean blockItemAtStart;

    /**
     * A null {@code hit} identifies direct item use; {@code blockItemAtStart}
     * is sampled before the targeted-block interaction.
     */
    public BlockUseAttemptEvent(LocalPlayer player, InteractionHand hand,
                                BlockHitResult hit, InteractionResult result, boolean blockItemAtStart) {
        this.player = player;
        this.hand = hand;
        this.hit = hit;
        this.result = result;
        this.blockItemAtStart = blockItemAtStart;
    }

}
