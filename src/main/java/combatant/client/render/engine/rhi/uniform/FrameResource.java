/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.uniform;

public interface FrameResource {
    String name();

    boolean usedThisFrame();

    void onFramePresented();
}
