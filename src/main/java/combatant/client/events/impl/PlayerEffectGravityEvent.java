package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;

@Getter
public final class PlayerEffectGravityEvent extends Event {
    private final LocalPlayer player;
    private final double normalGravity;
    @Setter
    private double gravity;

    public PlayerEffectGravityEvent(LocalPlayer player, double gravity, double normalGravity) {
        this.player = player;
        this.gravity = gravity;
        this.normalGravity = normalGravity;
    }

}
