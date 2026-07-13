/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor("destroyBlockPos")
    BlockPos combatant$getCurrentBreakingPos();

    @Accessor("destroyProgress")
    float combatant$getCurrentBreakingProgress();

    @Accessor("destroyProgress")
    void combatant$setCurrentBreakingProgress(float progress);

    @Accessor("destroyDelay")
    int combatant$getBlockBreakingCooldown();

    @Accessor("destroyDelay")
    void combatant$setBlockBreakingCooldown(int cooldown);

    @Accessor("carriedIndex")
    int combatant$getLastSelectedSlot();

    @Accessor("carriedIndex")
    void combatant$setLastSelectedSlot(int slot);

    @Invoker("ensureHasSentCarriedItem")
    void combatant$syncSelectedSlot();
}
