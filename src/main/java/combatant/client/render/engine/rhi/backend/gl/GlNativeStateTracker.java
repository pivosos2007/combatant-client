/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;

/**
 * Differential tracker for GL state that Combatant owns outside Blaze3D's {@code GlStateManager}.
 *
 * <p>Do not mirror depth/cull/color/scissor here: Mojang already caches those states and Combatant
 * must mutate them through {@code GlStateManager}. Stencil, sample-alpha-to-coverage and legacy
 * line smoothing are not represented by that public cache in this Minecraft snapshot, so the RHI
 * owns them explicitly and invalidates the shadow state at foreign/native boundaries.</p>
 */
public final class GlNativeStateTracker {
    private static int generation = 1;

    private static boolean stencilEnableKnown;
    private static boolean stencilEnabled;
    private static boolean stencilMaskKnown;
    private static int stencilWriteMask;
    private static boolean stencilFuncKnown;
    private static int stencilFunc;
    private static int stencilReference;
    private static int stencilValueMask;
    private static boolean stencilOpKnown;
    private static int stencilFail;
    private static int stencilDepthFail;
    private static int stencilDepthPass;

    private static boolean alphaToCoverageKnown;
    private static boolean alphaToCoverageEnabled;

    private static boolean lineSmoothKnown;
    private static boolean lineSmoothEnabled;
    private static boolean lineWidthKnown;
    private static float lineWidth;

    private GlNativeStateTracker() {
    }

    public static int generation() {
        return generation;
    }

    /**
     * Marks all Combatant-owned native state unknown without issuing GL calls.
     * Use this when execution crosses a boundary at which foreign code may have mutated raw GL.
     */
    public static void invalidateAll() {
        generation++;
        if (generation == 0) generation = 1;
        stencilEnableKnown = false;
        stencilMaskKnown = false;
        stencilFuncKnown = false;
        stencilOpKnown = false;
        alphaToCoverageKnown = false;
        lineSmoothKnown = false;
        lineWidthKnown = false;
    }

    public static void invalidateStencil() {
        stencilEnableKnown = false;
        stencilMaskKnown = false;
        stencilFuncKnown = false;
        stencilOpKnown = false;
    }

    public static void stencilEnabled(boolean enabled) {
        if (stencilEnableKnown && stencilEnabled == enabled) return;
        if (enabled) {
            GL11C.glEnable(GL11C.GL_STENCIL_TEST);
        } else {
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        }
        stencilEnabled = enabled;
        stencilEnableKnown = true;
    }

    public static void stencilMask(int mask) {
        if (stencilMaskKnown && stencilWriteMask == mask) return;
        GL11C.glStencilMask(mask);
        stencilWriteMask = mask;
        stencilMaskKnown = true;
    }

    public static void stencilFunc(int function, int reference, int mask) {
        if (stencilFuncKnown
                && stencilFunc == function
                && stencilReference == reference
                && stencilValueMask == mask) {
            return;
        }
        GL11C.glStencilFunc(function, reference, mask);
        stencilFunc = function;
        stencilReference = reference;
        stencilValueMask = mask;
        stencilFuncKnown = true;
    }

    public static void stencilOp(int fail, int depthFail, int depthPass) {
        if (stencilOpKnown
                && stencilFail == fail
                && stencilDepthFail == depthFail
                && stencilDepthPass == depthPass) {
            return;
        }
        GL11C.glStencilOp(fail, depthFail, depthPass);
        stencilFail = fail;
        stencilDepthFail = depthFail;
        stencilDepthPass = depthPass;
        stencilOpKnown = true;
    }

    public static void sampleAlphaToCoverage(boolean enabled) {
        if (alphaToCoverageKnown && alphaToCoverageEnabled == enabled) return;
        if (enabled) {
            GL11C.glEnable(GL13C.GL_SAMPLE_ALPHA_TO_COVERAGE);
        } else {
            GL11C.glDisable(GL13C.GL_SAMPLE_ALPHA_TO_COVERAGE);
        }
        alphaToCoverageEnabled = enabled;
        alphaToCoverageKnown = true;
    }

    public static void lineWidth(float width) {
        if (lineWidthKnown && Float.compare(lineWidth, width) == 0) return;
        GL11C.glLineWidth(width);
        lineWidth = width;
        lineWidthKnown = true;
    }

    public static void lineSmooth(boolean enabled) {
        if (lineSmoothKnown && lineSmoothEnabled == enabled) return;
        if (enabled) {
            GL11C.glEnable(GL11C.GL_LINE_SMOOTH);
        } else {
            GL11C.glDisable(GL11C.GL_LINE_SMOOTH);
        }
        lineSmoothEnabled = enabled;
        lineSmoothKnown = true;
    }
}
