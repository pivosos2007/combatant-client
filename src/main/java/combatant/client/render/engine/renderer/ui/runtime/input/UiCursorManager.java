/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.helpers.SystemCursor;

import java.util.Locale;

public final class UiCursorManager {
    public void apply(String cursor) {
        if (cursor == null || cursor.isBlank()) return;
        SystemCursor.set(resolve(cursor));
    }

    private SystemCursor.CursorType resolve(String cursor) {
        return switch (cursor.toLowerCase(Locale.ROOT)) {
            case "hand", "pointer", "click" -> SystemCursor.CursorType.HAND;
            case "move", "drag" -> SystemCursor.CursorType.MOVE;
            case "text", "ibeam", "input" -> SystemCursor.CursorType.TEXT;
            case "crosshair", "pick" -> SystemCursor.CursorType.CROSSHAIR;
            case "scroll" -> SystemCursor.CursorType.SCROLL;
            case "ew-resize", "resize-x", "horizontal" -> SystemCursor.CursorType.RESIZE_HORIZONTAL;
            case "ns-resize", "resize-y", "vertical" -> SystemCursor.CursorType.RESIZE_VERTICAL;
            case "nwse-resize" -> SystemCursor.CursorType.RESIZE_NWSE;
            case "nesw-resize" -> SystemCursor.CursorType.RESIZE_NESW;
            case "all-scroll", "resize-all" -> SystemCursor.CursorType.RESIZE_ALL;
            case "not-allowed", "disabled" -> SystemCursor.CursorType.NOT_ALLOWED;
            default -> SystemCursor.CursorType.DEFAULT;
        };
    }
}
