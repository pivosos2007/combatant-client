/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import combatant.client.mixininterface.IGpuDevice;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Unified GPU scissor utility for both scaled (HUD/UI) and unscaled (screen) spaces.
 * <p>
 * Coordinates are always top-left based before conversion to Blaze3D framebuffer space.
 */
public enum ScissorFunction {
    ;

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Deque<ScissorState> STACK = new ArrayDeque<>();
    private static ScissorState appliedScissor;
    private static long nextSnapshotId = 1L;

    /**
     * Push scissor using top-left coordinates in the caller's current framebuffer/UI space.
     */
    public static boolean pushRaw(
            float x,
            float y,
            float width,
            float height
    ) {
        if (mc == null || mc.getWindow() == null) return false;

        float uiScale = ViewportContext.getUiScale();
        if (uiScale != 1.0f) {
            x *= uiScale;
            y *= uiScale;
            width *= uiScale;
            height *= uiScale;
        }

        if (!(width > 0.0f) || !(height > 0.0f)) {
            return false;
        }

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        if (fbw <= 0 || fbh <= 0) return false;

        int sx = fastRound(x);
        int sy = fastRound(fbh - (y + height));
        int sw = fastRound(width);
        int sh = fastRound(height);

        ScissorRect raw = new ScissorRect(sx, sy, sw, sh);
        ScissorRect clipped = clampToFramebuffer(raw, fbw, fbh);

        if (!STACK.isEmpty()) {
            ScissorRect top = STACK.peek().rect;
            clipped = intersect(top, clipped);
            if (clipped.isEmpty()) {
                // Completely outside the parent clip. Keep the parent scissor active and let
                // the caller render normally; the parent clip will discard the geometry.
                // Returning false is important because callers must not pop a clip we did not push.
                return false;
            }
        }

        if (clipped.isEmpty()) {
            return false;
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        STACK.push(new ScissorState(clipped, new UiScissorSnapshot(
                allocateSnapshotId(), clipped.x, clipped.y, clipped.w, clipped.h
        )));
        applyTop();
        return true;
    }


    /**
     * Push scissor in scaled UI space.
     *
     * @param scale UI scale factor (same you use for rendering)
     */
    public static boolean pushScaled(
            float x,
            float y,
            float width,
            float height,
            float scale
    ) {
        if (scale <= 0f) return false;
        return pushRaw(
                x * scale,
                y * scale,
                width * scale,
                height * scale
        );
    }

    public static boolean pushRawRect(
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        return pushRaw(
                x1,
                y1,
                x2 - x1,
                y2 - y1
        );
    }

    public static boolean pushScaledRect(
            float x1,
            float y1,
            float x2,
            float y2,
            float scale
    ) {
        return pushScaled(
                x1,
                y1,
                x2 - x1,
                y2 - y1,
                scale
        );
    }


    public static int[] currentFramebufferScissor() {
        return currentSnapshot().framebufferRect();
    }

    public static UiScissorSnapshot currentSnapshot() {
        ScissorState state = appliedScissor;
        return state != null ? state.snapshot : UiScissorSnapshot.NONE;
    }

    public static void pop() {
        if (STACK.isEmpty()) return;
        // Preserve the closing scissor in deferred submit metadata.
        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        STACK.pop();
        applyTop();
    }

    private static int fastRound(float v) {
        return (int) (v + (v >= 0 ? 0.5f : -0.5f));
    }

    private static void applyTop() {
        ScissorState next = STACK.peek();
        if (next != null && appliedScissor != null && next.rect.equals(appliedScissor.rect)) {
            // Logical identity still changes even when the effective GPU rectangle does not.
            appliedScissor = next;
            return;
        }
        if (appliedScissor != null) {
            ((IGpuDevice) RenderSystem.getDevice()).combatant$popScissor();
            appliedScissor = null;
        }

        if (next == null) return;
        if (next.rect.isEmpty()) {
            throw new IllegalStateException("Attempted to apply empty scissor: " + next);
        }

        ((IGpuDevice) RenderSystem.getDevice()).combatant$pushScissor(
                next.rect.x, next.rect.y, next.rect.w, next.rect.h
        );
        appliedScissor = next;
    }

    private static long allocateSnapshotId() {
        long id = nextSnapshotId++;
        if (id == 0L) id = nextSnapshotId++;
        return id;
    }

    private static ScissorRect clampToFramebuffer(ScissorRect r, int fbw, int fbh) {
        int x1 = Math.max(0, r.x);
        int y1 = Math.max(0, r.y);
        int x2 = Math.min(fbw, r.x + r.w);
        int y2 = Math.min(fbh, r.y + r.h);
        return new ScissorRect(x1, y1, x2 - x1, y2 - y1);
    }

    private static ScissorRect intersect(ScissorRect a, ScissorRect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.w, b.x + b.w);
        int y2 = Math.min(a.y + a.h, b.y + b.h);
        return new ScissorRect(x1, y1, x2 - x1, y2 - y1);
    }

    private static IllegalStateException invalid(
            String reason,
            float inputX,
            float inputY,
            float inputW,
            float inputH,
            ScissorRect raw,
            ScissorRect parent
    ) {
        int fbw = mc != null && mc.getWindow() != null ? mc.getWindow().getWidth() : -1;
        int fbh = mc != null && mc.getWindow() != null ? mc.getWindow().getHeight() : -1;
        return new IllegalStateException(
                "Invalid scissor: reason=" + reason
                        + ", input=[x=" + inputX + ", y=" + inputY + ", w=" + inputW + ", h=" + inputH + "]"
                        + ", raw=" + raw
                        + ", parent=" + parent
                        + ", framebuffer=" + fbw + "x" + fbh
                        + ", stackDepth=" + STACK.size()
        );
    }

    private record ScissorRect(int x, int y, int w, int h) {
        boolean isEmpty() {
            return w <= 0 || h <= 0;
        }
    }

    private record ScissorState(ScissorRect rect, UiScissorSnapshot snapshot) {
    }
}
