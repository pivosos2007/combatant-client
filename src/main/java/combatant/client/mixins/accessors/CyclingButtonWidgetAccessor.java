/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(CycleButton.class)
public interface CyclingButtonWidgetAccessor<T> {
    @Accessor("spriteSupplier")
    CycleButton.SpriteSupplier<T> combatant$getIcon();

    @Accessor("displayState")
    CycleButton.DisplayState combatant$getLabelType();

    @Accessor("valueStringifier")
    Function<T, net.minecraft.network.chat.Component> combatant$getValueToText();
}


