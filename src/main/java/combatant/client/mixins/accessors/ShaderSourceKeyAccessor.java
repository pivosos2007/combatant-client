/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$ShaderSourceKey")
public interface ShaderSourceKeyAccessor {
    @Accessor("id")
    Identifier combatant$getId();

    @Accessor("type")
    ShaderType combatant$getType();
}
