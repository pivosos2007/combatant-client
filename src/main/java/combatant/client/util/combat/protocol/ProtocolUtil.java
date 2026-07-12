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

package combatant.client.util.combat.protocol;

/*
 * Portions of this file are adapted from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015 - 2026 CCBlueX
 */

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.combat.protocol.vfp.VfpCompatibility;
import combatant.client.util.combat.protocol.vfp.VfpCompatibility1_8;

import java.util.Map;

/**
 * Protocol helpers with optional ViaFabricPlus support.
 */
public enum ProtocolUtil {
    ;

    public static final ClientProtocolVersion DEFAULT_PROTOCOL_VERSION =
            new ClientProtocolVersion(SharedConstants.getCurrentVersion().name(), SharedConstants.getProtocolVersion());
    private static final Minecraft MC = Minecraft.getInstance();
    public static final boolean USES_VIAFABRICPLUS = detectViaFabricPlus();

    private static boolean detectViaFabricPlus() {
        boolean loaded = FabricLoader.getInstance().isModLoaded("viafabricplus");
        if (!loaded) {
            return false;
        }

        try {
            ViaFabricPlus.getImpl().registerOnChangeProtocolVersionCallback((oldVer, newVer) -> {
                // Update the window title if available
                MC.execute(MC::updateTitle);
            });
        } catch (Throwable t) {
            DebugLog.error("Failed to register ViaFabricPlus protocol callback", t);
        }

        return true;
    }

    public static ClientProtocolVersion getProtocolVersion() {
        if (USES_VIAFABRICPLUS) {
            try {
                ClientProtocolVersion version = VfpCompatibility.INSTANCE.unsafeGetProtocolVersion();
                return version != null ? version : DEFAULT_PROTOCOL_VERSION;
            } catch (Throwable t) {
                DebugLog.error("Failed to get protocol version", t);
            }
        }
        return DEFAULT_PROTOCOL_VERSION;
    }

    public static ClientProtocolVersion[] getProtocolVersions() {
        if (USES_VIAFABRICPLUS) {
            try {
                return VfpCompatibility.INSTANCE.unsafeGetProtocolVersions();
            } catch (Throwable t) {
                DebugLog.error("Failed to get protocol versions", t);
            }
        }
        return new ClientProtocolVersion[]{DEFAULT_PROTOCOL_VERSION};
    }

    public static boolean isEqual1_8() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isEqual1_8,
                "Failed to check if protocol is 1.8");
    }

    public static boolean isOlderThanOrEqual1_8() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isOlderThanOrEqual1_8,
                "Failed to check if protocol is <= 1.8");
    }

    public static boolean isLegacyAttackProtocol(Map<String, Boolean> heuristicSources) {
        return CombatProtocolHeuristics.INSTANCE.resolveLegacyOrDefault(
                heuristicSources,
                isOlderThanOrEqual1_8()
        );
    }

    public static boolean isOlderThanOrEquals1_7_10() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isOlderThanOrEqual1_7_10,
                "Failed to check if protocol is <= 1.7.10");
    }

    public static boolean isNewerThanOrEquals1_16() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isNewerThanOrEqual1_16,
                "Failed to check if protocol is >= 1.16");
    }

    public static boolean isNewerThanOrEquals1_21_5() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isNewerThanOrEqual1_21_5,
                "Failed to check if protocol is >= 1.21.5");
    }

    public static boolean isNewerThanOrEquals1_21_6() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isNewerThanOrEqual1_21_6,
                "Failed to check if protocol is >= 1.21.6");
    }

    public static boolean isNewerThanOrEquals1_21_9() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isNewerThanOrEqual1_21_9,
                "Failed to check if protocol is >= 1.21.9");
    }

    public static boolean isOlderThanOrEqual1_11_1() {
        return USES_VIAFABRICPLUS && safeBoolean(VfpCompatibility.INSTANCE::isOlderThanOrEqual1_11_1,
                "Failed to check if protocol is <= 1.11.1");
    }

    public static void selectProtocolVersion(int protocolId) {
        if (!USES_VIAFABRICPLUS) {
            throw new IllegalStateException("ViaFabricPlus is not loaded");
        }
        VfpCompatibility.INSTANCE.unsafeSelectProtocolVersion(protocolId);
    }

    public static void openVfpProtocolSelection() {
        if (!USES_VIAFABRICPLUS) {
            DebugLog.error("ViaFabricPlus is not loaded");
            return;
        }
        VfpCompatibility.INSTANCE.unsafeOpenVfpProtocolSelection();
    }

    public static void send1_8SignUpdate(BlockPos blockPos, String[] lines) {
        if (!USES_VIAFABRICPLUS) {
            throw new IllegalStateException("ViaFabricPlus is missing");
        }
        if (!isEqual1_8()) {
            throw new IllegalStateException("Not 1.8 protocol");
        }
        VfpCompatibility1_8.INSTANCE.sendSignUpdate(blockPos, lines);
    }

    public static void send1_8PlayerInput(float sideways, float forward, boolean jumping, boolean sneaking) {
        if (!USES_VIAFABRICPLUS) {
            throw new IllegalStateException("ViaFabricPlus is missing");
        }
        if (!isEqual1_8()) {
            throw new IllegalStateException("Not 1.8 protocol");
        }
        VfpCompatibility1_8.INSTANCE.sendPlayerInput(sideways, forward, jumping, sneaking);
    }

    private static boolean safeBoolean(BooleanSupplierCall call, String error) {
        try {
            return call.getAsBoolean();
        } catch (Throwable t) {
            DebugLog.error(error, t);
            return false;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplierCall {
        boolean getAsBoolean();
    }
}
