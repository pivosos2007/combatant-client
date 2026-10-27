/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Single entry point for held-slot changes, silent item use and inventory window clicks.
 * <p>
 * Everything here obeys Grim's packet-order rules, tracked by {@link PacketWindow}: a held-slot change is only sent
 * while it neither follows a conflicting action of the same tick window nor follows this tick's movement packet, and
 * window clicks only while the server sees a stationary player. A silent use therefore is "swap, use now, keep the
 * swapped slot until the start of the next client tick, restore there". Operations that are not allowed right now
 * return {@code false} (callers retry next tick) or, for window clicks queued through {@link #command}, run once the
 * window opens.
 */
public final class InventorySwap {
    public static final InventorySwap INSTANCE = new InventorySwap();

    private static final int OFFHAND_SWAP_BUTTON = 40;
    /** Ticks the inputs stay cleared before a blocked window click is retried. */
    private static final int MOVEMENT_LOCK_TICKS = 2;

    private final Minecraft mc = Minecraft.getInstance();
    private final PacketWindow window = PacketWindow.INSTANCE;

    // Serialized, owner-scoped window-click queue. No module implements its own tick jitter.
    private final Deque<QueuedAction> queue = new ArrayDeque<>();
    private int queuedMinTicks = 1;
    private int queuedMaxTicks = 4;
    private UUID queuedPlayerUuid;
    private ClientLevel queuedLevel;

    /** Carried slot the server holds, from our own packets and the server's held-slot updates; -1 unknown. */
    private int lastServerSelectedSlot = -1;
    /** Slot a silent use left the server on until the next tick start restores it; wins over leases and client slot. */
    private int silentSlot = -1;
    /** The server slot is off the desired slot because the restore was not allowed yet (sent at the next safe point). */
    private boolean restorePending;
    private int oneShotDepth;
    private int internalSwapDepth;
    private int clientViewDepth;
    private HotbarLease hotbarLease;
    private InventoryLease inventoryLease;
    private Object inventoryActionOwner;
    private int movementLockTicks;
    private boolean sendingLockedInput;

    private InventorySwap() {
    }

    // ---- slot mapping --------------------------------------------------------------------------------------

    public static int mapInventoryToScreenSlot(int inventorySlot) {
        LocalPlayer player = INSTANCE.mc.player;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        Container inventory = player != null ? player.getInventory() : null;
        return mapInventoryToScreenSlot(handler, inventory, inventorySlot);
    }

    public static int mapInventoryToScreenSlot(AbstractContainerMenu handler, Container playerInventory, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= 41) return -1;
        return InventoryComponentMap.menuSlotFor(handler, playerInventory, inventorySlot);
    }

    public static int mapHotbarToScreenSlot(int hotbarSlot) {
        if (!isHotbarSlot(hotbarSlot)) return -1;
        return mapInventoryToScreenSlot(hotbarSlot);
    }

    public static int playerInventoryStart(AbstractContainerMenu handler) {
        if (handler == null) return -1;
        LocalPlayer player = INSTANCE.mc.player;
        if (player == null) return -1;
        int min = Integer.MAX_VALUE;
        for (int id = 0; id < handler.slots.size(); id++) {
            int inventoryIndex = InventoryComponentMap.playerInventoryIndex(handler, id, player);
            if (inventoryIndex >= 9 && inventoryIndex < 36) min = Math.min(min, id);
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    public static int hotbarStart(AbstractContainerMenu handler) {
        if (handler == null) return -1;
        LocalPlayer player = INSTANCE.mc.player;
        if (player == null) return -1;
        int min = Integer.MAX_VALUE;
        for (int id = 0; id < handler.slots.size(); id++) {
            int inventoryIndex = InventoryComponentMap.playerInventoryIndex(handler, id, player);
            if (inventoryIndex >= 0 && inventoryIndex < 9) min = Math.min(min, id);
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    private static boolean isSwapButton(int button) {
        return isHotbarSlot(button) || button == OFFHAND_SWAP_BUTTON;
    }

    private static boolean isWindowKind(InventoryActionKind kind) {
        return kind == InventoryActionKind.INVENTORY_CLICK || kind == InventoryActionKind.INVENTORY_CLOSE;
    }

    // ---- events --------------------------------------------------------------------------------------------

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Object packet = event.getPacket();
        if (packet instanceof ServerboundSetCarriedItemPacket selectedSlot) {
            lastServerSelectedSlot = selectedSlot.getSlot();
        } else if (packet instanceof ServerboundPlayerInputPacket(Input input)) {
            boolean moving = input.forward() || input.backward() || input.left() || input.right();
            if (isMovementLocked() && !sendingLockedInput && (moving || input.shift()) && mc.getConnection() != null) {
                event.cancel();
                sendingLockedInput = true;
                try {
                    mc.getConnection().send(new ServerboundPlayerInputPacket(
                            new Input(false, false, false, false, false, false, false)
                    ));
                } finally {
                    sendingLockedInput = false;
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Object packet = event.getPacket();
        // The server moves the held slot itself on join, respawn and pick-block (server-side since 1.21.4).
        // Without tracking it, a swap to that slot is skipped as "already selected" and uses the wrong item.
        // Receive fires on the netty thread, so hop to the client thread before touching swap state.
        if (packet instanceof ClientboundSetHeldSlotPacket(int slot)) {
            mc.execute(() -> lastServerSelectedSlot = slot);
        } else if (packet instanceof ClientboundLoginPacket) {
            mc.execute(() -> {
                lastServerSelectedSlot = -1;
                silentSlot = -1;
                restorePending = false;
                hotbarLease = null;
                clearQueue();
            });
        }
    }

    /** Start of the client tick: the one point where nothing was sent yet, so held-slot restores are always safe. */
    @EventHandler(priority = Integer.MAX_VALUE - 1)
    private void onGameTick(GameTickEvent event) {
        tick();
        if (movementLockTicks > 0) {
            movementLockTicks--;
        }
        tickQueue();
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
    }

    @EventHandler(priority = -10000)
    private void onSprintControl(SprintControlEvent event) {
        if (isMovementLocked()) {
            event.setSprint(false);
        }
    }

    /** Expires leases and sends any pending held-slot restore that is allowed right now. Safe to call every tick. */
    public void tick() {
        activeLease();
        activeInventoryLease();
        reconcileServerSlot();
    }

    // ---- client view / internal markers --------------------------------------------------------------------

    /**
     * True while the game draws or animates what the player sees (hand, hotbar, item name popup)
     * or reads the scroll wheel. Those paths get the real client slot, so a hotbar lease or silent
     * swap stays server-side instead of flashing the tool in hand.
     */
    public boolean isClientView() {
        return clientViewDepth > 0;
    }

    public void beginClientView() {
        clientViewDepth++;
    }

    public void endClientView() {
        if (clientViewDepth > 0) clientViewDepth--;
    }

    public boolean isInternalSwap() {
        return internalSwapDepth > 0;
    }

    /** Owner identity is set only while an inventory action is actually executing. */
    public Object executingActionOwner() {
        return internalSwapDepth > 0 ? inventoryActionOwner : null;
    }

    // ---- window-click queue --------------------------------------------------------------------------------

    public boolean hasPendingActions() {
        return !queue.isEmpty();
    }

    /** A queued transaction is executed at most once and only while its owner still needs it. */
    public boolean enqueueInventoryAction(Object owner, InventoryActionKind kind, boolean priority,
                                          BooleanSupplier valid, Runnable action, Runnable onCancel) {
        if (owner == null || action == null) return false;
        if (hasQueuedInventoryAction(owner, kind)) return false;
        return enqueue(new QueuedAction(owner, kind, priority, valid, action, onCancel, randomQueuedDelay()));
    }

    public boolean hasQueuedInventoryAction(Object owner, InventoryActionKind kind) {
        for (QueuedAction action : queue) {
            if (action.owner == owner && (kind == null || action.kind == kind)) return true;
        }
        return false;
    }

    public void cancelQueuedInventoryActions(Object owner) {
        cancelQueuedInventoryActions(owner, null);
    }

    public void cancelQueuedInventoryActions(Object owner, InventoryActionKind kind) {
        if (owner == null) return;
        for (Iterator<QueuedAction> it = queue.iterator(); it.hasNext();) {
            QueuedAction pending = it.next();
            if (pending.owner != owner || kind != null && pending.kind != kind) continue;
            it.remove();
            pending.cancel();
        }
    }

    public int queuedMaxTicks() {
        return queuedMaxTicks;
    }

    public void setQueuedDelayRange(int minimum, int maximum) {
        queuedMinTicks = Math.max(0, Math.min(20, Math.min(minimum, maximum)));
        queuedMaxTicks = Math.max(queuedMinTicks, Math.min(20, Math.max(minimum, maximum)));
    }

    private int randomQueuedDelay() {
        return queuedMinTicks == queuedMaxTicks ? queuedMinTicks
                : ThreadLocalRandom.current().nextInt(queuedMinTicks, queuedMaxTicks + 1);
    }

    private boolean enqueue(QueuedAction queued) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return false;
        UUID uuid = player.getUUID();
        if (queuedPlayerUuid != null && (!queuedPlayerUuid.equals(uuid) || queuedLevel != mc.level)) clearQueue();
        queuedPlayerUuid = uuid;
        queuedLevel = mc.level;
        if (queued.priority) {
            // Priority goes ahead of normal requests but never skips its sampled delay; FIFO among priority ones.
            Deque<QueuedAction> reordered = new ArrayDeque<>();
            while (!queue.isEmpty() && queue.peekFirst().priority) reordered.addLast(queue.removeFirst());
            reordered.addLast(queued);
            reordered.addAll(queue);
            queue.clear();
            queue.addAll(reordered);
        } else {
            queue.addLast(queued);
        }
        return true;
    }

    private void tickQueue() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null || mc.isPaused()
                || queuedPlayerUuid != null && (!queuedPlayerUuid.equals(player.getUUID()) || queuedLevel != mc.level)) {
            clearQueue();
            return;
        }
        QueuedAction queued = queue.peekFirst();
        if (queued == null) return;
        if (!queued.isValid()) {
            queue.removeFirst();
            queued.cancel();
            return;
        }
        if (queued.waitTicks > 0) {
            queued.waitTicks--;
            return;
        }
        if (isWindowKind(queued.kind)) {
            if (!inventoryAccessAllowed(queued.owner)) return;
            if (!window.windowClickAllowed(isMovementLocked())) {
                // A window click must not execute against a moving player's input packet; pause movement until
                // the server has seen a stationary player.
                requestMovementLock(MOVEMENT_LOCK_TICKS);
                return;
            }
        } else if (!window.actionAllowed(queued.kind)) {
            return;
        }
        queue.removeFirst();
        runGuarded(queued.owner, queued.action);
    }

    private void clearQueue() {
        while (!queue.isEmpty()) queue.removeFirst().cancel();
        queuedPlayerUuid = null;
        queuedLevel = null;
    }

    // ---- held slot -----------------------------------------------------------------------------------------

    /** Slot the server holds right now (silent hold, then the server's own state, then the client's). */
    public int serverSelectedSlot() {
        if (isHotbarSlot(silentSlot)) return silentSlot;
        if (isHotbarSlot(lastServerSelectedSlot)) return lastServerSelectedSlot;
        return clientSelectedSlot();
    }

    /**
     * Slot vanilla item logic must treat as selected: the slot the server holds while a silent hold, a lease or a
     * deferred restore keeps it off the client's slot, otherwise the client's own.
     */
    public int effectiveSelectedSlot() {
        if (isHotbarSlot(silentSlot)) return silentSlot;
        HotbarLease active = activeLease();
        if (active != null) return isHotbarSlot(lastServerSelectedSlot) ? lastServerSelectedSlot : active.slot;
        if (restorePending && isHotbarSlot(lastServerSelectedSlot)) return lastServerSelectedSlot;
        return clientSelectedSlot();
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

    /**
     * Reserves the server-held slot for {@code owner}. Returns {@code true} once the server actually holds
     * {@code slot}. When the change is not allowed right now the lease is kept and the slot is sent at the next
     * allowed point; {@code false} is returned until then.
     */
    public boolean leaseHotbar(Object owner, int slot, int ticksUntilReset) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || owner == null || !isHotbarSlot(slot)) return false;

        // An unrelated mining/placement transaction cannot replace another owner's active tool lease.
        HotbarLease active = activeLease();
        if (active != null && active.owner != owner) return false;

        int resetAfterAge = player.tickCount + Math.max(0, ticksUntilReset) + 1;
        hotbarLease = new HotbarLease(player.getUUID(), owner, slot, resetAfterAge);
        if (lastServerSelectedSlot == slot) {
            silentSlot = -1;
            restorePending = false;
            return true;
        }
        if (!window.slotChangeAllowed()) return false;
        silentSlot = -1;
        restorePending = false;
        sendCarriedSlot(slot);
        return true;
    }

    public void releaseHotbar(Object owner) {
        HotbarLease active = activeLease();
        if (active != null && active.owner == owner) {
            clearLease();
        }
    }

    public boolean leaseInventory(Object owner, int ticksUntilReset) {
        LocalPlayer player = mc.player;
        if (player == null || owner == null) return false;

        InventoryLease active = activeInventoryLease();
        if (active != null && active.owner != owner) return false;

        int resetAfterAge = player.tickCount + Math.max(1, ticksUntilReset) + 1;
        inventoryLease = new InventoryLease(player.getUUID(), owner, resetAfterAge);
        return true;
    }

    public boolean isInventoryLeased() {
        return activeInventoryLease() != null;
    }

    public boolean isInventoryLeasedBy(Object owner) {
        InventoryLease active = activeInventoryLease();
        return active != null && active.owner == owner;
    }

    public void releaseInventory(Object owner) {
        InventoryLease active = activeInventoryLease();
        if (active != null && active.owner == owner) inventoryLease = null;
    }

    /**
     * Selects {@code slot} on the client and the server so an action may use it right now. Returns {@code false}
     * and changes nothing when the server cannot be told yet (conflicting action in the Grim window, after this
     * tick's movement packet, or a lease/silent hold elsewhere); the caller retries on a later tick. To go back to
     * a slot afterwards use {@link #restoreHotbar}.
     */
    public boolean selectHotbar(int slot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !isHotbarSlot(slot)) return false;

        if (lastServerSelectedSlot != slot) {
            if (activeLease() != null || isHotbarSlot(silentSlot) || !window.slotChangeAllowed()) return false;
            sendCarriedSlot(slot);
        }
        setClientSelectedSlot(slot);
        restorePending = false;
        return true;
    }

    /**
     * Puts the player's own selection back on {@code slot}. The client moves at once; the server follows now when
     * that is allowed, otherwise at the start of the next tick (the server keeps its slot until then and vanilla's
     * carried-item sync is held back so it cannot send the change in a conflicting window).
     */
    public void restoreHotbar(int slot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !isHotbarSlot(slot)) return;

        setClientSelectedSlot(slot);
        if (activeLease() != null || isHotbarSlot(silentSlot) || lastServerSelectedSlot == slot) {
            restorePending = false;
            return;
        }
        if (window.slotChangeAllowed()) {
            restorePending = false;
            sendCarriedSlot(slot);
        } else {
            restorePending = true;
        }
    }

    private void setClientSelectedSlot(int slot) {
        LocalPlayer player = mc.player;
        if (player != null && clientSelectedSlot() != slot) {
            ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(slot);
        }
    }

    /** Sends the carried slot (deduplicated: Grim BadPacketsA flags the same slot twice in a row). */
    private void sendCarriedSlot(int slot) {
        LocalPlayer player = mc.player;
        boolean changed = lastServerSelectedSlot != slot;
        if (changed) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            lastServerSelectedSlot = slot;
        }
        syncInteractionManagerSlot(slot);
        // Vanilla servers stop main-hand item use whenever the carried slot changes. Mirror that locally,
        // otherwise the client keeps "eating" an item the server already cancelled. A held use key restarts it.
        if (changed && player != null && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }
    }

    private void syncInteractionManagerSlot(int slot) {
        if (mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$setLastSelectedSlot(slot);
        }
    }

    /**
     * Puts the server back on the slot it should hold (lease slot, else the client's) once whatever kept it elsewhere
     * (silent hold, expired lease, deferred select) is over and the change is allowed. Never fires on a plain
     * scroll: a client slot the server has not been told about yet is vanilla's to sync.
     */
    private void reconcileServerSlot() {
        LocalPlayer player = mc.player;
        if (oneShotDepth > 0 || player == null || mc.getConnection() == null) return;
        HotbarLease lease = hotbarLease;
        boolean managed = isHotbarSlot(silentSlot) || restorePending || lease != null;
        if (!managed) return;

        int desired = lease != null ? lease.slot : clientSelectedSlot();
        if (lastServerSelectedSlot < 0 && lease == null || lastServerSelectedSlot == desired) {
            silentSlot = -1;
            restorePending = false;
            return;
        }
        if (!window.slotChangeAllowed()) return;
        silentSlot = -1;
        restorePending = false;
        sendCarriedSlot(desired);
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
            if (player.getUUID().equals(current.playerUuid)) clearLease();
            else hotbarLease = null;
            return null;
        }

        return current;
    }

    private void clearLease() {
        hotbarLease = null;
        if (lastServerSelectedSlot != clientSelectedSlot() && isHotbarSlot(lastServerSelectedSlot)) {
            // The restore is a held-slot change: sent now only if allowed, else at the next tick start.
            restorePending = true;
            reconcileServerSlot();
        }
    }

    private InventoryLease activeInventoryLease() {
        InventoryLease current = inventoryLease;
        if (current == null) return null;

        LocalPlayer player = mc.player;
        if (player == null || !player.getUUID().equals(current.playerUuid) || player.tickCount >= current.resetAfterAge) {
            inventoryLease = null;
            return null;
        }
        return current;
    }

    // ---- window clicks (run inside command / execute) ------------------------------------------------------

    public boolean clickSwap(int containerSlot) {
        return clickSwap(containerSlot, clientSelectedSlot());
    }

    public boolean clickSwap(int containerSlot, int hotbarButton) {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (!isSwapButton(hotbarButton) || containerSlot < 0) return false;
        if (InventorySlotLocks.blocks(handler, containerSlot, hotbarButton,
                ContainerInput.SWAP, player, executingActionOwner())) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, containerSlot, hotbarButton, ContainerInput.SWAP, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    public boolean pickupSwap(int slotA, int slotB) {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (slotA < 0 || slotB < 0 || slotA == slotB) return false;
        if (InventorySlotLocks.blocks(handler, slotA, 0, ContainerInput.PICKUP,
                player, executingActionOwner()) || InventorySlotLocks.blocks(handler, slotB,
                0, ContainerInput.PICKUP, player, executingActionOwner())) return false;

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

    /** Throws the whole stack of a container screen slot out (Ctrl+Q); must run inside {@link #command}. */
    public boolean throwSlot(int slotId) {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        if (InventorySlotLocks.blocks(handler, slotId, 1, ContainerInput.THROW,
                player, executingActionOwner())) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, slotId, 1, ContainerInput.THROW, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    /** Shift-clicks a container screen slot (QUICK_MOVE); must run inside {@link #command}. */
    public boolean quickMove(int slotId) {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        if (InventorySlotLocks.blocks(handler, slotId, 0, ContainerInput.QUICK_MOVE,
                player, executingActionOwner())) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    /** Shift-clicks a container screen slot through the serialized click queue. */
    public boolean quickMoveSlot(int slotId, Object owner) {
        return command(owner, InventoryActionKind.INVENTORY_CLICK, () -> quickMove(slotId));
    }

    /** One PICKUP click (button 0 = whole stack, 1 = half / one item) on a screen slot; must run inside {@link #command}. */
    public boolean pickupClick(int slotId, int button) {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null) return false;
        if (slotId < 0 || slotId >= handler.slots.size() || (button != 0 && button != 1)) return false;
        if (InventorySlotLocks.blocks(handler, slotId, button, ContainerInput.PICKUP,
                player, executingActionOwner())) return false;

        beginInternalSwap();
        try {
            interaction.handleContainerInput(handler.containerId, slotId, button, ContainerInput.PICKUP, player);
            return true;
        } finally {
            endInternalSwap();
        }
    }

    public boolean swapScreenSlots(int slotA, int slotB) {
        return swapScreenSlots(slotA, slotB, null);
    }

    public boolean swapScreenSlots(int slotA, int slotB, Object owner) {
        return command(owner, InventoryActionKind.INVENTORY_CLICK, () -> pickupSwap(slotA, slotB));
    }

    public boolean swapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
        if (!isHotbarSlot(hotbarSlot) || inventorySlot < 0 || inventorySlot >= 36) return false;
        if (inventorySlot == hotbarSlot) return selectHotbar(hotbarSlot);

        int sourceScreenSlot = mapInventoryToScreenSlot(inventorySlot);
        int targetScreenSlot = mapHotbarToScreenSlot(hotbarSlot);
        if (sourceScreenSlot < 0 || targetScreenSlot < 0) return false;
        return command(null, InventoryActionKind.INVENTORY_CLICK, () -> {
            pickupSwap(sourceScreenSlot, targetScreenSlot);
            selectHotbar(hotbarSlot);
        });
    }

    public boolean swapInventoryToOffhand(int inventorySlot) {
        return swapInventoryToOffhand(inventorySlot, null, null);
    }

    public boolean swapInventoryToOffhand(int inventorySlot, Object owner, Runnable afterSwap) {
        if (inventorySlot < 0 || inventorySlot >= 36 ||
                InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, owner)) return false;
        int sourceScreenSlot = mapInventoryToScreenSlot(inventorySlot);
        if (sourceScreenSlot < 0) return false;
        return command(owner, InventoryActionKind.INVENTORY_CLICK, () -> {
            if (!clickSwap(sourceScreenSlot, OFFHAND_SWAP_BUTTON)) return;
            if (afterSwap != null) afterSwap.run();
        });
    }

    public boolean dropStack(Slot slot) {
        LocalPlayer player = mc.player;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (handler == null || slot == null) return false;
        int slotId = slot.index;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        return command(null, InventoryActionKind.INVENTORY_CLICK, () -> throwSlot(slotId));
    }

    public int quickMoveMatchingFromContainer(Set<String> itemIds) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (player == null || interaction == null || handler == null || itemIds == null || itemIds.isEmpty()) return 0;

        int moved = 0;
        int playerStart = playerInventoryStart(handler);
        if (playerStart < 0) return 0;
        for (int slotId = 0; slotId < playerStart; slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null || !slot.hasItem()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString().toLowerCase();
            if (!itemIds.contains(itemId)) continue;

            int finalSlotId = slotId;
            if (command(null, InventoryActionKind.INVENTORY_CLICK, () -> {
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

    // ---- actions -------------------------------------------------------------------------------------------

    /**
     * Runs a raw action that needs the Grim safety gate. Window clicks and closes run now when the server sees a
     * stationary player and nothing is queued ahead, otherwise they are queued (movement is paused until they can
     * go) and {@code true} is returned. Any other kind runs now or returns {@code false} so the caller can retry.
     */
    public boolean command(Object owner, InventoryActionKind actionKind, Runnable action) {
        if (action == null || mc.player == null || mc.getConnection() == null) return false;
        InventoryActionKind kind = actionKind != null ? actionKind : InventoryActionKind.GENERIC;
        if (!isWindowKind(kind)) {
            if (!window.actionAllowed(kind)) return false;
            runGuarded(owner, action);
            return true;
        }

        if (!inventoryAccessAllowed(owner)) return false;
        if (queue.isEmpty() && window.windowClickAllowed(isMovementLocked())) {
            runGuarded(owner, action);
            return true;
        }
        requestMovementLock(MOVEMENT_LOCK_TICKS);
        return enqueue(new QueuedAction(owner, kind, false, null, action, null, 0));
    }

    /**
     * Finds the item, brings it to the server-held slot, runs the action and (silent) leaves the restore to the
     * start of the next tick. Returns {@code true} only if the action ran now; {@code false} means nothing happened
     * and the caller should retry on a later tick.
     */
    public boolean execute(InventorySwapRequest request) {
        LocalPlayer player = mc.player;
        if (request == null || player == null || mc.getConnection() == null || mc.gameMode == null) return false;
        if (!window.actionAllowed(request.actionKind())) return false;

        SlotResult found = find(request.predicate(), request.scope());
        if (!found.found()) return false;

        boolean silent = request.visibility() == InventorySwapVisibility.SILENT || request.restore();
        beginInternalSwap();
        try {
            if (found.isHotbar()) {
                return silent ? runSilentHotbar(found.slot(), request.action())
                        : runNormalHotbar(found.slot(), request.action());
            }
            return silent ? runSilentInventory(found.slot(), request.action())
                    : runNormalInventory(found.slot(), request.action());
        } finally {
            endInternalSwap();
        }
    }

    public InteractionResult useItem(InteractionHand hand) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interaction = mc.gameMode;
        if (player == null || interaction == null || hand == null) return InteractionResult.FAIL;
        return interaction.useItem(player, hand);
    }

    /** Swings {@code hand}; {@code false} if the swing is not allowed right now (using an item). */
    public boolean swingHand(InteractionHand hand) {
        LocalPlayer player = mc.player;
        if (player == null || hand == null || !window.actionAllowed(InventoryActionKind.SWING)) return false;
        runGuarded(null, () -> player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false));
        return true;
    }

    // ---- search --------------------------------------------------------------------------------------------

    /** First match in the hotbar; {@link SlotResult#slot()} is the player-inventory index (0-8). */
    public SlotResult findHotbar(Predicate<ItemStack> predicate) {
        return findRange(predicate, 0, 9);
    }

    /** First match in the main inventory; {@link SlotResult#slot()} is the player-inventory index (9-35). */
    public SlotResult findInventory(Predicate<ItemStack> predicate) {
        return findRange(predicate, 9, 36);
    }

    /** Hotbar first, then main inventory, as far as {@code scope} allows; slots are player-inventory indices. */
    public SlotResult find(Predicate<ItemStack> predicate, InventorySearchScope scope) {
        if (predicate == null) return SlotResult.empty();
        InventorySearchScope safeScope = scope != null ? scope : InventorySearchScope.FULL;
        return switch (safeScope) {
            case HOTBAR -> findHotbar(predicate);
            case INVENTORY -> findInventory(predicate);
            case FULL -> {
                SlotResult hotbar = findHotbar(predicate);
                yield hotbar.found() ? hotbar : findInventory(predicate);
            }
        };
    }

    private SlotResult findRange(Predicate<ItemStack> predicate, int from, int to) {
        LocalPlayer player = mc.player;
        if (player == null || predicate == null) return SlotResult.empty();

        for (int slot = from; slot < to; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return new SlotResult(slot, true, stack);
            }
        }
        return SlotResult.empty();
    }

    // ---- one-shot swaps ------------------------------------------------------------------------------------

    private boolean runNormalHotbar(int slot, Runnable action) {
        if (lastServerSelectedSlot != slot && !window.slotChangeAllowed()) return false;
        if (!selectHotbar(slot)) return false;
        action.run();
        return true;
    }

    private boolean runSilentHotbar(int slot, Runnable action) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !isHotbarSlot(slot)) return false;
        if (lastServerSelectedSlot != slot && !window.slotChangeAllowed()) return false;

        int previousClientSlot = clientSelectedSlot();
        // The raw client slot moves too: setItemInHand writes through Inventory.selected, so a bucket fill or
        // similar result stack must land in the swapped slot, not the one the player is looking at.
        oneShotDepth++;
        silentSlot = slot;
        setClientSelectedSlot(slot);
        sendCarriedSlot(slot);
        try {
            action.run();
            return true;
        } finally {
            oneShotDepth--;
            setClientSelectedSlot(previousClientSlot);
            // The restore is a held-slot change after the action: sending it now would be a PacketOrderE conflict
            // and, after the movement packet, a Post violation. Keep the slot until the next tick start.
            int desired = hotbarLease != null ? hotbarLease.slot : previousClientSlot;
            silentSlot = lastServerSelectedSlot == desired ? -1 : lastServerSelectedSlot;
        }
    }

    private boolean runNormalInventory(int inventorySlot, Runnable action) {
        if (!inventoryClickReady()) return false;
        int screenSlot = mapInventoryToScreenSlot(inventorySlot);
        if (screenSlot < 0 || !clickSwap(screenSlot)) return false;
        action.run();
        return true;
    }

    private boolean runSilentInventory(int inventorySlot, Runnable action) {
        if (!inventoryClickReady()) return false;
        int screenSlot = mapInventoryToScreenSlot(inventorySlot);
        if (screenSlot < 0) return false;
        // Swap into the slot the server is actually holding (lease or silent hold), not the one on screen.
        int hotbarButton = effectiveSelectedSlot();
        if (!clickSwap(screenSlot, hotbarButton)) return false;

        try {
            return runSilentHotbar(hotbarButton, action);
        } finally {
            clickSwap(screenSlot, hotbarButton);
        }
    }

    /** Window clicks need a stationary player on the server's side; pause movement until that holds. */
    private boolean inventoryClickReady() {
        if (!inventoryAccessAllowed(inventoryActionOwner)) return false;
        if (window.windowClickAllowed(isMovementLocked())) return true;
        requestMovementLock(MOVEMENT_LOCK_TICKS);
        return false;
    }

    // ---- internals -----------------------------------------------------------------------------------------

    private void runGuarded(Object owner, Runnable action) {
        Object previousOwner = inventoryActionOwner;
        inventoryActionOwner = owner;
        beginInternalSwap();
        try {
            action.run();
        } finally {
            endInternalSwap();
            inventoryActionOwner = previousOwner;
        }
    }

    private boolean inventoryAccessAllowed(Object owner) {
        InventoryLease active = activeInventoryLease();
        return active == null || active.owner == owner;
    }

    private void requestMovementLock(int ticks) {
        movementLockTicks = Math.max(movementLockTicks, Math.max(0, ticks));
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private boolean isMovementLocked() {
        return movementLockTicks > 0;
    }

    private void beginInternalSwap() {
        internalSwapDepth++;
    }

    private void endInternalSwap() {
        if (internalSwapDepth > 0) internalSwapDepth--;
    }

    private record HotbarLease(UUID playerUuid, Object owner, int slot, int resetAfterAge) {
    }

    private record InventoryLease(UUID playerUuid, Object owner, int resetAfterAge) {
    }

    private static final class QueuedAction {
        private final Object owner;
        private final InventoryActionKind kind;
        private final boolean priority;
        private final BooleanSupplier valid;
        private final Runnable action;
        private final Runnable onCancel;
        private int waitTicks;

        private QueuedAction(Object owner, InventoryActionKind kind, boolean priority, BooleanSupplier valid,
                             Runnable action, Runnable onCancel, int waitTicks) {
            this.owner = owner;
            this.kind = kind != null ? kind : InventoryActionKind.GENERIC;
            this.priority = priority;
            this.valid = valid != null ? valid : () -> true;
            this.action = action;
            this.onCancel = onCancel;
            this.waitTicks = Math.max(0, waitTicks);
        }

        private boolean isValid() {
            try {
                return valid.getAsBoolean();
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private void cancel() {
            if (onCancel != null) onCancel.run();
        }
    }
}
