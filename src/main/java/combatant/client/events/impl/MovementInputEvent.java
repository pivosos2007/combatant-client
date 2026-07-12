/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.player.Input;
import combatant.client.events.Event;

@Setter
@Getter
public final class MovementInputEvent extends Event {
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public MovementInputEvent(Input input) {
        this.forward = input.forward();
        this.backward = input.backward();
        this.left = input.left();
        this.right = input.right();
        this.jump = input.jump();
        this.sneak = input.shift();
        this.sprint = input.sprint();
    }

    public Input toPlayerInput() {
        return new Input(forward, backward, left, right, jump, sneak, sprint);
    }
}
