/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import combatant.client.render.engine.core.RenderFrameContext;

public interface RenderCommandBuffer {
    void clear();

    int size();

    void submit(RenderFrameContext context);
}
