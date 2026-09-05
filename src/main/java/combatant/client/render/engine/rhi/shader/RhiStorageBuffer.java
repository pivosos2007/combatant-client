/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.nio.ByteBuffer;

public interface RhiStorageBuffer extends AutoCloseable {
    StorageBufferDescriptor descriptor();

    void upload(ByteBuffer source, long destinationOffset);

    @Override
    void close();
}
