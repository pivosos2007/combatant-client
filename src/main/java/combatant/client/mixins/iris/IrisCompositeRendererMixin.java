/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import combatant.client.render.iris.IrisShaderpackMsaaIntegration;
import combatant.client.render.iris.IrisShaderpackTemporalIntegration;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = CompositeRenderer.class, remap = false)
public abstract class IrisCompositeRendererMixin {
    @WrapOperation(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_drawElements(IIIJ)V"
            ),
            remap = false
    )
    private void combatant$resolvePackedGbufferAfterShading(int mode,
                                                            int count,
                                                            int type,
                                                            long indices,
                                                            Operation<Void> original) {
        Runnable draw = () -> original.call(mode, count, type, indices);
        if (!IrisShaderpackMsaaIntegration.drawDeferredPerSample(draw)) {
            draw.run();
        }
    }

    @Inject(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_drawElements(IIIJ)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void combatant$finishShaderpackPassDraw(CallbackInfo ci) {
        IrisShaderpackTemporalIntegration.afterPassDraw();
    }

    @Inject(method = "renderAll", at = @At("TAIL"), remap = false)
    private void combatant$finishShaderpackTemporalBoundary(CallbackInfo ci) {
        IrisShaderpackTemporalIntegration.endRenderer();
    }
}
