/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.iris.patch.ShaderPatchEngine;

@Pseudo
@Mixin(value = Iris.class, remap = false)
public abstract class IrisShaderPackLoadMixin {
    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"), remap = false)
    private static void combatant$beginShaderPatchSession(String shaderPackName, CallbackInfoReturnable<Boolean> cir) {
        ShaderPatchEngine.beginShaderPackLoad(shaderPackName);
    }

    @Inject(method = "loadExternalShaderpack", at = @At("RETURN"), remap = false)
    private static void combatant$endShaderPatchSession(String shaderPackName, CallbackInfoReturnable<Boolean> cir) {
        ShaderPatchEngine.endShaderPackLoad(shaderPackName);
    }
}
