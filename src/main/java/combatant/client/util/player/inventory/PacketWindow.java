/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Input;

/**
 * Tracks what the server (Grim) has seen in the current "tick window" of outgoing packets.
 * <p>
 * A window ends at the movement packet, or, on a client tick that sent none, at the client-tick-end packet.
 * Within a window Grim's PacketOrderE flags a held-slot change that follows an attack, a right click, a release,
 * a sprint/sneak/glide/bed/mount state change or an inventory open. Grim's Post check flags a held-slot change that
 * arrives after the movement packet of the tick, so the only safe moment for one is the start of a client tick,
 * before the vanilla key handling has produced any packet.
 * <p>
 * The state machine is driven by {@code note*} methods so it can be exercised without a running game;
 * {@link #onPacketSend} classifies real packets into them.
 */
public final class PacketWindow {
    public static final PacketWindow INSTANCE = new PacketWindow();

    private final Minecraft mc = Minecraft.getInstance();

    /** True from this tick's movement packet until the next client tick starts (Grim Post). */
    private boolean movementSent;
    // Held-slot conflicts of the current window (PacketOrderE).
    private boolean attacked;
    private boolean rightClicked;
    private boolean released;
    private boolean stateChanged;
    // Same-window action ordering (MultiActions-style block vs entity rule, swing while using).
    private boolean entityAction;
    private boolean blockAction;
    private boolean useItemAction;
    // Last input packet the server holds; MultiActionsC flags window clicks while it shows movement or sneak.
    private boolean lastInputMoving;
    private boolean lastInputSneaking;

    private PacketWindow() {
    }

    // ---- queries -------------------------------------------------------------------------------------------

    /** A held-slot packet sent right now is neither a PacketOrderE conflict nor a Post violation. */
    public boolean slotChangeAllowed() {
        return !movementSent && !attacked && !rightClicked && !released && !stateChanged;
    }

    /** True once this client tick's movement packet went out; item/slot packets sent now are "post" packets. */
    public boolean isPostMovement() {
        return movementSent;
    }

    /** Same-window ordering of a non-slot action. Held-slot changes are gated by {@link #slotChangeAllowed()}. */
    public boolean actionAllowed(InventoryActionKind kind) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        return actionAllowed(kind, player.isUsingItem());
    }

    boolean actionAllowed(InventoryActionKind kind, boolean usingItem) {
        InventoryActionKind safeKind = kind != null ? kind : InventoryActionKind.GENERIC;
        return switch (safeKind) {
            case ATTACK_ENTITY -> !usingItem && !blockAction;
            case ENTITY_INTERACT -> !blockAction;
            case BLOCK_INTERACT, BLOCK_BREAK -> !entityAction;
            case SWING -> !usingItem && !useItemAction;
            default -> true;
        };
    }

    /** Window click / close gate: the server must see a stationary, non-sneaking, non-sprinting player. */
    public boolean windowClickAllowed(boolean movementLocked) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        Input input = player.input != null ? player.input.keyPresses : null;
        boolean keys = input != null && isMoving(input);
        return !grimMovingForInventory(movementLocked, player.isSprinting(),
                player.isSwimming() && player.onGround(), lastInputMoving, lastInputSneaking, keys);
    }

    /** Shared "stationary for click" check: what any module must test before it plans a window click. */
    public boolean stationaryForClick() {
        return windowClickAllowed(false);
    }

    /** Grim knownInput.moving(): forward, back, left, right or jump. */
    static boolean isMoving(Input input) {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump();
    }

    /**
     * Mirrors Grim MultiActionsC: a window click is flagged while sprinting (unless swimming on the ground) or when
     * the last input packet the server has seen held movement or sneak. The movement lock only clears the keys of the
     * coming tick, so the input the server already has is checked even while locked.
     */
    static boolean grimMovingForInventory(boolean movementLocked, boolean sprinting, boolean swimmingOnGround,
                                          boolean lastInputMoving, boolean lastInputSneaking, boolean movementKeys) {
        if (sprinting && !swimmingOnGround) return true;
        if (lastInputMoving || lastInputSneaking) return true;
        return !movementLocked && movementKeys;
    }

    // ---- state machine -------------------------------------------------------------------------------------

    void noteTickStart() {
        movementSent = false;
    }

    void noteMovement() {
        clearWindow();
        movementSent = true;
    }

    void noteTickEnd() {
        // Grim ignores the tick-end packet when a movement packet already closed this tick's window.
        if (!movementSent) clearWindow();
    }

    void noteAttack() {
        attacked = true;
        entityAction = true;
    }

    void noteEntityInteract() {
        rightClicked = true;
        entityAction = true;
    }

    void noteUseItem() {
        rightClicked = true;
        useItemAction = true;
    }

    void noteUseItemOn() {
        rightClicked = true;
        blockAction = true;
    }

    void noteDigging() {
        blockAction = true;
    }

    void noteRelease() {
        released = true;
    }

    void noteStateChange() {
        stateChanged = true;
    }

    void noteInput(boolean moving, boolean sneaking) {
        if (sneaking != lastInputSneaking) stateChanged = true;
        lastInputMoving = moving;
        lastInputSneaking = sneaking;
    }

    void reset() {
        clearWindow();
        movementSent = false;
        lastInputMoving = false;
        lastInputSneaking = false;
    }

    private void clearWindow() {
        attacked = false;
        rightClicked = false;
        released = false;
        stateChanged = false;
        entityAction = false;
        blockAction = false;
        useItemAction = false;
    }

    // ---- packet classification -----------------------------------------------------------------------------

    @EventHandler(priority = Integer.MAX_VALUE)
    private void onGameTick(GameTickEvent event) {
        noteTickStart();
    }

    /** Runs after every filter that may cancel a packet, so only packets that really leave are recorded. */
    @EventHandler(priority = -20000)
    private void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        Object packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket) {
            noteMovement();
        } else if (packet instanceof ServerboundClientTickEndPacket) {
            noteTickEnd();
        } else if (packet instanceof ServerboundAttackPacket) {
            noteAttack();
        } else if (packet instanceof ServerboundInteractPacket) {
            noteEntityInteract();
        } else if (packet instanceof ServerboundUseItemPacket) {
            noteUseItem();
        } else if (packet instanceof ServerboundUseItemOnPacket) {
            noteUseItemOn();
        } else if (packet instanceof ServerboundPlayerActionPacket action) {
            switch (action.getAction()) {
                case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> noteDigging();
                case RELEASE_USE_ITEM -> noteRelease();
                default -> {
                }
            }
        } else if (packet instanceof ServerboundPlayerCommandPacket command) {
            // OPEN_INVENTORY, sprint, leave-bed, glide start and mount jump are all PacketOrderE conflicts.
            if (command.getAction() != null) noteStateChange();
        } else if (packet instanceof ServerboundPlayerInputPacket(Input input)) {
            noteInput(isMoving(input), input.shift());
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            mc.execute(this::reset);
        }
    }
}
