/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/**
 * Swallows left clicks that would hit nothing while a sword or axe is held, so a missed swing never
 * costs the attack cooldown. Blocks and entities under the crosshair are untouched.
 */
@ModuleInfo(
        id = "antimiss",
        displayName = "AntiMiss",
        aliases = {"NoMiss"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.LEGIT,
        description = "module.antimiss.description")
public final class AntiMiss extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    /** Called from the manual attack entry point; true swallows the click. */
    public static boolean blocksManualAttack() {
        AntiMiss module = Modules.get(AntiMiss.class);
        return module != null && module.isEnabled() && module.shouldBlock();
    }

    private boolean shouldBlock() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.hitResult == null) return false;
        if (mc.hitResult.getType() != HitResult.Type.MISS) return false;
        ItemStack held = player.getMainHandItem();
        return held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES);
    }
}
