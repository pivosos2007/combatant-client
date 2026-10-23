/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.SlotResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/**
 * Lets you keep a totem in hand: a left click on an entity swaps to a hotbar sword just before the attack
 * is sent, and the totem slot is restored after a short delay.
 */
@ModuleInfo(
        id = "totemhit",
        displayName = "TotemHit",
        aliases = {"TotemSwordSwap"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.totemhit.description")
public final class TotemHit extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> switchBackDelay = num("totemHitSwitchDelay", "switch_delay_ms", 30, 10, 100);

    private int totemSlot = -1;
    private int swordSlot = -1;
    private long restoreAt;

    /** Called at the head of the manual attack; swaps to a sword when a totem is about to hit an entity. */
    public static void beforeManualAttack() {
        TotemHit module = Modules.get(TotemHit.class);
        if (module != null && module.isEnabled()) module.swapForAttack();
    }

    private void swapForAttack() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || totemSlot >= 0) return;
        if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return;

        SlotResult sword = InventorySwap.INSTANCE.findHotbar(stack -> stack.is(ItemTags.SWORDS));
        if (!sword.found()) return;

        int current = InventorySwap.INSTANCE.clientSelectedSlot();
        if (!InventorySwap.INSTANCE.selectHotbar(sword.slot())) return;
        totemSlot = current;
        swordSlot = sword.slot();
        restoreAt = System.currentTimeMillis() + switchBackDelay.get();
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || totemSlot < 0) return;
        if (mc.player == null || System.currentTimeMillis() < restoreAt) return;
        restore();
    }

    @Override
    public void onEnable() {
        totemSlot = -1;
        swordSlot = -1;
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void restore() {
        if (totemSlot >= 0 && mc.player != null && InventorySwap.INSTANCE.clientSelectedSlot() == swordSlot) {
            InventorySwap.INSTANCE.selectHotbar(totemSlot);
        }
        totemSlot = -1;
        swordSlot = -1;
    }
}
