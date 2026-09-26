/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import combatant.client.config.MainConfig;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Canonical production TAA pass. MSAA/TAA exclusivity is owned by VisualConfig.
 *  Native-resolution reprojection intentionally reconstructs the current jittered screen sample
 *  through the stable projection, matching Photon: the raster jitter is not cancelled before
 *  history lookup, so a static output pixel accumulates different sub-pixel samples over time.
 */
public final class TemporalAntiAliasingPass implements PostProcessPass, PostProcessBackendResourceOwner {
    public static final TemporalAntiAliasingPass INSTANCE = new TemporalAntiAliasingPass();

    private final TemporalAntiAliasingBackend backend = new TemporalAntiAliasingBackend();
    private Matrix4f previousView;
    private Matrix4f previousProjection;
    private Vec3 previousCameraPosition;
    private Object previousWorld;
    private boolean historyValid;
    private boolean computeSupported = true;
    private boolean wasSelected;

    private TemporalAntiAliasingPass() { }

    public static boolean shouldJitter() {
        if (!MainConfig.get().isTaaRuntimeActive() || IrisRuntime.isShaderpackRendererActive()) return false;
        try {
            return INSTANCE.computeSupported && PostProcessExecutionPolicy.useCompute(CombatantRenderSystem.rhi());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isActive() {
        boolean selected = MainConfig.get().isTaaRuntimeActive() && !IrisRuntime.isShaderpackRendererActive();
        if (!selected && wasSelected) invalidateHistory();
        wasSelected = selected;
        return selected && computeSupported;
    }

    @Override public int getPriority() { return -100; }
    @Override public Phase getPhase() { return Phase.PRE_HAND; }

    @Override
    public boolean prefersStorageOutput(CombatantRhi rhi) {
        return isActive() && PostProcessExecutionPolicy.useCompute(rhi);
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (!isActive() || execution == null || execution.context() == null || execution.destinationStorage() == null) {
            invalidateHistory();
            return false;
        }

        Matrix4f currentView = CombatantWorldMatrices.positionMatrix();
        Matrix4f currentProjection = CombatantWorldMatrices.unjitteredRenderProjectionMatrix();
        Vec3 currentCamera = CombatantWorldMatrices.cameraPosition();
        if (currentView == null || currentProjection == null
                || currentCamera == null || execution.context().mainDepth() == null) {
            capture(currentView, currentProjection, currentCamera);
            backend.invalidateHistory();
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean sameWorld = mc != null && previousWorld == mc.level;
        boolean usableHistory = historyValid && sameWorld && previousView != null && previousProjection != null
                && previousCameraPosition != null && currentCamera.distanceToSqr(previousCameraPosition) < 4096.0;
        Matrix4f reprojectionView = usableHistory ? previousView : currentView;
        Matrix4f reprojectionProjection = usableHistory ? previousProjection : currentProjection;
        Vec3 cameraDelta = usableHistory ? currentCamera.subtract(previousCameraPosition) : Vec3.ZERO;
        try {
            MainConfig config = MainConfig.get();
            backend.render(
                    execution.rhi(), execution.destinationStorage(), execution.source(), execution.context().mainDepth(),
                    currentView, currentProjection, reprojectionView, reprojectionProjection,
                    cameraDelta, usableHistory,
                    config.isTaaFxaaEnabled(), config.isTaaSharpenEnabled(),
                    config.getTaaSharpeningIntensity());
            capture(currentView, currentProjection, currentCamera);
            PostProcessExecutionPolicy.logComputeActive("taa", "Temporal AA");
            return true;
        } catch (Throwable t) {
            computeSupported = false;
            backend.close();
            invalidateHistory();
            DebugLog.error("[Combatant] TAA compute path failed", t);
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
        Minecraft mc = Minecraft.getInstance();
        previousWorld = mc != null ? mc.level : null;
        historyValid = true;
    }

    public void invalidateHistory() {
        previousView = null;
        previousProjection = null;
        previousCameraPosition = null;
        previousWorld = null;
        historyValid = false;
        backend.invalidateHistory();
    }

    @Override
    public void releaseBackendResources(CombatantRhi owner) {
        backend.release(owner);
        computeSupported = true;
        invalidateHistory();
    }
}
