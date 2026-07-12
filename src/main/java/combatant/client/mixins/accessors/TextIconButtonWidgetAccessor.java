/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteIconButton.class)
public interface TextIconButtonWidgetAccessor {
    @Accessor("spriteWidth")
    int combatant$getTextureWidth();

    @Accessor("spriteHeight")
    int combatant$getTextureHeight();

    @Accessor("sprite")
    WidgetSprites combatant$getTexture();
}


