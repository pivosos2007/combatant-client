/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import combatant.client.render.engine.core.policy.VisibilityQuery;
import combatant.client.util.logging.DebugLog;

/**
 * Single Combatant entry point to Sodium runtime state.
 * <p>
 * Renderers/modules must not call Sodium internals directly. The only allowed consumers are:
 * - this bridge;
 * - Sodium mixins which expose/redirect Sodium-owned internals into this bridge/interop state;
 * - SodiumGL backend classes where GL/Sodium implementation details belong.
 */
public final class SodiumRenderBridge {
    private final SodiumTerrainInterop terrainInterop = new SodiumTerrainInterop(this);
    private final SodiumShaderWorkarounds shaderWorkarounds = new SodiumShaderWorkarounds();
    private final SodiumSectionVisibilityProvider sectionVisibilityProvider = new SodiumSectionVisibilityProvider(this);

    private SodiumFrameContext currentFrameContext = SodiumFrameContext.UNAVAILABLE;
    private boolean primaryVisibilityReady;

    private long visibilityQueries;
    private long visibilityAccepts;
    private long visibilityRejects;
    private long visibilityBypasses;
    private long visibilityUnavailableBypasses;
    private long visibilityOptOutBypasses;
    private long visibilityErrors;

    public SodiumFrameContext beginFrame() {
        resetFrameStats();
        primaryVisibilityReady = false;
        currentFrameContext = captureFrameContext();
        terrainInterop.beginFrame();
        return currentFrameContext;
    }

    public SodiumFrameContext currentFrameContext() {
        return currentFrameContext;
    }

    public SodiumSectionVisibilityProvider visibilityProvider() {
        return sectionVisibilityProvider;
    }

    public SodiumTerrainInterop terrainInterop() {
        return terrainInterop;
    }

    public SodiumShaderWorkarounds shaderWorkarounds() {
        return shaderWorkarounds;
    }

    public SodiumFrameContext captureFrameContext() {
        Object renderer = sodiumWorldRenderer();
        if (!(renderer instanceof SodiumWorldVisibilityView)) return SodiumFrameContext.UNAVAILABLE;
        return SodiumFrameContext.available(primaryVisibilityReady, 0,
                primaryVisibilityReady ? "primary-visibility-ready" : "primary-visibility-pending");
    }

    public boolean isAvailableForWorldFrame() {
        return primaryVisibilityReady && sodiumWorldRenderer() instanceof SodiumWorldVisibilityView;
    }

    /** Called by the Sodium setupTerrain boundary before rebuilding primary-camera visibility. */
    public void markPrimaryVisibilityPending() {
        primaryVisibilityReady = false;
        currentFrameContext = captureFrameContext();
    }

    /** Called only after Sodium has published the current setupTerrain visibility tree. */
    public void markPrimaryVisibilityReady() {
        primaryVisibilityReady = true;
        currentFrameContext = captureFrameContext();
    }

    /**
     * Compatibility entry point. Prefer visibilityProvider().isBoxVisible(...).
     */
    public boolean isBoxVisible(AABB box) {
        return isSectionBoxVisible(box, VisibilityQuery.worldOverlay(box));
    }

    public boolean isSectionBoxVisible(AABB box, VisibilityQuery query) {
        visibilityQueries++;
        if (box == null) {
            visibilityBypasses++;
            return true;
        }
        if (query != null && query.alwaysVisible()) {
            visibilityBypasses++;
            visibilityOptOutBypasses++;
            return true;
        }
        if (query != null && !query.useSectionVisibility()) {
            visibilityBypasses++;
            visibilityOptOutBypasses++;
            return true;
        }

        if (!primaryVisibilityReady) {
            visibilityBypasses++;
            visibilityUnavailableBypasses++;
            return true;
        }

        Object renderer = sodiumWorldRenderer();
        if (!(renderer instanceof SodiumWorldVisibilityView visibility)) {
            visibilityBypasses++;
            visibilityUnavailableBypasses++;
            return true;
        }
        try {
            boolean visible = visibility.combatant$isBoxVisible(box);
            if (visible) visibilityAccepts++;
            else visibilityRejects++;
            return visible;
        } catch (Throwable ignored) {
            // Visibility is an optimization only. A compat failure must never hide geometry.
            visibilityErrors++;
            visibilityBypasses++;
            return true;
        }
    }

