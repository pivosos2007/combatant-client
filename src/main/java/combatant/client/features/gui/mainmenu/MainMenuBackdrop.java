/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.Renderer2D;
import net.minecraft.util.Mth;

/** Shared main-menu backdrop: the exact blur and pointy-hex glass matrix used by menu screens. */
final class MainMenuBackdrop {
    static final float MENU_SCALE = 1.22f;
    static final float SQRT_3 = 1.7320508f;
    static final float CELL_RADIUS = 31.5f * MENU_SCALE;
    static final float CELL_GAP = 0.0f;
    static final float CURSOR_LIGHT_RADIUS = 165f * MENU_SCALE;
    static final float HONEYCOMB_RIM_WIDTH = 0.92f * MENU_SCALE;
    static final float BACKGROUND_BLUR_QUALITY = 17.0f;
    static final float BACKGROUND_BLUR_ALPHA = 0.96f;

    private MainMenuBackdrop() {
    }

    static GridLayout layout(float width, float height) {
        float radius = CELL_RADIUS;
        float stepX = SQRT_3 * radius;
        float stepY = 1.5f * radius;
        float originX = width * 0.5f - stepX * 0.5f;
        float originY = height * 0.56f;
        return new GridLayout(radius, stepX, stepY, originX, originY);
    }

    static void render(float width, float height, float opacity, float mouseX, float mouseY,
                       GridLayout grid, Cutout exclusion) {
        renderBlur(width, height, opacity);
        renderHoneycomb(width, height, opacity, mouseX, mouseY, grid, exclusion);
    }

    static void renderBlur(float width, float height, float opacity) {
        float alpha = BACKGROUND_BLUR_ALPHA * Mth.clamp(opacity, 0f, 1f);
        if (alpha <= 0.001f) return;
        Renderer2D.COLOR.blurRect(
                0, 0, width, height,
                0.0f, BACKGROUND_BLUR_QUALITY, 1.0f,
                alpha, 0x00FFFFFF
        );
    }

    static void renderHoneycomb(float width, float height, float opacity, float mouseX, float mouseY,
                                GridLayout grid, Cutout exclusion) {
        if (grid == null || opacity <= 0.001f) return;

        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : 0xFFFFFFFF;
        int windowBg = theme != null ? theme.windowBg() : 0xFF10141B;
        int glassBody = withAlpha(HudRenderUtil.mixColor(windowBg, accent, 0.56f), 255);
        int warmRim = withAlpha(0xFFFFC857, 255);

        Cutout cutout = exclusion != null ? exclusion : Cutout.NONE;
        Renderer2D.COLOR.mainMenuHoneycombGlass(
                0f, 0f, width, height,
                grid.radius, CELL_GAP, HONEYCOMB_RIM_WIDTH, opacity,
                mouseX, mouseY, CURSOR_LIGHT_RADIUS,
                grid.originX, grid.originY,
                glassBody, warmRim,
                cutout.x, cutout.y, cutout.w, cutout.h,
                cutout.cut, cutout.enabled ? 1.0f : 0.0f
        );
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    record GridLayout(float radius, float stepX, float stepY, float originX, float originY) {
    }


    record Cutout(float x, float y, float w, float h, float cut, boolean enabled) {
        private static final Cutout NONE = new Cutout(0f, 0f, 0f, 0f, 0f, false);

        static Cutout chamfered(float x, float y, float w, float h, float cut) {
            if (w <= 0f || h <= 0f) return NONE;
            return new Cutout(x, y, w, h, Math.max(0f, cut), true);
        }
    }
}
