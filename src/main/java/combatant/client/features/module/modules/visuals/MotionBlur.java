/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.postprocess.*;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Motion-vector compute blur extracted from the latest ReimaginedVisual camera-post path. */
@ModuleInfo(id = "motionblur", displayName = "MotionBlur", category = ModuleCategory.VISUALS,
        description = "module.motionblur.description")
public final class MotionBlur extends Module implements PostProcessPass, PostProcessBackendResourceOwner {
    private final Minecraft mc = Minecraft.getInstance();
    private final TemporalMotionBlurBackend backend = new TemporalMotionBlurBackend();

    private final NumberValue<Float> strength = num("motionBlurStrength", "strength", 0.42f, 0.0f, 1.5f);
    private final NumberValue<Integer> maxPixels = num("motionBlurMaxPixels", "max_pixels", 18, 0, 64);
    private final NumberValue<Float> minMotionPixels = num("motionBlurMinMotionPixels", "min_motion_pixels", 0.45f, 0.0f, 4.0f);
    private final NumberValue<Float> shutterScale = num("motionBlurShutterScale", "shutter_scale", 1.0f, 0.0f, 6.0f);
    private final NumberValue<Integer> samples = num("motionBlurSamples", "samples", 12, 4, 32);
    private final NumberValue<Float> depthEdgeProtection = num("motionBlurDepthEdgeProtection", "depth_edge_protection", 1.0f, 0.0f, 4.0f);

    private Matrix4f previousView;
    private Matrix4f previousProjection;
    private Vec3 previousCameraPosition;
    private Object previousWorld;
    private boolean historyValid;
    private boolean computeSupported = true;

    public MotionBlur() {
        PostProcessManager.register(this);
    }

    public static boolean isActiveStatic() {
        MotionBlur module = Modules.get(MotionBlur.class);
        return module != null && module.isActive();
    }

    @Override
    public boolean isActive() {
        return isEnabled() && computeSupported && strength.get() > 0.0001f
                && mc != null && mc.player != null && mc.level != null;
    }

    @Override public int getPriority() { return 4; }
    @Override public Phase getPhase() { return Phase.PRE_HAND; }

    @Override
    public boolean prefersStorageOutput(CombatantRhi rhi) {
        return isActive() && PostProcessExecutionPolicy.useCompute(rhi);
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (!isActive() || execution == null || execution.context() == null) {
            invalidateHistory();
            return false;
        }
        Matrix4f currentView = CombatantWorldMatrices.positionMatrix();
        Matrix4f currentProjection = CombatantWorldMatrices.unjitteredRenderProjectionMatrix();
        Vec3 currentCamera = CombatantWorldMatrices.cameraPosition();
        if (currentView == null || currentProjection == null || currentCamera == null
                || execution.context().mainDepth() == null || execution.destinationStorage() == null) {
            capture(currentView, currentProjection, currentCamera);
            return false;
        }

        boolean sameWorld = previousWorld == mc.level;
        boolean usableHistory = historyValid && sameWorld && previousView != null
                && previousProjection != null && previousCameraPosition != null
                && currentCamera.distanceToSqr(previousCameraPosition) < 4096.0;
        Matrix4f reprojectionView = usableHistory ? previousView : currentView;
        Matrix4f reprojectionProjection = usableHistory ? previousProjection : currentProjection;
        Vec3 cameraDelta = usableHistory ? currentCamera.subtract(previousCameraPosition) : Vec3.ZERO;

        try {
            backend.render(
                    execution.rhi(), execution.destinationStorage(), execution.source(), execution.context().mainDepth(),
                    currentView, currentProjection, reprojectionView, reprojectionProjection, cameraDelta, usableHistory,
                    strength.get(), maxPixels.get(), minMotionPixels.get(), shutterScale.get(), samples.get(),
                    depthEdgeProtection.get());
            capture(currentView, currentProjection, currentCamera);
            PostProcessExecutionPolicy.logComputeActive("motion-blur", "Motion Blur");
            return usableHistory;
        } catch (Throwable t) {
            computeSupported = false;
            backend.close();
            invalidateHistory();
            DebugLog.error("[Combatant] motion-vector MotionBlur compute path failed", t);
            return false;
        }
    }

    private void capture(Matrix4f view, Matrix4f projection, Vec3 cameraPosition) {
        if (view == null || projection == null || cameraPosition == null) {
            invalidateHistory();
            return;
        }
        previousView = new Matrix4f(view);
        previousProjection = new Matrix4f(projection);
        previousCameraPosition = cameraPosition;
        previousWorld = mc.level;
        historyValid = true;
    }

    private void invalidateHistory() {
        previousView = null;
        previousProjection = null;
        previousCameraPosition = null;
        previousWorld = null;
        historyValid = false;
    }

    @Override public void onEnable() { computeSupported = true; invalidateHistory(); }
    @Override public void onDisable() { backend.close(); invalidateHistory(); }

    @Override
    public void releaseBackendResources(CombatantRhi owner) {
        backend.release(owner);
        computeSupported = true;
        invalidateHistory();
    }
}
