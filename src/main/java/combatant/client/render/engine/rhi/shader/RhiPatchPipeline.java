/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

public interface RhiPatchPipeline extends AutoCloseable {
    String label();

    int controlPoints();

    @Override
    void close();
}
