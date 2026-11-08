package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Getter
public final class PlayerEffectMoveEvent extends Event {
    public enum EffectType {
        LEVITATION,
        SLOW_FALLING
    }

    private final LocalPlayer player;
    private final EffectType effectType;
    @Setter
    private Vec3 movement;

    public PlayerEffectMoveEvent(LocalPlayer player, EffectType effectType, Vec3 movement) {
        this.player = player;
        this.effectType = effectType;
        this.movement = movement;
    }

}
