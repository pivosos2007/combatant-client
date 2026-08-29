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

function abs(x, y, w, h, extra) {
  return cls("absolute", `x-${s(x)}`, `y-${s(y)}`, `w-${s(w)}`, `h-${s(h)}`, extra);
}

function prop(obj, key, fallback) {
  if (!obj) return fallback;
  const direct = obj[key];
  if (direct !== undefined && direct !== null) return direct;
  if (typeof obj.get === "function") {
    const value = obj.get(key);
    return value !== undefined && value !== null ? value : fallback;
  }
  return fallback;
}

function arr(value) {
  if (!value) return [];
  try {
    return Array.from(value);
  } catch (_) {
    return [];
  }
}

function parseArgb(value, fallback = "#FFFFFFFF") {
  const src = c(value, fallback);
  if (!src.startsWith("#") || (src.length !== 9 && src.length !== 7)) {
    return parseArgb(fallback, "#FFFFFFFF");
  }
  const normalized = src.length === 7 ? `#FF${src.slice(1)}` : src;
  const raw = Number.parseInt(normalized.slice(1), 16) >>> 0;
  return {
    a: (raw >>> 24) & 255,
    r: (raw >>> 16) & 255,
    g: (raw >>> 8) & 255,
    b: raw & 255,
  };
}

function argb(color) {
  const a = Math.max(0, Math.min(255, Math.round(color.a)));
  const r = Math.max(0, Math.min(255, Math.round(color.r)));
  const g = Math.max(0, Math.min(255, Math.round(color.g)));
  const b = Math.max(0, Math.min(255, Math.round(color.b)));
  const raw = (((a << 24) | (r << 16) | (g << 8) | b) >>> 0);
  return `#${raw.toString(16).padStart(8, "0").toUpperCase()}`;
}

function mix(first, second, amount) {
  const t = Math.max(0, Math.min(1, n(amount, 0)));
  const a = parseArgb(first);
  const b = parseArgb(second);
  return argb({
    a: a.a * (1 - t) + b.a * t,
    r: a.r * (1 - t) + b.r * t,
    g: a.g * (1 - t) + b.g * t,
    b: a.b * (1 - t) + b.b * t,
  });
}

function alpha(value, amount) {
  const src = parseArgb(value);
  src.a = 255 * Math.max(0, Math.min(1, n(amount, 1)));
  return argb(src);
}

function scaleAlpha(value, amount) {
  const src = parseArgb(value);
  src.a *= Math.max(0, Math.min(1, n(amount, 1)));
  return argb(src);
}

function matteColors(p) {
  const themeStart = c(p.themePanelStart, "#FF777B82");
  const themeEnd = c(p.themePanelEnd, "#FF555B64");
  const accentStart = c(p.themeAccentStart, "#FF8E9AA7");
  const accentEnd = c(p.themeAccentEnd, "#FF747E8A");
  const window = c(p.themeWindow, "#FF3A3C41");
  const surface = c(p.themeSurface, "#FF4A4D53");
  const strokeSoft = c(p.themeStrokeSoft, "#FF777D85");

  const bgAlpha = Math.max(0, Math.min(255, Math.round(n(p.bgAlpha, 156))));
  const bgOpacity = bgAlpha / 255;
  const themeStrength = Math.max(0, Math.min(1, n(p.themeGradientStrength, 52) / 100));

  // Light-grey matte remains the base, but unlike the old ~10% tint this
  // deliberately takes a visible amount of both theme chrome and panel gradient.
  const neutralTop = "#FFD0D3D8";
  const neutralMiddle = "#FFC2C6CD";
  const neutralBottom = "#FFADB3BB";

  const chromeTop = mix(window, themeStart, 0.54);
  const chromeMiddle = mix(surface, mix(themeStart, themeEnd, 0.50), 0.46);
  const chromeBottom = mix(surface, themeEnd, 0.58);

  const topMix = 0.12 + themeStrength * 0.38;
  const middleMix = 0.10 + themeStrength * 0.34;
  const bottomMix = 0.12 + themeStrength * 0.40;

  const topRgb = mix(neutralTop, chromeTop, topMix);
  const middleRgb = mix(neutralMiddle, chromeMiddle, middleMix);
  const bottomRgb = mix(neutralBottom, chromeBottom, bottomMix);

  const top = alpha(topRgb, bgOpacity);
  const middle = alpha(middleRgb, bgOpacity * 0.96);
  const bottom = alpha(bottomRgb, bgOpacity * 0.94);
  const themeVeilAlpha = bgOpacity * (0.05 + themeStrength * 0.20);
  const themeVeilStart = alpha(themeStart, themeVeilAlpha);
  const themeVeilEnd = alpha(themeEnd, themeVeilAlpha * 0.94);
  const strokeChromeStart = mix(strokeSoft, topRgb, 0.54);
  const strokeChromeEnd = mix(strokeSoft, bottomRgb, 0.54);
  const strokeThemeMix = 0.18 + themeStrength * 0.30;
  const strokeAlpha = 0.34 + bgOpacity * 0.18;
  const strokeStart = alpha(mix(strokeChromeStart, accentStart, strokeThemeMix), strokeAlpha);
  const strokeEnd = alpha(mix(strokeChromeEnd, accentEnd, strokeThemeMix), strokeAlpha);

  const dividerThemeMix = 0.12 + themeStrength * 0.24;
  const dividerStart = alpha(mix(middleRgb, accentStart, dividerThemeMix), 0.24 + bgOpacity * 0.10);
  const dividerEnd = alpha(mix(middleRgb, accentEnd, dividerThemeMix), 0.24 + bgOpacity * 0.10);

  return {
    top,
    middle,
    bottom,
    themeVeilStart,
    themeVeilEnd,
    strokeStart,
    strokeEnd,
    dividerStart,
    dividerEnd,
  };
}

