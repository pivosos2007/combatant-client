/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import com.mojang.renderpearl.api.vertex.VertexFormat;

/**
 * Backend-agnostic description of the native vertex layout currently used by a pipeline.
 */
public record VertexLayoutSpec(String id,
                               VertexFormat nativeFormat,
                               com.mojang.renderpearl.api.pipeline.PrimitiveTopology primitive) {
    public static VertexLayoutSpec of(String id, VertexFormat format, com.mojang.renderpearl.api.pipeline.PrimitiveTopology primitive) {
        return new VertexLayoutSpec(id != null ? id : "unknown", format, primitive != null ? primitive : com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES);
    }

    public String getId() {
        return id;
    }
}
