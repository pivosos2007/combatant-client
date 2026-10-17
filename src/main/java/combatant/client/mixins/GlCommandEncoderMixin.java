/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import combatant.client.render.engine.rhi.backend.gl.GlNativeStateTracker;
import combatant.client.render.iris.IrisRuntime;
import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GlCommandEncoder is package-private in Mojang's OpenGL backend, so this mixin
 * intentionally targets it by binary name instead of importing the class.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    @Shadow
    private GlRenderPipeline lastPipeline;

    /**
     * Iris unlocks its deferred depth/color masks before drawing. RenderPearl normally
     * reapplies the pipeline immediately afterwards, but skips that work when its cached pipeline
     * identity is unchanged. Imported geometry uses a persistent pipeline and a fresh render pass,
     * so this cache hit could inherit depth-test/depth-write state from the preceding shaderpack
     * pass. safeToMultiply is scoped specifically around Combatant's Iris geometry submission.
     */
    @Inject(method = "setupDraw", at = @At("HEAD"))
    private void combatant$reapplyImportedIrisPipelineState(CallbackInfo ci) {
        // Mod-loaded check first: this mixin applies with or without Iris installed.
        if (IrisRuntime.isImportedGeometrySubmission() && IrisRuntime.isShaderpackRendererActive()) {
            lastPipeline = null;
        }
    }

    /**
     * setupDraw is shared by ordinary draws, direct multi-draw and indirect draws. At RETURN the
     * concrete Iris/Blaze3D program is selected. ExtendedShader also has a dedicated RETURN hook
     * so injection ordering cannot leave its later custom-uniform push as the final writer.
     */
    @Inject(method = "setupDraw", at = @At("RETURN"))
    private void combatant$applyImportedGeometryDrawUniforms(CallbackInfo ci) {
        IrisRuntime.applyImportedGeometryDrawUniforms();
    }

    @WrapOperation(
            method = "setupDraw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlRenderPipeline;bind()V")
    )
    private void combatant$trackNativeProgram(GlRenderPipeline pipeline, Operation<Void> original) {
        original.call(pipeline);
        GlNativeStateTracker.recordBlaze3dProgram(pipeline.program().getProgramId());
    }

    /**
     * Blaze3D exposes copyTextureToTexture in origin + extent form, while the OpenGL blit call
     * consumes origin + absolute end coordinates. Convert the extents at the backend boundary so
     * offset copies (for example atlas tiles) retain their requested size.
     */
    @WrapOperation(
            method = "copyTextureToTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/backend/opengl/DirectStateAccess;blitFrameBuffers(IIIIIIIIIIII)V"
            )
    )
    private void combatant$fixTextureCopyRectangles(
            DirectStateAccess access,
            int readFramebuffer,
            int drawFramebuffer,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            int mask,
            int filter,
            Operation<Void> original
    ) {
        original.call(
                access,
                readFramebuffer,
                drawFramebuffer,
                sourceX,
                sourceY,
                sourceX + sourceWidth,
                sourceY + sourceHeight,
                targetX,
                targetY,
                targetX + targetWidth,
                targetY + targetHeight,
                mask,
                filter
        );
    }
}
