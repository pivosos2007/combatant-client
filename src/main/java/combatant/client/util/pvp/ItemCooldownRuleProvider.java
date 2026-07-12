/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.world.item.Item;
import combatant.client.config.values.ItemCooldownRulesValue;

import java.util.Set;

/**
 * Supplies configurable item cooldown rules to the generic item-use cooldown engine.
 * <p>
 * The engine itself is owner-based and does not know whether an owner is the local player,
 * an opponent, a fake preview, or another subsystem. PvP modules can provide rules through
 * this interface without making the storage local-player-only.
 */
public interface ItemCooldownRuleProvider {
    boolean isItemCooldownRulesEnabled();

    ItemCooldownRulesValue.Rule getCooldownRule(Item item);

    Set<Item> getTrackedCooldownItems();
}
