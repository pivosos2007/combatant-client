/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

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
                               boolean lineMode) {
    public static Builder builder(PipelineDomain domain) {
        return new Builder(domain);
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

        public PipelineMetadata build() {
            return new PipelineMetadata(domain, fullscreen, worldSpace, uiSpace, text, effect, requiresWorldFog, additive, lineMode);
        }
    }
}
