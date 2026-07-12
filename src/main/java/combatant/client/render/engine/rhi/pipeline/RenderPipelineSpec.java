/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import combatant.client.render.engine.core.RenderPhase;

/**
 * Backend-neutral pipeline policy. Native RenderPipeline still exists in the SodiumGL backend era,
 * but policy decisions must go through this spec rather than object identity.
 */
public record RenderPipelineSpec(PipelineKey key,
                                 RenderPhase phase,
                                 VertexLayoutSpec vertexLayout,
                                 DepthPolicy depthPolicy,
                                 FogPolicy fogPolicy,
                                 BlendPolicy blendPolicy,
                                 CullPolicy cullPolicy,
                                 String vertexShaderId,
                                 String fragmentShaderId,
                                 UniformLayoutSpec uniformLayout,
                                 PipelineMetadata metadata) {
    public static Builder builder(String id) {
        return new Builder(PipelineKey.of(id));
    }

    public static Builder builder(PipelineKey key) {
        return new Builder(key);
    }

    public String id() {
        return key.getId();
    }

    public String getId() {
        return id();
    }

    public com.mojang.blaze3d.PrimitiveTopology primitive() {
        return vertexLayout.primitive();
    }

    public boolean bindsWorldFog() {
        return metadata.requiresWorldFog() || fogPolicy.requiresWorldFogUniform() || uniformLayout.hasUniform("Fog");
    }

    public static final class Builder {
        private final PipelineKey key;
        private RenderPhase phase = RenderPhase.NONE;
        private VertexLayoutSpec vertexLayout = VertexLayoutSpec.of("unknown", null, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES);
        private DepthPolicy depthPolicy = DepthPolicy.NONE;
        private FogPolicy fogPolicy = FogPolicy.NONE;
        private BlendPolicy blendPolicy = BlendPolicy.TRANSLUCENT;
        private CullPolicy cullPolicy = CullPolicy.NONE;
        private String vertexShaderId = "";
        private String fragmentShaderId = "";
        private UniformLayoutSpec uniformLayout = UniformLayoutSpec.builder().build();
        private PipelineMetadata metadata = PipelineMetadata.builder(PipelineDomain.UNKNOWN).build();

        private Builder(PipelineKey key) {
            this.key = key;
        }

        public Builder phase(RenderPhase phase) {
            this.phase = phase != null ? phase : RenderPhase.NONE;
            return this;
        }

        public Builder vertexLayout(VertexLayoutSpec vertexLayout) {
            if (vertexLayout != null) this.vertexLayout = vertexLayout;
            return this;
        }

        public Builder primitive(com.mojang.blaze3d.PrimitiveTopology primitive) {
            this.vertexLayout = VertexLayoutSpec.of(vertexLayout.getId(), vertexLayout.nativeFormat(), primitive);
            return this;
        }

        public Builder depth(DepthPolicy depthPolicy) {
            this.depthPolicy = depthPolicy != null ? depthPolicy : DepthPolicy.NONE;
            return this;
        }

        public Builder fog(FogPolicy fogPolicy) {
            this.fogPolicy = fogPolicy != null ? fogPolicy : FogPolicy.NONE;
            return this;
        }

        public Builder blend(BlendPolicy blendPolicy) {
            this.blendPolicy = blendPolicy != null ? blendPolicy : BlendPolicy.TRANSLUCENT;
            return this;
        }

        public Builder cull(CullPolicy cullPolicy) {
            this.cullPolicy = cullPolicy != null ? cullPolicy : CullPolicy.NONE;
            return this;
        }

        public Builder shader(String shaderId) {
            this.vertexShaderId = shaderId != null ? shaderId : "";
            return this;
        }

        public Builder vertexShader(String shaderId) {
            this.vertexShaderId = shaderId != null ? shaderId : "";
            return this;
        }

        public Builder fragmentShader(String shaderId) {
            this.fragmentShaderId = shaderId != null ? shaderId : "";
            return this;
        }

        public Builder uniforms(UniformLayoutSpec uniformLayout) {
            if (uniformLayout != null) this.uniformLayout = uniformLayout;
            return this;
        }

        public Builder metadata(PipelineMetadata metadata) {
            if (metadata != null) this.metadata = metadata;
            return this;
        }

        public RenderPipelineSpec build() {
            return new RenderPipelineSpec(key, phase, vertexLayout, depthPolicy, fogPolicy, blendPolicy, cullPolicy,
                    vertexShaderId, fragmentShaderId, uniformLayout, metadata);
        }
    }
}
