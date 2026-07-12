/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

/**
 * Stable Combatant pipeline key. Native RenderPipeline identity is backend detail, not policy.
 */
public record PipelineKey(String namespace, String path) {
    public PipelineKey {
        namespace = namespace == null || namespace.isBlank() ? "combatant" : namespace;
        path = path == null ? "unknown" : path;
    }

    public static PipelineKey of(String id) {
        if (id == null || id.isBlank()) return new PipelineKey("combatant", "unknown");
        int colon = id.indexOf(':');
        if (colon > 0 && colon < id.length() - 1) {
            return new PipelineKey(id.substring(0, colon), id.substring(colon + 1));
        }
        return new PipelineKey("combatant", id);
    }

    public static PipelineKey of(Identifier id) {
        if (id == null) return new PipelineKey("combatant", "unknown");
        return new PipelineKey(id.getNamespace(), id.getPath());
    }

    public static PipelineKey of(RenderPipeline pipeline) {
        return pipeline != null ? of(pipeline.getLocation()) : of("unknown");
    }

    public String id() {
        return namespace + ":" + path;
    }

    public String getId() {
        return id();
    }

    @Override
    public String toString() {
        return id();
    }
}
