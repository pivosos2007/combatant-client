/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface WorldAccessor {

    @Accessor("isClientSide")
    boolean combatant$isClient();

    @Accessor("rainLevel")
    float combatant$getRainGradientRaw();

    @Accessor("oRainLevel")
    float combatant$getLastRainGradientRaw();

    @Accessor("thunderLevel")
    float combatant$getThunderGradientRaw();

    @Accessor("oThunderLevel")
    float combatant$getLastThunderGradientRaw();
}


