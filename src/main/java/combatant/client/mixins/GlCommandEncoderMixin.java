/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import combatant.client.util.logging.DebugLog;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GlCommandEncoder is package-private in Mojang's OpenGL backend, so this mixin
 * intentionally targets it by binary name instead of importing the class.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    @WrapOperation(
            method = "trySetup",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"
            )
    )
    private void combatant$wrapPipelineWarning(Logger logger,
                                                 String format,
                                                 Object pipelineId,
                                                 Operation<Void> original) {
        if (pipelineId instanceof Identifier id && "combatant".equals(id.getNamespace())) {
            DebugLog.warn("[RenderPipeline] Render pipeline %s wants a depth texture but none was provided - this is probably a bug", id);
            return;
        }

        original.call(logger, format, pipelineId);
    }
}
