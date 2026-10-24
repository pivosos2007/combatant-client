package combatant.client.util.player.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TotemSlotPickerTest {
    private static boolean[] slots(int... idx) {
        boolean[] out = new boolean[36];
        for (int i : idx) out[i] = true;
        return out;
    }

    @Test
    void noTotemReturnsMinusOne() {
        assertEquals(-1, TotemSlotPicker.pick(slots(), slots(), true, false, -1));
    }

    @Test
    void mainInventoryFirstUnlessHotbarPreferred() {
        boolean[] totem = slots(2, 20);
        assertEquals(20, TotemSlotPicker.pick(totem, slots(), false, false, -1));
        assertEquals(2, TotemSlotPicker.pick(totem, slots(), false, true, -1));
    }

    @Test
    void hotbarIsUsedWhenMainInventoryHasNone() {
        assertEquals(4, TotemSlotPicker.pick(slots(4), slots(), false, false, -1));
    }

    @Test
    void enchantedOnlyAsLastResortWhenSaving() {
        assertEquals(30, TotemSlotPicker.pick(slots(10, 30), slots(10), true, false, -1));
        assertEquals(10, TotemSlotPicker.pick(slots(10), slots(10), true, false, -1));
        assertEquals(10, TotemSlotPicker.pick(slots(10, 30), slots(10), false, false, -1));
    }

    @Test
    void excludedSlotIsSkipped() {
        assertEquals(-1, TotemSlotPicker.pick(slots(8), slots(), false, false, 8));
        assertEquals(12, TotemSlotPicker.pick(slots(8, 12), slots(), false, true, 8));
    }
}
