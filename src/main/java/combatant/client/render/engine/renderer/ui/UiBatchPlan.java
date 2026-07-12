/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

public record UiBatchPlan(int commandCount,
                          int shapeCount,
                          int pathCount,
                          int textureCount,
                          int textCount,
                          int itemCount,
                          int effectCount,
                          int backendCommandCount) {
    public static final UiBatchPlan EMPTY = new UiBatchPlan(0, 0, 0, 0, 0, 0, 0, 0);

    public int batchCount() {
        int count = 0;
        if (shapeCount > 0) count++;
        if (pathCount > 0) count++;
        if (textureCount > 0) count++;
        if (textCount > 0) count++;
        if (itemCount > 0) count++;
        if (effectCount > 0) count++;
        if (backendCommandCount > 0) count++;
        return count;
    }
}
