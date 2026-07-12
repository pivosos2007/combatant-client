/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractWidget.class)
public interface ClickableWidgetAccessor {
    @Accessor("tooltip")
    WidgetTooltipHolder combatant$getTooltipState();
}


