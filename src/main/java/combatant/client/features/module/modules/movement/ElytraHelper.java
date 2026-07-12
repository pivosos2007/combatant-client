/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.util.player.ElytraRecastUtil;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.pvp.client.CooldownsState;

//todo Description
@ModuleInfo(
        id = "elytrahelper",
        displayName = "ElytraHelper",
        category = ModuleCategory.MOVEMENT
)
public class ElytraHelper extends Module {

    private static final String SETTING_AUTO_TAKEOFF = "auto_takeoff";
    private static final String ACTION_SWAP_ELYTRA = "swap_elytra";
    private static final String ACTION_USE_FIREWORK = "use_firework";
    private static final String SETTING_RECAST_MODE = "recast_mode";
    private static final String SETTING_RECAST_AUTO_WALK = "recast_auto_walk";
    private static final String SETTING_RECAST_AUTO_JUMP = "recast_auto_jump";
    private static final String SETTING_RECAST_ALLOW_BROKEN = "recast_allow_broken";
    private static final String SETTING_RECAST_DELAY = "recast_delay";
    private static final int MANUAL_FIREWORK_COOLDOWN_TICKS = FireworkUseController.DEFAULT_MANUAL_COOLDOWN_TICKS;
    private final Minecraft client = Minecraft.getInstance();
    private final BooleanValue autoTakeoff =
            bool("auto_takeoff", SETTING_AUTO_TAKEOFF, true);
    private final EnumValue<ElytraRecastUtil.Mode> recastMode =
            enumSetting("recast_mode", SETTING_RECAST_MODE, ElytraRecastUtil.Mode.NORMAL, ElytraRecastUtil.Mode.values());
    private final BooleanValue recastAutoWalk =
            bool("recast_auto_walk", SETTING_RECAST_AUTO_WALK, true);
    private final BooleanValue recastAutoJump =
            bool("recast_auto_jump", SETTING_RECAST_AUTO_JUMP, true);
    private final BooleanValue recastAllowBroken =
            bool("recast_allow_broken", SETTING_RECAST_ALLOW_BROKEN, true);
    private final NumberValue<Integer> recastDelay =
            num("recast_delay", SETTING_RECAST_DELAY, 0, 0, 5);
    private ItemStack previousChest = ItemStack.EMPTY;
    private boolean swapLock = false;
    private boolean pendingFirework = false;
    private boolean queuedFireworkUse = false;
    private boolean queuedJump = false;
    private boolean queuedStartGlide = false;
    private boolean pendingAutoTakeoff = false;
    private boolean queuedAutoTakeoffRelease = false;
    private int queuedAutoTakeoffJumpTicks = 0;
    // Delay before START_FALL_FLYING during auto takeoff
    private int glideDelayTicks = 0;

    {
        setDefaultBind("LEFT_CTRL+R");
        addAction(ACTION_SWAP_ELYTRA, "R");
        addAction(ACTION_USE_FIREWORK, "F");
    }

    private boolean isRecastActive() {
        if (!isEnabled()) return false;
        return client != null && client.options != null && client.options.keyJump.isDown();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (client.player == null || client.gameMode == null) return;

        LocalPlayer player = client.player;

        // ===== SWAP ELYTRA =====
        if (isActionPressedOnce(ACTION_SWAP_ELYTRA) && !swapLock) {
            swapLock = true;
            swapChest();
        }
        if (!isActionHeld(ACTION_SWAP_ELYTRA)) {
            swapLock = false;
        }

        if (glideDelayTicks > 0) {
            glideDelayTicks--;
        }
        if (pendingFirework && player.isFallFlying()) {
            pendingFirework = false;
            queueFireworkUse();
        }
        if (pendingAutoTakeoff) {
            tickAutoTakeoff(player);
        }

        // ===== FIREWORK / TAKEOFF =====
        if (isActionPressedOnce(ACTION_USE_FIREWORK)) {
            handleFireworkOrTakeoff(player);
        } else if (isActionHeld(ACTION_USE_FIREWORK) && player.isFallFlying() && canQueueManualFirework(player, false)) {
            queueFireworkUse();
        }
        if (isRecastActive()) {
            handleRecast(player);
        } else {
            ElytraRecastUtil.INSTANCE.reset(this);
        }
    }

    @Override
    public void onDisable() {
        if (client == null) return;
        InventorySwap.INSTANCE.releaseHotbar(this);
        queuedJump = false;
        queuedStartGlide = false;
        pendingAutoTakeoff = false;
        queuedAutoTakeoffRelease = false;
        queuedAutoTakeoffJumpTicks = 0;
        pendingFirework = false;
        queuedFireworkUse = false;
        glideDelayTicks = 0;
        ElytraRecastUtil.INSTANCE.reset(this);
    }

