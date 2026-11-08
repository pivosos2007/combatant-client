package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Getter
public final class ElytraTravelEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private Vec3 velocity;

    public ElytraTravelEvent(LocalPlayer player, Vec3 velocity) {
        this.player = player;
        this.velocity = velocity;
    }

}
