/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.nio.ByteBuffer;

/**
 * Backend storage buffer with explicit byte-offset uploads and close semantics.
 */
public interface RhiStorageBuffer extends AutoCloseable {
    StorageBufferDescriptor descriptor();

    /** Uploads source bytes beginning at the specified destination byte offset. */
    void upload(ByteBuffer source, long destinationOffset);

    @Override
    void close();
}
