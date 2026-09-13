/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import java.util.Objects;

/** Stable logical resource identity. Physical allocation is intentionally not encoded here. */
public record FrameGraphResourceKey(String name,
                                    FrameGraphResourceKind kind,
                                    FrameGraphResourceLifetime lifetime) {
    public FrameGraphResourceKey {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Frame-graph resource name must not be blank");
        name = name.trim();
        kind = Objects.requireNonNull(kind, "kind");
        lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (kind == FrameGraphResourceKind.EXTERNAL && lifetime != FrameGraphResourceLifetime.EXTERNAL) {
            throw new IllegalArgumentException("EXTERNAL resource kind requires EXTERNAL lifetime: " + name);
        }
        if (lifetime == FrameGraphResourceLifetime.EXTERNAL && kind != FrameGraphResourceKind.EXTERNAL) {
            throw new IllegalArgumentException("EXTERNAL resource lifetime requires EXTERNAL kind: " + name);
        }
    }

    public static FrameGraphResourceKey transientTexture(String name) {
        return new FrameGraphResourceKey(name, FrameGraphResourceKind.TEXTURE, FrameGraphResourceLifetime.TRANSIENT);
    }

    public static FrameGraphResourceKey transientBuffer(String name) {
        return new FrameGraphResourceKey(name, FrameGraphResourceKind.BUFFER, FrameGraphResourceLifetime.TRANSIENT);
    }

    public static FrameGraphResourceKey persistentTexture(String name) {
        return new FrameGraphResourceKey(name, FrameGraphResourceKind.TEXTURE, FrameGraphResourceLifetime.PERSISTENT);
    }

    public static FrameGraphResourceKey persistentBuffer(String name) {
        return new FrameGraphResourceKey(name, FrameGraphResourceKind.BUFFER, FrameGraphResourceLifetime.PERSISTENT);
    }

    public static FrameGraphResourceKey external(String name) {
        return new FrameGraphResourceKey(name, FrameGraphResourceKind.EXTERNAL, FrameGraphResourceLifetime.EXTERNAL);
    }
}
