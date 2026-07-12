/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Backend-agnostic description of the native vertex layout currently used by a pipeline.
 */
public record VertexLayoutSpec(String id,
                               VertexFormat nativeFormat,
                               com.mojang.blaze3d.PrimitiveTopology primitive) {
    public static VertexLayoutSpec of(String id, VertexFormat format, com.mojang.blaze3d.PrimitiveTopology primitive) {
        return new VertexLayoutSpec(id != null ? id : "unknown", format, primitive != null ? primitive : com.mojang.blaze3d.PrimitiveTopology.TRIANGLES);
    }

    public String getId() {
        return id;
    }
}
