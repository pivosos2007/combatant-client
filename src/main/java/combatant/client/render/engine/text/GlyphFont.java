/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.Texture;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.uniform.MeshBuilder;

public interface GlyphFont {
    Texture getTexture();

    boolean isReady();

    double getWidth(String string, int length);

    int height();

    double render(MeshBuilder mesh, String string, double x, double y, RenderColor color, double scale);

    double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphGradient gradient);

    default double renderQuadGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphQuadGradient gradient) {
        if (gradient == null) return x;
        int[] quad = new int[4];
        return renderGradient(mesh, string, x, y, scale, (index, codePoint, glyphX, out) -> {
            gradient.colors(index, codePoint, glyphX, y, glyphX, y, quad);
            out[0] = quad[0];
            out[1] = quad[3];
        });
    }

    double emitGlyphs(String string, double x, double y, double scale, GlyphConsumer consumer);

    void close();

    default boolean isMsdf() {
        return false;
    }

    default float getPxRange() {
        return 0f;
    }

    default int getAtlasWidth() {
        Texture texture = getTexture();
        return texture != null ? texture.getWidth() : 0;
    }

    default int getAtlasHeight() {
        Texture texture = getTexture();
        return texture != null ? texture.getHeight() : 0;
    }

    default boolean hasGlyph(int codePoint) {
        return false;
    }

    default double getAdvance(int codePoint) {
        String glyph = new String(Character.toChars(codePoint));
        return getWidth(glyph, glyph.length());
    }

    @FunctionalInterface
    interface GlyphConsumer {
        void accept(double x0, double y0, double x1, double y1,
                    float u0, float v0, float u1, float v1);
    }
}
