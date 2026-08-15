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
 * Metadata used by compilers/backends instead of native RenderPipeline identity checks.
 */
public record PipelineMetadata(PipelineDomain domain,
                               boolean fullscreen,
                               boolean worldSpace,
                               boolean uiSpace,
                               boolean text,
                               boolean effect,
                               boolean requiresWorldFog,
                               boolean additive,
                               boolean lineMode,
                               TransformPolicy transformPolicy,
                               String shapeFamily,
                               DepthPolicy depthPolicy,
                               boolean clipSupport,
                               List<String> requiredUniforms,
                               List<String> samplers,
                               String vertexLayoutId) {
    public PipelineMetadata {
        domain = domain != null ? domain : PipelineDomain.UNKNOWN;
        transformPolicy = transformPolicy != null ? transformPolicy : TransformPolicy.EXTENDED;
        shapeFamily = shapeFamily != null && !shapeFamily.isBlank() ? shapeFamily : "none";
        depthPolicy = depthPolicy != null ? depthPolicy : DepthPolicy.NONE;
        requiredUniforms = requiredUniforms != null ? List.copyOf(requiredUniforms) : List.of();
        samplers = samplers != null ? List.copyOf(samplers) : List.of();
        vertexLayoutId = vertexLayoutId != null && !vertexLayoutId.isBlank() ? vertexLayoutId : "unknown";
    }

    public static Builder builder(PipelineDomain domain) {
        return new Builder(domain);
    }

    public Builder toBuilder() {
        return new Builder(domain)
                .fullscreen(fullscreen)
                .worldSpace(worldSpace)
                .uiSpace(uiSpace)
                .text(text)
                .effect(effect)
                .requiresWorldFog(requiresWorldFog)
                .additive(additive)
                .lineMode(lineMode)
                .transformPolicy(transformPolicy)
                .shapeFamily(shapeFamily)
                .depthPolicy(depthPolicy)
                .clipSupport(clipSupport)
                .requiredUniforms(requiredUniforms)
                .samplers(samplers)
                .vertexLayoutId(vertexLayoutId);
    }

    public boolean requiresUniform(String name) {
        return name != null && requiredUniforms.contains(name);
    }

    public static final class Builder {
        private PipelineDomain domain;
        private boolean fullscreen;
        private boolean worldSpace;
        private boolean uiSpace;
        private boolean text;
        private boolean effect;
        private boolean requiresWorldFog;
        private boolean additive;
        private boolean lineMode;
        private TransformPolicy transformPolicy = TransformPolicy.EXTENDED;
        private String shapeFamily = "none";
        private DepthPolicy depthPolicy = DepthPolicy.NONE;
        private boolean clipSupport;
        private final Set<String> requiredUniforms = new LinkedHashSet<>();
        private final Set<String> samplers = new LinkedHashSet<>();
        private String vertexLayoutId = "unknown";

        private Builder(PipelineDomain domain) {
            this.domain = domain != null ? domain : PipelineDomain.UNKNOWN;
        }

        public Builder domain(PipelineDomain domain) {
            this.domain = domain != null ? domain : PipelineDomain.UNKNOWN;
            return this;
        }

        public Builder fullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public Builder worldSpace(boolean worldSpace) {
            this.worldSpace = worldSpace;
            return this;
        }

        public Builder uiSpace(boolean uiSpace) {
            this.uiSpace = uiSpace;
            return this;
        }

        public Builder text(boolean text) {
            this.text = text;
            return this;
        }

        public Builder effect(boolean effect) {
            this.effect = effect;
            return this;
        }

        public Builder requiresWorldFog(boolean requiresWorldFog) {
            this.requiresWorldFog = requiresWorldFog;
            return this;
        }

        public Builder additive(boolean additive) {
            this.additive = additive;
            return this;
        }

        public Builder lineMode(boolean lineMode) {
            this.lineMode = lineMode;
            return this;
        }

        public Builder transformPolicy(TransformPolicy transformPolicy) {
            this.transformPolicy = transformPolicy != null ? transformPolicy : TransformPolicy.EXTENDED;
            return this;
        }

        public Builder shapeFamily(String shapeFamily) {
            this.shapeFamily = shapeFamily != null ? shapeFamily : "none";
            return this;
        }

        public Builder depthPolicy(DepthPolicy depthPolicy) {
            this.depthPolicy = depthPolicy != null ? depthPolicy : DepthPolicy.NONE;
            return this;
        }

        public Builder clipSupport(boolean clipSupport) {
            this.clipSupport = clipSupport;
            return this;
        }

        public Builder requiredUniform(String name) {
            if (name != null && !name.isBlank()) requiredUniforms.add(name);
            return this;
        }

        public Builder requiredUniforms(Iterable<String> names) {
            if (names != null) names.forEach(this::requiredUniform);
            return this;
        }

        public Builder sampler(String name) {
            if (name != null && !name.isBlank()) samplers.add(name);
            return this;
        }

        public Builder samplers(Iterable<String> names) {
            if (names != null) names.forEach(this::sampler);
            return this;
        }

        public Builder vertexLayoutId(String vertexLayoutId) {
            this.vertexLayoutId = vertexLayoutId != null ? vertexLayoutId : "unknown";
            return this;
        }

        public PipelineMetadata build() {
            return new PipelineMetadata(domain, fullscreen, worldSpace, uiSpace, text, effect,
                    requiresWorldFog, additive, lineMode, transformPolicy, shapeFamily, depthPolicy,
                    clipSupport, List.copyOf(requiredUniforms), List.copyOf(samplers), vertexLayoutId);
        }
    }
}
