/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.mixininterface.IBiome;
import combatant.client.mixins.accessors.BiomeClimateSettingsAccessor;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Biome.class)
public abstract class BiomeMixin implements IBiome {
    @Unique
    private float combatant$downfall;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureDownfall(
            @Coerce Object climateSettings,
            EnvironmentAttributeMap attributes,
            BiomeSpecialEffects specialEffects,
            BiomeGenerationSettings generationSettings,
            CallbackInfo ci
    ) {
        combatant$downfall = ((BiomeClimateSettingsAccessor) climateSettings).combatant$getDownfall();
    }

    @Override
    public float combatant$getDownfall() {
        return combatant$downfall;
    }
}
