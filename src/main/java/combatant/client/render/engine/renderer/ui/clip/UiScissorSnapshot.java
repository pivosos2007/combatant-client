/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.clip;

/** Immutable framebuffer-space state captured independently from shape clipping. */
public record UiScissorSnapshot(long id, int x, int y, int width, int height) {
    public static final UiScissorSnapshot NONE = new UiScissorSnapshot(0L, 0, 0, 0, 0);

    public boolean active() {
        return id != 0L && width > 0 && height > 0;
    }

    public int[] framebufferRect() {
        return active() ? new int[]{x, y, width, height} : null;
    }
}
