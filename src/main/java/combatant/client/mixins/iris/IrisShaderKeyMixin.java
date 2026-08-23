/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.IrisLogging;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.util.logging.DebugLog;

@Pseudo
@Mixin(value = ShaderKey.class, remap = false)
public abstract class IrisShaderKeyMixin {
    @WrapOperation(
            method = "findBestMatch",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/IrisLogging;warn(Ljava/lang/String;)V",
                    remap = false
            ),
            remap = false
    )
    private static void combatant$wrapCombatantProgramMatchWarning(IrisLogging logger,
                                                                    String message,
                                                                    Operation<Void> original,
                                                                    RenderPipeline pipeline,
                                                                    ProgramId requestedProgram) {
        if (pipeline != null
                && pipeline.getLocation() != null
                && "combatant".equals(pipeline.getLocation().getNamespace())) {
            DebugLog.warn("[Iris] %s", message);
            return;
        }

        original.call(logger, message);
    }
}