    public void scheduleTerrainUpdate() {
        Object renderer = sodiumWorldRenderer();
        if (renderer == null) {
            terrainInterop.recordInteropError();
            return;
        }
        try {
            renderer.getClass().getMethod("scheduleTerrainUpdate").invoke(renderer);
            terrainInterop.recordTerrainUpdateScheduled();
        } catch (Throwable ignored) {
            terrainInterop.recordInteropError();
        }
    }

    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        Object renderer = sodiumWorldRenderer();
        if (renderer == null) {
            terrainInterop.recordInteropError();
            return;
        }
        try {
            renderer.getClass()
                    .getMethod("scheduleRebuildForBlockArea", int.class, int.class, int.class, int.class, int.class, int.class, boolean.class)
                    .invoke(renderer, minX, minY, minZ, maxX, maxY, maxZ, important);
            terrainInterop.recordRebuildScheduled();
        } catch (Throwable ignored) {
            terrainInterop.recordInteropError();
        }
    }

    public void reloadWorldRenderer() {
        Object renderer = sodiumWorldRenderer();
        if (renderer == null) {
            terrainInterop.recordInteropError();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.gameRenderer == null) {
            terrainInterop.recordInteropError();
            return;
        }

        // This method used to invoke SodiumWorldRenderer.reload(). Surface ownership changes are
        // discovered from the renderLayer() boundary, where destroying RenderSectionManager is
        // re-entrant and can leave its ChunkBuilder stopped if teardown throws halfway through.
        // Re-mesh every loaded section instead: it updates the captured material/routing flags
        // without replacing Sodium's live manager or stopping its worker threads.
        if (minecraft.gameRenderer.mainCamera() == null) {
            terrainInterop.recordInteropError();
            return;
        }
        BlockPos camera = minecraft.gameRenderer.mainCamera().blockPosition();
        int centerChunkX = camera.getX() >> 4;
        int centerChunkZ = camera.getZ() >> 4;
        int radius = Math.max(2, minecraft.options.getEffectiveRenderDistance() + 2);
        try {
            renderer.getClass().getMethod(
                            "scheduleRebuildForChunks",
                            int.class, int.class, int.class,
                            int.class, int.class, int.class,
                            boolean.class)
                    .invoke(renderer,
                            centerChunkX - radius, minecraft.level.getMinSectionY(), centerChunkZ - radius,
                            centerChunkX + radius, minecraft.level.getMaxSectionY() - 1, centerChunkZ + radius,
                            true);
            renderer.getClass().getMethod("scheduleTerrainUpdate").invoke(renderer);
            terrainInterop.recordRebuildScheduled();
        } catch (Throwable failure) {
            terrainInterop.recordInteropError();
            DebugLog.error("[SODIUM] failed to schedule loaded-section rebuild", failure);
        }
    }

    private static Object sodiumWorldRenderer() {
        try {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            return type.getMethod("instanceNullable").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public SodiumVisibilityStatsSnapshot visibilityStatsSnapshot() {
        return new SodiumVisibilityStatsSnapshot(
                visibilityQueries,
                visibilityAccepts,
                visibilityRejects,
                visibilityBypasses,
                visibilityUnavailableBypasses,
                visibilityOptOutBypasses,
                visibilityErrors
        );
    }

    public void resetFrameStats() {
        visibilityQueries = 0L;
        visibilityAccepts = 0L;
        visibilityRejects = 0L;
        visibilityBypasses = 0L;
        visibilityUnavailableBypasses = 0L;
        visibilityOptOutBypasses = 0L;
        visibilityErrors = 0L;
    }

}
