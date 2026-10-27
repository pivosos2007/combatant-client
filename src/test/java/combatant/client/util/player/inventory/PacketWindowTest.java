package combatant.client.util.player.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketWindowTest {
    private final PacketWindow window = PacketWindow.INSTANCE;

    @BeforeEach
    void reset() {
        window.reset();
    }

    @Test
    void cleanWindowAtTickStartAllowsSlotChange() {
        window.noteTickStart();
        assertTrue(window.slotChangeAllowed());
    }

    @Test
    void slotChangeBeforeTheActionIsFineButNotAfterIt() {
        window.noteTickStart();
        assertTrue(window.slotChangeAllowed());
        window.noteUseItem();
        assertFalse(window.slotChangeAllowed());
    }

    @Test
    void everyPacketOrderEConflictBlocksTheSlotChange() {
        Runnable[] conflicts = {
                window::noteAttack, window::noteEntityInteract, window::noteUseItem, window::noteUseItemOn,
                window::noteRelease, window::noteStateChange
        };
        for (Runnable conflict : conflicts) {
            window.reset();
            window.noteTickStart();
            conflict.run();
            assertFalse(window.slotChangeAllowed());
        }
    }

    @Test
    void movementPacketClosesTheWindowButBlocksSlotChangesUntilNextTick() {
        window.noteTickStart();
        window.noteUseItem();
        window.noteMovement();
        // Grim Post: nothing held-slot related after this tick's movement packet.
        assertFalse(window.slotChangeAllowed());
        assertTrue(window.isPostMovement());

        // The silent restore at the start of the next tick is in a fresh window.
        window.noteTickStart();
        assertTrue(window.slotChangeAllowed());
    }

    @Test
    void tickEndClosesTheWindowOnlyWhenNoMovementPacketWasSent() {
        window.noteTickStart();
        window.noteAttack();
        window.noteTickEnd();
        assertTrue(window.slotChangeAllowed());

        window.noteTickStart();
        window.noteMovement();
        window.noteUseItem();
        window.noteTickEnd();
        assertFalse(window.slotChangeAllowed());
    }

    @Test
    void sneakChangeInTheInputPacketIsAStateConflictButSteadyInputIsNot() {
        window.noteTickStart();
        window.noteInput(true, false);
        assertTrue(window.slotChangeAllowed());
        window.noteInput(true, true);
        assertFalse(window.slotChangeAllowed());
    }

    @Test
    void blockAndEntityActionsDoNotShareAWindow() {
        window.noteTickStart();
        window.noteUseItemOn();
        assertFalse(window.actionAllowed(InventoryActionKind.ATTACK_ENTITY, false));
        assertFalse(window.actionAllowed(InventoryActionKind.ENTITY_INTERACT, false));
        assertTrue(window.actionAllowed(InventoryActionKind.BLOCK_INTERACT, false));

        window.noteMovement();
        window.noteTickStart();
        window.noteAttack();
        assertFalse(window.actionAllowed(InventoryActionKind.BLOCK_INTERACT, false));
        assertFalse(window.actionAllowed(InventoryActionKind.BLOCK_BREAK, false));
    }

    @Test
    void attackAndSwingWaitWhileUsingAnItem() {
        assertFalse(window.actionAllowed(InventoryActionKind.ATTACK_ENTITY, true));
        assertFalse(window.actionAllowed(InventoryActionKind.SWING, true));
        assertTrue(window.actionAllowed(InventoryActionKind.SWING, false));
        assertTrue(window.actionAllowed(InventoryActionKind.USE_ITEM, true));
    }

    @Test
    void clicksWaitWhileTheServerSeesSprintMovementOrSneak() {
        assertTrue(PacketWindow.grimMovingForInventory(false, true, false, false, false, false));
        assertTrue(PacketWindow.grimMovingForInventory(false, false, false, true, false, false));
        assertTrue(PacketWindow.grimMovingForInventory(false, false, false, false, true, false));
        assertTrue(PacketWindow.grimMovingForInventory(false, false, false, false, false, true));
        assertFalse(PacketWindow.grimMovingForInventory(false, false, false, false, false, false));
        assertFalse(PacketWindow.grimMovingForInventory(false, true, true, false, false, false));
    }

    @Test
    void lockClearsOnlyTheComingTicksKeysNotWhatTheServerAlreadyHas() {
        assertFalse(PacketWindow.grimMovingForInventory(true, false, false, false, false, true));
        assertTrue(PacketWindow.grimMovingForInventory(true, false, false, true, false, false));
        assertTrue(PacketWindow.grimMovingForInventory(true, false, false, false, true, false));
        assertTrue(PacketWindow.grimMovingForInventory(true, true, false, false, false, false));
    }

    @Test
    void jumpCountsAsMovementLikeGrimKnownInput() {
        assertTrue(PacketWindow.isMoving(new net.minecraft.world.entity.player.Input(false, false, false, false, true, false, false)));
        assertFalse(PacketWindow.isMoving(new net.minecraft.world.entity.player.Input(false, false, false, false, false, true, false)));
    }
}
