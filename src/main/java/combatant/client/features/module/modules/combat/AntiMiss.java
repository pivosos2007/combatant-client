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
import combatant.client.features.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/**
 * Swallows left clicks that would hit nothing while a sword or axe is held, so a missed swing never
 * costs the attack cooldown. Blocks and entities under the crosshair are untouched. With <i>fake swing</i>
 * the swallowed click still swings the arm on this client only: no packet goes out and the cooldown is kept.
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
    private final BooleanValue fakeSwing = bool("antiMissFakeSwing", "fake_swing", true);

    /** Called from the manual attack entry point; true swallows the click. */
    public static boolean blocksManualAttack() {
        AntiMiss module = Modules.get(AntiMiss.class);
        if (module == null || !module.isEnabled() || !module.shouldBlock()) return false;
        if (module.fakeSwing.get()) module.swingLocally();
        return true;
    }

    private boolean shouldBlock() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.hitResult == null) return false;
        if (mc.hitResult.getType() != HitResult.Type.MISS) return false;
        ItemStack held = player.getMainHandItem();
        return held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES);
    }

    /**
     * The arm animation of a vanilla miss without its packets: {@code LivingEntity.swing} only animates locally in
     * 26.3 (the server learns about swings from {@code ServerboundPunchPacket}, which is not sent), and the attack
     * strength ticker is left alone.
     */
    private void swingLocally() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
    }
}
