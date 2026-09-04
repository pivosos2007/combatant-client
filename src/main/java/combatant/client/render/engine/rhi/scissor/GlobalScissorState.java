/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.scissor;

import com.mojang.blaze3d.systems.RenderPass;

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

    private GlobalScissorState() {
    }

    public static void push(int x, int y, int width, int height) {
        if (set) {
            throw new IllegalStateException("Global scissor already set");
        }
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid global scissor: x=" + x + ", y=" + y
                    + ", width=" + width + ", height=" + height);
        }
        GlobalScissorState.x = x;
        GlobalScissorState.y = y;
        GlobalScissorState.width = width;
        GlobalScissorState.height = height;
        set = true;
    }

    public static void pop() {
        if (!set) {
            throw new IllegalStateException("No global scissor set");
        }
        set = false;
    }

    public static boolean isSet() {
        return set;
    }

    public static Snapshot snapshot() {
        return set ? new Snapshot(x, y, width, height) : null;
    }

    public static void replace(int x, int y, int width, int height) {
        if (set) pop();
        push(x, y, width, height);
    }

    public static boolean applyTo(RenderPass pass, RenderPass.RenderArea renderArea) {
        if (!set || pass == null || renderArea == null) {
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
            return false;
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
