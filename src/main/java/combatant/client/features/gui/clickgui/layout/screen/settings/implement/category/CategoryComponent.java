/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.category;

import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.MenuScreen;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

public final class CategoryComponent {
    private static final float PARALLAX_MAX_ANGLE = 5.5f;
    private static final float PARALLAX_DEPTH = 2.8f;
    private static final float PARALLAX_PERSPECTIVE = 1.0f;
    private static final float PARALLAX_SCALE_BOOST = 0.014f;
    private static final float PARALLAX_CONTENT_SHIFT = 1.0f;

    private final MenuScreen.Category category;
    private float hoverAnim;
    private float selectAnim;

    public CategoryComponent(MenuScreen.Category category) {
        this.category = category;
    }

    private static Parallax computeParallax(float mx, float my, float x, float y, float w, float h, float hover) {
        float ease = smooth(Math.max(0f, Math.min(1f, hover)));
        if (ease <= 0.001f || w <= 0f || h <= 0f) return Parallax.NONE;
        float nx = clamp((mx - x) / w * 2f - 1f, -1f, 1f);
        float ny = clamp((my - y) / h * 2f - 1f, -1f, 1f);
        return new Parallax(
                -nx * PARALLAX_MAX_ANGLE * ease,
                ny * PARALLAX_MAX_ANGLE * ease,
                1f + PARALLAX_SCALE_BOOST * ease,
                nx * PARALLAX_CONTENT_SHIFT * ease,
                ny * PARALLAX_CONTENT_SHIFT * ease,
                true
        );
    }

    private static RenderWarpStack.Scope pushParallax(Parallax p, float x, float y, float w, float h) {
        if (p == null || !p.active()) return Renderer2D.pushWarp(null);
        return Renderer2D.pushPerspectiveWarp(x, y, w, h, p.yawDeg(), p.pitchDeg(), 0f, PARALLAX_DEPTH, PARALLAX_PERSPECTIVE, p.scale());
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public MenuScreen.Category category() {
        return category;
    }

    public void render(float x, float y, float mx, float my, MenuScreen.Category selected, float scale) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float baseX = x + 5.25f * scale;
        float baseY = y;
        float tile = 20f * scale;
        boolean selectedState = selected == category;
        boolean hover = ClickGuiMath.insideRect(mx, my, baseX, baseY, tile, tile);

        hoverAnim = AnimationUtility.approach(hoverAnim, hover ? 1f : 0f, 0.25f);
        selectAnim = AnimationUtility.approach(selectAnim, selectedState ? 1f : 0f, 0.25f);

        float scaled = 0.5f + selectAnim * 0.5f;
        float cx = baseX + tile * 0.5f;
        float cy = baseY + tile * 0.5f;
        float sw = tile * scaled;
        float sh = tile * scaled;
        float sx = cx - sw * 0.5f;
        float sy = cy - sh * 0.5f;
        if (!selectedState && hoverAnim > 0.01f) {
            LayoutRender2D.roundedQuad(
                    baseX, baseY, tile, tile, 4f * scale,
                    LayoutRender2D.alpha(palette.menuCategoryHoverLeft(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverRight(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverLeft(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverRight(), hoverAnim)
            );
        }

        LayoutRender2D.roundedQuad(
                sx, sy, sw, sh, 5f * scale,
                LayoutRender2D.alpha(palette.menuCategorySelectedLeft(), selectAnim),
                LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selectAnim),
                LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selectAnim),
                LayoutRender2D.alpha(palette.menuCategorySelectedLeft(), selectAnim)
        );

        float pcx = cx;
        float pcy = cy;

        if (category.svgIcon()) {
            float iconSize = 10.4f * scale;
            Renderer2D.COLOR.svg(
                    category.token(),
                    pcx - iconSize * 0.5f,
                    pcy - iconSize * 0.5f,
                    iconSize,
                    iconSize,
                    SvgRenderOptions.overrideColor(LayoutRender2D.alpha(palette.menuCategoryText(), 0.9f + 0.1f * selectAnim))
            );
            return;
        }

        TextRenderer font = category.iconToken()
                ? Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterMedium())
                : ClickGuiRenderer.getInterMedium();
        float size = category.iconToken() ? 8.7f * scale : (category.token().length() > 2 ? 7.6f * scale : 9.2f * scale);
        float tw = ClickGuiRenderer.textWidth(font, category.token(), size);
        float th = ClickGuiRenderer.textHeight(font, size);
        ClickGuiRenderer.drawText(
                font,
                category.token(),
                pcx - tw * 0.5f,
                pcy - th * 0.5f,
                size,
                LayoutRender2D.alpha(palette.menuCategoryText(), 0.9f + 0.1f * selectAnim),
                false
        );
    }

    public boolean click(float x, float y, float mx, float my, float scale) {
        float baseX = x + 5.25f * scale;
        float baseY = y;
        return ClickGuiMath.insideRect(mx, my, baseX, baseY, 20f * scale, 20f * scale);
    }

    private record Parallax(float yawDeg, float pitchDeg, float scale, float shiftX, float shiftY, boolean active) {
        private static final Parallax NONE = new Parallax(0f, 0f, 1f, 0f, 0f, false);
    }
}
