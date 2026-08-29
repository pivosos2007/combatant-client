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

function clamp01(value) {
  return Math.max(0, Math.min(1, n(value, 0)));
}

function fixed(value) {
  return n(value, 0).toFixed(2);
}

function alpha(color, amount) {
  const value = c(color, "#00000000");
  return ui.color.alpha(value, clamp01(amount), value);
}

function abs(x, y, w, h, extra) {
  const parts = [
    "absolute",
    `x-${fixed(x)}`,
    `y-${fixed(y)}`,
    `w-${fixed(w)}`,
    `h-${fixed(h)}`,
  ];
  if (extra) parts.push(extra);
  return parts.join(" ");
}

function rect(key, x, y, w, h, fill, extra) {
  return ui.shape({
    key,
    shape: "rect",
    class: abs(x, y, w, h),
    fill,
    ...(extra || {}),
  });
}

function quadGradient(key, x, y, w, h, startColor, endColor, angle) {
  return ui.shape({
    key,
    shape: "quad-gradient",
    class: abs(x, y, w, h),
    startColor,
    endColor,
    angle: n(angle, 90),
  });
}

function gradientStroke(key, x, y, w, h, thickness, startColor, endColor, angle) {
  return ui.shape({
    key,
    shape: "box",
    class: abs(x, y, w, h),
    corners: ui.corner.all(ui.corner.square()),
    fill: "#00000000",
    stroke: startColor,
    strokeWidth: thickness,
    strokeStartColor: startColor,
    strokeEndColor: endColor,
    strokeAngle: n(angle, 90),
  });
}

function label(key, value, x, y, w, h, color, size, align, family) {
  const alignment = align ? ` text-align-${align}` : "";
  return ui.text({
    key,
    text: c(value, ""),
    color,
    class: `${abs(x, y, w, h)} font-${c(family, "OnestMedium")}-${fixed(size)}${alignment}`,
  });
}

