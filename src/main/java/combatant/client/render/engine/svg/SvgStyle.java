/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

final class SvgStyle {
    @Nullable Integer fill;
    @Nullable Integer stroke;
    float strokeWidth;
    float fillOpacity;
    float strokeOpacity;
    float groupOpacity;
    int lineCap;
    int lineJoin;
    float miterLimit;
    boolean visible;
    String fillRule;
    int currentColor;

    static SvgStyle root() {
        SvgStyle s = new SvgStyle();
        s.fill = 0xFF000000;
        s.stroke = null;
        s.strokeWidth = 1.0f;
        s.fillOpacity = 1.0f;
        s.strokeOpacity = 1.0f;
        s.groupOpacity = 1.0f;
        s.lineCap = BasicStroke.CAP_BUTT;
        s.lineJoin = BasicStroke.JOIN_MITER;
        s.miterLimit = 4.0f;
        s.visible = true;
        s.fillRule = "nonzero";
        s.currentColor = 0xFF000000;
        return s;
    }

    SvgStyle copy() {
        SvgStyle out = new SvgStyle();
        out.fill = fill;
        out.stroke = stroke;
        out.strokeWidth = strokeWidth;
        out.fillOpacity = fillOpacity;
        out.strokeOpacity = strokeOpacity;
        out.groupOpacity = groupOpacity;
        out.lineCap = lineCap;
        out.lineJoin = lineJoin;
        out.miterLimit = miterLimit;
        out.visible = visible;
        out.fillRule = fillRule;
        out.currentColor = currentColor;
        return out;
    }
}
