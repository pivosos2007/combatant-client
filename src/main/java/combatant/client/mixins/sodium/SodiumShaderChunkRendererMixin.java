/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import combatant.client.render.engine.core.CombatantRenderSystem;

/**
 * Sodium 26.2 builds terrain shaders as Mojang RenderPipeline objects.
 * The old ShaderLoader/DefaultShaderInterface/GlUniform path no longer exists,
 * so Combatant overrides the pipeline shader ids and keeps the shader resources
 * in vanilla .vsh/.fsh form.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShaderChunkRendererMixin {
    @ModifyArg(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withVertexShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
            ),
            index = 0
    )
    private Identifier combatant$useCombatantSodiumVertexShader(Identifier original) {
        return CombatantRenderSystem.sodium().shaderWorkarounds().overrideShaderIdentifier(original);
    }

    @ModifyArg(
            method = "createShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
            ),
            index = 0
    )
    private Identifier combatant$useCombatantSodiumFragmentShader(Identifier original) {
        return CombatantRenderSystem.sodium().shaderWorkarounds().overrideShaderIdentifier(original);
    }
}
