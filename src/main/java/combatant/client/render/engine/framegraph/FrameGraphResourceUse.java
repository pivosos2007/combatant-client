/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import java.util.Objects;

public record FrameGraphResourceUse(FrameGraphResourceKey resource, FrameGraphAccess access) {
    public FrameGraphResourceUse {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(access, "access");
    }

    public static FrameGraphResourceUse read(FrameGraphResourceKey resource) {
        return new FrameGraphResourceUse(resource, FrameGraphAccess.READ);
    }

    public static FrameGraphResourceUse write(FrameGraphResourceKey resource) {
        return new FrameGraphResourceUse(resource, FrameGraphAccess.WRITE);
    }

    public static FrameGraphResourceUse readWrite(FrameGraphResourceKey resource) {
        return new FrameGraphResourceUse(resource, FrameGraphAccess.READ_WRITE);
    }
}
