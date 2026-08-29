/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import combatant.client.util.logging.DebugLog;

public enum IrisRuntime {
    ;
    private static final boolean MOD_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static volatile boolean loggedApiFailure;

    public static IrisRuntimeSnapshot snapshot() {
        if (!MOD_LOADED) {
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
        return MOD_LOADED;
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
        if (!MOD_LOADED) {
            return false;
        }
        try {
            return IrisRuntimeBridge.isHandRenderingSolid();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isHeldItemTranslucent(ItemStack stack) {
        if (stack == null || !MOD_LOADED) {
            return false;
        }
        try {
            return IrisRuntimeBridge.isHeldItemTranslucent(stack);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean hasAnySolidHand() {
        if (!MOD_LOADED) {
            return false;
        }
        try {
            return IrisRuntimeBridge.hasAnySolidHand();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Hot-path variant used by Combatant RHI. No lambda/allocation per draw.
     */
    public static void setNativePipeline(RenderPass pass, RenderPipeline pipeline) {
        if (pass == null || pipeline == null) return;
        if (!MOD_LOADED) {
            pass.setPipeline(pipeline);
            return;
        }

        final boolean previous;
        try {
            previous = IrisRuntimeBridge.beginNativeShaderBypass();
        } catch (LinkageError | RuntimeException ignored) {
            pass.setPipeline(pipeline);
            return;
        }

        try {
            pass.setPipeline(pipeline);
        } finally {
            try {
                IrisRuntimeBridge.restoreNativeShaderBypass(previous);
            } catch (LinkageError | RuntimeException ignored) {
                // Iris reload/teardown: pipeline was already set, never issue the draw operation twice.
            }
        }
    }

    /**
     * Executes a native Combatant pipeline operation without allowing Iris to replace its shader program.
     * Used only for pipelines with a custom vertex contract that cannot be consumed by an Iris ShaderKey.
     */
    public static void runWithNativeShaderBypass(Runnable action) {
        if (action == null) return;
        if (!MOD_LOADED) {
            action.run();
            return;
        }

        final boolean previous;
        try {
            previous = IrisRuntimeBridge.beginNativeShaderBypass();
        } catch (LinkageError | RuntimeException ignored) {
            action.run();
            return;
        }

        try {
            action.run();
        } finally {
            try {
                IrisRuntimeBridge.restoreNativeShaderBypass(previous);
            } catch (LinkageError | RuntimeException ignored) {
                // Iris may be tearing down/reloading. The native operation already completed; do not rerun it.
            }
        }
    }

    public static void registerCombatantPipelines() {
        if (!MOD_LOADED) {
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