    private void handleRecast(LocalPlayer player) {
        ElytraRecastUtil.INSTANCE.request(
                this,
                player,
                recastMode.get(),
                recastAllowBroken.get(),
                !recastAutoJump.get(),
                recastAutoWalk.get(),
                recastDelay.get()
        );
        ElytraRecastUtil.INSTANCE.tick(this, player);
    }

    // =========================================================
    //           FIREWORK + AUTO TAKEOFF
    // =========================================================
    private void handleFireworkOrTakeoff(LocalPlayer player) {

        // Respect PVP firework cooldown first.
        if (CooldownsState.MANAGER.isInPvp()
                && CooldownsState.MANAGER.isCooling(Items.FIREWORK_ROCKET)) {
            Notifier.warning("Firework is on cooldown");
            return;
        }

        // Already gliding: queue the firework in sync phase. Holding the action keeps retrying,
        // but FireworkUseController and KillAura sync gates decide the actual shot frame.
        if (player.isFallFlying()) {
            pendingAutoTakeoff = false;
            queuedJump = false;
            queuedStartGlide = false;
            if (canQueueManualFirework(player, true)) {
                queueFireworkUse();
            }
            pendingFirework = false;
            return;
        }

        // AUTO TAKEOFF
        if (!autoTakeoff.get()) return;
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        if (player.isPassenger()) return;
        if (pendingAutoTakeoff) {
            pendingFirework = true;
            return;
        }

        // Queue jump for sync phase; water and lava are allowed.
        pendingAutoTakeoff = true;
        queuedStartGlide = false;
        if (canJumpNow(player)) {
            queueJump();
            glideDelayTicks = player.onGround() ? 1 : 0;
        }

        // Firework will be used after glide starts.
        pendingFirework = true;
    }

    // =========================================================
    //                 DEFERRED FIREWORK USE
    // =========================================================
    private void queueFireworkUse() {
        queuedFireworkUse = true;
    }

    private boolean canQueueManualFirework(LocalPlayer player, boolean notifyCooldown) {
        if (player == null || !player.isFallFlying() || player.isUsingItem()) return false;
        if (CooldownsState.MANAGER.isInPvp()
                && CooldownsState.MANAGER.isCooling(Items.FIREWORK_ROCKET)) {
            if (notifyCooldown) {
                Notifier.warning("Firework is on cooldown");
            }
            return false;
        }
        return FireworkUseController.INSTANCE.canPassCooldown(MANUAL_FIREWORK_COOLDOWN_TICKS);
    }

    private void queueJump() {
        queuedJump = true;
    }

    private boolean canJumpNow(LocalPlayer player) {
        return player != null
                && !player.isPassenger()
                && !player.getAbilities().flying
                && (player.onGround() || player.isInWater() || player.isInLava());
    }

    private void clearAutoTakeoff() {
        pendingAutoTakeoff = false;
        queuedJump = false;
        queuedStartGlide = false;
        queuedAutoTakeoffRelease = false;
        queuedAutoTakeoffJumpTicks = 0;
        glideDelayTicks = 0;
    }

    private void tickAutoTakeoff(LocalPlayer player) {
        if (player == null) {
            clearAutoTakeoff();
            return;
        }
        if (player.isFallFlying()) {
            clearAutoTakeoff();
            return;
        }
        if (player.isPassenger() || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            clearAutoTakeoff();
            pendingFirework = false;
            return;
        }

        if (player.isInWater() || player.isInLava()) {
            queueJump();
            queuedStartGlide = false;
            queuedAutoTakeoffRelease = false;
            glideDelayTicks = 0;
            return;
        }

        if (player.onGround()) {
            queueJump();
            queuedStartGlide = false;
            queuedAutoTakeoffRelease = false;
            if (glideDelayTicks <= 0) {
                glideDelayTicks = 1;
            }
            return;
        }

        if (glideDelayTicks > 0) {
            return;
        }

        if (player.getDeltaMovement().y > 0.0) {
            return;
        }

        if (queuedStartGlide || queuedAutoTakeoffRelease) {
            return;
        }

        queuedAutoTakeoffRelease = true;
        queuedStartGlide = true;
    }

    @EventHandler(priority = -1000)
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;

        if (ElytraRecastUtil.INSTANCE.handleMovementInput(this, event)) {
            return;
        }

        if (queuedAutoTakeoffRelease) {
            event.setJump(false);
            queuedAutoTakeoffRelease = false;
            return;
        }

        if (queuedAutoTakeoffJumpTicks > 0) {
            event.setJump(true);
            queuedAutoTakeoffJumpTicks--;
            return;
        }

        if (!queuedJump) return;

        LocalPlayer player = client.player;
        if (player == null) {
            queuedJump = false;
            return;
        }

