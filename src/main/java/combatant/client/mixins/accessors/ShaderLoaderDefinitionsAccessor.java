/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ShaderManager.Configs.class)
public interface ShaderLoaderDefinitionsAccessor {
    @Accessor("shaderSources")
    Map<?, String> combatant$getShaderSources();

    @Accessor("postChains")
    Map<Identifier, PostChainConfig> combatant$getPostChains();
}
