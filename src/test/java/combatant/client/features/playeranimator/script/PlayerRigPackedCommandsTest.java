/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playeranimator.script;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRigPackedCommandsTest {
    @Test
    void nativeDoubleRecordsDecodeWithCorrectTargetArgumentCountAndOrder() {
        ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(PlayerRigPackedCommands.BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        nativeBuffer.putDouble(0, 11);          // reach hand
        nativeBuffer.putDouble(8, 2);           // upper arm
        nativeBuffer.putDouble(16, 8);          // 8 args
        for (int i = 0; i < 8; i++) nativeBuffer.putDouble(24 + i * 8, i + .25);
        int second = PlayerRigPackedCommands.STRIDE * Double.BYTES;
        nativeBuffer.putDouble(second, 0);       // reset
        nativeBuffer.putDouble(second + 8, -1);
        nativeBuffer.putDouble(second + 16, 0);

        var commands = PlayerRigPackedCommands.decode(nativeBuffer, 2);
        assertEquals(2, commands.size());
        assertEquals(11, commands.get(0).operation());
        assertEquals(2, commands.get(0).target());
        assertArrayEquals(new float[]{.25f, 1.25f, 2.25f, 3.25f, 4.25f, 5.25f, 6.25f, 7.25f},
                commands.get(0).arguments());
        assertEquals(0, commands.get(1).arguments().length);
        assertEquals(-1, commands.get(1).target());
    }

    @Test
    void rejectInvalidCommandCountsAndLengths() {
        ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(PlayerRigPackedCommands.BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        assertThrows(IllegalArgumentException.class,
                () -> PlayerRigPackedCommands.decode(nativeBuffer, -1));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerRigPackedCommands.decode(nativeBuffer, PlayerRigPackedCommands.CAPACITY + 1));
        nativeBuffer.putDouble(16, 9);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerRigPackedCommands.decode(nativeBuffer, 1));
    }
}
