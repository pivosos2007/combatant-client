/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.combat.TapRules;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.TotemSlotPicker;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Strict totem replenish: after a pop (or with an empty offhand) opens the player inventory like a human
 * would, waits a randomised number of ticks, moves a totem to the offhand (and optionally a hotbar slot)
 * through {@link InventorySwap} and closes the screen again. Offhand's own swaps never open a screen, so
 * this lives in its own module; do not run both with different totem policies.
 */
@ModuleInfo(
        id = "autoinvtotem",
        displayName = "AutoInvTotem",
        aliases = {"InvTotem", "LegitTotem"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autoinvtotem.description")
public final class AutoInvTotem extends Module {

    private static final int OFFHAND_BUTTON = 40;
    private static final int SESSION_TIMEOUT_TICKS = 80;

    private enum Phase { IDLE, OPENING, WAIT_BEFORE, SWAPPING, WAIT_AFTER, CLOSING }

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue openInventory = bool("autoInvTotemOpenInventory", "open_inventory", true);
    private final NumberValue<Integer> minDelay = num("autoInvTotemMinDelay", "min_delay_ticks", 2, 0, 20);
    private final NumberValue<Integer> maxDelay = num("autoInvTotemMaxDelay", "max_delay_ticks", 5, 0, 20);
    private final NumberValue<Integer> hotbarSlot = num("autoInvTotemHotbarSlot", "hotbar_slot", 0, 0, 9);
    private final BooleanValue saveEnchanted = bool("autoInvTotemSaveEnchanted", "save_enchanted", true);
    private final BooleanValue preferHotbar = bool("autoInvTotemPreferHotbar", "prefer_hotbar", false);
    private final BooleanValue closeAfter = bool("autoInvTotemCloseAfter", "close_after", true);
    private final EnumValue<InventorySwapPolicy> policy =
            enumSetting("autoInvTotemPolicy", "policy", InventorySwapPolicy.GRIM_STRICT, InventorySwapPolicy.values());

    private Phase phase = Phase.IDLE;
    private int wait;
    private int sessionTicks;
    private boolean weOpened;
    private boolean swapPending;
    private boolean hadTotem;
    private boolean popped;

    @Override
    public void onEnable() {
        resetSession();
        hadTotem = false;
        popped = false;
    }

    @Override
    public void onDisable() {
        if (phase != Phase.IDLE) InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
        resetSession();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            resetSession();
            hadTotem = false;
            return;
        }

        boolean hasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (hadTotem && !hasTotem) popped = true;
        hadTotem = hasTotem;

        if (phase == Phase.IDLE) {
            if (!player.isAlive()) return;
            if (hasTotem) {
                popped = false;
                return;
            }
            if (!(popped || player.getOffhandItem().isEmpty()) || findSource(player, -1) < 0) return;
            begin(player);
            return;
        }

        if (!player.isAlive() || ++sessionTicks > SESSION_TIMEOUT_TICKS) {
            abort();
            return;
        }

        switch (phase) {
            case OPENING -> {
                if (ClientScreen.current() instanceof InventoryScreen) enterWait(Phase.WAIT_BEFORE);
                else if (wait-- <= 0) abort();
            }
            case WAIT_BEFORE -> {
                if (!(ClientScreen.current() instanceof InventoryScreen)) {
                    abort();
                } else if (wait-- <= 0) {
                    phase = Phase.SWAPPING;
                    swap(player);
                }
            }
            case SWAPPING -> {
                if (!swapPending) {
                    if (needsMore(player)) enterWait(Phase.WAIT_BEFORE);
                    else enterWait(Phase.WAIT_AFTER);
                }
            }
            case WAIT_AFTER -> {
                if (wait-- <= 0) close(player);
            }
            case CLOSING -> {
                if (!swapPending) finish();
            }
            default -> { }
        }
    }

    private void begin(LocalPlayer player) {
        sessionTicks = 0;
        weOpened = false;
        if (ClientScreen.current() instanceof InventoryScreen) {
            enterWait(Phase.WAIT_BEFORE);
            return;
        }
        if (!openInventory.get() || ClientScreen.current() != null) return;
        weOpened = true;
        ClientScreen.show(new InventoryScreen(player));
        phase = Phase.OPENING;
        wait = 3;
    }

    private void enterWait(Phase next) {
        phase = next;
        wait = TapRules.pickTicks(minDelay.get(), maxDelay.get(), ThreadLocalRandom.current().nextDouble());
    }

    private boolean needsMore(LocalPlayer player) {
        return offhandNeeded(player) || hotbarNeeded(player);
    }

    private boolean offhandNeeded(LocalPlayer player) {
        return !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                && !InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, this)
                && findSource(player, hotbarIndex()) >= 0;
    }

    private boolean hotbarNeeded(LocalPlayer player) {
        int slot = hotbarIndex();
        return slot >= 0 && !player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)
                && findSource(player, slot) >= 0;
    }

    private int hotbarIndex() {
        return hotbarSlot.get() - 1;
    }

    /** Queues one swap: offhand first, then the hotbar slot. */
    private void swap(LocalPlayer player) {
        boolean toOffhand = offhandNeeded(player);
        int hotbar = hotbarIndex();
        int source = toOffhand ? findSource(player, hotbar) : hotbarNeeded(player) ? findSource(player, hotbar) : -1;
        if (source < 0) {
            enterWait(Phase.WAIT_AFTER);
            return;
        }
        int screenSlot = InventorySwap.mapInventoryToScreenSlot(source);
        int button = toOffhand ? OFFHAND_BUTTON : hotbar;
        if (screenSlot < 0) {
            abort();
            return;
        }
        swapPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(this, InventoryActionKind.INVENTORY_CLICK, policy.get(), () -> {
            try {
                LocalPlayer current = mc.player;
                if (isEnabled() && current != null && ClientScreen.current() instanceof InventoryScreen
                        && current.containerMenu.getSlot(screenSlot).getItem().is(Items.TOTEM_OF_UNDYING)) {
                    InventorySwap.INSTANCE.clickSwap(screenSlot, button);
                }
            } finally {
                swapPending = false;
            }
        });
        if (!accepted) {
            swapPending = false;
            abort();
        }
    }

    private void close(LocalPlayer player) {
        if (!weOpened || !closeAfter.get()) {
            finish();
            return;
        }
        phase = Phase.CLOSING;
        swapPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(this, InventoryActionKind.INVENTORY_CLOSE, policy.get(), () -> {
            try {
                LocalPlayer current = mc.player;
                if (current != null && ClientScreen.current() instanceof InventoryScreen) current.closeContainer();
            } finally {
                swapPending = false;
            }
        });
        if (!accepted) {
            swapPending = false;
            if (ClientScreen.current() instanceof InventoryScreen) player.closeContainer();
            finish();
        }
    }

    private void finish() {
        popped = false;
        resetSession();
    }

    /** Gives up the session; closes the screen if this module opened it. */
    private void abort() {
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
        LocalPlayer player = mc.player;
        if (weOpened && player != null && ClientScreen.current() instanceof InventoryScreen) player.closeContainer();
        popped = false;
        resetSession();
    }

    private void resetSession() {
        phase = Phase.IDLE;
        wait = 0;
        sessionTicks = 0;
        weOpened = false;
        swapPending = false;
    }

    private int findSource(LocalPlayer player, int excludedSlot) {
        boolean[] totem = new boolean[36];
        boolean[] enchanted = new boolean[36];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            totem[i] = stack.is(Items.TOTEM_OF_UNDYING);
            enchanted[i] = totem[i] && stack.isEnchanted();
        }
        return TotemSlotPicker.pick(totem, enchanted, saveEnchanted.get(), preferHotbar.get(), excludedSlot);
    }
}
