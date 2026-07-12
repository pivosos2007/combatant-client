/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.runtime.RuntimeGate;

//todo Description
@ModuleInfo(
        id = "betterminecraft",
        displayName = "BetterMinecraft",
        category = ModuleCategory.MISC
)
public final class BetterMinecraft extends Module {

    private final BooleanValue hideRecipeBook = bool("hide_recipe_book", true);
    private final BooleanValue tablistRelations = bool("tablist_relations", true);

    public boolean isHideRecipeBookEnabled() {
        return !RuntimeGate.isPanic() && isEnabled() && hideRecipeBook.get();
    }

    public boolean isTablistRelationsEnabled() {
        return !RuntimeGate.isPanic() && isEnabled() && tablistRelations.get();
    }
}
