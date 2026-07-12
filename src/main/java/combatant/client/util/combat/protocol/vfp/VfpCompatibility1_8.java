/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.combat.protocol.vfp;

/*
 * Portions of this file are adapted from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015 - 2026 CCBlueX
 */

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_8;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/**
 * Compatibility layer for ViaFabricPlus on protocol 1.8.
 * <p>
 * DO NOT CALL ANY OF THESE METHODS WITHOUT CHECKING IF VIAFABRICPLUS IS LOADED AND YOU ARE ON 1.8 PROTOCOL.
 */
public enum VfpCompatibility1_8 {

    INSTANCE;

    public void sendSignUpdate(final BlockPos blockPos, final String[] lines) throws IllegalArgumentException {
        if (lines.length != 4) {
            throw new IllegalArgumentException("Lines length does not match 4");
        }

        writePacket(ServerboundPackets1_8.SIGN_UPDATE, packet -> {
            packet.write(Types.BLOCK_POSITION1_8, new BlockPosition(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            for (String line : lines) {
                packet.write(Types.STRING, line);
            }
        });
    }

    public void sendPlayerInput(float sideways, float forwards, boolean jumping, boolean sneaking) {
        writePacket(ServerboundPackets1_8.PLAYER_INPUT, packet -> {
            packet.write(Types.FLOAT, sideways);
            packet.write(Types.FLOAT, forwards);
            byte b = 0;
            if (jumping) {
                b = (byte) (b | 1);
            }
            if (sneaking) {
                b = (byte) (b | 2);
            }
            packet.write(Types.BYTE, b);
        });
    }

    private void writePacket(ServerboundPacketType packetType, Consumer<PacketWrapper> writer) {
        if (!VfpCompatibility.INSTANCE.isEqual1_8()) {
            throw new IllegalStateException("Not on 1.8 protocol");
        }

        var packet = PacketWrapper.create(packetType, ViaFabricPlus.getImpl().getPlayNetworkUserConnection());
        writer.accept(packet);
        packet.sendToServerRaw();
    }
}
