/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("minecraft")
    Minecraft getClient();

    @Accessor("portalEffectIntensity")
    float combatant$getNauseaIntensity();

    @Accessor("portalEffectIntensity")
    void combatant$setNauseaIntensity(float value);

    @Accessor("oPortalEffectIntensity")
    float combatant$getLastNauseaIntensity();

    @Accessor("oPortalEffectIntensity")
    void combatant$setLastNauseaIntensity(float value);

    @Accessor("positionReminder")
    void combatant$setTicksSinceLastPositionPacketSent(int ticks);

    @Accessor("wasSprinting")
    void combatant$setLastSprinting(boolean sprinting);

    @Invoker("canStartSprinting")
    boolean combatant$canStartSprinting();

    @Invoker("shouldStopRunSprinting")
    boolean combatant$shouldStopSprinting();

    @Invoker("shouldStopSwimSprinting")
    boolean combatant$shouldStopSwimSprinting();
}


