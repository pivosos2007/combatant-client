/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import combatant.client.render.engine.depth.WorldSceneDepth;

public enum IrisSecondHandScene {
    ;
    private static WorldRenderingPipeline pendingPipeline;
    private static boolean rendering;

    public static boolean deferFinalization(WorldRenderingPipeline pipeline) {
        if (rendering) {
            return false;
        }

        if (!IrisCompatibilityGuards.deferIrisFinalizationForSecondHandScene()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            WorldSceneDepth.captureResolvedMain(client.gameRenderer.mainRenderTarget());
        }

        if (pendingPipeline != null && pendingPipeline != pipeline) {
            pendingPipeline.finalizeLevelRendering();
        }
        pendingPipeline = pipeline;
        return true;
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static void finalizeWorld() {
        WorldRenderingPipeline pipeline = pendingPipeline;
        pendingPipeline = null;
        if (pipeline == null) {
            return;
        }

        rendering = true;
        try {
            pipeline.finalizeLevelRendering();
        } finally {
            rendering = false;
        }
    }
}