function dividerNode(key, horizontal, x, y, slotSize, gap, scale, colors, angle) {
  const thickness = Math.max(0.5, 0.42 * scale);
  const length = Math.max(6.0 * scale, slotSize * 0.50);
  const inset = Math.max(0, (slotSize - length) * 0.5);
  if (horizontal) {
    return ui.shape({
      key,
      shape: "rounded-gradient",
      class: abs(x + gap * 0.5 - thickness * 0.5, y + inset, thickness, length),
      radius: thickness * 0.5,
      startColor: colors.dividerStart,
      endColor: colors.dividerEnd,
      angle,
    });
  }
  return ui.shape({
    key,
    shape: "rounded-gradient",
    class: abs(x + inset, y + gap * 0.5 - thickness * 0.5, length, thickness),
    radius: thickness * 0.5,
    startColor: colors.dividerStart,
    endColor: colors.dividerEnd,
    angle,
  });
}

function itemNode(cell, fallbackIndex, p, horizontal, slotSize, gap, scale) {
  const visualIndex = Math.max(0, Math.round(n(prop(cell, "visualIndex", fallbackIndex), fallbackIndex)));
  const size = 16 * scale;
  const offset = (slotSize - size) * 0.5;
  const x = (horizontal ? visualIndex * (slotSize + gap) : 0) + offset;
  const y = (horizontal ? 0 : visualIndex * (slotSize + gap)) + offset;
  const overlayMode = c(p.stateMode, "TEXT").toUpperCase() === "TEXT" ? "durability-text" : "durability";
  return ui.item({
    key: `armor:item:${visualIndex}`,
    stack: prop(cell, "stack", null),
    item: c(prop(cell, "item", ""), ""),
    count: n(prop(cell, "count", 1), 1),
    damage: n(prop(cell, "damage", 0), 0),
    maxDamage: n(prop(cell, "maxDamage", 0), 0),
    overlay: true,
    overlayMode,
    durabilityThreshold: n(p.durabilityThreshold, 70),
    durabilityColorThreshold: n(p.durabilityColorThreshold, 70),
    seed: visualIndex,
    class: abs(x, y, size, size),
  });
}

export function render(ctx) {
  const p = ctx && ctx.props ? ctx.props : {};
  const width = n(p.width, ctx && ctx.width ? ctx.width : 72);
  const height = n(p.height, ctx && ctx.height ? ctx.height : 18);
  const scale = Math.max(0.001, n(p.scale, 1));
  const slotSize = n(p.slotSize, 18 * scale);
  const gap = n(p.slotGap, 2 * scale);
  const horizontal = p.horizontal !== false;
  const radius = Math.max(1.6 * scale, Math.min(3.5 * scale, Math.min(width, height) * 0.22));
  const themeAngle = n(p.themePanelAngle, 90);
  const accentAngle = n(p.themeAccentAngle, 90);
  const colors = matteColors(p);
  const nodes = [];

  nodes.push(ui.blurSurface({
    key: "armor:blur",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius,
    alpha: 1.0,
    brightness: 1.025,
  }));

  nodes.push(ui.roundedSoftShadow({
    key: "armor:shadow",
    x: 0,
    y: 0.35 * scale,
    w: width,
    h: height,
    radius,
    blur: 3.2 * scale,
    innerAlpha: 0.045,
    color: "#36000000",
  }));

  nodes.push(ui.roundedGradient({
    key: "armor:matte-base",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius,
    startColor: colors.top,
    endColor: colors.bottom,
    angle: themeAngle,
  }));

  nodes.push(ui.roundedGradient({
    key: "armor:theme-veil",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius,
    startColor: colors.themeVeilStart,
    endColor: colors.themeVeilEnd,
    angle: themeAngle,
  }));

  // Soft middle veil keeps the panel matte instead of looking like glossy glass.
  nodes.push(ui.roundedGradient({
    key: "armor:matte-veil",
    x: 0.55 * scale,
    y: 0.55 * scale,
    w: Math.max(0, width - 1.1 * scale),
    h: Math.max(0, height - 1.1 * scale),
    radius: Math.max(0, radius - 0.55 * scale),
    startColor: scaleAlpha(colors.middle, 0.30),
    endColor: "#00000000",
    angle: 90,
  }));

  nodes.push(ui.roundedStrokeGradient({
    key: "armor:stroke",
    x: 0,
    y: 0,
    w: width,
    h: height,
    radius,
    thickness: Math.max(0.45, 0.50 * scale),
    startColor: colors.strokeStart,
    endColor: colors.strokeEnd,
    angle: accentAngle,
  }));

  for (let i = 0; i < 3; i++) {
    if (horizontal) {
      const base = slotSize + i * (slotSize + gap);
      nodes.push(dividerNode(`armor:divider:${i}`, true, base, 0, slotSize, gap, scale, colors, accentAngle));
    } else {
      const base = slotSize + i * (slotSize + gap);
      nodes.push(dividerNode(`armor:divider:${i}`, false, 0, base, slotSize, gap, scale, colors, accentAngle));
    }
  }

  const items = arr(p.items);
  for (let i = 0; i < items.length; i++) {
    nodes.push(itemNode(items[i], i, p, horizontal, slotSize, gap, scale));
  }

  return ui.root({
    key: "armor",
    class: abs(0, 0, width, height),
    children: nodes,
  });
}
