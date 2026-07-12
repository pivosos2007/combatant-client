/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public interface ChatInputSuggestorAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList getCombatant$window();

    @Accessor("pendingSuggestions")
    CompletableFuture<Suggestions> getCombatant$pendingSuggestions();
}


