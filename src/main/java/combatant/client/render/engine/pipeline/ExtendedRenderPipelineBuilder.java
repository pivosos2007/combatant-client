/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.render.engine.rhi.pipeline.DepthPolicy;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.rhi.pipeline.PipelineMetadata;
import combatant.client.render.engine.rhi.pipeline.TransformPolicy;
import combatant.client.render.engine.vertex.CombatantVertexFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small delegating builder so we can tag RenderPipeline with additional features.
 */
public class ExtendedRenderPipelineBuilder {
    private static final Map<RenderPipeline.Snippet, SnippetContract> SNIPPET_CONTRACTS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final RenderPipeline.Builder delegate;
    private final List<String> samplers = new ArrayList<>();
    private final List<UniformSpec> uniforms = new ArrayList<>();
    private final List<String> inheritedSamplers = new ArrayList<>();
    private final List<String> inheritedUniforms = new ArrayList<>();
    private boolean lineSmooth;
    private ShapeClipRenderPassContract shapeClipContract = ShapeClipRenderPassContract.NONE;
    private DepthTestFunction depthTestFunction;
    private net.minecraft.resources.Identifier location;
    private boolean depthWrite = true;
    private float depthBiasScaleFactor;
    private float depthBiasConstant;
    private boolean customDepthState;
    private boolean bindGroupLayoutApplied;
    private RenderPipelineContract contract = RenderPipelineContract.EXTENDED;
    private VertexFormat vertexFormat;
    private com.mojang.blaze3d.PrimitiveTopology primitiveTopology = com.mojang.blaze3d.PrimitiveTopology.TRIANGLES;
    private PipelineDomain metadataDomain;
    private TransformPolicy transformPolicy;
    private String shapeFamily;
    private net.minecraft.resources.Identifier vertexShader;

    public ExtendedRenderPipelineBuilder(RenderPipeline.Snippet... snippets) {
        this.delegate = RenderPipeline.builder(snippets);
        if (snippets != null) {
            for (RenderPipeline.Snippet snippet : snippets) {
                SnippetContract inherited = SNIPPET_CONTRACTS.get(snippet);
                if (inherited == null) continue;
                inheritedSamplers.addAll(inherited.samplers());
                inheritedUniforms.addAll(inherited.uniforms());
            }
        }
    }

