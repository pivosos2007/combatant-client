/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import combatant.client.render.engine.shader.CombatantShaderSources;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipelineBuilder.class, remap = false)
public abstract class PipelineBuilderMixin {
    @Inject(
            method = "loadShaderSource(Lnet/minecraft/resources/Identifier;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void combatant$loadExtendedShaderSource(Identifier id,
                                                            ShaderType type,
                                                            ShaderSource source,
                                                            CallbackInfoReturnable<String> cir) {
        if (!CombatantShaderSources.isCombatantExtendedSource(id)) return;

        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resources = minecraft != null ? minecraft.getResourceManager() : null;
        if (resources == null) return;

        cir.setReturnValue(CombatantShaderSources.load(resources, id, type));
    }
}
