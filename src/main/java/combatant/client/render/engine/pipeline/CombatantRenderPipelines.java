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

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rig.shader.RigRenderMode;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.engine.shader.ShaderCostRegistry;
import combatant.client.render.engine.uniform.ShaderUniformBindings;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.runtime.discovery.CombatantIndex;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public enum CombatantRenderPipelines {
    ;

    private static final String UI_BACKDROP_BLOCK = ShaderUniformBindings.block("UIBackdrop").name();
    private static final String UI_BLEND_BLOCK = ShaderUniformBindings.block("UIBlend").name();
    private static final String UI_GLASS_FRAME_BLOCK = ShaderUniformBindings.block("UIGlassFrame").name();
    private static final String UI_GLASS_STACK_BLOCK = ShaderUniformBindings.block("UIGlassStack").name();
    private static final String UI_BLUR_BLOCK = ShaderUniformBindings.block("UIBlur").name();

    private static final RenderPipeline.Snippet MESH_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet FOG_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet RIG_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("RigBones", UniformType.UNIFORM_BUFFER)
            .withUniform("RigDeform", UniformType.UNIFORM_BUFFER)
            .withUniform("RigRibbon", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_BATCH_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("UIBatch", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_ANALYTIC_CLIP_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform("UIClip", UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_BACKDROP_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform(UI_BACKDROP_BLOCK, UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_BLEND_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform(UI_BLEND_BLOCK, UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_GLASS_FRAME_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform(UI_GLASS_FRAME_BLOCK, UniformType.UNIFORM_BUFFER)
            .buildSnippet();
    private static final RenderPipeline.Snippet UI_GLASS_STACK_UNIFORMS = new ExtendedRenderPipelineBuilder()
            .withUniform(UI_GLASS_STACK_BLOCK, UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
    private static final Set<RenderPipeline> REGISTERED_PIPELINES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean initialized;

    public static synchronized void initialize() {
        if (initialized) return;

        for (String reference : CombatantIndex.entries(CombatantIndex.Kind.PIPELINE, "combatant.client")) {
            registerPipeline(resolve(reference));
        }
        initialized = true;
    }

    @AssetLoad(value = AssetLoadPhase.POST_RELOAD, order = 100)
    public static void precompile(ResourceManager resources) {
        List<RenderPipeline> pipelines = all();

        // In 26.3 the active PipelineCache owns compiled ordinary graphics pipelines.
        // Do not create a second private cache via GpuDevice.compilePipeline(): those
        // compiled objects would not be visible to Minecraft's render passes.
        if (RenderSystem.tryGetDevice() == null) {
            DebugLog.warn("[Combatant] RenderSystem not ready in precompile(ResourceManager)");
            return;
        }

        DebugLog.renderThread("[Combatant] Precompiling pipelines: " + pipelines.size());
        ShaderCostRegistry.clear();

        for (RenderPipeline pipeline : pipelines) {
            DebugLog.renderThread("[Combatant] -> pipeline " + pipeline.getLocation());
            DebugLog.renderThread("   VS = " + pipeline.getShaders().get(ShaderType.VERTEX));
            DebugLog.renderThread("   FS = " + pipeline.getShaders().get(ShaderType.FRAGMENT));
            DebugLog.renderThread("   VF = " + pipeline.getVertexFormatBinding(0));
            DebugLog.renderThread("   MODE = " + pipeline.getPrimitiveTopology());

            // Source analysis remains independent from GPU compilation. 26.3 compiles
            // ordinary VS/FS through RenderPearl/ShaderC and ShaderManager's cache.
            pipeline.getShaders().forEach((type, identifier) -> {
                String source = CombatantShaderSources.load(resources, identifier, type);
                ShaderCostRegistry.analyze(identifier, type, source);
            });
            Runnable compile = () -> {
                if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) {
                    throw new IllegalStateException("Failed to compile pipeline " + pipeline.getLocation());
                }
            };
            if (isRigPipeline(pipeline)) {
                IrisRuntime.runWithNativeShaderBypass(compile);
            } else {
                compile.run();
            }
        }

        DebugLog.renderThread("[Combatant] Precompile finished; shader cost audit entries="
                + ShaderCostRegistry.all().size());
        for (var estimate : ShaderCostRegistry.top(12)) {
            DebugLog.renderThread("[ShaderCost] " + estimate.shaderId()
                    + " stage=" + estimate.stage()
                    + " score=" + estimate.weightedScore()
                    + " alu=" + estimate.aluOps()
                    + " trans=" + estimate.transcendentalOps()
                    + " tex=" + estimate.textureOps()
                    + " branch=" + estimate.branchOps()
                    + " loops=" + estimate.loopOps());
        }
    }

    public static List<RenderPipeline> all() {
        initialize();
        synchronized (CombatantRenderPipelines.class) {
            return List.copyOf(PIPELINES);
        }
    }

    public static RenderPipeline analyticClipTextPipeline(RenderPipeline pipeline) {
        if (pipeline == UiRenderPipelines.UI_TEXT) return UiRenderPipelines.UI_TEXT_ANALYTIC_CLIP;
        if (pipeline == UiRenderPipelines.UI_TEXT_FAST) return UiRenderPipelines.UI_TEXT_FAST_ANALYTIC_CLIP;
        if (pipeline == UiRenderPipelines.UI_TEXT_MSDF) return UiRenderPipelines.UI_TEXT_MSDF_ANALYTIC_CLIP;
        if (pipeline == UiRenderPipelines.UI_TEXT_MSDF_FAST) {
            return UiRenderPipelines.UI_TEXT_MSDF_FAST_ANALYTIC_CLIP;
        }
        if (pipeline == UiRenderPipelines.UI_TEXT_ANALYTIC_CLIP
                || pipeline == UiRenderPipelines.UI_TEXT_FAST_ANALYTIC_CLIP
                || pipeline == UiRenderPipelines.UI_TEXT_MSDF_ANALYTIC_CLIP
                || pipeline == UiRenderPipelines.UI_TEXT_MSDF_FAST_ANALYTIC_CLIP) {
            return pipeline;
        }
        return null;
    }

    public static RenderPipeline analyticClipTexturedPipeline(RenderPipeline pipeline) {
        if (pipeline == UiRenderPipelines.UI_TEXTURED) return UiRenderPipelines.UI_TEXTURED_ANALYTIC_CLIP;
        if (pipeline == UiRenderPipelines.UI_TEXTURED_FAST) {
            return UiRenderPipelines.UI_TEXTURED_FAST_ANALYTIC_CLIP;
        }
        if (pipeline == UiRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA) {
            return UiRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA_ANALYTIC_CLIP;
        }
        if (pipeline == UiRenderPipelines.UI_TEXTURED_ANALYTIC_CLIP
                || pipeline == UiRenderPipelines.UI_TEXTURED_FAST_ANALYTIC_CLIP
                || pipeline == UiRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA_ANALYTIC_CLIP) {
            return pipeline;
        }
        return null;
    }

    public static RenderPipeline.Snippet meshUniforms() {
        return MESH_UNIFORMS;
    }

    static RenderPipeline.Snippet fogUniforms() {
        return FOG_UNIFORMS;
    }

    static RenderPipeline.Snippet rigUniforms() {
        return RIG_UNIFORMS;
    }

    static RenderPipeline.Snippet uiBatchUniforms() {
        return UI_BATCH_UNIFORMS;
    }

    static RenderPipeline.Snippet uiAnalyticClipUniforms() {
        return UI_ANALYTIC_CLIP_UNIFORMS;
    }

    static RenderPipeline.Snippet uiBackdropUniforms() {
        return UI_BACKDROP_UNIFORMS;
    }

    static RenderPipeline.Snippet uiBlendUniforms() {
        return UI_BLEND_UNIFORMS;
    }

    static RenderPipeline.Snippet uiGlassFrameUniforms() {
        return UI_GLASS_FRAME_UNIFORMS;
    }

    static RenderPipeline.Snippet uiGlassStackUniforms() {
        return UI_GLASS_STACK_UNIFORMS;
    }

    static String uiBlurBlock() {
        return UI_BLUR_BLOCK;
    }

    public static RenderPipeline registerAddonPipeline(RenderPipeline pipeline) {
        return registerPipeline(pipeline);
    }

    public static RenderPipeline rigEntity(RigRenderMode mode) {
        RigRenderMode resolved = mode == null ? RigRenderMode.CUTOUT : mode;
        return switch (resolved) {
            case CUTOUT -> RigRenderPipelines.RIG_ENTITY_CUTOUT;
            case CUTOUT_CULL -> RigRenderPipelines.RIG_ENTITY_CUTOUT_CULL;
            case TRANSLUCENT -> RigRenderPipelines.RIG_ENTITY_TRANSLUCENT;
            case TRANSLUCENT_CULL -> RigRenderPipelines.RIG_ENTITY_TRANSLUCENT_CULL;
            case TRANSLUCENT_NO_DEPTH_WRITE -> RigRenderPipelines.RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE;
            case TRANSLUCENT_NO_DEPTH_WRITE_CULL ->
                    RigRenderPipelines.RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE_CULL;
        };
    }

    public static boolean isRigPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getVertexFormatBinding(0)
                == CombatantVertexFormats.RIG_POSITION_TEXTURE_NORMAL_COLOR_BONES_DEFORM;
    }

    private static RenderPipeline resolve(String reference) {
        int separator = reference.lastIndexOf('#');
        if (separator <= 0 || separator == reference.length() - 1) {
            throw new IllegalStateException("Invalid indexed pipeline reference: " + reference);
        }

        String className = reference.substring(0, separator);
        String fieldName = reference.substring(separator + 1);
        try {
            Class<?> owner = Class.forName(
                    className,
                    true,
                    CombatantRenderPipelines.class.getClassLoader()
            );
            Field field = owner.getField(fieldName);
            Object value = field.get(null);
            if (!(value instanceof RenderPipeline pipeline)) {
                throw new IllegalStateException("Indexed field is not a RenderPipeline: " + reference);
            }
            return pipeline;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot resolve indexed pipeline: " + reference, error);
        }
    }

    private static synchronized RenderPipeline registerPipeline(RenderPipeline pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("pipeline");

        if (REGISTERED_PIPELINES.add(pipeline)) {
            declareDefaultShapeClipContract(pipeline);
            PIPELINES.add(pipeline);
            RenderPipelineRegistry.global().registerNative(pipeline);
        }
        return pipeline;
    }

    private static void declareDefaultShapeClipContract(RenderPipeline pipeline) {
        if (!(pipeline instanceof IRenderPipeline combatantPipeline) || pipeline.getLocation() == null) return;
        ShapeClipRenderPassContract current = combatantPipeline.combatant$getShapeClipContract();
        if (current != ShapeClipRenderPassContract.NONE) return;

        String namespace = pipeline.getLocation().getNamespace();
        String path = pipeline.getLocation().getPath();
        if ("combatant".equals(namespace)
                && (path.startsWith("pipeline/ui_") || path.equals("pipeline/gui_texture_lookup"))) {
            combatantPipeline.combatant$setShapeClipContract(ShapeClipRenderPassContract.WHEN_ACTIVE);
        }
    }
}
