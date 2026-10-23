package combatant.client.util.player.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventorySwapGrimGateTest {
    @Test
    void clicksWaitWhileTheServerSeesSprintMovementOrSneak() {
        assertTrue(InventorySwap.grimMovingForInventory(false, true, false, false, false, false));
        assertTrue(InventorySwap.grimMovingForInventory(false, false, false, true, false, false));
        assertTrue(InventorySwap.grimMovingForInventory(false, false, false, false, true, false));
        assertTrue(InventorySwap.grimMovingForInventory(false, false, false, false, false, true));
        assertFalse(InventorySwap.grimMovingForInventory(false, false, false, false, false, false));
        assertFalse(InventorySwap.grimMovingForInventory(false, true, true, false, false, false));
    }

    @Test
    void lockClearsOnlyTheComingTicksKeysNotWhatTheServerAlreadyHas() {
        // Held keys this tick are cleared by the lock before the next input packet goes out.
        assertFalse(InventorySwap.grimMovingForInventory(true, false, false, false, false, true));
        // Movement or sneak the server already received still flags MultiActionsC under the lock.
        assertTrue(InventorySwap.grimMovingForInventory(true, false, false, true, false, false));
        assertTrue(InventorySwap.grimMovingForInventory(true, false, false, false, true, false));
        assertTrue(InventorySwap.grimMovingForInventory(true, true, false, false, false, false));
    }
}
