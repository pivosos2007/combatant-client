/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import net.minecraft.resources.Identifier;

/** Complete native compute pipeline contract. */
public record ComputePipelineDescriptor(String label,
                                        Identifier shader,
                                        ShaderResourceLayout resources) {
    public ComputePipelineDescriptor {
        if (shader == null) throw new IllegalArgumentException("shader");
        label = label == null || label.isBlank() ? shader.toString() : label;
        resources = resources == null ? ShaderResourceLayout.EMPTY : resources;
    }
}
