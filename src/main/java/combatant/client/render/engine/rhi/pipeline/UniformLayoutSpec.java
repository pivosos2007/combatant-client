/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Declares logical uniform blocks/samplers expected by the pipeline.
 */
public final class UniformLayoutSpec {
    private final List<String> uniformBlocks;
    private final List<String> samplers;

    private UniformLayoutSpec(Set<String> uniformBlocks, Set<String> samplers) {
        this.uniformBlocks = List.copyOf(uniformBlocks);
        this.samplers = List.copyOf(samplers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> uniformBlocks() {
        return uniformBlocks;
    }

    public List<String> samplers() {
        return samplers;
    }

    public boolean hasUniform(String name) {
        return uniformBlocks.contains(name);
    }

    public boolean hasSampler(String name) {
        return samplers.contains(name);
    }

    public static final class Builder {
        private final Set<String> uniforms = new LinkedHashSet<>();
        private final Set<String> samplers = new LinkedHashSet<>();

        public Builder uniform(String name) {
            if (name != null && !name.isBlank()) uniforms.add(name);
            return this;
        }

        public Builder sampler(String name) {
            if (name != null && !name.isBlank()) samplers.add(name);
            return this;
        }

        public UniformLayoutSpec build() {
            return new UniformLayoutSpec(uniforms, samplers);
        }
    }
}