        if (canJumpNow(player)) {
            event.setJump(true);
        }
        queuedJump = false;
    }

    @EventHandler(priority = -1000)
    private void onSync(EventSync event) {
        if (!isEnabled()) return;

        LocalPlayer player = client.player;
        MultiPlayerGameMode manager = client.gameMode;
        if (player == null || manager == null) return;

        if (queuedStartGlide) {
            if (player.isFallFlying()) {
                queuedStartGlide = false;
                pendingAutoTakeoff = false;
            } else if (!player.onGround()
                    && !player.isInWater()
                    && !player.isInLava()
                    && player.getDeltaMovement().y <= 0.0) {
                queuedStartGlide = false;
                if (player.tryToStartFallFlying()) {
                    pendingAutoTakeoff = false;
                    queuedAutoTakeoffJumpTicks = 1;
                }
                if (player.isFallFlying() && client.getConnection() != null) {
                    client.getConnection().send(
                            new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
                    );
                } else {
                    glideDelayTicks = 1;
                }
            } else if (player.onGround() || player.isInWater() || player.isInLava()) {
                queuedStartGlide = false;
                queuedAutoTakeoffRelease = false;
            }
        }

        if (ElytraRecastUtil.INSTANCE.handleSync(this, event)) {
            return;
        }

        if (!queuedFireworkUse) return;
        queuedFireworkUse = false;
        FireworkUseController.INSTANCE.use(
                event.getYaw(),
                event.getPitch(),
                true,
                true,
                true,
                MANUAL_FIREWORK_COOLDOWN_TICKS,
                true
        );
    }

    // =========================================================
    //                     SWAP / UTILS
    // =========================================================
    private void swapChest() {
        LocalPlayer player = client.player;
        if (player == null) return;

        ItemStack equipped = getChestStack(player);

        if (!isElytra(equipped)) {
            int elytraSlot = findElytra(player);
            if (elytraSlot == -1) return;
            if (!equipped.isEmpty()) previousChest = equipped.copy();
            swapInventoryWithChest(player, elytraSlot);
            return;
        }

        int chestSlot = findPreviousOrAnyChest(player);
        if (chestSlot == -1) return;
        swapInventoryWithChest(player, chestSlot);
        previousChest = ItemStack.EMPTY;
    }

    private void swapInventoryWithChest(LocalPlayer player, int invSlot) {
        var handler = player.inventoryMenu;
        if (handler == null) return;

        int syncId = handler.containerId;
        int chestScreenSlot = 6;
        int invScreenSlot = InventorySwap.mapInventoryToScreenSlot(invSlot);

        client.execute(() -> InventorySwap.INSTANCE.swapScreenSlots(invScreenSlot, chestScreenSlot));
    }

    private ItemStack getChestStack(LocalPlayer player) {
        return player.getInventory()
                .getItem(EquipmentSlot.CHEST.getIndex(36));
    }

    private boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ELYTRA);
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ELYTRA)) return false;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.CHEST;
    }

    private int findElytra(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.ELYTRA)) return i;
        }
        return -1;
    }

    private int findPreviousOrAnyChest(LocalPlayer player) {
        if (!previousChest.isEmpty()) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItem(s, previousChest)) {
                    return i;
                }
            }
        }

        int best = findBestChestplate(player);
        if (best != -1) return best;

        for (int i = 0; i < 36; i++) {
            if (isChestplate(player.getInventory().getItem(i))) return i;
        }

        return -1;
    }

    private int findBestChestplate(LocalPlayer player) {
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            int score = getChestDefenseScore(s);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int getChestDefenseScore(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ELYTRA)) return 0;

        int base =
                stack.is(Items.LEATHER_CHESTPLATE) ? 3 :
                        stack.is(Items.COPPER_CHESTPLATE) ? 4 :
                                stack.is(Items.CHAINMAIL_CHESTPLATE) ? 5 :
                                        stack.is(Items.GOLDEN_CHESTPLATE) ? 5 :
                                                stack.is(Items.IRON_CHESTPLATE) ? 6 :
                                                        stack.is(Items.DIAMOND_CHESTPLATE) ? 8 :
                                                                stack.is(Items.NETHERITE_CHESTPLATE) ? 9 : 0;

        if (base == 0) return 0;
        return base * 10 + getProtectionLevel(stack) * 4;
    }

    private int getProtectionLevel(ItemStack stack) {
        Holder<Enchantment> prot = getProtectionEnchant();
        return prot == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(prot, stack);
    }

    private Holder<Enchantment> getProtectionEnchant() {
        if (client.level == null) return null;
        var registry = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment ench = registry.getValue(Enchantments.PROTECTION);
        return ench == null ? null : registry.wrapAsHolder(ench);
    }

}
