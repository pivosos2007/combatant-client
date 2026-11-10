/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

/**
 * Backend compute pipeline handle with an optional declared shader-resource layout.
 */
public interface RhiComputePipeline extends AutoCloseable {
    String label();

    default ShaderResourceLayout resources() {
        return ShaderResourceLayout.EMPTY;
    }

    @Override
    void close();
}
