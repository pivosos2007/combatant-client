package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Getter
public final class PlayerEffectVelocityEvent extends Event {
    private final LocalPlayer player;
    private final PlayerEffectMoveEvent.EffectType effectType;
    @Setter
    private Vec3 velocity;

    public PlayerEffectVelocityEvent(LocalPlayer player, PlayerEffectMoveEvent.EffectType effectType, Vec3 velocity) {
        this.player = player;
        this.effectType = effectType;
        this.velocity = velocity;
    }

}
