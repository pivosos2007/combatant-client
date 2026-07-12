/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;

public enum SystemCursor {
    ;

    private static CursorType current = null;
    private static boolean requestedThisFrame = false;
    private static int frameDepth = 0;
    private static long activeRenderFrameId = Long.MIN_VALUE;
    private static GuiGraphicsExtractor activeContext;

    public static void set(CursorType type) {
        if (type == null) type = CursorType.DEFAULT;
        requestedThisFrame = true;
        com.mojang.blaze3d.platform.cursor.CursorType cursor = resolveCursor(type);
        if (activeContext != null) {
            activeContext.requestCursor(cursor);
            current = type;
            return;
        }
        if (type == current) return;
        Minecraft mc = Minecraft.getInstance();
        Window window = mc != null ? mc.getWindow() : null;
        if (window != null) {
            window.selectCursor(cursor);
        } else {
            return;
        }
        current = type;
    }

    public static void reset() {
        set(CursorType.DEFAULT);
    }

    public static void invalidate() {
        current = null;
    }

    public static void beginFrame() {
        beginFrame(null);
    }

    public static void beginFrame(GuiGraphicsExtractor context) {
        long renderFrameId = currentRenderFrameId();
        if (frameDepth++ == 0) {
            activeRenderFrameId = renderFrameId;
            requestedThisFrame = false;
        }
        if (context != null) {
            activeContext = context;
        }
    }

    public static void endFrame() {
        if (frameDepth > 0 && --frameDepth > 0) return;
        frameDepth = 0;
        boolean contextBacked = activeContext != null;
        if (!requestedThisFrame && !contextBacked) {
            set(CursorType.DEFAULT);
        }
        activeContext = null;
    }

    private static com.mojang.blaze3d.platform.cursor.CursorType resolveCursor(CursorType type) {
        return switch (type) {
            case MOVE, HAND -> CursorTypes.POINTING_HAND;
            case TEXT -> CursorTypes.IBEAM;
            case CROSSHAIR -> CursorTypes.CROSSHAIR;
            case SCROLL, RESIZE_VERTICAL, RESIZE_NWSE, RESIZE_NESW -> CursorTypes.RESIZE_NS;
            case RESIZE_HORIZONTAL -> CursorTypes.RESIZE_EW;
            case RESIZE_ALL -> CursorTypes.RESIZE_ALL;
            case NOT_ALLOWED -> CursorTypes.NOT_ALLOWED;
            case DEFAULT -> com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT;
        };
    }

    private static long currentRenderFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : Long.MIN_VALUE;
    }

    public enum CursorType {
        DEFAULT,
        MOVE,
        HAND,
        TEXT,
        CROSSHAIR,
        SCROLL,
        RESIZE_HORIZONTAL,
        RESIZE_VERTICAL,
        RESIZE_NWSE,
        RESIZE_NESW,
        RESIZE_ALL,
        NOT_ALLOWED
    }
}
