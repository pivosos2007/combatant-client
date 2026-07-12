/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import combatant.client.render.engine.renderer.Renderer2D;

/**
 * Simple batched renderer for GUI atlas sprites (framebuffer-space).
 */
public enum GuiSpriteBatch {
    ;
    private static boolean active;
    private static TextureAtlas atlas;
    private static Identifier atlasId;

    public static void begin() {
        if (active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        if (atlas == null) return;
        atlasId = atlas.location();
        if (atlasId == null) return;
        Renderer2D.TEXTURE.begin();
        active = true;
    }

    public static void draw(Identifier spriteId, float x, float y, float w, float h, int argb) {
        if (spriteId == null) return;
        boolean single = false;
        if (!active) {
            begin();
            single = active;
        }
        if (!active || atlas == null) return;
        TextureAtlasSprite sprite = atlas.getSprite(spriteId);
        if (sprite == null) return;
        Renderer2D.TEXTURE.texQuad(x, y, w, h,
                sprite.getU0(), sprite.getV0(),
                sprite.getU1(), sprite.getV1(),
                argb);
        if (single) {
            end();
        }
    }

    public static void end() {
        if (!active) return;
        Renderer2D.TEXTURE.end();
        Renderer2D.TEXTURE.render(atlasId);
        active = false;
    }
}
