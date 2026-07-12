/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.iris.CombatantIrisUniforms;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisCommonUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), remap = false)
    private static void combatant$addCombatantUniforms(UniformHolder uniforms,
                                                       IdMap idMap,
                                                       PackDirectives packDirectives,
                                                       FrameUpdateNotifier updateNotifier,
                                                       CallbackInfo ci) {
        CombatantIrisUniforms.add(uniforms);
    }
}
