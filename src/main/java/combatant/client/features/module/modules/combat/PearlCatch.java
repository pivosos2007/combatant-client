/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;

/**
 * One bind throws an ender pearl and, a few ticks later, a wind charge at your feet, so the burst carries you after
 * the pearl. The wind charge is aimed straight down through the {@link RotationManager} (silently, the camera does
 * not move) and thrown the moment the rotation is held. Both items are taken from the offhand, the hand, the hotbar
 * or, optionally, the inventory through {@link InventorySwap}, and the previous slot is restored afterwards.
 *
 * <p>Unlike {@link TargetPearl}, which answers an enemy's pearl, this one is driven only by its own bind.
 */
@ModuleInfo(
        id = "pearlcatch",
        displayName = "PearlCatch",
        aliases = {"PearlWind", "WindCatch"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.pearlcatch.description")
public final class PearlCatch extends Module {

    private static final String ACTION_TRIGGER = "pearl_catch";
    private static final int ROTATION_PRIORITY = 55;
    /** Ticks the downward aim may take before the wind charge is given up on. */
    private static final int AIM_TIMEOUT_TICKS = 12;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> windDelay = num("pearlCatchWindDelay", "wind_delay", 2, 1, 10);
    private final BooleanValue aimDown = bool("pearlCatchAimDown", "aim_down", true);
    private final BooleanValue fromInventory = bool("pearlCatchFromInventory", "from_inventory", true);
    private final BooleanValue switchBack = bool("pearlCatchSwitchBack", "switch_back", true);
    private final BooleanValue notifications = bool("pearlCatchNotifications", "notifications", true);

    private Stage stage = Stage.IDLE;
    private int wait;
    private int aimTicks;
    private boolean pendingTrigger;
    public PearlCatch() {
        action(ACTION_TRIGGER, "NONE");
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        boolean pressed = isActionPressedOnce(ACTION_TRIGGER);
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }

        switch (stage) {
            case IDLE -> {
                if (pressed) pendingTrigger = true;
            }
            case WAITING -> {
                // Handled in RotationUpdateEvent.PRE so item use never goes out post-movement (BadPackets R/X).
            }
            case AIMING -> {
                if (++aimTicks > AIM_TIMEOUT_TICKS) {
                    warn("notification.pearlcatch.aim_failed");
                    reset();
                }
            }
        }
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (stage == Stage.IDLE && pendingTrigger) {
            pendingTrigger = false;
            start(player);
            return;
        }
        if (stage == Stage.WAITING) {
            if (--wait > 0) return;
            if (!has(Items.WIND_CHARGE)) {
                warn("notification.pearlcatch.no_wind_charge");
                reset();
                return;
            }
            if (!aimDown.get()) {
                use(Items.WIND_CHARGE);
                reset();
                return;
            }
            stage = Stage.AIMING;
            aimTicks = 0;
        }
        if (stage != Stage.AIMING) return;
        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), 90.0f),
                null,
                List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(this::throwWindCharge)
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private void start(LocalPlayer player) {
        if (ClientScreen.current() != null) return;
        if (!has(Items.ENDER_PEARL)) {
            warn("notification.pearlcatch.no_pearl");
            return;
        }
        if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;
        // Throwing the pearl with nothing to catch it would only waste it.
        if (!has(Items.WIND_CHARGE)) {
            warn("notification.pearlcatch.no_wind_charge");
            return;
        }
        if (!use(Items.ENDER_PEARL)) return;

        stage = Stage.WAITING;
        wait = windDelay.get();
    }

    private void throwWindCharge() {
        if (stage == Stage.AIMING && isEnabled()) use(Items.WIND_CHARGE);
        reset();
    }

    /** Uses the item from the offhand, the hand or a (silent) swap; true when the use was accepted. */
    private boolean use(Item item) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;

        if (player.getOffhandItem().is(item)) return useHand(player, InteractionHand.OFF_HAND);
        if (player.getMainHandItem().is(item)) return useHand(player, InteractionHand.MAIN_HAND);

        boolean[] used = new boolean[1];
        InventorySwapRequest request = InventorySwapRequest
                .builder(matching(item), () -> used[0] = useHand(player, InteractionHand.MAIN_HAND))
                .scope(fromInventory.get() ? InventorySearchScope.FULL : InventorySearchScope.HOTBAR)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(switchBack.get())
                .policy(InventorySwapPolicy.NONE)
                .actionKind(InventoryActionKind.USE_ITEM)
                .build();
        return InventorySwap.INSTANCE.execute(request) && used[0];
    }

    private boolean useHand(LocalPlayer player, InteractionHand hand) {
        InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
        if (result == null || !result.consumesAction()) return false;
        player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
        return true;
    }

    private boolean has(Item item) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (player.getOffhandItem().is(item) || player.getMainHandItem().is(item)) return true;
        Predicate<ItemStack> match = matching(item);
        return InventorySwap.INSTANCE.findHotbar(match).found()
                || (fromInventory.get() && InventorySwap.INSTANCE.findInventory(match).found());
    }

    private static Predicate<ItemStack> matching(Item item) {
        return stack -> stack != null && stack.is(item);
    }

    private void warn(String key) {
        if (notifications.get()) Notifier.warning(I18n.get(key));
    }

    private void reset() {
        stage = Stage.IDLE;
        wait = 0;
        aimTicks = 0;
        RotationManager.INSTANCE.release(this);
    }

    private enum Stage {
        IDLE,
        WAITING,
        AIMING
    }
}
