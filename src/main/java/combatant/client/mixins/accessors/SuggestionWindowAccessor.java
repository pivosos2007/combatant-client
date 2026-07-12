/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionWindowAccessor {
    @Accessor("rect")
    Rect2i getCombatant$area();

    @Accessor("suggestionList")
    List<?> getCombatant$suggestions();

    @Accessor("current")
    int getCombatant$selection();

    @Accessor("current")
    void setCombatant$selection(int selection);

    @Accessor("offset")
    int getCombatant$inWindowIndex();

    @Accessor("offset")
    void setCombatant$inWindowIndex(int index);
}


