/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import java.util.LinkedHashMap;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * While the held item is one of the configured kinds, the crosshair ignores entities and targets the block behind
 * them, so a mob or item frame in the line of sight no longer steals a mining or placing click.
 */
@ModuleInfo(
        id = "noentitytrace",
        displayName = "NoEntityTrace",
        aliases = {"NoEntityRaycast", "ThroughEntities"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.noentitytrace.description")
public final class NoEntityTrace extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue always = bool("noEntityTraceAlways", "always", false);
    private final BooleanMapValue itemTypes = group("noEntityTraceTypes", "item_types", defaultItemTypes());

    private static LinkedHashMap<String, Boolean> defaultItemTypes() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("pickaxe", true);
        defaults.put("axe", false);
        defaults.put("shovel", false);
        defaults.put("hoe", false);
        defaults.put("blocks", true);
        return defaults;
    }

    @EventHandler
    private void onCrosshairTarget(CrosshairTargetUpdateEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!(event.getHitResult() instanceof EntityHitResult) && event.getTargetedEntity() == null) return;
        if (!appliesTo(player.getMainHandItem())) return;

        HitResult blockHit = player.pick(player.blockInteractionRange(), event.getTickDelta(), false);
        event.setHitResult(blockHit);
        event.setTargetedEntity(null);
    }

    private boolean appliesTo(ItemStack held) {
        if (always.get()) return true;
        if (held.isEmpty()) return false;
        if (itemTypes.get("pickaxe") && held.is(ItemTags.PICKAXES)) return true;
        if (itemTypes.get("axe") && held.is(ItemTags.AXES)) return true;
        if (itemTypes.get("shovel") && held.is(ItemTags.SHOVELS)) return true;
        if (itemTypes.get("hoe") && held.is(ItemTags.HOES)) return true;
        return itemTypes.get("blocks") && held.getItem() instanceof BlockItem;
    }
}
