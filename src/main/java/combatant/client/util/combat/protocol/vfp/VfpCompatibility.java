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
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionType;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.combat.protocol.ClientProtocolVersion;

/**
 * Compatibility layer for ViaFabricPlus.
 * <p>
 * DO NOT CALL ANY OF THESE METHODS WITHOUT CHECKING IF VIAFABRICPLUS IS LOADED.
 */
public enum VfpCompatibility {

    INSTANCE;

    public ClientProtocolVersion unsafeGetProtocolVersion() {
        try {
            ProtocolVersion version = ViaFabricPlus.getImpl().getTargetVersion();
            return new ClientProtocolVersion(version.getName(), version.getVersion());
        } catch (Throwable t) {
            DebugLog.error("Failed to get protocol version", t);
            return null;
        }
    }

    public ClientProtocolVersion[] unsafeGetProtocolVersions() {
        try {
            var protocols = ProtocolVersion.getProtocols()
                    .stream()
                    .filter(version -> version.getVersionType() == VersionType.RELEASE)
                    .map(version -> new ClientProtocolVersion(version.getName(), version.getVersion()))
                    .toArray(ClientProtocolVersion[]::new);

            for (int left = 0, right = protocols.length - 1; left < right; left++, right--) {
                var tmp = protocols[left];
                protocols[left] = protocols[right];
                protocols[right] = tmp;
            }
            return protocols;
        } catch (Throwable t) {
            DebugLog.error("Failed to get protocol versions", t);
            return new ClientProtocolVersion[0];
        }
    }

    public void unsafeOpenVfpProtocolSelection() {
        try {
            var currentScreen = ClientScreen.current(Minecraft.getInstance());
            if (currentScreen == null) {
                currentScreen = new TitleScreen();
            }
            ViaFabricPlus.getImpl().openProtocolSelectionScreen(currentScreen);
        } catch (Throwable t) {
            DebugLog.error("Failed to open ViaFabricPlus screen", t);
        }
    }

    public void unsafeSelectProtocolVersion(int protocolId) {
        try {
            if (!ProtocolVersion.isRegistered(protocolId)) {
                throw new IllegalArgumentException("Protocol version is not registered");
            }
            ProtocolVersion version = ProtocolVersion.getProtocol(protocolId);
            ViaFabricPlus.getImpl().setTargetVersion(version);
        } catch (Throwable t) {
            DebugLog.error("Failed to select protocol version", t);
        }
    }

    public boolean isEqual1_8() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.equalTo(ProtocolVersion.v1_8);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is 1.8", t);
            return false;
        }
    }

    public boolean isOlderThanOrEqual1_8() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.olderThanOrEqualTo(ProtocolVersion.v1_8);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is <= 1.8", t);
            return false;
        }
    }

    public boolean isOlderThanOrEqual1_7_10() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.olderThanOrEqualTo(ProtocolVersion.v1_7_6);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is <= 1.7.10", t);
            return false;
        }
    }

    public boolean isNewerThanOrEqual1_16() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.newerThanOrEqualTo(ProtocolVersion.v1_16);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is >= 1.16", t);
            return false;
        }
    }

    public boolean isNewerThanOrEqual1_21_5() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.newerThanOrEqualTo(ProtocolVersion.v1_21_5);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is >= 1.21.5", t);
            return false;
        }
    }

    public boolean isNewerThanOrEqual1_21_6() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.newerThanOrEqualTo(ProtocolVersion.v1_21_6);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is >= 1.21.6", t);
            return false;
        }
    }

    public boolean isNewerThanOrEqual1_21_9() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.newerThanOrEqualTo(ProtocolVersion.v1_21_9);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is >= 1.21.9", t);
            return false;
        }
    }

    public boolean isOlderThanOrEqual1_11_1() {
        try {
            var version = ViaFabricPlus.getImpl().getTargetVersion();
            return version.olderThanOrEqualTo(ProtocolVersion.v1_11_1);
        } catch (Throwable t) {
            DebugLog.error("Failed to check if protocol is <= 1.11.1", t);
            return false;
        }
    }
}
