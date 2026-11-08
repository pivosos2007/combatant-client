package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Getter
public final class PlayerWaterTravelEvent extends Event {
    private final LocalPlayer player;
    private final Vec3 incomingVelocity;
    @Setter
    private Vec3 velocity;

    public PlayerWaterTravelEvent(LocalPlayer player, Vec3 incomingVelocity, Vec3 velocity) {
        this.player = player;
        this.incomingVelocity = incomingVelocity;
        this.velocity = velocity;
    }

}
