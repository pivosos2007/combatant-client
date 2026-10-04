/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.item.ItemStack;
import combatant.client.util.logging.DebugLog;

public enum IrisRuntime {
    ;
    private static final boolean MOD_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static final IrisIntegrationEpochTracker INTEGRATION_EPOCH = new IrisIntegrationEpochTracker();
    private static volatile boolean loggedApiFailure;

    public static IrisRuntimeSnapshot snapshot() {
        if (!MOD_LOADED) {
            return IrisRuntimeSnapshot.UNLOADED;
        }

        try {
            return INTEGRATION_EPOCH.stamp(IrisRuntimeBridge.snapshot());
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

    public static void observeFrame(Object worldOwner, String dimensionId, int width, int height) {
        if (!MOD_LOADED) return;
        INTEGRATION_EPOCH.observeFrame(worldOwner, dimensionId, width, height);
    }

    public static void observePipeline(Object pipelineOwner) {
        if (!MOD_LOADED) return;
        INTEGRATION_EPOCH.observePipeline(pipelineOwner);
    }

    public static void pipelineDestroyed(Object pipelineOwner) {
        if (!MOD_LOADED) return;
        INTEGRATION_EPOCH.pipelineDestroyed(pipelineOwner);
    }

    public static void invalidateIntegration(String reason) {
        if (!MOD_LOADED) return;
        INTEGRATION_EPOCH.invalidate(reason);
    }

    public static long integrationEpoch() {
        return MOD_LOADED ? INTEGRATION_EPOCH.epoch() : 0L;
    }

    public static boolean isShaderpackRendererActive() {
        IrisRuntimeSnapshot snapshot = snapshot();
        return snapshot.modLoaded() && snapshot.apiAvailable() && snapshot.shadersEnabled() && snapshot.shaderpackInUse();
    }

    /**
     * True while Combatant submits its imported geometry through Iris (Iris' safeToMultiply
     * window). Reading Iris' ImmediateState directly from always-applied mixins crashes when
     * Iris is not installed, so the access lives behind the loaded check here.
     */
    public static boolean isImportedGeometrySubmission() {
        if (!MOD_LOADED) {
            return false;
        }
        try {
            return IrisRuntimeBridge.isImportedGeometrySubmission();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
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

    /** Builds the complete first-person hand scene for the native post-shaderpack pass. */
    public static boolean submitNativeHandScene(float tickDelta,
                                                PoseStack poseStack,
                                                SubmitNodeStorage storage) {
        if (!MOD_LOADED || poseStack == null || storage == null) return false;
        try {
            return IrisRuntimeBridge.submitNativeHandScene(tickDelta, poseStack, storage);
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

    /** Submits canonical imported geometry only when the selected data-driven patch advertises it. */
    public static void renderImportedGeometryPrimary() {
        if (!MOD_LOADED) return;
        try {
            IrisRuntimeBridge.renderImportedGeometryPrimary();
        } catch (LinkageError | RuntimeException t) {
            DebugLog.warnOnChange("iris.imported.geometry.submit", t.getClass().getName(),
                    "[IrisCompat] imported geometry submission failed: %s: %s",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /** Called from Iris beginTranslucents after the shaderpack switched to its forward target. */
    public static void renderImportedGeometryTranslucent() {
        if (!MOD_LOADED) return;
        try {
            IrisRuntimeBridge.renderImportedGeometryTranslucent();
        } catch (LinkageError | RuntimeException t) {
            DebugLog.warnOnChange("iris.imported.geometry.translucent", t.getClass().getName(),
                    "[IrisCompat] imported translucent geometry submission failed: %s: %s",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    public static void releaseImportedGeometryBackend(combatant.client.render.engine.rhi.CombatantRhi rhi) {
        if (!MOD_LOADED || rhi == null) return;
        try {
            IrisRuntimeBridge.releaseImportedGeometryBackend(rhi);
        } catch (LinkageError | RuntimeException ignored) {
            // Iris may already be tearing down; backend close remains authoritative.
        }
    }

    public static void invalidateImportedGeometryResidency() {
        if (!MOD_LOADED) return;
        try {
            IrisRuntimeBridge.invalidateImportedGeometryResidency();
        } catch (LinkageError | RuntimeException ignored) {
            // The optional Iris adapter may not be linkable during reload or teardown.
        }
    }

    /** Applies draw-scoped imported geometry uniforms after Iris has bound the real GL program. */
    public static void applyImportedGeometryDrawUniforms() {
        if (!MOD_LOADED) return;
        try {
            IrisRuntimeBridge.applyImportedGeometryDrawUniforms();
        } catch (LinkageError | RuntimeException ignored) {
            // Optional Iris adapter may be unavailable during reload/teardown.
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
                "",
                java.util.Set.of(),
                INTEGRATION_EPOCH.epoch(),
                INTEGRATION_EPOCH.reason(),
                status == null || status.isBlank() ? "api unavailable" : status
        );
    }
}
