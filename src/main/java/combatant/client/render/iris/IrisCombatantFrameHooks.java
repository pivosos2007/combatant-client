/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import combatant.client.util.logging.DebugLog;

public enum IrisCombatantFrameHooks {
    ;

    private static DeltaTracker currentTickCounter;
    private static boolean renderedAfterFinalization;
    private static boolean rendering;
    private static boolean loggedFrameHook;
    private static boolean loggedMissingFrame;
    private static boolean loggedFinalRender;

    public static void beginRenderLevel(DeltaTracker tickCounter) {
        currentTickCounter = tickCounter;
        renderedAfterFinalization = false;
        if (!loggedFrameHook) {
            loggedFrameHook = true;
            DebugLog.info("[IrisCompat] GameRenderer.renderLevel frame hook active");
        }
    }

    public static void endRenderLevel(DeltaTracker tickCounter) {
        if (currentTickCounter == tickCounter) {
            currentTickCounter = null;
        }
        renderedAfterFinalization = false;
        rendering = false;
    }

    public static boolean isRenderingAfterIrisFinalization() {
        return rendering;
    }

    public static void renderAfterIrisFinalization() {
        if (rendering || renderedAfterFinalization || currentTickCounter == null) {
            if (currentTickCounter == null && !loggedMissingFrame) {
                loggedMissingFrame = true;
                DebugLog.info("[IrisCompat] Iris final pass hook fired without active GameRenderer frame");
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.gameRenderer instanceof IrisFinalizedSceneRenderer renderer)) {
            return;
        }

        renderedAfterFinalization = true;
        rendering = true;
        if (!loggedFinalRender) {
            loggedFinalRender = true;
            DebugLog.info("[IrisCompat] rendering Combatant after Iris final pass");
        }
        try {
            renderer.combatant$renderAfterIrisFinalization(currentTickCounter);
        } finally {
            rendering = false;
        }
    }
}
