/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.render.engine.hdr.HdrScene;
import combatant.client.render.engine.hdr.SceneColor;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class HdrLevelRendererMixin {
    /** The clear value is separate from the legacy sRGB fog uniform consumed by shader variants. */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private Vector4f combatant$linearClear(Vector4f color) {
        return HdrScene.active() ? new Vector4f(SceneColor.srgbToLinear(color.x),
                SceneColor.srgbToLinear(color.y), SceneColor.srgbToLinear(color.z), color.w) : color;
    }
}
