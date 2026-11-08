/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fired for the local player after vanilla collision restitution has resolved.
 * Listeners may change the final velocity without hooking or referencing a particular module.
 */
@Getter
public final class PlayerCollisionRestitutionEvent extends Event {
    private final LocalPlayer player;
    private final BlockState blockState;
    private final boolean xCollision;
    private final boolean zCollision;
    private final boolean verticalCollisionBelow;
    private final Vec3 attemptedMovement;
    @Setter
    private Vec3 velocity;

    public PlayerCollisionRestitutionEvent(LocalPlayer player, BlockState blockState,
                                           boolean xCollision, boolean zCollision,
                                           boolean verticalCollisionBelow, Vec3 attemptedMovement,
                                           Vec3 velocity) {
        this.player = player;
        this.blockState = blockState;
        this.xCollision = xCollision;
        this.zCollision = zCollision;
        this.verticalCollisionBelow = verticalCollisionBelow;
        this.attemptedMovement = attemptedMovement;
        this.velocity = velocity;
    }
}
