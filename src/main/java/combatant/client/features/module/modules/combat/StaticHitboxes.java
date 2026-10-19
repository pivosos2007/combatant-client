/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.combat.StandingBox;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Targeting treats other players as standing even while they sneak, crawl, swim or glide, so their box
 * does not shrink under the crosshair. Applied from the entity ray-cast in {@code ProjectileUtilMixin}.
 */
@ModuleInfo(
        id = "statichitboxes",
        displayName = "StaticHitboxes",
        aliases = {"StaticHitbox", "NoShrinkHitbox"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.statichitboxes.description")
public final class StaticHitboxes extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue playersOnly = bool("staticHitboxesPlayersOnly", "players_only", true);

    /** Box to use for ray-casting {@code target}; {@code vanilla} when this module does not apply. */
    public AABB adjust(Entity target, AABB vanilla) {
        if (!isEnabled() || !(target instanceof LivingEntity) || target == mc.player) return vanilla;
        if (playersOnly.get() && !(target instanceof Player)) return vanilla;
        return StandingBox.of(target, vanilla);
    }
}
