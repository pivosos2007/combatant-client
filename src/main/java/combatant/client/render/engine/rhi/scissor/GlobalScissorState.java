/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.scissor;

import com.mojang.renderpearl.api.commands.RenderPass;

/**
 * Backend-neutral pending scissor for Combatant immediate/RHI submissions.
 *
 * <p>The stored rectangle uses the same framebuffer-space convention that
 * vanilla {@code GuiRenderer} passes to {@link RenderPass#enableScissor}:
 * X is left, Y is already converted from top-left UI coordinates to the
 * framebuffer scissor origin expected by Blaze3D. Do not add a Vulkan-specific
 * second Y conversion here; Blaze3D's RenderPass abstraction owns that backend
 * detail.</p>
 */
public final class GlobalScissorState {
    private static int x;
    private static int y;
    private static int width;
    private static int height;
    private static boolean set;

    // A deferred HUD replay may run inside a logical UI clip. Keep the previous GPU
    // scissor instead of treating nested submissions as an illegal state. Reuse a
    // primitive stack to avoid allocations during the render thread hot path.
    private static int depth;
    private static int[] saved = new int[4 * 8];

    // Offscreen post-process targets have their own coordinate system. Suspending
    // clipping must mask ALL nested scopes, not pop just the innermost scope.
    private static int suspensionDepth;

    private GlobalScissorState() {
    }

    public static void push(int x, int y, int width, int height) {
        validate(x, y, width, height);
        if (set) {
            int offset = depth * 4;
            if (offset + 4 > saved.length) {
                saved = java.util.Arrays.copyOf(saved, saved.length * 2);
            }
            saved[offset] = GlobalScissorState.x;
            saved[offset + 1] = GlobalScissorState.y;
            saved[offset + 2] = GlobalScissorState.width;
            saved[offset + 3] = GlobalScissorState.height;
            depth++;
        }
        setRect(x, y, width, height);
    }

    public static void pop() {
        if (!set) {
            throw new IllegalStateException("No global scissor set");
        }
        if (depth == 0) {
            set = false;
            return;
        }
        int offset = --depth * 4;
        setRect(saved[offset], saved[offset + 1], saved[offset + 2], saved[offset + 3]);
    }

    public static boolean isSet() {
        return set;
    }

    /**
     * Called only at the beginning of the next UI extraction frame. The previous
     * frame may have aborted a HUD element before its matching pop/resume.
     * Discard stale logical scopes; no render pass may inherit their rectangles.
     */
    public static void resetForNewFrame() {
        set = false;
        depth = 0;
        suspensionDepth = 0;
    }

    /** The actually applied rectangle (null while an offscreen pass masks clipping). */
    public static Snapshot snapshot() {
        return set && suspensionDepth == 0 ? new Snapshot(x, y, width, height) : null;
    }

    /** Replace only the current scope, retaining any enclosing UI scissor. */
    public static void replace(int x, int y, int width, int height) {
        validate(x, y, width, height);
        if (!set) {
            push(x, y, width, height);
        } else {
            setRect(x, y, width, height);
        }
    }

    /** Mask all scissor scopes while drawing into a different framebuffer space. */
    public static boolean suspend() {
        if (!set) return false;
        suspensionDepth++;
        return true;
    }

    /** Resume the previous effective scope after an offscreen draw. */
    public static void resume() {
        if (suspensionDepth <= 0) {
            throw new IllegalStateException("No suspended global scissor");
        }
        suspensionDepth--;
    }

    private static void setRect(int x, int y, int width, int height) {
        GlobalScissorState.x = x;
        GlobalScissorState.y = y;
        GlobalScissorState.width = width;
        GlobalScissorState.height = height;
        set = true;
    }

    private static void validate(int x, int y, int width, int height) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid global scissor: x=" + x + ", y=" + y
                    + ", width=" + width + ", height=" + height);
        }
    }

    public static boolean applyTo(RenderPass pass, RenderPass.RenderArea renderArea) {
        if (!set || suspensionDepth > 0 || pass == null || renderArea == null) {
            return false;
        }

        int ax1 = renderArea.x();
        int ay1 = renderArea.y();
        int ax2 = ax1 + renderArea.width();
        int ay2 = ay1 + renderArea.height();

        int sx1 = Math.max(x, ax1);
        int sy1 = Math.max(y, ay1);
        int sx2 = Math.min(x + width, ax2);
        int sy2 = Math.min(y + height, ay2);
        int sw = sx2 - sx1;
        int sh = sy2 - sy1;
        if (sw <= 0 || sh <= 0) {
            // "No intersection" must not silently disable clipping and render the
            // entire offscreen target. RenderPass does not accept an empty scissor;
            // restrict to a single legal pixel instead.
            if (renderArea.width() <= 0 || renderArea.height() <= 0) return false;
            pass.enableScissor(ax1, ay1, 1, 1);
            return true;
        }

        pass.enableScissor(sx1, sy1, sw, sh);
        return true;
    }

    public record Snapshot(int x, int y, int width, int height) {
        public int[] rect() {
            return new int[]{x, y, width, height};
        }
    }
}
