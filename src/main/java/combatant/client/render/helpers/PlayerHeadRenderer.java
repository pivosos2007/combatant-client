/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import combatant.client.mixininterface.IGuiGraphics;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.player.PlayerSkinResolver;

public enum PlayerHeadRenderer {
    ;

    private static final float SOFTNESS = 1.0f;
    private static final float INSET_FACTOR = 0.06f;

    // 64x64 skin UVs
    private static final float FACE_U1 = 8f / 64f;
    private static final float FACE_V1 = 8f / 64f;
    private static final float FACE_U2 = 16f / 64f;
    private static final float FACE_V2 = 16f / 64f;

    private static final float HAT_U1 = 40f / 64f;
    private static final float HAT_V1 = 8f / 64f;
    private static final float HAT_U2 = 48f / 64f;
    private static final float HAT_V2 = 16f / 64f;

    /* ============================================================
       ROUNDED
       ============================================================ */

    public static void drawRounded(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            float radius,
            AbstractClientPlayer player,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        Identifier skin = PlayerSkinResolver.resolvePlayerSkin(player);
        drawRounded(ctx, x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness, unscaled);
    }

    public static void drawRounded(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            float radius,
            Identifier skin,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        if (skin == null || color == null || color.a <= 0 || size <= 0f) return;

        if (!unscaled) {
            drawRoundedInternal(x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness);
            return;
        }
        inProjection(ctx, true,
                () -> drawRoundedInternal(x, y, size, radius, skin, color, secondLayer, outlineColor, outlineThickness));
    }

    private static void drawRoundedInternal(float x,
                                            float y,
                                            float size,
                                            float radius,
                                            Identifier skin,
                                            RenderColor color,
                                            boolean secondLayer,
                                            RenderColor outlineColor,
                                            float outlineThickness) {
        if (outlineColor != null && outlineThickness > 0f) {
            Renderer2D.COLOR.roundedRectStroke(
                    x, y, size, size,
                    radius, SOFTNESS,
                    outlineThickness,
                    outlineColor.argb()
            );
        }

        float inset = size * INSET_FACTOR;
        float innerX = x + inset;
        float innerY = y + inset;
        float innerS = size - inset * 2f;
        float innerRadius = Math.max(0.5f, radius * 0.6f);

        int argb = color.argb();

        Renderer2D.TEXTURE.roundedTexRect(
                innerX, innerY, innerS, innerS,
                innerRadius, SOFTNESS,
                FACE_U1, FACE_V1, FACE_U2, FACE_V2,
                argb, skin
        );

        if (secondLayer) {
            Renderer2D.TEXTURE.roundedTexRect(
                    innerX, innerY, innerS, innerS,
                    innerRadius, SOFTNESS,
                    HAT_U1, HAT_V1, HAT_U2, HAT_V2,
                    argb, skin
            );
        }
    }

    /* ============================================================
       RECT (texQuad)
       ============================================================ */

    public static void drawRect(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            AbstractClientPlayer player,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        Identifier skin = PlayerSkinResolver.resolvePlayerSkin(player);
        drawRect(ctx, x, y, size, skin, color, secondLayer, outlineColor, outlineThickness, unscaled);
    }

    public static void drawRect(
            GuiGraphicsExtractor ctx,
            float x, float y, float size,
            Identifier skin,
            RenderColor color,
            boolean secondLayer,
            RenderColor outlineColor,
            float outlineThickness,
            boolean unscaled
    ) {
        if (skin == null || color == null || color.a <= 0 || size <= 0f) return;

        if (!unscaled) {
            drawRectInternal(x, y, size, skin, color, secondLayer, outlineColor, outlineThickness);
            return;
        }
        inProjection(ctx, true,
                () -> drawRectInternal(x, y, size, skin, color, secondLayer, outlineColor, outlineThickness));
    }

    private static void drawRectInternal(float x,
                                         float y,
                                         float size,
                                         Identifier skin,
                                         RenderColor color,
                                         boolean secondLayer,
                                         RenderColor outlineColor,
                                         float outlineThickness) {
        if (outlineColor != null && outlineThickness > 0f) {
            Renderer2D.COLOR.roundedRectStroke(
                    x, y, size, size,
                    0f, SOFTNESS,
                    outlineThickness,
                    outlineColor.argb()
            );
        }

        float inset = size * INSET_FACTOR;
        float innerX = x + inset;
        float innerY = y + inset;
        float innerS = size - inset * 2f;

        int argb = color.argb();

        Renderer2D tex = Renderer2D.TEXTURE;
        tex.begin();
        try {
            tex.texQuad(innerX, innerY, innerS, innerS, FACE_U1, FACE_V1, FACE_U2, FACE_V2, argb);
            if (secondLayer) {
                tex.texQuad(innerX, innerY, innerS, innerS, HAT_U1, HAT_V1, HAT_U2, HAT_V2, argb);
            }
        } finally {
            tex.end();
        }
        tex.render(skin);
    }

    /* ============================================================
       PROJECTION
       ============================================================ */

    private static void inProjection(GuiGraphicsExtractor ctx, boolean unscaled, Runnable draw) {
        if (!unscaled) {
            draw.run();
            return;
        }

        if (ctx instanceof IGuiGraphics accessor) {
            accessor.combatant$runUnscaled(draw);
            return;
        }

        ViewportContext.beginUnscaled(ctx);
        try {
            draw.run();
        } finally {
            ViewportContext.end(ctx);
        }
    }
}
