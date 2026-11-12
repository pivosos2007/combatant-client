/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.category;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.features.gui.clickgui.layout.screen.settings.MenuScreen;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBoxShape;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.TextRenderer;

public final class CategoryComponent {
    private final MenuScreen.Category category;
    private float hoverAnim;
    private float selectAnim;

    public CategoryComponent(MenuScreen.Category category) {
        this.category = category;
    }

    public MenuScreen.Category category() {
        return category;
    }

    public void render(float x, float y, float mx, float my, MenuScreen.Category selected) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float baseX = x + 10.5f;
        float baseY = y;
        float tile = 40f;
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
                    baseX, baseY, tile, tile, 8f,
                    LayoutRender2D.alpha(palette.menuCategoryHoverLeft(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverRight(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverLeft(), hoverAnim),
                    LayoutRender2D.alpha(palette.menuCategoryHoverRight(), hoverAnim)
            );
        }

        if (selectAnim > 0.01f) {
            Renderer2D.COLOR.box(
                    UiBoxShape.squircle(sx, sy, sw, sh, 4.0f),
                    UiPaint.corners(
                            LayoutRender2D.alpha(palette.menuCategorySelectedLeft(), selectAnim),
                            LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selectAnim),
                            LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selectAnim),
                            LayoutRender2D.alpha(palette.menuCategorySelectedLeft(), selectAnim)
                    )
            );
        }

        float pcx = cx;
        float pcy = cy;

        if (category.svgIcon()) {
            float iconSize = 20.8f;
            Renderer2D.COLOR.svg(
                    category.token(),
                    pcx - iconSize * 0.5f,
                    pcy - iconSize * 0.5f,
                    iconSize,
                    iconSize,
                    SvgRenderOptions.overrideColor(LayoutRender2D.alpha(palette.menuCategoryText(), 0.9f + 0.1f * selectAnim))
            );
        } else {
            TextRenderer font = category.iconToken()
                    ? BuiltinFontCatalog.ICONS.renderer(BuiltinFontCatalog.ONEST_MEDIUM.renderer())
                    : BuiltinFontCatalog.ONEST_MEDIUM.renderer();
            float size = category.iconToken() ? 17.4f : (category.token().length() > 2 ? 15.2f : 18.4f);
            float tw = LayoutRender2D.textWidth(font, category.token(), size);
            float th = LayoutRender2D.textHeight(font, size);
            LayoutRender2D.drawText(
                    font,
                    category.token(),
                    pcx - tw * 0.5f,
                    pcy - th * 0.5f,
                    size,
                    LayoutRender2D.alpha(palette.menuCategoryText(), 0.9f + 0.1f * selectAnim),
                    false
            );
        }

    }

    public boolean click(float x, float y, float mx, float my) {
        float baseX = x + 10.5f;
        float baseY = y;
        return ClickGuiMath.insideRect(mx, my, baseX, baseY, 40f, 40f);
    }
}
