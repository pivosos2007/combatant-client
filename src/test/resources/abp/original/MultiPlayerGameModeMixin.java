package net.clayborn.accurateblockplacement.mixin;

import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @ModifyConstant(method = "continueDestroyBlock", constant = @Constant(intValue = 5), require = 0)
    private int FastBlockBreaking(int value) {
        return AccurateBlockPlacementMod.isFastBlockBreakingEnabled ? 0 : value;
    }
}