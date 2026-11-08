package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;

@Getter
public final class MountJumpChargeEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private float charge;

    public MountJumpChargeEvent(LocalPlayer player, float charge) {
        this.player = player;
        this.charge = charge;
    }

}
