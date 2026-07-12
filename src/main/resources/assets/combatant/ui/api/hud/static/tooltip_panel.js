/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

function n(value, fallback) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function c(value, fallback) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function s(value) {
  return Number.isFinite(value) ? value.toFixed(2) : "0";
}

function cls(...parts) {
  return parts.filter(Boolean).join(" ");
}

function clamp01(value) {
  return Math.max(0, Math.min(1, value));
}

function alpha(hex, amount) {
  const src = c(hex, "#00000000");
  if (!src.startsWith("#") || src.length !== 9) return src;
  const raw = Number.parseInt(src.slice(1), 16);
  const nextA = Math.max(0, Math.min(255, Math.round(((raw >>> 24) & 255) * clamp01(amount))));
  return "#" + (((nextA << 24) | (raw & 0x00ffffff)) >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

export function render(ctx) {
  const p = ctx.props || {};
  const w = n(p.width, 80);
  const h = n(p.height, 20);
  const strokeWidth = Math.max(0, n(p.strokeWidth, 0.55));
  const glowSize = n(p.glowSize, 3.75);
  const vis = clamp01(n(p.panelAlpha, 1));
  const bg = alpha(c(p.bg, "#EE101114"), vis);
  const stroke = alpha(c(p.stroke, "#A85A6270"), vis);
  const glow = alpha(c(p.glow, "#665E78FF"), vis);

  return ui.root({
    key: "tooltip-panel",
    class: cls(`w-${s(w)}`, `h-${s(h)}`),
    children: [
      ui.roundedGlow({
        key: "edge-glow",
        x: 0,
        y: 0,
        w,
        h,
        radius: 0,
        softness: 0,
        glow: glowSize,
        color: glow,
      }),
      ui.shape({
        key: "panel-fill",
        shape: "quad",
        class: ui.abs(0, 0, w, h),
        fill: bg,
      }),
      ui.shape({
        key: "stroke-top",
        shape: "quad",
        class: ui.abs(0, 0, w, strokeWidth),
        fill: stroke,
      }),
      ui.shape({
        key: "stroke-bottom",
        shape: "quad",
        class: ui.abs(0, Math.max(0, h - strokeWidth), w, strokeWidth),
        fill: stroke,
      }),
      ui.shape({
        key: "stroke-left",
        shape: "quad",
        class: ui.abs(0, strokeWidth, strokeWidth, Math.max(0, h - strokeWidth * 2)),
        fill: stroke,
      }),
      ui.shape({
        key: "stroke-right",
        shape: "quad",
        class: ui.abs(Math.max(0, w - strokeWidth), strokeWidth, strokeWidth, Math.max(0, h - strokeWidth * 2)),
        fill: stroke,
      }),
    ],
  });
}
