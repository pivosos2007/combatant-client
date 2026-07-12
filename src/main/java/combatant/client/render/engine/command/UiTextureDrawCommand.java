/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiShape;

public record UiTextureDrawCommand(GpuTextureView view,
                                   GpuSampler sampler,
                                   UiShape shape,
                                   UiPaint tint,
                                   float u0,
                                   float v0,
                                   float u1,
                                   float v1,
                                   boolean masked) implements UiCommand {
    @Override
    public UiCommandKind kind() {
        return UiCommandKind.TEXTURE;
    }
}
