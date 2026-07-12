/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Packed flexible-box data for the unified UI shape shader.
 */
public record UiShapeShaderParams(float modeTL, float modeTR, float modeBR, float modeBL,
                                  float extentTLX, float extentTRX, float extentBRX, float extentBLX,
                                  float extentTLY, float extentTRY, float extentBRY, float extentBLY,
                                  float edgeTop, float edgeRight, float edgeBottom, float edgeLeft,
                                  float strokeWidth, float fillMode, float reserved0, float reserved1) {
    public static UiShapeShaderParams of(UiBoxShape box, UiStroke stroke, UiPaint paint) {
        UiCornerSpec tl = box.topLeft();
        UiCornerSpec tr = box.topRight();
        UiCornerSpec br = box.bottomRight();
        UiCornerSpec bl = box.bottomLeft();
        return new UiShapeShaderParams(
                tl.kind().shaderCode(), tr.kind().shaderCode(), br.kind().shaderCode(), bl.kind().shaderCode(),
                tl.extentX(), tr.extentX(), br.extentX(), bl.extentX(),
                tl.extentY(), tr.extentY(), br.extentY(), bl.extentY(),
                box.top().kind().shaderCode(), box.right().kind().shaderCode(), box.bottom().kind().shaderCode(), box.left().kind().shaderCode(),
                stroke != null ? stroke.thickness() : 0f,
                paint != null ? paint.kind().ordinal() : 0f,
                0f,
                0f
        );
    }
}
