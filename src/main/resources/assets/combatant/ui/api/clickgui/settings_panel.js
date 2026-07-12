/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

function n(value, fallback) {
  const out = Number(value);
  return Number.isFinite(out) ? out : fallback;
}

function c(value, fallback) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function b(value, fallback) {
  return typeof value === "boolean" ? value : fallback;
}

function a(value, mul) {
  return ui.color.alpha(c(value, "#00000000"), Math.max(0, Math.min(1, n(mul, 1))), c(value, "#00000000"));
}

export function buildTemplate(ctx) {
  const p = ctx.props || {};
  const w = n(p.width, 180);
  const h = n(p.height, 220);
  const scale = n(p.scale, 1);
  const open = Math.max(0, Math.min(1, n(p.open, 1)));
  const title = c(p.title, "Settings");
  const hudContext = b(p.hudContext, false);
  const radius = n(p.radius, 10 * scale);
  const headerH = n(p.headerH, 24 * scale);
  const bgL = a(c(p.bgTl, "#AF121314"), open);
  const bgR = a(c(p.bgTr, "#AF000205"), open);
  const bgB = a(c(p.bgBr, p.bgTr || "#AF000205"), open);
  const stroke = a(c(p.stroke, "#E1121314"), open);
  const line = a(c(p.line, "#FF303030"), 0.52 * open);
  const text = a(c(p.text, "#F5F5F5FF"), open);
  const muted = a(c(p.muted, "#A0A5A5A5"), open);
  const accent = a(c(p.accent, p.strokeStart || p.stroke || "#FFFFFFFF"), 0.55 * open);
  const accentSoft = a(c(p.accent, p.stroke || "#FFFFFFFF"), 0.22 * open);
  const closeW = 16 * scale;
  const closeH = 14 * scale;
  const closeX = w - closeW - 6 * scale;
  const closeY = (headerH - closeH) * 0.5;
  const icon = 6.8 * scale;

  const children = [
    ui.roundedSoftShadow({ key: "panel:shadow", x: -1.4 * scale, y: 1.2 * scale, w: w + 2.8 * scale, h: h + 2.4 * scale, radius: radius + 1.6 * scale, blur: 9.5 * scale, innerAlpha: 0.028, color: a("#FF000000", 0.72 * open) }),
    ui.blurSurface({ key: "panel:blur", x: 0, y: 0, w, h, radius, alpha: 0.58 * open }),
    ui.roundedGradientQuad({ key: "panel:bg", x: 0, y: 0, w, h, radius, topLeftColor: bgL, topRightColor: bgR, bottomRightColor: bgB, bottomLeftColor: bgL }),
    ui.roundedStrokeGradient({ key: "panel:stroke", x: 0, y: 0, w, h, radius, thickness: 0.58 * scale, startColor: stroke, endColor: a(c(p.strokeEnd, p.stroke || "#E1121314"), 0.82 * open), angle: 130 }),
    ui.roundedGradientQuad({ key: "panel:highlight", x: 1 * scale, y: 1 * scale, w: Math.max(1, w - 2 * scale), h: Math.max(1, headerH - 1 * scale), radius: Math.max(0, radius - scale), topLeftColor: a(c(p.headerA, p.bgTl || "#AF121314"), open), topRightColor: a(c(p.headerB, p.bgTr || "#AF000205"), open), bottomRightColor: a("#000000", 0.14 * open), bottomLeftColor: a("#000000", 0.10 * open) }),
    ui.shape({ key: "panel:divider", shape: "rect", class: ui.abs(8 * scale, headerH, Math.max(1, w - 16 * scale), Math.max(0.45, 0.55 * scale)), fill: line }),
    ui.shape({ key: "panel:accent-line", shape: "rect", class: ui.abs(8 * scale, headerH + 0.6 * scale, Math.max(1, w - 16 * scale), Math.max(0.45, 0.65 * scale)), fill: accentSoft }),
    ui.roundedGradientQuad({ key: "panel:title-mark", x: 8 * scale, y: 7.2 * scale, w: 2.4 * scale, h: 9.2 * scale, radius: 1.2 * scale, topLeftColor: accent, topRightColor: accentSoft, bottomRightColor: accentSoft, bottomLeftColor: accent }),
    ui.text({ key: "panel:title", text: title, color: text, class: ui.cls(ui.abs(14 * scale, 7 * scale, Math.max(1, w - 40 * scale), 11 * scale), `font-OnestMedium-${n(8 * scale, 8).toFixed(2)}`) }),
    ui.blurSurface({ key: "panel:close-blur", x: closeX, y: closeY, w: closeW, h: closeH, radius: closeH * 0.5, alpha: 0.28 * open }),
    ui.roundedGradientQuad({ key: "panel:close-hit", x: closeX, y: closeY, w: closeW, h: closeH, radius: closeH * 0.5, topLeftColor: a(c(p.buttonA, p.bgTl || "#AF121314"), open), topRightColor: a(c(p.buttonB, p.bgTr || "#AF000205"), open), bottomRightColor: a(c(p.buttonC, p.bgBr || "#AF000205"), open), bottomLeftColor: a(c(p.buttonA, p.bgTl || "#AF121314"), open) }),
    ui.roundedStrokeGradient({ key: "panel:close-stroke", x: closeX, y: closeY, w: closeW, h: closeH, radius: closeH * 0.5, thickness: 0.42 * scale, startColor: stroke, endColor: accentSoft, angle: 120 }),
    ui.svg({ key: "panel:close", asset: "x", tint: muted, class: ui.abs(closeX + (closeW - icon) * 0.5, closeY + (closeH - icon) * 0.5, icon, icon) }),
  ];

  if (hudContext) {
    children.push(ui.blurSurface({ key: "panel:hud-mode-blur", x: 7 * scale, y: headerH + 4 * scale, w: Math.max(1, w - 14 * scale), h: 16 * scale, radius: 8 * scale, alpha: 0.35 * open }));
  }

  return ui.template({
    key: "settings-panel-root",
    class: ui.abs(0, 0, w, h),
    children,
  });
}
