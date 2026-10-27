/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.features.gui.hud.actions.HudActionRegistry;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.modules.player.offhand.OffhandItems.ItemChoice;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.CombatConsume;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.ConsumeControl;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.ConsumeHand;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/**
 * The eat/gapple lifecycle: offer a suggestion, bring the item to a hand, start the use, watch it finish, lock out
 * the next one. Moving items belongs to {@link OffhandSwapper} (offhand) and {@link MainhandSwitcher} (main hand);
 * putting the main hand back is only ever requested here and carried out by the switcher on a later tick.
 */
public final class OffhandConsumer {
    private static final int REQUEST_TIMEOUT_TICKS = 10;

    private final Minecraft mc = Minecraft.getInstance();
    private final Object owner;
    private final OffhandSettings settings;
    private final BooleanSupplier enabled;
    private final BooleanSupplier actionPressed;
    private final OffhandThreats threats;
    private final OffhandItems items;
    private final OffhandSwapper swapper;
    private final MainhandSwitcher switcher;

    private boolean semiArmed;
    private int semiDeadlineTick = -1;
    private boolean suggestedReady;
    private ItemStack suggestedStack = ItemStack.EMPTY;
    private ItemStack consumingStack = ItemStack.EMPTY;
    private int requestTick = -1;
    private int displayUntilTick = -1;
    private int appleGuardUntilTick = -1;
    private int lastUseObservedTick = -1;
    private int lockedUntilTick = -1;
    private boolean forcingUse;
    private boolean offhandActive;
    private boolean useObserved;
    private boolean gappleConsumption;
    private boolean gappleWasEnchanted;
    private boolean useQueued;

    public OffhandConsumer(Object owner, OffhandSettings settings, BooleanSupplier enabled,
                           BooleanSupplier actionPressed, OffhandThreats threats, OffhandItems items,
                           OffhandSwapper swapper, MainhandSwitcher switcher) {
        this.owner = owner;
        this.settings = settings;
        this.enabled = enabled;
        this.actionPressed = actionPressed;
        this.threats = threats;
        this.items = items;
        this.swapper = swapper;
        this.switcher = switcher;
    }

    public HudActionRegistry.State hudState(LocalPlayer player, boolean moduleEnabled) {
        boolean ready = player != null && moduleEnabled && suggestedReady;
        boolean inProgress = player != null && (forcingUse || offhandActive || useQueued || switcher.isRestoring()
                || semiArmed && player.tickCount <= semiDeadlineTick);
        ItemStack display = inProgress && !consumingStack.isEmpty() ? consumingStack : suggestedStack;
        boolean linger = player != null && player.tickCount < displayUntilTick;
        if (display.isEmpty() && linger) display = consumingStack;
        boolean semi = settings.consumeControl().get() == ConsumeControl.SEMI;
        float cooldown = player == null ? 0.0f
                : Math.max(0.0f, Math.min(1.0f,
                    (lockedUntilTick - player.tickCount) / (float) Math.max(1, settings.consumeCooldown().get())));
        float progress = inProgress && player != null && player.isUsingItem()
                ? Math.min(1.0f, player.getTicksUsingItem() / 32.0f) : 0.0f;
        return new HudActionRegistry.State(display, ready || inProgress || linger,
                inProgress, ready && semi && !inProgress, cooldown, inProgress, progress);
    }

    // ---- per tick ------------------------------------------------------------------------------------------

    /** Notices that a use ended (cooldown, apple guard) or never started, and winds the state down. */
    public void observeUse(LocalPlayer player) {
        if (useObserved && !player.isUsingItem()) {
            lockedUntilTick = Math.max(lockedUntilTick, player.tickCount
                    + (gappleWasEnchanted ? Math.max(120, settings.consumeCooldown().get()) : settings.consumeCooldown().get()));
            if (gappleConsumption) appleGuardUntilTick = Math.max(
                    appleGuardUntilTick, player.tickCount + (gappleWasEnchanted ? 160 : 80));
            displayUntilTick = Math.max(displayUntilTick, player.tickCount + 12);
            stop(player);
            semiArmed = false;
            useObserved = false;
            requestTick = -1;
        }
        if (requestTick >= 0 && !useObserved && !player.isUsingItem()
                && player.tickCount - requestTick > REQUEST_TIMEOUT_TICKS) {
            lockedUntilTick = Math.max(lockedUntilTick, player.tickCount + settings.consumeCooldown().get());
            stop(player);
            semiArmed = false;
            requestTick = -1;
        }
    }

