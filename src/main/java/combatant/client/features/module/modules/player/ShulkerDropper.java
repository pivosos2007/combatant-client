/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.InventoryAction;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Throws every shulker box out of your inventory, one stack at a time through the shared inventory runtime
 * (InventoryAction, with the chosen swap policy and a delay between throws). Switches itself off when the inventory
 * is clean unless "keep watching" is on. A throw the server refuses ends the pass; after a few failed passes the
 * module gives up instead of spamming clicks.
 */
@ModuleInfo(
        id = "shulkerdropper",
        displayName = "ShulkerDropper",
        aliases = {"DropShulkers", "ShulkerThrower", "ShulkerShopDropper", "ShulkerBuyer", "ShopShulkerDrop"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.shulkerdropper.description")
public final class ShulkerDropper extends Module {

    private static final int MAX_FAILED_PASSES = 3;
    private static final int DROP_TIMEOUT_TICKS = 60;
    private static final int RETRY_COOLDOWN_TICKS = 20;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<InventorySwapPolicy> policy = enumSetting(
            "shulkerDropperPolicy", "policy", InventorySwapPolicy.LEGIT, InventorySwapPolicy.values());
    private final NumberValue<Integer> delayTicks = num("shulkerDropperDelayTicks", "delay_ticks", 4, 0, 40);
    private final BooleanValue includeHotbar = bool("shulkerDropperIncludeHotbar", "include_hotbar", true);
    private final BooleanValue keepWatching = bool("shulkerDropperKeepWatching", "keep_watching", false);
    private final BooleanValue buyFromShop = bool("shulkerDropperBuyFromShop", "buy_from_shop", false);
    private final NumberValue<Integer> clicksPerBuy = visibleWhen(
            num("shulkerDropperClicksPerBuy", "clicks_per_buy", 8, 1, 32), buyFromShop::get);
    private final NumberValue<Integer> shopReopenDelay = visibleWhen(
            num("shulkerDropperReopenDelay", "reopen_delay_ticks", 20, 1, 60), buyFromShop::get);

    private AutomationTask task;
    private int cooldown;
    private int failedPasses;
    private int shopWait;

    @Override
    public void onEnable() {
        task = null;
        cooldown = 0;
        failedPasses = 0;
        shopWait = 0;
    }

    @Override
    public void onDisable() {
        task = null;
    }

    /** Shop mode: keep /shop open, buy shulker boxes in bulk and throw them straight out of the menu. */
    private void shopStep(LocalPlayer player) {
        if (shopWait-- > 0) return;
        if (!(player.containerMenu instanceof ChestMenu menu)) {
            ServerChat.command("shop");
            shopWait = shopReopenDelay.get();
            return;
        }
        int menuSlots = menu.getRowCount() * 9;
        int shulker = -1;
        for (int slot = 0; slot < menuSlots; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.SHULKER_BOX)) {
                shulker = slot;
                break;
            }
        }
        if (shulker < 0) {
            shopWait = Math.max(1, delayTicks.get());
            return;
        }
        for (int i = 0; i < clicksPerBuy.get(); i++) {
            mc.gameMode.handleContainerInput(menu.containerId, shulker, 0, ContainerInput.PICKUP, player);
        }
        for (int slot = menuSlots; slot < menu.slots.size(); slot++) {
            if (isShulker(menu.getSlot(slot).getItem())) {
                mc.gameMode.handleContainerInput(menu.containerId, slot, 1, ContainerInput.THROW, player);
            }
        }
        shopWait = Math.max(1, delayTicks.get());
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            setEnabled(false);
            return;
        }
        if (buyFromShop.get()) {
            shopStep(player);
            return;
        }
        if (task != null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // Only the player's own inventory menu has the slot layout this module reads.
        if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()) return;

        List<Slot> shulkers = findShulkers(player.inventoryMenu);
        if (shulkers.isEmpty()) {
            if (!keepWatching.get()) {
                Notifier.info(I18n.get("notification.shulkerdropper.done"));
                setEnabled(false);
            }
            return;
        }
        startPass(player.inventoryMenu, shulkers);
    }

    private List<Slot> findShulkers(InventoryMenu menu) {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) continue;
            int index = slot.getContainerSlot();
            if (index < 0 || index >= 36) continue;
            if (!includeHotbar.get() && index < 9) continue;
            if (isShulker(slot.getItem())) out.add(slot);
        }
        return out;
    }

    static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ShulkerBoxBlock;
    }

    private void startPass(InventoryMenu menu, List<Slot> shulkers) {
        InventorySwapPolicy swapPolicy = policy.get();
        int wait = delayTicks.get();
        List<Action> steps = new ArrayList<>(shulkers.size() * 2);
        for (Slot slot : shulkers) {
            int screenSlot = slot.index;
            steps.add(InventoryAction.dropScreenSlot(screenSlot, swapPolicy,
                    () -> !isShulker(menu.getSlot(screenSlot).getItem()), DROP_TIMEOUT_TICKS));
            if (wait > 0) steps.add(Actions.waitTicks(wait));
        }
        task = AutomationRuntime.INSTANCE.submit(AutomationTask
                .builder(this, "ShulkerDropper", Actions.sequence("DropShulkers", steps.toArray(new Action[0])))
                .priority(ActionPriority.UTILITY)
                .onFinish(this::onPassFinished)
                .build());
    }

    private void onPassFinished(AutomationTask finished) {
        if (finished != task) return;
        task = null;
        if (!isEnabled()) return;
        if (finished.result().isSuccess()) {
            failedPasses = 0;
            return;
        }
        // Cancelled by the runtime (world change, higher-priority owner) or refused by the server.
        if (++failedPasses >= MAX_FAILED_PASSES) {
            Notifier.warning(I18n.get("notification.shulkerdropper.failed"));
            setEnabled(false);
            return;
        }
        cooldown = RETRY_COOLDOWN_TICKS;
    }
}
