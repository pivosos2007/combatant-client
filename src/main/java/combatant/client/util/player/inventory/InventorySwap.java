/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.mixins.accessors.SlotAccessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class InventorySwap {
    public static final InventorySwap INSTANCE = new InventorySwap();

    private static final int OFFHAND_SCREEN_SLOT = 45;
    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<QueuedAction> strictQueue = new ArrayDeque<>();
    private int lastServerSelectedSlot = -1;
    private int savedSelectedSlot = -1;
    private int internalSwapDepth;
    private HotbarLease hotbarLease;
    private InventorySwapPolicy defaultPolicy = InventorySwapPolicy.NONE;
    private InventorySearchScope defaultScope = InventorySearchScope.FULL;
    private InventorySwapVisibility defaultVisibility = InventorySwapVisibility.SILENT;
    private boolean restoreByDefault = true;
    private boolean preferHotbar = true;
    private int legitWaitTicks = 2;
    private int strictInventoryWaitTicks = 1;
    private int strictMovementLockTicks = 2;
    private int packetTick;
    private int lastUseItemTick = Integer.MIN_VALUE;
    private int lastEntityActionTick = Integer.MIN_VALUE;
    private int lastBlockActionTick = Integer.MIN_VALUE;
    private boolean lastInputMoving;
    private int movementLockTicks;
    private boolean sendingLockedInput;

    private InventorySwap() {
    }

    public static int mapInventoryToScreenSlot(int inventorySlot) {
        LocalPlayer player = INSTANCE.mc.player;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        Container inventory = player != null ? player.getInventory() : null;
        return mapInventoryToScreenSlot(handler, inventory, inventorySlot);
    }

    public static int mapInventoryToScreenSlot(AbstractContainerMenu handler, Container playerInventory, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= 41) return -1;
        if (handler != null && playerInventory != null) {
            for (Slot slot : handler.slots) {
                if (slot == null || slot.container != playerInventory) continue;
                if (((SlotAccessor) slot).combatant$getIndex() == inventorySlot) {
                    return slot.index;
                }
            }
        }
        return legacyPlayerScreenSlot(inventorySlot);
    }

    public static int mapHotbarToScreenSlot(int hotbarSlot) {
        if (!isHotbarSlot(hotbarSlot)) return -1;
        return mapInventoryToScreenSlot(hotbarSlot);
    }

    public static int playerInventoryStart(AbstractContainerMenu handler) {
        if (handler == null) return -1;
        LocalPlayer player = INSTANCE.mc.player;
        Container inventory = player != null ? player.getInventory() : null;
        if (handler != null && inventory != null) {
            int min = Integer.MAX_VALUE;
            for (Slot slot : handler.slots) {
                if (slot == null || slot.container != inventory) continue;
                int index = ((SlotAccessor) slot).combatant$getIndex();
                if (index >= 9 && index < 36) {
                    min = Math.min(min, slot.index);
                }
            }
            if (min != Integer.MAX_VALUE) return min;
        }
        return handler.slots.size() - 36;
    }

    public static int hotbarStart(AbstractContainerMenu handler) {
        if (handler == null) return -1;
        LocalPlayer player = INSTANCE.mc.player;
        Container inventory = player != null ? player.getInventory() : null;
        if (handler != null && inventory != null) {
            int min = Integer.MAX_VALUE;
            for (Slot slot : handler.slots) {
                if (slot == null || slot.container != inventory) continue;
                int index = ((SlotAccessor) slot).combatant$getIndex();
                if (index >= 0 && index < 9) {
                    min = Math.min(min, slot.index);
                }
            }
            if (min != Integer.MAX_VALUE) return min;
        }
        return playerInventoryStart(handler) + 27;
    }

    private static int legacyPlayerScreenSlot(int inventorySlot) {
        if (inventorySlot == 40) return OFFHAND_SCREEN_SLOT;
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    private static boolean isSwapButton(int button) {
        return isHotbarSlot(button) || button == OFFHAND_SWAP_BUTTON;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Object packet = event.getPacket();
        if (packet instanceof ServerboundSetCarriedItemPacket selectedSlot) {
            lastServerSelectedSlot = selectedSlot.getSlot();
        } else if (packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket) {
            packetTick++;
        } else if (packet instanceof ServerboundPlayerInputPacket(Input input)) {
            boolean moving = input.forward() || input.backward() || input.left() || input.right();
            if (isMovementLocked() && !sendingLockedInput && moving && mc.getConnection() != null) {
                event.cancel();
                sendingLockedInput = true;
                try {
                    mc.getConnection().send(new ServerboundPlayerInputPacket(
                            new Input(false, false, false, false, false, false, false)
                    ));
                } finally {
                    sendingLockedInput = false;
                }
                lastInputMoving = false;
                return;
            }
            lastInputMoving = moving;
        } else if (packet instanceof ServerboundUseItemPacket) {
            lastUseItemTick = packetTick;
        } else if (packet instanceof ServerboundSwingPacket) {
            // MultiActionsE checks animation while using item; the safety gate uses player.isUsingItem().
        } else if (packet instanceof ServerboundInteractPacket) {
            lastEntityActionTick = packetTick;
        } else if (packet instanceof ServerboundUseItemOnPacket) {
            lastBlockActionTick = packetTick;
        } else if (packet instanceof ServerboundPlayerActionPacket action
                && (action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || action.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)) {
            lastBlockActionTick = packetTick;
        } else if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            // Present for explicit packet classification. Grim C/D safety is evaluated before sending.
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        tick();
        if (movementLockTicks > 0) {
            movementLockTicks--;
        }

        if (strictQueue.isEmpty()) {
            return;
        }

        requestStrictMovementLock();

        QueuedAction next = strictQueue.peekFirst();
        if (next == null) return;

        if (next.waitTicks > 0) {
            next.waitTicks--;
            return;
        }

        if (!next.isSafe()) {
            next.waitTicks = 1;
            return;
        }

        strictQueue.removeFirst();
        runGuarded(next.action);

        if (strictQueue.isEmpty()) {
            requestMovementLock(1);
        }
    }

    @EventHandler(priority = -10000)
    private void onMovementInput(MovementInputEvent event) {
        if (!isMovementLocked()) return;

        event.setForward(false);
        event.setBackward(false);
        event.setLeft(false);
        event.setRight(false);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
        lastInputMoving = false;
    }

    @EventHandler(priority = -10000)
    private void onSprintControl(SprintControlEvent event) {
        if (isMovementLocked()) {
            event.setSprint(false);
        }
    }

    public boolean isInternalSwap() {
        return internalSwapDepth > 0;
    }

    public boolean hasPendingStrictActions() {
        return !strictQueue.isEmpty();
    }

    public void clearPendingStrictActions() {
        strictQueue.clear();
        movementLockTicks = 0;
    }

    public boolean canRunStrict(InventoryActionKind kind) {
        return isSafeFor(kind, InventorySwapPolicy.GRIM_STRICT);
    }

    public InventorySwapPolicy defaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(InventorySwapPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy != null ? defaultPolicy : InventorySwapPolicy.NONE;
    }

    public InventorySearchScope defaultScope() {
        return defaultScope;
    }

    public void setDefaultScope(InventorySearchScope defaultScope) {
        this.defaultScope = defaultScope != null ? defaultScope : InventorySearchScope.FULL;
    }

    public InventorySwapVisibility defaultVisibility() {
        return defaultVisibility;
    }

    public void setDefaultVisibility(InventorySwapVisibility defaultVisibility) {
        this.defaultVisibility = defaultVisibility != null ? defaultVisibility : InventorySwapVisibility.SILENT;
    }

    public boolean restoreByDefault() {
        return restoreByDefault;
    }

    public void setRestoreByDefault(boolean restoreByDefault) {
        this.restoreByDefault = restoreByDefault;
    }

    public boolean preferHotbar() {
        return preferHotbar;
    }

    public void setPreferHotbar(boolean preferHotbar) {
        this.preferHotbar = preferHotbar;
    }

    public int legitWaitTicks() {
        return legitWaitTicks;
    }

    public void setLegitWaitTicks(int legitWaitTicks) {
        this.legitWaitTicks = Math.max(0, legitWaitTicks);
    }

    public int strictInventoryWaitTicks() {
        return strictInventoryWaitTicks;
    }

    public void setStrictInventoryWaitTicks(int strictInventoryWaitTicks) {
        this.strictInventoryWaitTicks = Math.max(0, strictInventoryWaitTicks);
    }

    public int strictMovementLockTicks() {
        return strictMovementLockTicks;
    }

    public void setStrictMovementLockTicks(int strictMovementLockTicks) {
        this.strictMovementLockTicks = Math.max(0, strictMovementLockTicks);
    }

    public void resetDefaults() {
        defaultPolicy = InventorySwapPolicy.NONE;
        defaultScope = InventorySearchScope.FULL;
        defaultVisibility = InventorySwapVisibility.SILENT;
        restoreByDefault = true;
        preferHotbar = true;
        legitWaitTicks = 2;
        strictInventoryWaitTicks = 1;
        strictMovementLockTicks = 2;
    }

    public int serverSelectedSlot() {
        HotbarLease active = activeLease();
        if (active != null) return active.enforcedSlot;
        if (lastServerSelectedSlot >= 0 && lastServerSelectedSlot <= 8) return lastServerSelectedSlot;
        return clientSelectedSlot();
    }

    public int effectiveSelectedSlot() {
        HotbarLease active = activeLease();
        return active != null ? active.enforcedSlot : clientSelectedSlot();
    }

    public int clientSelectedSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return 0;
        return ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
    }

    public boolean isHotbarLeased() {
        return activeLease() != null;
    }

    public boolean isHotbarLeasedBy(Object owner) {
        HotbarLease active = activeLease();
        return active != null && active.owner == owner;
    }

    public boolean leaseHotbar(Object owner, int slot, int ticksUntilReset) {
        LocalPlayer player = mc.player;
        if (player == null || !isHotbarSlot(slot)) return false;

        int resetAfterAge = player.tickCount + Math.max(0, ticksUntilReset) + 1;
        hotbarLease = new HotbarLease(player.getUUID(), owner, slot, clientSelectedSlot(), resetAfterAge);
        if (!sendSelectedSlotIfNeeded(slot)) {
            hotbarLease = null;
            return false;
        }
        return true;
    }

    public void releaseHotbar(Object owner) {
        HotbarLease active = activeLease();
        if (active != null && active.owner == owner) {
            clearLease(active, true);
        }
    }

    public void tick() {
        activeLease();
    }

    public void saveSelectedSlot() {
        savedSelectedSlot = clientSelectedSlot();
    }

    public void restoreSavedSelectedSlot() {
        if (isHotbarSlot(savedSelectedSlot)) {
            selectHotbar(savedSelectedSlot);
        }
        savedSelectedSlot = -1;
    }

    public boolean selectHotbar(int slot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !isHotbarSlot(slot)) return false;

        int current = clientSelectedSlot();
        if (current != slot) {
            ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(slot);
        }

        syncInteractionManagerSlot(slot);
        if (lastServerSelectedSlot != slot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            lastServerSelectedSlot = slot;
        }
        return true;
    }

    public boolean sendSelectedSlotIfNeeded(int slot) {
        if (mc.player == null || mc.getConnection() == null || !isHotbarSlot(slot)) return false;
        if (lastServerSelectedSlot != slot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            lastServerSelectedSlot = slot;
        }
        syncInteractionManagerSlot(slot);
        return true;
    }

    public boolean clickSwap(int containerSlot) {
        return clickSwap(containerSlot, clientSelectedSlot());
    }

    public boolean clickSwap(int containerSlot, int hotbarButton) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (!isSwapButton(hotbarButton) || containerSlot < 0) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, containerSlot, hotbarButton, ContainerInput.SWAP, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    public boolean pickupSwap(int slotA, int slotB) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (slotA < 0 || slotB < 0 || slotA == slotB) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, slotA, 0, ContainerInput.PICKUP, player);
            interaction.handleContainerInput(handler.containerId, slotB, 0, ContainerInput.PICKUP, player);
            interaction.handleContainerInput(handler.containerId, slotA, 0, ContainerInput.PICKUP, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    public boolean swapScreenSlots(int slotA, int slotB) {
        return command(InventoryActionKind.INVENTORY_CLICK, () -> pickupSwap(slotA, slotB));
    }

    public boolean swapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
        if (!isHotbarSlot(hotbarSlot) || inventorySlot < 0 || inventorySlot >= 36) return false;
        if (inventorySlot == hotbarSlot) return selectHotbar(hotbarSlot);

        int sourceScreenSlot = mapInventoryToScreenSlot(inventorySlot);
        int targetScreenSlot = mapHotbarToScreenSlot(hotbarSlot);
        return command(InventoryActionKind.INVENTORY_CLICK, () -> {
            pickupSwap(sourceScreenSlot, targetScreenSlot);
            selectHotbar(hotbarSlot);
        });
    }

    public boolean swapInventoryToOffhand(int inventorySlot) {
        return swapInventoryToOffhand(inventorySlot, null);
    }

    public boolean swapInventoryToOffhand(int inventorySlot, Runnable afterSwap) {
        if (inventorySlot < 0 || inventorySlot >= 36) return false;
        int sourceScreenSlot = mapInventoryToScreenSlot(inventorySlot);
        return command(InventoryActionKind.INVENTORY_CLICK, () -> {
            clickSwap(sourceScreenSlot, OFFHAND_SWAP_BUTTON);
            if (afterSwap != null) {
                afterSwap.run();
            }
        });
    }

    public boolean dropStack(Slot slot) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null || slot == null) return false;
        int slotId = slot.index;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;

        return command(InventoryActionKind.INVENTORY_CLICK, () -> {
            beginInternalSwap();
            try {
                interaction.handleContainerInput(handler.containerId, slotId, 1, ContainerInput.THROW, player);
            } finally {
                endInternalSwap();
            }
        });
    }

    public int quickMoveMatchingFromContainer(Set<String> itemIds) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null || itemIds == null || itemIds.isEmpty()) return 0;

        int moved = 0;
        int playerStart = playerInventoryStart(handler);
        for (int slotId = 0; slotId < playerStart; slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null || !slot.hasItem()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString().toLowerCase();
            if (!itemIds.contains(itemId)) continue;

            int finalSlotId = slotId;
            if (command(InventoryActionKind.INVENTORY_CLICK, () -> {
                beginInternalSwap();
                try {
                    interaction.handleContainerInput(handler.containerId, finalSlotId, 0, ContainerInput.QUICK_MOVE, player);
                } finally {
                    endInternalSwap();
                }
            })) {
                moved++;
            }
        }
        return moved;
    }

    public boolean withSwap(SlotResult result, InventorySwapMode mode, Runnable action) {
        if (result == null || !result.found()) return false;
        return withSwap(result.slot(), mode, action);
    }

    public boolean withSwap(SlotResult result, InventorySwapMode mode, InventorySwapPolicy policy,
                            InventoryActionKind kind, Runnable action) {
        if (result == null || !result.found()) return false;
        return withSwap(result.slot(), mode, policy, kind, action);
    }

    public boolean withSwap(int slot, InventorySwapMode mode, Runnable action) {
        if (action == null || mode == null) return false;

        beginInternalSwap();
        try {
            return switch (mode) {
                case NORMAL -> runNormal(slot, action);
                case SILENT -> runSilent(slot, action);
                case INVENTORY_NORMAL -> runInventoryNormal(slot, action);
                case INVENTORY_SILENT -> runInventorySilent(slot, action);
                case SILENT_FULL ->
                        withSwap(slot, isHotbarSlot(slot) ? InventorySwapMode.SILENT : InventorySwapMode.INVENTORY_SILENT, action);
                case NORMAL_FULL ->
                        withSwap(slot, isHotbarSlot(slot) ? InventorySwapMode.NORMAL : InventorySwapMode.INVENTORY_NORMAL, action);
            };
        } finally {
            endInternalSwap();
        }
    }

    public boolean withSwap(int slot, InventorySwapMode mode, InventorySwapPolicy policy,
                            InventoryActionKind kind, Runnable action) {
        if (policy == null || policy == InventorySwapPolicy.NONE) {
            return withSwap(slot, mode, action);
        }
        if (action == null || mode == null) return false;

        Runnable wrapped = () -> withSwap(slot, mode, action);
        BooleanSupplier safety = () -> isSafeForSwap(slot, mode, kind, policy);
        if (safety.getAsBoolean()) {
            runGuarded(wrapped);
            return true;
        }

        strictQueue.addLast(new QueuedAction(kind, policy, wrapped, initialWaitTicks(policy, kind), safety));
        requestStrictMovementLock();
        return true;
    }

    public SlotResult findSlotForMode(Predicate<ItemStack> predicate, InventorySwapMode mode) {
        if (predicate == null || mode == null) return SlotResult.empty();
        return switch (mode) {
            case NORMAL, SILENT -> findHotbar(predicate);
            case INVENTORY_NORMAL, INVENTORY_SILENT -> findInventory(predicate);
            case SILENT_FULL, NORMAL_FULL -> {
                SlotResult hotbar = findHotbar(predicate);
                yield hotbar.found() ? hotbar : findInventory(predicate);
            }
        };
    }

    public boolean withFoundSlot(Predicate<ItemStack> predicate, InventorySwapMode mode, Runnable action) {
        SlotResult result = findSlotForMode(predicate, mode);
        return result.found() && withSwap(result, mode, action);
    }

    public boolean withFoundSlot(Predicate<ItemStack> predicate, InventorySwapMode mode, InventorySwapPolicy policy,
                                 InventoryActionKind kind, Runnable action) {
        SlotResult result = findSlotForMode(predicate, mode);
        return result.found() && withSwap(result, mode, policy, kind, action);
    }

    public boolean execute(InventorySwapRequest request) {
        if (request == null || request.predicate() == null || request.action() == null) return false;

        InventorySearchScope scope = resolveScope(request);
        InventorySwapMode mode = resolveMode(request, scope);
        SlotResult result = findSlotForRequest(request, mode, scope);
        if (!result.found()) return false;

        InventorySwapPolicy policy = request.policy(defaultPolicy);
        return withSwap(result, mode, policy, request.actionKind(), request.action());
    }

    public boolean command(InventorySwapRequest request) {
        return execute(request);
    }

    public boolean command(InventoryActionKind actionKind, Runnable action) {
        return command(actionKind, defaultPolicy, action);
    }

    public boolean command(InventoryActionKind actionKind, InventorySwapPolicy policy, Runnable action) {
        if (action == null) return false;
        InventoryActionKind kind = actionKind != null ? actionKind : InventoryActionKind.GENERIC;
        InventorySwapPolicy resolvedPolicy = policy != null ? policy : defaultPolicy;

        if (resolvedPolicy == InventorySwapPolicy.NONE || isSafeFor(kind, resolvedPolicy)) {
            runGuarded(action);
            return true;
        }

        strictQueue.addLast(new QueuedAction(
                kind,
                resolvedPolicy,
                action,
                initialWaitTicks(resolvedPolicy, kind),
                () -> isSafeFor(kind, resolvedPolicy)
        ));
        requestStrictMovementLock();
        return true;
    }

    public boolean command(Predicate<ItemStack> predicate, InventoryActionKind actionKind, Runnable action) {
        return execute(predicate, actionKind, action);
    }

    public boolean command(Predicate<ItemStack> predicate, Runnable action) {
        return execute(InventorySwapRequest.builder(predicate, action).build());
    }

    public boolean execute(Predicate<ItemStack> predicate, InventoryActionKind actionKind, Runnable action) {
        return execute(InventorySwapRequest.builder(predicate, action)
                .actionKind(actionKind)
                .build());
    }

    public boolean executeSilent(Predicate<ItemStack> predicate, InventoryActionKind actionKind, Runnable action) {
        return execute(InventorySwapRequest.builder(predicate, action)
                .scope(InventorySearchScope.FULL)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(true)
                .actionKind(actionKind)
                .build());
    }

    public boolean executeNormal(Predicate<ItemStack> predicate, InventoryActionKind actionKind, Runnable action) {
        return execute(InventorySwapRequest.builder(predicate, action)
                .scope(InventorySearchScope.FULL)
                .visibility(InventorySwapVisibility.NORMAL)
                .restore(false)
                .actionKind(actionKind)
                .build());
    }

    public boolean closeHandledScreen(InventorySwapPolicy policy) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || player.containerMenu == null) return false;

        Runnable close = () -> mc.getConnection().send(
                new ServerboundContainerClosePacket(player.containerMenu.containerId)
        );

        if (policy == null || policy == InventorySwapPolicy.NONE
                || isSafeFor(InventoryActionKind.INVENTORY_CLOSE, policy)) {
            runGuarded(close);
            return true;
        }

        strictQueue.addLast(new QueuedAction(
                InventoryActionKind.INVENTORY_CLOSE,
                policy,
                close,
                initialWaitTicks(policy, InventoryActionKind.INVENTORY_CLOSE),
                () -> isSafeFor(InventoryActionKind.INVENTORY_CLOSE, policy)
        ));
        requestStrictMovementLock();
        return true;
    }

    public InteractionResult useItem(InteractionHand hand) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        if (player == null || interaction == null || hand == null) return InteractionResult.FAIL;
        return interaction.useItem(player, hand);
    }

    public boolean useItemWith(Predicate<ItemStack> predicate, InventorySwapMode mode, InteractionHand hand, boolean swing) {
        return withFoundSlot(predicate, mode, () -> {
            InteractionResult result = useItem(hand);
            if (swing && result.consumesAction() && mc.player != null) {
                mc.player.swing(hand);
            }
        });
    }

    public boolean useItemWith(Predicate<ItemStack> predicate, InventorySwapMode mode, InventorySwapPolicy policy,
                               InteractionHand hand, boolean swing) {
        return withFoundSlot(predicate, mode, policy, InventoryActionKind.USE_ITEM, () -> {
            InteractionResult result = useItem(hand);
            if (swing && result.consumesAction() && mc.player != null) {
                swingHand(hand, policy);
            }
        });
    }

    public boolean swingHand(InteractionHand hand, InventorySwapPolicy policy) {
        if (mc.player == null || hand == null) return false;
        Runnable swing = () -> mc.player.swing(hand);
        if (policy == null || policy == InventorySwapPolicy.NONE || isSafeFor(InventoryActionKind.SWING, policy)) {
            runGuarded(swing);
            return true;
        }

        strictQueue.addLast(new QueuedAction(
                InventoryActionKind.SWING,
                policy,
                swing,
                initialWaitTicks(policy, InventoryActionKind.SWING),
                () -> isSafeFor(InventoryActionKind.SWING, policy)
        ));
        requestStrictMovementLock();
        return true;
    }

    public SlotResult findHotbar(Predicate<ItemStack> predicate) {
        LocalPlayer player = mc.player;
        if (player == null || predicate == null) return SlotResult.empty();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return new SlotResult(slot, true, stack);
            }
        }

        return SlotResult.empty();
    }

    public SlotResult findInventory(Predicate<ItemStack> predicate) {
        LocalPlayer player = mc.player;
        if (player == null || predicate == null) return SlotResult.empty();

        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return new SlotResult(mapInventoryToScreenSlot(slot), true, stack);
            }
        }

        return SlotResult.empty();
    }

    private boolean runNormal(int slot, Runnable action) {
        if (!isHotbarSlot(slot)) return false;
        if (!selectHotbar(slot)) return false;
        action.run();
        return true;
    }

    private boolean runSilent(int slot, Runnable action) {
        if (!isHotbarSlot(slot)) return false;
        int previous = clientSelectedSlot();

        if (lastServerSelectedSlot != slot) {
            if (!selectHotbar(slot)) return false;
        }

        try {
            action.run();
            return true;
        } finally {
            if (lastServerSelectedSlot != previous) {
                selectHotbar(previous);
            }
        }
    }

    private boolean runInventoryNormal(int slot, Runnable action) {
        if (isHotbarSlot(slot)) return runNormal(slot, action);
        if (!clickSwap(slot)) return false;
        action.run();
        return true;
    }

    private boolean runInventorySilent(int slot, Runnable action) {
        if (isHotbarSlot(slot)) return runSilent(slot, action);
        int hotbarButton = clientSelectedSlot();
        if (!clickSwap(slot, hotbarButton)) return false;

        try {
            action.run();
            return true;
        } finally {
            clickSwap(slot, hotbarButton);
        }
    }

    private InventorySearchScope resolveScope(InventorySwapRequest request) {
        return request.scope() != null ? request.scope() : defaultScope;
    }

    private InventorySwapVisibility resolveVisibility(InventorySwapRequest request) {
        return request.visibility() != null ? request.visibility() : defaultVisibility;
    }

    private InventorySwapMode resolveMode(InventorySwapRequest request, InventorySearchScope scope) {
        InventorySearchScope safeScope = scope != null ? scope : defaultScope;
        boolean restore = request.restore(restoreByDefault);
        boolean silent = resolveVisibility(request) == InventorySwapVisibility.SILENT || restore;

        return switch (safeScope) {
            case HOTBAR -> silent ? InventorySwapMode.SILENT : InventorySwapMode.NORMAL;
            case INVENTORY -> silent ? InventorySwapMode.INVENTORY_SILENT : InventorySwapMode.INVENTORY_NORMAL;
            case FULL -> silent ? InventorySwapMode.SILENT_FULL : InventorySwapMode.NORMAL_FULL;
        };
    }

    private SlotResult findSlotForRequest(InventorySwapRequest request, InventorySwapMode mode, InventorySearchScope scope) {
        if (scope != InventorySearchScope.FULL || preferHotbar) {
            return findSlotForMode(request.predicate(), mode);
        }

        SlotResult inventory = findInventory(request.predicate());
        return inventory.found() ? inventory : findHotbar(request.predicate());
    }

    private boolean isSafeFor(InventoryActionKind kind, InventorySwapPolicy policy) {
        if (policy == null || policy == InventorySwapPolicy.NONE) return true;
        LocalPlayer player = mc.player;
        if (player == null) return false;

        InventoryActionKind safeKind = kind != null ? kind : InventoryActionKind.GENERIC;

        if ((safeKind == InventoryActionKind.INVENTORY_CLICK || safeKind == InventoryActionKind.INVENTORY_CLOSE)
                && isGrimMovingForInventory(player)) {
            return false;
        }

        if ((safeKind == InventoryActionKind.ATTACK_ENTITY || safeKind == InventoryActionKind.SWING)
                && player.isUsingItem()) {
            return false;
        }

        if ((safeKind == InventoryActionKind.ATTACK_ENTITY || safeKind == InventoryActionKind.ENTITY_INTERACT)
                && lastBlockActionTick == packetTick) {
            return false;
        }

        if ((safeKind == InventoryActionKind.BLOCK_INTERACT || safeKind == InventoryActionKind.BLOCK_BREAK)
                && lastEntityActionTick == packetTick) {
            return false;
        }

        return safeKind != InventoryActionKind.SWING || lastUseItemTick != packetTick;
    }

    private boolean isSafeForSwap(int slot, InventorySwapMode mode, InventoryActionKind kind, InventorySwapPolicy policy) {
        if (!isSafeFor(kind, policy)) return false;
        if (requiresInventoryClick(slot, mode)) {
            return isSafeFor(InventoryActionKind.INVENTORY_CLICK, policy);
        }
        return true;
    }

    private boolean requiresInventoryClick(int slot, InventorySwapMode mode) {
        if (mode == null) return false;
        return switch (mode) {
            case INVENTORY_NORMAL, INVENTORY_SILENT -> !isHotbarSlot(slot);
            case SILENT_FULL, NORMAL_FULL -> !isHotbarSlot(slot);
            case NORMAL, SILENT -> false;
        };
    }

    private int initialWaitTicks(InventorySwapPolicy policy, InventoryActionKind kind) {
        if (policy == InventorySwapPolicy.LEGIT) return legitWaitTicks;
        if (kind == InventoryActionKind.INVENTORY_CLICK || kind == InventoryActionKind.INVENTORY_CLOSE) {
            return strictInventoryWaitTicks;
        }
        return 0;
    }

    private boolean isGrimMovingForInventory(LocalPlayer player) {
        if (isMovementLocked()) return false;
        if (player.isSprinting() && (!player.isSwimming() || player.onGround())) return true;
        if (lastInputMoving) return true;
        if (player.input == null) return false;
        Input input = player.input.keyPresses;
        if (input == null) return false;
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private void runGuarded(Runnable action) {
        beginInternalSwap();
        try {
            action.run();
        } finally {
            endInternalSwap();
        }
    }

    private void requestStrictMovementLock() {
        requestMovementLock(strictMovementLockTicks);
    }

    private void requestMovementLock(int ticks) {
        movementLockTicks = Math.max(movementLockTicks, Math.max(0, ticks));
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private boolean isMovementLocked() {
        return movementLockTicks > 0 || !strictQueue.isEmpty();
    }

    private HotbarLease activeLease() {
        HotbarLease current = hotbarLease;
        if (current == null) return null;

        LocalPlayer player = mc.player;
        if (player == null) {
            hotbarLease = null;
            return null;
        }

        if (!player.getUUID().equals(current.playerUuid) || player.tickCount >= current.resetAfterAge) {
            clearLease(current, player.getUUID().equals(current.playerUuid));
            return null;
        }

        return current;
    }

    private void clearLease(HotbarLease lease, boolean restoreServerSlot) {
        hotbarLease = null;
        if (!restoreServerSlot) return;

        int currentClientSlot = clientSelectedSlot();
        if (currentClientSlot != lease.enforcedSlot) {
            sendSelectedSlotIfNeeded(currentClientSlot);
        }
    }

    private void beginInternalSwap() {
        internalSwapDepth++;
    }

    private void endInternalSwap() {
        if (internalSwapDepth > 0) internalSwapDepth--;
    }

    private void syncInteractionManagerSlot(int slot) {
        if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$setLastSelectedSlot(slot);
        }
    }

    private record HotbarLease(UUID playerUuid, Object owner, int enforcedSlot, int clientSlot, int resetAfterAge) {
    }

    private static final class QueuedAction {
        private final InventoryActionKind kind;
        private final InventorySwapPolicy policy;
        private final Runnable action;
        private final BooleanSupplier safety;
        private int waitTicks;

        private QueuedAction(InventoryActionKind kind, InventorySwapPolicy policy, Runnable action, int waitTicks,
                             BooleanSupplier safety) {
            this.kind = kind != null ? kind : InventoryActionKind.GENERIC;
            this.policy = policy != null ? policy : InventorySwapPolicy.NONE;
            this.action = action;
            this.safety = safety != null ? safety : () -> true;
            this.waitTicks = Math.max(0, waitTicks);
        }

        private boolean isSafe() {
            return safety.getAsBoolean();
        }
    }
}