export function buildTemplate(ctx) {
  const p = ctx.props || {};
  const w = Math.max(1, n(p.width, 180));
  const h = Math.max(1, n(p.height, 220));
  const scale = Math.max(0.25, n(p.scale, 1));
  const open = clamp01(p.open);
  const headerH = Math.max(1, n(p.headerH, 22 * scale));
  const strokeW = Math.max(0.22, 0.14 * scale);
  const dividerH = Math.max(0.18, 0.10 * scale);

  const bodyA = alpha(c(p.bodyA, "#C2181B20"), open);
  const bodyB = alpha(c(p.bodyB, "#C20E1116"), open);
  const bodyAngle = n(p.bodyAngle, 90);
  const bodyGlintA = alpha(c(p.bodyGlintA, "#08FFFFFF"), open);
  const bodyGlintB = alpha(c(p.bodyGlintB, "#00FFFFFF"), open);

  const headerA = alpha(c(p.headerA, "#D0181B20"), open);
  const headerB = alpha(c(p.headerB, "#D014171C"), open);
  const headerAngle = n(p.headerAngle, 90);
  const headerGlintA = alpha(c(p.headerGlintA, "#0EFFFFFF"), open);
  const headerGlintB = alpha(c(p.headerGlintB, "#00FFFFFF"), open);

  const strokeA = alpha(c(p.strokeA, "#465A5A64"), open);
  const strokeB = alpha(c(p.strokeB, "#4050505A"), open);
  const strokeAngle = n(p.strokeAngle, 90);
  const dividerA = alpha(c(p.dividerA, p.strokeA || "#345A5A64"), open);
  const dividerB = alpha(c(p.dividerB, p.strokeB || "#2E50505A"), open);

  const text = alpha(c(p.text, "#F0FFFFFF"), open);
  const muted = alpha(c(p.muted, "#B8D3D3D3"), open);
  const closeTint = alpha(c(p.closeTint, p.muted || "#CDD3D3D3"), open);
  const closeHover = clamp01(p.closeHover) * open;
  const closeHoverBg = alpha(c(p.closeHoverBg, "#18FFFFFF"), closeHover);

  const surfaceA = alpha(c(p.surfaceA, "#76161B23"), open);
  const surfaceB = alpha(c(p.surfaceB, "#84202733"), open);
  const surfaceAngle = n(p.surfaceAngle, 90);
  const activeA = alpha(c(p.activeA, "#A0475664"), open);
  const activeB = alpha(c(p.activeB, "#96504760"), open);
  const activeAngle = n(p.activeAngle, 90);

  const closeW = 14 * scale;
  const closeH = 12 * scale;
  const closeX = w - closeW - 5.5 * scale;
  const closeY = (headerH - closeH) * 0.5;
  const closeIcon = 5.0 * scale;

  const children = [
    ui.blurSurface({
      key: "settings-panel:blur-quad",
      x: 0,
      y: 0,
      w,
      h,
      radius: 0,
      alpha: clamp01(p.blurAlpha) * open,
      brightness: n(p.blurBrightness, 0.96),
      blurQuality: n(p.blurQuality, 27.35),
    }),
    quadGradient("settings-panel:body", 0, 0, w, h, bodyA, bodyB, bodyAngle),
    quadGradient("settings-panel:body-glint", 1, 1,
      Math.max(0, w - 2), Math.max(1, h * 0.16), bodyGlintA, bodyGlintB, 90),

    quadGradient("settings-panel:header", 0, 0, w, headerH, headerA, headerB, headerAngle),
    quadGradient("settings-panel:header-glint", 1, 1,
      Math.max(0, w - 2), Math.max(1, headerH * 0.42), headerGlintA, headerGlintB, 90),
    gradientStroke("settings-panel:stroke", strokeW * 0.5, strokeW * 0.5,
      Math.max(0, w - strokeW), Math.max(0, h - strokeW), strokeW, strokeA, strokeB, strokeAngle),
    quadGradient("settings-panel:header-divider", strokeW, headerH - dividerH,
      Math.max(0, w - strokeW * 2), dividerH, dividerA, dividerB, strokeAngle),

    label(
      "settings-panel:title",
      c(p.title, "Settings"),
      6.6 * scale,
      6.2 * scale,
      Math.max(1, w - 34 * scale),
      9.0 * scale,
      text,
      0.44 * scale,
      "left",
      "OnestMedium"
    ),

    rect("settings-panel:close-hover", closeX, closeY, closeW, closeH, closeHoverBg),
    ui.svg({
      key: "settings-panel:close",
      asset: "x",
      tint: closeTint,
      class: abs(
        closeX + (closeW - closeIcon) * 0.5,
        closeY + (closeH - closeIcon) * 0.5,
        closeIcon,
        closeIcon
      ),
    }),
  ];

  if (b(p.hudContext, false)) {
    const selectorX = 7 * scale;
    const selectorY = headerH + 4 * scale;
    const selectorW = Math.max(1, w - 14 * scale);
    const selectorH = 15 * scale;
    const segmentW = selectorW * 0.5;
    const pad = 1.0 * scale;
    const only = c(p.hudMode, "only") === "only";
    const selectorProgress = clamp01(p.pillProgress);
    const activeX = selectorX + pad + segmentW * selectorProgress;
    const enabledHover = b(p.enabledHover, false);
    const onlyHover = b(p.onlyHover, false);
    const selectorStrokeW = Math.max(0.20, strokeW * 0.72);

    children.push(
      quadGradient("settings-panel:hud-base", selectorX, selectorY, selectorW, selectorH,
        surfaceA, surfaceB, surfaceAngle),
      gradientStroke("settings-panel:hud-stroke",
        selectorX + selectorStrokeW * 0.5,
        selectorY + selectorStrokeW * 0.5,
        Math.max(0, selectorW - selectorStrokeW),
        Math.max(0, selectorH - selectorStrokeW),
        selectorStrokeW,
        alpha(strokeA, 0.66), alpha(strokeB, 0.66), strokeAngle),
      quadGradient("settings-panel:hud-active", activeX, selectorY + pad,
        Math.max(1, segmentW - pad * 2), selectorH - pad * 2,
        activeA, activeB, activeAngle),
      quadGradient("settings-panel:hud-divider", selectorX + segmentW - 0.18 * scale,
        selectorY + 3.4 * scale, 0.36 * scale, selectorH - 6.8 * scale,
        alpha(dividerA, 0.62), alpha(dividerB, 0.62), strokeAngle),
      label("settings-panel:hud-enabled", "Enabled", selectorX, selectorY + 4.8 * scale,
        segmentW, 6.2 * scale,
        !only || enabledHover ? text : muted, 0.36 * scale, "center", "OnestMedium"),
      label("settings-panel:hud-only", "Only this", selectorX + segmentW, selectorY + 4.8 * scale,
        segmentW, 6.2 * scale,
        only || onlyHover ? text : muted, 0.36 * scale, "center", "OnestMedium")
    );
  }

  if (b(p.scrollbarVisible, false)) {
    const sx = n(p.scrollbarX, w - 5 * scale);
    const sy = n(p.scrollbarY, headerH + 5 * scale);
    const sw = Math.max(0.7, n(p.scrollbarW, 1.5 * scale));
    const sh = Math.max(1, n(p.scrollbarH, h - headerH - 10 * scale));
    const thumbY = n(p.scrollbarThumbY, sy);
    const thumbH = Math.max(1, n(p.scrollbarThumbH, 18 * scale));
    children.push(
      rect("settings-panel:scroll-track", sx, sy, sw, sh,
        alpha(c(p.scrollbarTrack, "#303A3A3A"), open)),
      quadGradient("settings-panel:scroll-thumb", sx, thumbY, sw, thumbH,
        alpha(c(p.scrollbarThumbA, p.strokeA || "#705A6470"), open),
        alpha(c(p.scrollbarThumbB, p.strokeB || "#58505A66"), open),
        strokeAngle)
    );
  }

  return ui.root({
    key: "settings-panel:root",
    class: `w-${fixed(w)} h-${fixed(h)}`,
    children,
  });
}

export function render(ctx) {
  return buildTemplate(ctx);
}
