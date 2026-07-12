/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import com.mojang.blaze3d.textures.GpuTextureView;

public record UiTextureCommand(GpuTextureView texture, float x, float y, float width, float height,
                               int argb) implements UiCommand {
    @Override
    public UiCommandKind kind() {
        return UiCommandKind.TEXTURE;
    }
}
