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

public record EmptyGlyphFont(int height) implements GlyphFont {
    public EmptyGlyphFont(int height) {
        this.height = Math.max(1, height);
    }

    @Override
    public Texture getTexture() {
        return null;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public double getWidth(String string, int length) {
        return 0;
    }

    @Override
    public double render(MeshBuilder mesh, String string, double x, double y, RenderColor color, double scale) {
        return x;
    }

    @Override
    public double renderGradient(MeshBuilder mesh, String string, double x, double y, double scale, Font.GlyphGradient gradient) {
        return x;
    }

    @Override
    public double emitGlyphs(String string, double x, double y, double scale, GlyphConsumer consumer) {
        return x;
    }

    @Override
    public void close() {
    }
}
