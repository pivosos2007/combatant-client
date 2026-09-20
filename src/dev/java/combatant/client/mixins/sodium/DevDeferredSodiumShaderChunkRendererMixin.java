/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/**
 * Sodium 0.9.1 terrain pipeline integration.
 *
 * <p>Primary terrain can switch between forward/deferred attachment layouts at runtime. Secondary
 * views use Combatant-owned pipelines selected by explicit capture purpose, but still reuse
 * Sodium's vertex format, bind group and uploaded terrain geometry.</p>
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class DevDeferredSodiumShaderChunkRendererMixin {
    @Shadow(remap = false)
    @Final
    private static Map<TerrainRenderPass, RenderPipeline> programs;

    @Shadow(remap = false)
    @Final
    protected VertexFormat vertexFormat;

    @Unique
    private static long combatant$deferredPipelineGeneration = Long.MIN_VALUE;
    /**
     * Reversed-Z shadow-map raster bias. The shadow atlas clears to 0 and tests GEQUAL, so a
     * negative polygon offset moves caster depth away from the light. Applying the slope term at
     * rasterization removes the quantized self-shadow stripes that a constant receiver bias cannot
     * solve at grazing celestial angles.
     */
    @Unique
    private static final DepthStencilState combatant$shadowDepthState = new DepthStencilState(
            CompareOp.GREATER_THAN_OR_EQUAL, true, -1.0f, -1.0f
    );
    @Unique
    private RenderPipeline combatant$shadowSolidPipeline;
    @Unique
    private RenderPipeline combatant$shadowCutoutPipeline;
    @Unique
    private RenderPipeline combatant$localShadowSolidPipeline;
    @Unique
    private RenderPipeline combatant$localShadowCutoutPipeline;
    @Unique
    private RenderPipeline combatant$reflectionSolidPipeline;
    @Unique
    private RenderPipeline combatant$reflectionCutoutPipeline;

    /** Sodium's static primary pipeline cache survives renderer reloads; invalidate on layout generation. */
    @Inject(method = "compileProgram", at = @At("HEAD"), remap = false, cancellable = true)
    private void combatant$selectCombatantTerrainPipeline(
            TerrainRenderPass pass,
            CallbackInfoReturnable<RenderPipeline> cir
    ) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        if (secondary != null) {
            cir.setReturnValue(switch (secondary.purpose()) {
                case SHADOW_DEPTH -> combatant$shadowPipeline(pass, true);
                case LOCAL_LIGHT_SHADOW -> combatant$shadowPipeline(pass, false);
                case REFLECTION_CAPTURE -> combatant$reflectionPipeline(pass);
            });
            return;
        }

        long generation = DevDeferredRuntime.world().geometryPipelineGeneration();
        if (generation == combatant$deferredPipelineGeneration) return;
        programs.clear();
        combatant$deferredPipelineGeneration = generation;
    }

    @Unique
    private RenderPipeline combatant$shadowPipeline(TerrainRenderPass pass, boolean directionalBias) {
        if (pass == DefaultTerrainRenderPasses.SOLID) {
            if (directionalBias) {
                if (combatant$shadowSolidPipeline == null) {
                    combatant$shadowSolidPipeline = combatant$createSecondaryPipeline(
                            pass, "shadow_solid", true, combatant$shadowDepthState, true);
                }
                return combatant$shadowSolidPipeline;
            }
            if (combatant$localShadowSolidPipeline == null) {
                combatant$localShadowSolidPipeline = combatant$createSecondaryPipeline(
                        pass, "local_shadow_solid", true, DepthStencilState.DEFAULT, false);
            }
            return combatant$localShadowSolidPipeline;
        }
        if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            if (directionalBias) {
                if (combatant$shadowCutoutPipeline == null) {
                    combatant$shadowCutoutPipeline = combatant$createSecondaryPipeline(
                            pass, "shadow_cutout", true, combatant$shadowDepthState, true);
                }
                return combatant$shadowCutoutPipeline;
            }
            if (combatant$localShadowCutoutPipeline == null) {
                combatant$localShadowCutoutPipeline = combatant$createSecondaryPipeline(
                        pass, "local_shadow_cutout", true, DepthStencilState.DEFAULT, false);
            }
            return combatant$localShadowCutoutPipeline;
        }
        throw new IllegalArgumentException("Unsupported shadow terrain pass: " + pass);
    }

    @Unique
    private RenderPipeline combatant$reflectionPipeline(TerrainRenderPass pass) {
        if (pass == DefaultTerrainRenderPasses.SOLID) {
            if (combatant$reflectionSolidPipeline == null) {
                combatant$reflectionSolidPipeline = combatant$createSecondaryPipeline(pass, "reflection_solid", false, DepthStencilState.DEFAULT, false);
            }
            return combatant$reflectionSolidPipeline;
        }
        if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            if (combatant$reflectionCutoutPipeline == null) {
                combatant$reflectionCutoutPipeline = combatant$createSecondaryPipeline(pass, "reflection_cutout", false, DepthStencilState.DEFAULT, false);
            }
            return combatant$reflectionCutoutPipeline;
        }
        throw new IllegalArgumentException("Unsupported reflection terrain pass: " + pass);
    }

    @Unique
    private RenderPipeline combatant$createSecondaryPipeline(TerrainRenderPass pass,
                                                              String suffix,
                                                              boolean shadowDepth,
                                                              DepthStencilState depthStencilState,
                                                              boolean distortedDirectionalShadow) {
        Identifier sodiumShader = Identifier.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque");
        Identifier shader = CombatantRenderSystem.sodium().shaderWorkarounds().overrideShaderIdentifier(sodiumShader);
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withBindGroupLayout(ShaderChunkRenderer.BIND_GROUP)
                // Sodium's Vulkan pipeline hook identifies terrain pipelines by namespace and
                // installs the 20-byte region/time push-constant range used by this vertex shader.
                .withLocation(Identifier.fromNamespaceAndPath("sodium", "combatant/" + suffix))
                .withCull(true)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withDepthStencilState(depthStencilState)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, vertexFormat)
                .withColorTargetState(shadowDepth
                        ? ColorTargetState.DEFAULT
                        : new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
                .withShaderDefine("USE_VERTEX_COMPRESSION")
                .withShaderDefine("USE_FOG");
        if (shadowDepth) {
            builder.withShaderDefine("COMBATANT_SHADOW_PASS");
        }
        if (distortedDirectionalShadow) {
            builder.withShaderDefine("COMBATANT_DIRECTIONAL_SHADOW_DISTORTION");
        }
        if (pass.supportsFragmentDiscard()) {
            builder.withShaderDefine("ALPHA_CUTOUT", 0.5f);
        }
        return builder.build();
    }

    @WrapOperation(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
            )
    )
    private RenderPipeline combatant$configureDeferredTerrainPipeline(
            RenderPipeline.Builder builder,
            Operation<RenderPipeline> original,
            @Local(argsOnly = true) TerrainRenderPass pass
    ) {
        if (!pass.isTranslucent() && !SodiumSecondaryTerrainContext.active()) {
            DevDeferredRuntime.world().configureGeometryPipeline(builder);
        }
        return original.call(builder);
    }

}

