/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import combatant.client.render.engine.shader.CombatantShaderSources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRenderer's pipeline preloader resolves shader ids as {@code shaders/<id>.vsh}. Combatant shader
 * ids are full resource paths ({@code shaders/x.vert}), so resolve those directly.
 */
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer$1")
public abstract class GameRendererPreloadShaderSourceMixin {
    @Shadow(remap = false)
    @Final
    ResourceManager val$resourceManager;

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void combatant$loadCombatantShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (!CombatantShaderSources.COMBATANT_NAMESPACE.equals(id.getNamespace())
                || !id.getPath().startsWith(CombatantShaderSources.SHADER_PREFIX)
                || CombatantShaderSources.typeByLocation(id) == null) {
            return;
        }
        cir.setReturnValue(CombatantShaderSources.load(val$resourceManager, id, type));
    }
}
