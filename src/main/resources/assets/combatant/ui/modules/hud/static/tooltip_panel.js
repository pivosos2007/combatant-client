/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const n = ui.num;
const c = ui.str;
const arr = ui.arr;
const prop = ui.prop;
const cls = ui.cls;

function fmt3(value) {
  return ui.fmt(value, 3, "0");
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, n(value, min)));
}

function clamp01(value) {
  return clamp(value, 0, 1);
}

function parseArgb(value, fallback = 0) {
  const src = c(value, "");
  if (!/^#[0-9a-fA-F]{8}$/.test(src)) return fallback >>> 0;
  return Number.parseInt(src.slice(1), 16) >>> 0;
}

function hex(argb) {
  return "#" + (argb >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

function withAlpha(value, alpha) {
  const raw = parseArgb(value, 0);
  const a = Math.max(0, Math.min(255, Math.round(n(alpha, 255))));
  return hex((((a & 255) << 24) | (raw & 0x00ffffff)) >>> 0);
}

function multiplyAlpha(value, alpha) {
  const raw = parseArgb(value, 0);
  const a = Math.max(0, Math.min(255, Math.round(((raw >>> 24) & 255) * clamp01(alpha))));
  return hex((((a & 255) << 24) | (raw & 0x00ffffff)) >>> 0);
}

function mix(fromValue, toValue, t) {
  const from = parseArgb(fromValue, 0);
  const to = parseArgb(toValue, 0);
  const k = clamp01(t);
  const fa = (from >>> 24) & 255;
  const fr = (from >>> 16) & 255;
  const fg = (from >>> 8) & 255;
  const fb = from & 255;
  const ta = (to >>> 24) & 255;
  const tr = (to >>> 16) & 255;
  const tg = (to >>> 8) & 255;
  const tb = to & 255;
  const a = Math.round(fa + (ta - fa) * k);
  const r = Math.round(fr + (tr - fr) * k);
  const g = Math.round(fg + (tg - fg) * k);
  const b = Math.round(fb + (tb - fb) * k);
  return hex((((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255)) >>> 0);
}

function alphaScale(value) {
  return clamp(n(value, 255), 0, 255) / 255.0;
}

function angleLerp(from, to, t) {
  let delta = ((n(to, from) - from + 540) % 360) - 180;
  return from + delta * clamp01(t);
}

function palette(p, alpha) {
  /*
   * Base palette is intentionally a literal port of the old ItemVisualPreviewProvider.
   * With themeGradientStrength=0 and all layer-alpha controls at 255, the colors and
   * alpha values below are exactly the old Java formulas.
   */
  const panelLeft = c(p.panelBgLeft, "#AF121314");
  const panelRight = c(p.panelBgRight, "#AF000205");
  const moduleTop = c(p.moduleCardTop, "#91171819");
  const moduleBottom = c(p.moduleCardBottom, "#910A0C0F");
  const panelStroke = c(p.panelStroke, "#E1121314");
  const glassEdgeSoft = c(p.glassEdgeSoft, "#342F343D");
  const panelDivider = c(p.panelDivider, "#FF303030");

  const themeStart = c(p.themePanelGradientStart, panelLeft);
  const themeEnd = c(p.themePanelGradientEnd, panelRight);
  const themeMix = clamp(n(p.themeGradientStrength, 0), 0, 250) / 250.0;

  const surfaceAlpha = alphaScale(p.backgroundAlpha) * alpha;
  const headerAlpha = alphaScale(p.headerAlpha) * surfaceAlpha;
  const strokeAlpha = alphaScale(p.strokeAlpha) * alpha;
  const dividerAlpha = alphaScale(p.dividerAlpha) * alpha;
  const shadowAlpha = alphaScale(p.shadowAlpha) * alpha;

  const baseBodyA = panelLeft;
  const baseBodyB = panelRight;
  const baseHeaderA = mix(moduleTop, panelLeft, 0.20);
  const baseHeaderB = mix(moduleBottom, panelRight, 0.22);
  const baseStrokeA = panelStroke;
  const baseStrokeB = glassEdgeSoft;
  const baseDividerA = panelDivider;
  const baseDividerB = glassEdgeSoft;

  const bodyA = mix(baseBodyA, themeStart, themeMix);
  const bodyB = mix(baseBodyB, themeEnd, themeMix);
  const headerA = mix(baseHeaderA, themeStart, themeMix);
  const headerB = mix(baseHeaderB, themeEnd, themeMix);
  const strokeA = mix(baseStrokeA, themeStart, themeMix);
  const strokeB = mix(baseStrokeB, themeEnd, themeMix);
  const dividerA = mix(baseDividerA, themeStart, themeMix);
  const dividerB = mix(baseDividerB, themeEnd, themeMix);

  return {
    bodyA: withAlpha(bodyA, Math.round(210 * surfaceAlpha)),
    bodyB: withAlpha(bodyB, Math.round(218 * surfaceAlpha)),
    headerA: withAlpha(headerA, Math.round(226 * headerAlpha)),
    headerB: withAlpha(headerB, Math.round(232 * headerAlpha)),
    strokeA: withAlpha(strokeA, Math.round(205 * strokeAlpha)),
    strokeB: withAlpha(strokeB, Math.round(150 * strokeAlpha)),
    dividerA: withAlpha(dividerA, Math.round(105 * dividerAlpha)),
    dividerB: withAlpha(dividerB, Math.round(58 * dividerAlpha)),
    glintTL: withAlpha("#FFFFFFFF", Math.round(0x12 * headerAlpha)),
    glintTR: withAlpha("#FFFFFFFF", Math.round(0x08 * headerAlpha)),
    glintBR: "#00FFFFFF",
    glintBL: "#00FFFFFF",
    text: multiplyAlpha(c(p.panelText, "#F5F5F5FF"), alpha),
    shadow: multiplyAlpha(c(p.panelShadow, "#5F000000"), shadowAlpha),
    gradientAngle: angleLerp(90.0, n(p.themePanelGradientAngle, 90.0), themeMix)
      + clamp(n(p.gradientAngleOffset, 0), -180, 180),
  };
}

function metrics(p) {
  const scale = Math.max(0.05, n(p.scale, 1));
  return {
    scale,
    fontScale: (14.5 / 18.0) * scale,
    padX: 9.0 * scale,
    padY: 7.0 * scale,
    rowGap: 1.5 * scale,
    groupGap: 3.0 * scale,
    headerGap: 6.0 * scale,
    previewGap: 3.0 * scale,
    radius: 7.0 * scale,
    minCardW: 180.0 * scale,
    minContentW: 160.0 * scale,
    defaultMaxContentW: 360.0 * scale,
  };
}

function maxContentWidth(p, m) {
  return Math.max(m.minContentW, n(p.maxContentWidth, m.defaultMaxContentW));
}

function hasExplicitBreakAfterFirst(p) {
  const source = arr(p.lines);
  let firstSeen = false;
  let gapAfterFirst = false;

  for (const entry of source) {
    const raw = c(prop(entry, "text", ""), "").trim();
    if (!raw) {
      if (firstSeen) gapAfterFirst = true;
      continue;
    }
    if (!firstSeen) {
      firstSeen = true;
      continue;
    }
    return gapAfterFirst;
  }
  return false;
}

function shouldDrawHeader(p) {
  if (c(p.context, "generic").toLowerCase() === "item") return true;
  return hasExplicitBreakAfterFirst(p);
}

function shouldDrawDivider(p, visibleLineCount, header) {
  if (!header || visibleLineCount <= 1) return false;
  if (c(p.context, "generic").toLowerCase() === "item") return true;
  return hasExplicitBreakAfterFirst(p);
}

function normalizeLines(p, m, colors, header) {
  const source = arr(p.lines);
  const lines = [];
  let pendingGroupGap = false;

  for (let i = 0; i < source.length; i++) {
    const entry = source[i];
    const raw = c(prop(entry, "text", ""), "").trim();
    if (!raw) {
      pendingGroupGap = true;
      continue;
    }

    let gapBefore = 0;
    if (lines.length > 0) {
      gapBefore += m.rowGap;
      // A title/header gap only exists when the panel actually has a header. Generic
      // compact tooltips are one continuous surface and use ordinary row spacing.
      if (header && lines.length === 1) gapBefore += m.headerGap;
      if (pendingGroupGap) gapBefore += m.groupGap;
    }
    pendingGroupGap = false;
    lines.push({
      key: `line:${lines.length}`,
      text: raw,
      color: c(prop(entry, "color", ""), colors.text),
      gapBefore,
    });
  }

  if (lines.length === 0) {
    lines.push({ key: "line:0", text: " ", color: colors.text, gapBefore: 0 });
  }
  return lines;
}

function structure(p, m, colors) {
  const header = shouldDrawHeader(p);
  const lines = normalizeLines(p, m, colors, header);
  return {
    lines,
    header,
    divider: shouldDrawDivider(p, lines.length, header),
  };
}

function fontClass(m, maxWidth, shadow) {
  return cls(
    `font-Iosevka-Regular-${fmt3(m.fontScale)}`,
    `text-max-${fmt3(maxWidth)}`,
    "ellipsis",
    shadow ? "shadow-text" : ""
  );
}

function measureTree(p, m, colors) {
  const widthLimit = maxContentWidth(p, m);
  const nodes = [];
  const layout = structure(p, m, colors);
  for (const line of layout.lines) {
    nodes.push(ui.text({
      key: line.key,
      text: line.text,
      color: line.color,
      class: cls(
        line.gapBefore > 0 ? `mt-${fmt3(line.gapBefore)}` : "",
        fontClass(m, widthLimit, false),
        `text-${line.color}`
      ),
    }));
  }

  const footerW = Math.max(0, n(p.footerWidth, 0));
  const footerH = Math.max(0, n(p.footerHeight, 0));
  if (footerW > 0 && footerH > 0) {
    nodes.push(ui.spacer({
      key: "footer",
      intrinsicWidth: footerW,
      intrinsicHeight: footerH,
      class: cls(`w-${fmt3(footerW)}`, `h-${fmt3(footerH)}`, `mt-${fmt3(m.previewGap)}`),
    }));
  }

  return ui.column({
    key: "measure-content",
    class: cls(`px-${fmt3(m.padX)}`, `py-${fmt3(m.padY)}`, `min-w-${fmt3(m.minCardW)}`),
    children: nodes,
  });
}

function renderContent(p, m, colors, width, lineHeight, layout) {
  const widthLimit = maxContentWidth(p, m);
  const contentW = Math.max(1, width - m.padX * 2);
  const nodes = [];
  let cursorY = m.padY;

  for (const line of layout.lines) {
    cursorY += line.gapBefore;
    nodes.push(ui.text({
      key: line.key,
      text: line.text,
      color: line.color,
      class: cls(
        ui.abs(m.padX, cursorY, contentW, lineHeight),
        fontClass(m, widthLimit, true),
        `text-${line.color}`
      ),
    }));
    cursorY += lineHeight;
  }

  const footerW = Math.max(0, n(p.footerWidth, 0));
  const footerH = Math.max(0, n(p.footerHeight, 0));
  if (footerW > 0 && footerH > 0) {
    cursorY += m.previewGap;
    nodes.push(ui.spacer({
      key: "footer",
      intrinsicWidth: footerW,
      intrinsicHeight: footerH,
      class: ui.abs(Math.max(0, (width - footerW) * 0.5), cursorY, footerW, footerH),
    }));
  }

  return nodes;
}

function renderTree(p, m, colors) {
  const width = Math.max(1, n(p.width, m.minCardW));
  const height = Math.max(1, n(p.height, 20 * m.scale));
  const lineHeight = Math.max(1, n(p.lineHeight, 14.5 * m.scale));
  const layout = structure(p, m, colors);
  const headerH = Math.min(height, m.padY + lineHeight + m.headerGap * 0.55);
  const innerRadius = Math.max(0, m.radius - m.scale);
  // Stroke/divider are raster-detail thicknesses, not layout geometry. They must not
  // inflate with the vanilla SCALED projection used by BetterTooltips. ItemPreview
  // supplies rasterDetailScale=1, so its visual-scale behavior stays the reference.
  const rasterDetailScale = Math.max(0.01, n(p.rasterDetailScale, 1.0));
  const strokeWidth = 0.72 * rasterDetailScale;
  const dividerH = Math.max(0.01, 0.65 * m.scale * rasterDetailScale);
  const children = [];

  // 1:1 with renderer.roundedRectShadow(..., 11*scale, 1.5*scale, palette.panelShadow()).
  children.push(ui.roundedShadow({
    key: "shadow",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius: m.radius,
    softness: 11.0 * m.scale,
    spread: 1.5 * m.scale,
    color: colors.shadow,
  }));

  // 1:1 body geometry. Theme mixing only changes the two endpoint colors/angle.
  children.push(ui.roundedGradient({
    key: "body",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius: m.radius,
    startColor: colors.bodyA,
    endColor: colors.bodyB,
    angle: colors.gradientAngle,
  }));

  // Item tooltips (and explicit generic title/body layouts) retain the richer header.
  // Compact generic tooltips deliberately remain one continuous body surface.
  if (layout.header) {
    children.push(ui.shape({
      key: "header",
      shape: "rounded-corners",
      class: ui.abs(0, 0, width, headerH),
      radiusTL: m.radius,
      radiusTR: m.radius,
      radiusBR: 0,
      radiusBL: 0,
      topLeftColor: colors.headerA,
      topRightColor: colors.headerA,
      bottomRightColor: colors.headerB,
      bottomLeftColor: colors.headerB,
    }));

    // Exact old glint for full-header tooltips: height 42% and 0x12/0x08 top alpha.
    children.push(ui.shape({
      key: "header-glint",
      shape: "rounded-corners",
      class: ui.abs(
        m.scale,
        m.scale,
        Math.max(1, width - 2.0 * m.scale),
        Math.max(1, headerH * 0.42)
      ),
      radiusTL: innerRadius,
      radiusTR: innerRadius,
      radiusBR: 0,
      radiusBL: 0,
      topLeftColor: colors.glintTL,
      topRightColor: colors.glintTR,
      bottomRightColor: colors.glintBR,
      bottomLeftColor: colors.glintBL,
    }));
  }

  children.push(ui.roundedStrokeGradient({
    key: "stroke",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius: m.radius,
    thickness: strokeWidth,
    startColor: colors.strokeA,
    endColor: colors.strokeB,
    angle: colors.gradientAngle,
  }));

  // Unlike the old preview, the divider is contextual. The primitive/colors are still exact.
  if (layout.divider && clamp(n(p.dividerAlpha, 255), 0, 255) > 0) {
    children.push(ui.shape({
      key: "divider",
      shape: "gradient",
      class: ui.abs(m.scale, headerH, Math.max(1, width - 2.0 * m.scale), dividerH),
      topLeftColor: colors.dividerA,
      topRightColor: colors.dividerB,
      bottomRightColor: colors.dividerB,
      bottomLeftColor: colors.dividerA,
    }));
  }

  children.push(...renderContent(p, m, colors, width, lineHeight, layout));

  return ui.root({
    key: "tooltip-panel",
    class: cls(`w-${fmt3(width)}`, `h-${fmt3(height)}`),
    children,
  });
}

export function render(ctx) {
  const p = ctx.props || {};
  const m = metrics(p);
  const colors = palette(p, clamp01(n(p.alpha, 1)));
  return c(p.phase, "render") === "measure"
    ? measureTree(p, m, colors)
    : renderTree(p, m, colors);
}