    public ExtendedRenderPipelineBuilder withLocation(net.minecraft.resources.Identifier id) {
        this.location = id;
        delegate.withLocation(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withVertexFormat(VertexFormat format, com.mojang.blaze3d.PrimitiveTopology mode) {
        this.vertexFormat = format;
        this.primitiveTopology = mode != null ? mode : com.mojang.blaze3d.PrimitiveTopology.TRIANGLES;
        delegate.withVertexBinding(0, format);
        delegate.withPrimitiveTopology(mode);
        return this;
    }

    public ExtendedRenderPipelineBuilder withVertexShader(net.minecraft.resources.Identifier id) {
        this.vertexShader = id;
        delegate.withVertexShader(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withFragmentShader(net.minecraft.resources.Identifier id) {
        delegate.withFragmentShader(id);
        return this;
    }

    public ExtendedRenderPipelineBuilder withDepthTestFunction(DepthTestFunction fn) {
        this.depthTestFunction = fn;
        this.customDepthState = true;
        applyDepthStencilState();
        return this;
    }

    public ExtendedRenderPipelineBuilder withDepthWrite(boolean write) {
        this.depthWrite = write;
        this.customDepthState = true;
        applyDepthStencilState();
        return this;
    }

    /**
     * Applies raster depth bias in the same units used by Mojang's DepthStencilState.
     * Positive values move a fragment toward the camera under Minecraft's reversed-Z projection.
     */
    public ExtendedRenderPipelineBuilder withDepthBias(float scaleFactor, float constant) {
        this.depthBiasScaleFactor = scaleFactor;
        this.depthBiasConstant = constant;
        this.customDepthState = true;
        applyDepthStencilState();
        return this;
    }

    public ExtendedRenderPipelineBuilder withBlend(BlendFunction blend) {
        delegate.withColorTargetState(new ColorTargetState(Optional.of(blend), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL));
        return this;
    }

    public ExtendedRenderPipelineBuilder withCull(boolean cull) {
        delegate.withCull(isUiCanvasPipeline() ? false : cull);
        return this;
    }

    public ExtendedRenderPipelineBuilder withSampler(String name) {
        samplers.add(name);
        return this;
    }

    public ExtendedRenderPipelineBuilder withUniform(String name, UniformType type) {
        uniforms.add(new UniformSpec(name, type));
        return this;
    }

    public ExtendedRenderPipelineBuilder withContract(RenderPipelineContract contract) {
        this.contract = contract == null ? RenderPipelineContract.EXTENDED : contract;
        return this;
    }

    public ExtendedRenderPipelineBuilder withDomain(PipelineDomain domain) {
        this.metadataDomain = domain;
        return this;
    }

    public ExtendedRenderPipelineBuilder withTransformPolicy(TransformPolicy policy) {
        this.transformPolicy = policy;
        return this;
    }

    public ExtendedRenderPipelineBuilder withShapeFamily(String family) {
        this.shapeFamily = family;
        return this;
    }

    public ExtendedRenderPipelineBuilder withLineSmooth() {
        this.lineSmooth = true;
        return this;
    }

    /**
     * Declares that this pipeline participates in shape clipping. The backend decides how the
     * attachment is represented. GL uses stencil; Vulkan must create/route the equivalent
     * render-pass/mask attachment explicitly.
     */
    public ExtendedRenderPipelineBuilder withShapeClipSupport() {
        this.shapeClipContract = ShapeClipRenderPassContract.WHEN_ACTIVE;
        return this;
    }

    public ExtendedRenderPipelineBuilder withShapeClipContract(ShapeClipRenderPassContract contract) {
        this.shapeClipContract = contract == null ? ShapeClipRenderPassContract.NONE : contract;
        return this;
    }

    public RenderPipeline build() {
        validateKnownVertexShaderLayout();
        applyBindGroupLayout();
        RenderPipeline pipeline = delegate.build();
        IRenderPipeline combatantPipeline = (IRenderPipeline) pipeline;
        combatantPipeline.combatant$setLineSmooth(lineSmooth);
        combatantPipeline.combatant$setShapeClipContract(shapeClipContract);
        combatantPipeline.combatant$setContract(contract);
        combatantPipeline.combatant$setMetadata(buildMetadata());
        return pipeline;
    }

    public RenderPipeline.Snippet buildSnippet() {
        validateKnownVertexShaderLayout();
        applyBindGroupLayout();
        RenderPipeline.Snippet snippet = delegate.buildSnippet();
        List<String> declaredSamplers = new ArrayList<>(inheritedSamplers);
        declaredSamplers.addAll(samplers);
        List<String> declaredUniforms = new ArrayList<>(inheritedUniforms);
        for (UniformSpec uniform : uniforms) declaredUniforms.add(uniform.name());
        SNIPPET_CONTRACTS.put(snippet, new SnippetContract(List.copyOf(declaredSamplers), List.copyOf(declaredUniforms)));
        return snippet;
    }

    private boolean isUiCanvasPipeline() {
        if (location == null || !"combatant".equals(location.getNamespace())) return false;
        String path = location.getPath();
        return path.startsWith("pipeline/ui_")
                || path.startsWith("pipeline/menu_")
                || path.equals("pipeline/gui_texture_lookup");
    }

    private PipelineMetadata buildMetadata() {
        String path = location != null ? location.getPath() : "";
        PipelineDomain domain = metadataDomain != null ? metadataDomain : inferDomain(path);
        TransformPolicy transforms = transformPolicy != null ? transformPolicy : switch (contract) {
            case UI_FAST -> TransformPolicy.FRAME;
            case UI_WARPED, UI_EXTENDED -> TransformPolicy.EXTENDED;
            case EXTENDED -> domain == PipelineDomain.FULLSCREEN ? TransformPolicy.NONE : TransformPolicy.OBJECT;
        };
        DepthPolicy depth = domain == PipelineDomain.WORLD
                && depthTestFunction != null && depthTestFunction.compareOp() != null
                ? DepthPolicy.MAIN_FRAMEBUFFER
                : DepthPolicy.NONE;
        boolean text = isTextPipeline(path);
        boolean effect = domain == PipelineDomain.FULLSCREEN || domain == PipelineDomain.POSTPROCESS
                || path.contains("liquid") || path.contains("blur") || path.contains("glow")
                || path.contains("shadow") || path.contains("smoke") || path.contains("postprocess");
        boolean lineMode = primitiveTopology == com.mojang.blaze3d.PrimitiveTopology.LINES
                || primitiveTopology == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES
                || primitiveTopology == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINE_STRIP;

        PipelineMetadata.Builder metadata = PipelineMetadata.builder(domain)
                .fullscreen(domain == PipelineDomain.FULLSCREEN || domain == PipelineDomain.POSTPROCESS)
                .worldSpace(domain == PipelineDomain.WORLD)
                .uiSpace(domain == PipelineDomain.UI || domain == PipelineDomain.TEXT)
                .text(text)
                .effect(effect)
                .requiresWorldFog(path.contains("fog") || inheritedUniforms.contains("Fog"))
                .additive(path.contains("additive"))
                .lineMode(lineMode)
                .transformPolicy(transforms)
                .shapeFamily(shapeFamily != null ? shapeFamily : inferShapeFamily(path, text))
                .depthPolicy(depth)
                .clipSupport(shapeClipContract != ShapeClipRenderPassContract.NONE || isUiCanvasPipeline())
                .vertexLayoutId(vertexFormat != null ? vertexFormat.toString() : "unknown")
                .samplers(inheritedSamplers)
                .samplers(samplers);
        if (contract.meshDataRequired()) metadata.requiredUniform("MeshData");
        if (contract.uiBatchRequired()) metadata.requiredUniform("UIBatch");
        metadata.requiredUniforms(inheritedUniforms);
        for (UniformSpec uniform : uniforms) metadata.requiredUniform(uniform.name());
        return metadata.build();
    }

    private static PipelineDomain inferDomain(String path) {
        if (path.startsWith("pipeline/world_")) return PipelineDomain.WORLD;
        if (path.startsWith("pipeline/ui_") || path.startsWith("pipeline/gui_")) return PipelineDomain.UI;
        if (!path.isBlank()) return PipelineDomain.FULLSCREEN;
        return PipelineDomain.UNKNOWN;
    }

    private static boolean isTextPipeline(String path) {
        return !path.contains("textured")
                && (path.endsWith("_text") || path.contains("_text_") || path.contains("text_msdf"));
    }

    private static String inferShapeFamily(String path, boolean text) {
        if (text) return "text";
        if (path.contains("shape")) return "sdf-shape";
        if (path.contains("squircle")) return "squircle";
        if (path.contains("rounded")) return "rounded";
        if (path.contains("circle") || path.contains("arc")) return "radial";
        if (path.contains("textured") || path.contains("texture")) return "textured-quad";
        return "none";
    }

    /** Build-time contract guard for the minimal hot UI layouts. */
    private void validateKnownVertexShaderLayout() {
        if (vertexShader == null) return;
        VertexFormat expected = switch (vertexShader.getPath()) {
            case "shaders/ui_pos_color_fast.vert" -> CombatantVertexFormats.POS2_COLOR;
            case "shaders/ui_pos_tex_color_fast.vert" -> CombatantVertexFormats.POS2_TEXTURE_COLOR;
            case "shaders/ui_pos_color_rect_params_fast.vert" -> CombatantVertexFormats.POS2_COLOR_RECT_PARAMS;
            case "shaders/ui_pos_local_color_rect_params_fast.vert" -> CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS;
            case "shaders/ui_primitive_fast.vert" -> CombatantVertexFormats.POS2_LOCAL_COLOR_RECT_PARAMS5;
            case "shaders/pos_tex_local_color_rect_params6.vert" -> CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS6;
            case "shaders/rig_textured.vert" -> CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM;
            default -> null;
        };
        if (expected != null && vertexFormat != expected) {
            throw new IllegalStateException("Vertex layout does not match " + vertexShader
                    + ": expected=" + expected + ", actual=" + vertexFormat
                    + ", pipeline=" + location);
        }
    }

    private void applyDepthStencilState() {
        if (!customDepthState) return;
        if (depthTestFunction == null || depthTestFunction.compareOp() == null) {
            delegate.withDepthStencilState(Optional.empty());
            return;
        }
        delegate.withDepthStencilState(new DepthStencilState(
                depthTestFunction.compareOp(), depthWrite, depthBiasScaleFactor, depthBiasConstant));
    }

    private void applyBindGroupLayout() {
        if (bindGroupLayoutApplied || samplers.isEmpty() && uniforms.isEmpty()) {
            return;
        }
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (String sampler : samplers) {
            layout.withSampler(sampler);
        }
        for (UniformSpec uniform : uniforms) {
            layout.withUniform(uniform.name(), uniform.type());
        }
        delegate.withBindGroupLayout(layout.build());
        bindGroupLayoutApplied = true;
    }

    private record UniformSpec(String name, UniformType type) {
    }

    private record SnippetContract(List<String> samplers, List<String> uniforms) {
    }
}