    /**
     * Danger: abandon an offhand meal and let go of anything that would keep the totem out. The restore of the main
     * hand is only requested here; the switcher performs it at the start of a later tick, never in this window.
     */
    public void yieldToDanger(LocalPlayer player) {
        if (offhandActive) stop(player);
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            player.releaseUsingItem();
        }
        if (forcingUse && !items.isTotemInOffhand(player)) {
            if (player.isUsingItem()) player.releaseUsingItem();
            stop(player);
        }
    }

    public boolean holdsOffhandTurn() {
        return offhandActive;
    }

    /** The offhand swap this meal waited for never happened. */
    public void abandonOffhandTurn() {
        offhandActive = false;
    }

    public void clearSuggestionIfIdle() {
        if (!semiArmed && !forcingUse && !offhandActive) clearSuggestion();
    }

    /** Suggestion, semi-auto arming and the actual consumption for this tick. */
    public void tick(LocalPlayer player, boolean danger) {
        updateSuggestion(player, danger);

        boolean consumptionEnabled = settings.autoConsume().get()
                && settings.consumeControl().get() != ConsumeControl.DISABLED;
        if (consumptionEnabled && settings.consumeControl().get() == ConsumeControl.SEMI) {
            if (actionPressed.getAsBoolean() && canOfferConsumption(player, danger)) {
                semiArmed = true;
                semiDeadlineTick = player.tickCount + 80;
            }
            if (semiArmed && player.tickCount > semiDeadlineTick && !forcingUse && !player.isUsingItem()) {
                semiArmed = false;
            }
        }
        if (consumptionEnabled && (settings.consumeControl().get() == ConsumeControl.AUTO || semiArmed)) {
            updateConsumption(player, danger);
        } else if (forcingUse || switcher.needsRestore()) {
            stop(player);
        }
        if (!consumptionEnabled) semiArmed = false;
        if (!consumptionEnabled && useQueued) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(owner, InventoryActionKind.USE_ITEM);
            useQueued = false;
        }
    }

    // ---- consumption ---------------------------------------------------------------------------------------

    private void updateSuggestion(LocalPlayer player, boolean danger) {
        suggestedReady = settings.autoConsume().get() && settings.consumeControl().get() != ConsumeControl.DISABLED
                && canOfferConsumption(player, danger);
        ItemChoice choice = suggestedReady ? items.selectConsumable(player, appleGuardUntilTick) : null;
        if (choice != null) suggestedStack = choice.stack();
        else if (!forcingUse && !offhandActive && !semiArmed
                && player.tickCount >= displayUntilTick) suggestedStack = ItemStack.EMPTY;
    }

    private void clearSuggestion() {
        suggestedReady = false;
        suggestedStack = ItemStack.EMPTY;
    }

    private boolean canOfferConsumption(LocalPlayer player, boolean danger) {
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()
                || player.isSpectator() || player.getAbilities().instabuild
                || danger && settings.consumeHand().get() != ConsumeHand.MAIN_HAND
                || player.tickCount < lockedUntilTick || player.isUsingItem()
                || swapper.isPending() || switcher.isInventoryBusy()) return false;
        if (combatBlocksConsumption(player)) return false;
        return items.selectConsumable(player, appleGuardUntilTick) != null;
    }

    private boolean combatBlocksConsumption(LocalPlayer player) {
        return threats.isPvpContext(player)
                && (settings.consumeCombat().get() == CombatConsume.OUT_OF_COMBAT
                || settings.consumeCombat().get() == CombatConsume.EMERGENCY_ONLY
                && OffhandThreats.effectiveHealth(player) > settings.foodEmergencyHealth().get());
    }

    private void updateConsumption(LocalPlayer player, boolean danger) {
        if (player.isSpectator() || player.getAbilities().instabuild) {
            stop(player);
            return;
        }
        if (player.isUsingItem()) {
            if (forcingUse && FoodUtil.isFood(player.getUseItem())) {
                useObserved = true;
                lastUseObservedTick = player.tickCount;
                return;
            }
            if (forcingUse) stop(player);
            return;
        }
        if (requestTick >= 0 && !useObserved && player.tickCount - requestTick <= REQUEST_TIMEOUT_TICKS) return;
        if (useQueued) return;
        if (player.tickCount < lockedUntilTick) {
            if (forcingUse) stop(player);
            return;
        }
        boolean combat = threats.isPvpContext(player);
        if (combatBlocksConsumption(player)) {
            if (forcingUse) stop(player);
            return;
        }
        ItemChoice choice = items.selectConsumable(player, appleGuardUntilTick);
        if (choice == null) {
            if (forcingUse) stop(player);
            offhandActive = false;
            semiArmed = false;
            return;
        }
        if (danger && settings.consumeHand().get() != ConsumeHand.MAIN_HAND) {
            offhandActive = false;
            semiArmed = false;
            return;
        }
        boolean useOffhand = !danger && (settings.consumeHand().get() == ConsumeHand.OFF_HAND
                || settings.consumeHand().get() == ConsumeHand.AUTO && !combat);
        if (useOffhand) {
            if (!choice.matches(player.getOffhandItem())) {
                if (!offhandActive && choice.slot() >= 0) {
                    offhandActive = swapper.request(choice.slot(), false);
                }
                return;
            }
            offhandActive = true;
            if (!items.isItemUsable(player, player.getOffhandItem())) return;
            queueUse(player.getOffhandItem().copy(), InteractionHand.OFF_HAND);
            return;
        }
        if (offhandActive) {
            offhandActive = false;
            return;
        }
        if (switcher.isHoldingUnexpectedSwap() && !choice.matches(player.getMainHandItem())) {
            stop(player);
            return;
        }
        MainhandSwitcher.State mainhand = switcher.ensure(player, choice.slot(), choice.stack());
        if (mainhand == MainhandSwitcher.State.PENDING) return;
        if (mainhand == MainhandSwitcher.State.FAILED) {
            stop(player);
            return;
        }
        ItemStack active = player.getMainHandItem();
        if (!choice.matches(active) || !items.isItemUsable(player, active)) {
            stop(player);
            return;
        }
        queueUse(active.copy(), InteractionHand.MAIN_HAND);
    }

    private void queueUse(ItemStack display, InteractionHand hand) {
        if (useQueued || display.isEmpty()) return;
        useQueued = true;
        consumingStack = display.copy();
        boolean accepted = InventorySwap.INSTANCE.enqueueInventoryAction(owner, InventoryActionKind.USE_ITEM,
                false,
                () -> {
                    LocalPlayer player = mc.player;
                    return enabled.getAsBoolean() && player != null && !player.isUsingItem()
                            && player.tickCount >= lockedUntilTick
                            && settings.autoConsume().get()
                            && settings.consumeControl().get() != ConsumeControl.DISABLED
                            && (settings.consumeControl().get() == ConsumeControl.AUTO || semiArmed)
                            && !swapper.isPending() && !switcher.isSwapInFlight()
                            && ItemStack.isSameItemSameComponents(
                                    hand == InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getMainHandItem(),
                                    display)
                            && (!threats.shouldHoldTotem(player) || hand == InteractionHand.MAIN_HAND);
                },
                () -> {
                    useQueued = false;
                    LocalPlayer player = mc.player;
                    if (player != null) startUse(player, display, hand);
                },
                () -> useQueued = false);
        if (!accepted) useQueued = false;
    }

    private void startUse(LocalPlayer player, ItemStack display, InteractionHand hand) {
        if (requestTick >= 0 && player.tickCount - requestTick <= REQUEST_TIMEOUT_TICKS) return;
        forcingUse = true;
        lastUseObservedTick = player.tickCount;
        InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
        if (result.consumesAction()) {
            requestTick = player.tickCount;
            consumingStack = display.copy();
            displayUntilTick = player.tickCount + 16;
            player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
            gappleConsumption = OffhandItems.isGapple(display);
            gappleWasEnchanted = display.is(Items.ENCHANTED_GOLDEN_APPLE);
            if (gappleConsumption) appleGuardUntilTick = Math.max(
                    appleGuardUntilTick, player.tickCount + (gappleWasEnchanted ? 160 : 80));
            Itemizer.showAutoEat(display);
        } else {
            stop(player);
        }
    }

    /**
     * Ends the automatic use: forgets the queued use and asks the switcher to put the hands back. Nothing is sent
     * here; the restore happens at the start of a later tick, after any release of this tick's window.
     */
    private void stop(LocalPlayer player) {
        forcingUse = false;
        offhandActive = false;
        if (useQueued) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(owner, InventoryActionKind.USE_ITEM);
            useQueued = false;
        }
        switcher.requestRestore(lastUseObservedTick >= 0 ? lastUseObservedTick + 2 : 0);
        lastUseObservedTick = -1;
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------

    /** Module disabled: stop the use and put the hands back as far as possible right now. */
    public void shutdown(LocalPlayer player) {
        forcingUse = false;
        offhandActive = false;
        useQueued = false;
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(owner);
        switcher.shutdown(player);
        reset();
        semiArmed = false;
        clearSuggestion();
    }

    /** Forgets all consume state (pause, world change) without sending anything. */
    public void reset() {
        forcingUse = false;
        offhandActive = false;
        useQueued = false;
        lastUseObservedTick = -1;
        requestTick = -1;
        consumingStack = ItemStack.EMPTY;
        displayUntilTick = -1;
        appleGuardUntilTick = -1;
    }

    public void resetPaused() {
        reset();
        switcher.reset();
        semiArmed = false;
        clearSuggestion();
    }
}
