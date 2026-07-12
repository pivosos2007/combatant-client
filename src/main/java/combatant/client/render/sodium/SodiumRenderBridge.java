/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.minecraft.world.phys.AABB;
import combatant.client.render.engine.core.policy.VisibilityQuery;

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

    private long visibilityQueries;
    private long visibilityAccepts;
    private long visibilityRejects;
    private long visibilityBypasses;
    private long visibilityUnavailableBypasses;
    private long visibilityOptOutBypasses;
    private long visibilityErrors;

    public SodiumFrameContext beginFrame() {
        resetFrameStats();
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
        return SodiumFrameContext.UNAVAILABLE;
    }

    public boolean isAvailableForWorldFrame() {
        return false;
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

        visibilityBypasses++;
        visibilityUnavailableBypasses++;
        return true;
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
        try {
            renderer.getClass().getMethod("reload").invoke(renderer);
            terrainInterop.recordRebuildScheduled();
        } catch (Throwable ignored) {
            terrainInterop.recordInteropError();
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
