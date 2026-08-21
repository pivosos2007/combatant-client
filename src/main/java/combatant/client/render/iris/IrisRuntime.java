/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import combatant.client.util.logging.DebugLog;

public enum IrisRuntime {
    ;
    private static volatile boolean loggedApiFailure;

    public static IrisRuntimeSnapshot snapshot() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return IrisRuntimeSnapshot.UNLOADED;
        }

        try {
            return IrisRuntimeBridge.snapshot();
        } catch (LinkageError | RuntimeException t) {
            if (!loggedApiFailure) {
                loggedApiFailure = true;
                DebugLog.warn("[IrisCompat] Iris runtime probe failed: " + t);
            }
            return loadedApiUnavailable(t.getClass().getSimpleName());
        }
    }

    public static boolean isModLoaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean isShaderpackRendererActive() {
        IrisRuntimeSnapshot snapshot = snapshot();
        return snapshot.modLoaded() && snapshot.apiAvailable() && snapshot.shadersEnabled() && snapshot.shaderpackInUse();
    }

    public static boolean isRenderingShadowPass() {
        IrisRuntimeSnapshot snapshot = snapshot();
        return snapshot.modLoaded() && snapshot.apiAvailable() && snapshot.renderingShadowPass();
    }

    public static boolean isHandRenderingSolid() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            return IrisRuntimeBridge.isHandRenderingSolid();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isHeldItemTranslucent(ItemStack stack) {
        if (stack == null || !FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            return IrisRuntimeBridge.isHeldItemTranslucent(stack);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean hasAnySolidHand() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            return IrisRuntimeBridge.hasAnySolidHand();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static void registerCombatantPipelines() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }

        try {
            IrisRuntimeBridge.registerCombatantPipelines();
        } catch (LinkageError | RuntimeException t) {
            if (!loggedApiFailure) {
                loggedApiFailure = true;
                DebugLog.warn("[IrisCompat] Combatant pipeline registration failed: " + t);
            }
        }
    }

    public static boolean supports(IrisCompatibilityFeature feature) {
        return snapshot().profile().supports(feature);
    }

    private static IrisRuntimeSnapshot loadedApiUnavailable(String status) {
        return new IrisRuntimeSnapshot(
                true,
                false,
                false,
                false,
                false,
                "",
                IrisCompatibilityProfile.NONE,
                status == null || status.isBlank() ? "api unavailable" : status
        );
    }
}
