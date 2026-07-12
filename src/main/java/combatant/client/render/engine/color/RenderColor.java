/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.color;

public class RenderColor {
    public int r;
    public int g;
    public int b;
    public int a;

    public RenderColor(int r, int g, int b, int a) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
        this.a = clamp(a);
    }

    public RenderColor(int argb) {
        this.a = (argb >>> 24) & 0xFF;
        this.r = (argb >>> 16) & 0xFF;
        this.g = (argb >>> 8) & 0xFF;
        this.b = argb & 0xFF;
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public int argb() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public RenderColor copy() {
        return new RenderColor(r, g, b, a);
    }

    public void setAlpha(int alpha) {
        this.a = clamp(alpha);
    }
}
