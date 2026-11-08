package net.clayborn.accurateblockplacement.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.clayborn.accurateblockplacement.IKeyBindingAccessor;
import net.minecraft.client.KeyMapping;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyBindingAccessor
{
	@Shadow
	private int clickCount;
	
	@Override
	public int accurateblockplacement_GetTimesPressed()
	{
		return clickCount;
	}
}
