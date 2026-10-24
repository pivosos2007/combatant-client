/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.AbstractContainerScreenAccessor;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/** While a container screen is open, hovering a totem moves it to the offhand and/or a chosen hotbar slot. */
@ModuleInfo(
        id = "hovertotem",
        displayName = "HoverTotem",
        aliases = {"HoverTotems"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.hovertotem.description")
public final class HoverTotem extends Module {

    private static final int OFFHAND_BUTTON = 40;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Target> target = enumSetting("hoverTotemTarget", "target", Target.OFFHAND, Target.values());
    private final NumberValue<Integer> hotbarSlot = num("hoverTotemHotbarSlot", "hotbar_slot", 9, 1, 9);
    private final NumberValue<Integer> delay = num("hoverTotemDelay", "delay_ticks", 2, 0, 20);
    private final EnumValue<InventorySwapPolicy> policy =
            enumSetting("hoverTotemPolicy", "policy", InventorySwapPolicy.LEGIT, InventorySwapPolicy.values());

    private int hoveredIndex = -1;
    private int hoverTicks;
    private int containerId = -1;
    private boolean pending;

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || !(ClientScreen.current() instanceof AbstractContainerScreen<?> screen)) {
            reset();
            return;
        }
        if (pending) return;

        Slot slot = ((AbstractContainerScreenAccessor) screen).combatant$getHoveredSlot();
        if (slot == null || slot.index != hoveredIndex || screen.getMenu().containerId != containerId) {
            hoveredIndex = slot != null ? slot.index : -1;
            containerId = screen.getMenu().containerId;
            hoverTicks = 0;
        } else {
            hoverTicks++;
        }
        if (slot == null || hoverTicks < delay.get()) return;

        int button = buttonFor(player, slot);
        if (button < 0) return;
        if (button == OFFHAND_BUTTON && InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, this)) return;

        int slotIndex = slot.index;
        int menuId = containerId;
        pending = true;
        boolean accepted = InventorySwap.INSTANCE.command(this, InventoryActionKind.INVENTORY_CLICK, policy.get(), () -> {
            try {
                LocalPlayer current = mc.player;
                if (!isEnabled() || current == null || current.containerMenu.containerId != menuId) return;
                if (slotIndex < 0 || slotIndex >= current.containerMenu.slots.size()) return;
                if (!current.containerMenu.getSlot(slotIndex).getItem().is(Items.TOTEM_OF_UNDYING)) return;
                InventorySwap.INSTANCE.clickSwap(slotIndex, button);
            } finally {
                pending = false;
            }
        });
        if (!accepted) pending = false;
        hoverTicks = 0;
    }

    /** Swap button for moving the hovered slot's totem: 40 = offhand, 0..8 = hotbar slot, -1 = nothing to do. */
    private int buttonFor(LocalPlayer player, Slot slot) {
        if (!(slot.container instanceof Inventory) || !slot.getItem().is(Items.TOTEM_OF_UNDYING)) return -1;
        int own = slot.getContainerSlot();
        boolean offhandHasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        int hotbar = hotbarSlot.get() - 1;
        boolean hotbarHasTotem = player.getInventory().getItem(hotbar).is(Items.TOTEM_OF_UNDYING);

        Target mode = target.get();
        if (mode != Target.HOTBAR && own != OFFHAND_BUTTON && !offhandHasTotem) return OFFHAND_BUTTON;
        if (mode != Target.OFFHAND && own != hotbar && !hotbarHasTotem) return hotbar;
        return -1;
    }

    private void reset() {
        hoveredIndex = -1;
        hoverTicks = 0;
        containerId = -1;
        pending = false;
    }

    public enum Target {
        OFFHAND, HOTBAR, BOTH
    }
}
