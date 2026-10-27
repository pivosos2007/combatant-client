/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const n = ui.num;
const c = ui.str;
const b = ui.bool;
const s = ui.fmt;
const cls = ui.cls;
const abs = ui.abs;

function font(name, size, weight) {
  let family = name;
  let suffix = weight && weight !== "regular" ? `-${weight}` : "";
  if (name === "Onest") {
    if (weight === "semibold") { family = "OnestBold"; suffix = ""; }
    if (weight === "medium") { family = "OnestMedium"; suffix = ""; }
    if (weight === "light") { family = "OnestLight"; suffix = ""; }
  }
  return cls(`font-${family}${suffix}`, `font-size-${s(size)}`);
}

function alpha(hex, mul) {
  return ui.color.alpha(hex, Math.max(0, Math.min(1, n(mul, 1))), hex);
}

function shape(key, shapeName, x, y, w, h, props) {
  return ui.shape(Object.assign({
    key,
    shape: shapeName,
    class: abs(x, y, w, h),
  }, props || {}));
}

function rect(key, x, y, w, h, fill) {
  return shape(key, "rect", x, y, w, h, { fill });
}

function rounded(key, x, y, w, h, radius, fill, stroke, strokeWidth) {
  return shape(key, "rounded", x, y, w, h, {
    radius,
    fill,
    stroke,
    strokeWidth: strokeWidth || 0,
  });
}

function gradientShape(key, x, y, w, h, radius, startColor, endColor, angle, extra) {
  return shape(key, "rounded-gradient", x, y, w, h, Object.assign({
    radius,
    startColor,
    endColor,
    angle,
  }, extra || {}));
}

function text(key, value, x, y, w, h, color, fontSize, weight, extra) {
  return ui.text({
    key,
    text: value,
    color,
    class: cls(abs(x, y, w, h), font("Onest", fontSize, weight), extra),
  });
}

function grad(p, prefix, fallbackA, fallbackB) {
  const enabled = b(p[`${prefix}Enabled`], false);
  return {
    a: alpha(c(enabled ? p[`${prefix}Start`] : fallbackA, fallbackA), n(p.alpha, 1)),
    b: alpha(c(enabled ? p[`${prefix}End`] : fallbackB, fallbackB), n(p.alpha, 1)),
    angle: n(p[`${prefix}Angle`], 90),
  };
}

function swatches(p) {
  return [
    p.windowBg, p.windowHeader, p.surface, p.surfaceHover,
    p.cardEnabled, p.cardDisabled, p.accent, p.accentSoft,
    p.windowStroke, p.strokeSoft, p.textPrimary, p.textMuted,
  ];
}

// The existing theme editor owns only two local, non-persistent preview toggles.
// They are intentionally not general-purpose UI controls or new theme settings.
function specimenControls(p, unit, a) {
  const materials = b(p.previewMaterials, false);
  const tint = alpha(c(p.accent, "#FF90BAD5"), a);
  const muted = alpha(c(p.textMuted, "#AFFFFFFF"), a);
  const result = [
    rounded("theme-preview:materials-toggle", 207 * unit, 8 * unit, 61 * unit, 23 * unit,
      5 * unit, alpha(c(p.surfaceHover, "#44394452"), a), tint, 0.6 * unit),
    text("theme-preview:materials-toggle-label", materials ? "THEME" : "MATERIALS",
      210 * unit, 16.2 * unit, 55 * unit, 10 * unit, tint, 8.2 * unit, "medium", "text-align-center"),
  ];
  if (materials) {
    result.push(
      rounded("theme-preview:scene-toggle", 271 * unit, 8 * unit, 36 * unit, 23 * unit,
        5 * unit, alpha(c(p.surface, "#44394452"), a), muted, 0.55 * unit),
      text("theme-preview:scene-toggle-label", b(p.previewLightScene, false) ? "LIGHT" : "DARK",
        273 * unit, 16.2 * unit, 32 * unit, 10 * unit, muted, 7.8 * unit, "medium", "text-align-center")
    );
  }
  return result;
}

function materialSpecimens(p, w, h, unit, a) {
  const light = b(p.previewLightScene, false);
  const accent = alpha(c(p.accentSoft, "#FF90BAD5"), a);
  const sceneA = light ? "#FFECF2F7" : "#FF0B121C";
  const sceneB = light ? "#FFCFDCE6" : "#FF1E3040";
  const border = light ? "#FFB2C4D1" : "#FF405061";
  const themeG = grad(p, "windowGradient", c(p.windowBg, "#D0181D24"), c(p.windowHeader, "#D0222A34"));
  const result = [
    ui.blurSurface({ key: "theme-preview:blur", x: 0, y: 0, w, h, radius: 12 * unit, alpha: 0.55 * a }),
    gradientShape("theme-preview:root", 0, 0, w, h, 12 * unit, themeG.a, themeG.b, themeG.angle),
    text("theme-preview:title", c(p.entryName, "Custom Theme"), 14 * unit, 14 * unit,
      180 * unit, 11 * unit, alpha(c(p.textPrimary, "#FFFFFFFF"), a), 13.32 * unit, "semibold"),
    rect("theme-preview:header-line", 14 * unit, 34 * unit, w - 28 * unit,
      Math.max(0.45, 0.5 * unit), alpha(c(p.strokeSoft, "#33FFFFFF"), 0.42 * a)),
  ];
  const materials = [
    { name: "GRAPHITE", id: "graphite", rgb: "#FF3D4751", x: 14, y: 44, detail: 0.36, rough: 0.88 },
    { name: "SOFT TOUCH", id: "soft-touch", rgb: "#FF5B6670", x: 165, y: 44, detail: 0.34, rough: 0.86 },
    { name: "SATIN TITANIUM", id: "satin-titanium", rgb: "#FF778995", x: 14, y: 111, detail: 0.18, rough: 0.42 },
    { name: "CERAMIC", id: "ceramic", rgb: "#FFDFE7EB", x: 165, y: 111, detail: 0.22, rough: 0.22 },
  ];
  materials.forEach((m) => {
    const x = m.x * unit, y = m.y * unit;
    const cw = 141 * unit, ch = 57 * unit;
    // These are real primitive material submissions, not colored mockups.
    result.push(gradientShape(`theme-preview:scene:${m.id}`, x, y, cw, ch,
      5 * unit, alpha(sceneA, a), alpha(sceneB, a), 105,
      { stroke: alpha(border, a * 0.6), strokeWidth: 0.4 * unit }));
    // Fine calibration lines behind each tile reveal the light/dark scene.
    for (let i = 1; i <= 4; i++) {
      result.push(rect(`theme-preview:grid:${m.id}:${i}`,
        x + i * 28 * unit, y + 2 * unit, 0.5 * unit, ch - 4 * unit,
        alpha(border, a * 0.32)));
    }
    result.push(ui.shape({
      key: `theme-preview:material:${m.id}`,
      shape: "primitive", preset: "chamfered",
      class: abs(x + 6 * unit, y + 6 * unit, cw - 12 * unit, ch - 12 * unit),
      chamfer: 5 * unit,
      fill: alpha(m.rgb, a),
      materialMode: m.id,
      materialAccent: accent,
      materialDetailScale: m.detail,
      materialRoughness: m.rough,
      materialBevelStrength: 0.45,
      materialResponse: m.id === "satin-titanium" ? 0.55 : 0,
    }));
    result.push(text(`theme-preview:label:${m.id}`, m.name,
      x + 14 * unit, y + 20 * unit, cw - 28 * unit, 14 * unit,
      alpha(m.id === "ceramic" ? "#FF22323D" : "#FFECF4FC", a),
      10.1 * unit, "semibold"));
  });
  result.push(...specimenControls(p, unit, a));
  return ui.root({
    key: "theme-preview:root-node",
    class: cls(`w-${s(w)}`, `h-${s(h)}`),
    children: result,
  });
}

export function buildTemplate(ctx) {
  const p = ctx.props || {};
  const w = n(p.width, 320);
  const h = n(p.height, 190);
  const unit = n(p.unit, 1);
  const a = n(p.alpha, 1);

  if (b(p.previewMaterials, false)) return materialSpecimens(p, w, h, unit, a);

  const rootR = 12 * unit;
  const textPrimary = alpha(c(p.textPrimary, "#FFFFFFFF"), a);
  const textMuted = alpha(c(p.textMuted, "#99FFFFFF"), a);
  const strokeSoft = alpha(c(p.strokeSoft, "#44FFFFFF"), a);
  const windowStroke = alpha(c(p.windowStroke, "#66FFFFFF"), a);
  const accent = alpha(c(p.accent, "#FFFFFFFF"), a);
  const accentSoft = alpha(c(p.accentSoft, "#99FFFFFF"), a);

  const rootG = grad(p, "windowGradient", c(p.windowBg, "#D0181D24"), c(p.windowHeader, "#D0222A34"));
  const surfaceG = grad(p, "surfaceGradient", c(p.surface, "#55242A34"), c(p.surfaceHover, "#66313844"));
  const cardG = grad(p, "cardGradient", c(p.cardEnabled, "#66333A46"), c(p.cardDisabled, "#55212831"));
  const strokeG = grad(p, "strokeGradient", c(p.windowStroke, "#66FFFFFF"), c(p.strokeSoft, "#33FFFFFF"));

  const children = [
    ui.blurSurface({ key: "theme-preview:blur", x: 0, y: 0, w, h, radius: rootR, alpha: 0.55 * a }),
    gradientShape("theme-preview:root", 0, 0, w, h, rootR, rootG.a, rootG.b, rootG.angle, {
      stroke: windowStroke,
      strokeStartColor: strokeG.a,
      strokeEndColor: strokeG.b,
      strokeWidth: Math.max(0.45, 0.55 * unit),
    }),
    gradientShape("theme-preview:header-glass", 1.4 * unit, 1.2 * unit, Math.max(1, w - 2.8 * unit), 36 * unit, Math.max(0, rootR - 1.2 * unit), alpha("#2DFFFFFF", a), alpha("#04FFFFFF", a), 90),

    text("theme-preview:title", c(p.entryName, "Custom Theme"), 14 * unit, 14 * unit, 180 * unit, 11 * unit, textPrimary, 13.32 * unit, "semibold", "text-effect-Flow"),
    rect("theme-preview:header-line", 14 * unit, 34 * unit, w - 28 * unit, Math.max(0.45, 0.5 * unit), alpha(c(p.strokeSoft, "#33FFFFFF"), 0.42 * a)),

    gradientShape("theme-preview:surface-card", 14 * unit, 44 * unit, 164 * unit, 132 * unit, 10 * unit, surfaceG.a, surfaceG.b, surfaceG.angle, {
      stroke: strokeSoft,
      strokeWidth: Math.max(0.35, 8.1 * unit),
    }),
    text("theme-preview:surface-label", "Surface stack", 26 * unit, 55 * unit, 120 * unit, 8 * unit, textMuted, 9.0 * unit, "semibold"),
    gradientShape("theme-preview:inner-surface", 28 * unit, 75 * unit, 136 * unit, 74 * unit, 10 * unit, surfaceG.b, surfaceG.a, surfaceG.angle + 20, {
      stroke: alpha(c(p.strokeSoft, "#33FFFFFF"), 0.72 * a),
      strokeWidth: Math.max(0.35, 0.42 * unit),
    }),
    gradientShape("theme-preview:primary-layer", 41 * unit, 93 * unit, 110 * unit, 39 * unit, 8 * unit, cardG.a, cardG.b, cardG.angle, {
      stroke: alpha(c(p.strokeSoft, "#33FFFFFF"), 0.66 * a),
      strokeWidth: Math.max(0.32, 0.40 * unit),
    }),
    text("theme-preview:primary-title", "Primary layer", 50 * unit, 101 * unit, 72 * unit, 9 * unit, textPrimary, 10.08 * unit, "semibold"),
    text("theme-preview:primary-subtitle", "contrast / radius / stroke", 50 * unit, 113 * unit, 86 * unit, 8 * unit, textMuted, 7.74 * unit, "regular"),
    rounded("theme-preview:progress-bg", 50 * unit, 123 * unit, 82 * unit, 2.4 * unit, 1.2 * unit, alpha(c(p.cardDisabled, "#55212831"), 0.74 * a)),
    gradientShape("theme-preview:progress-fill", 50 * unit, 123 * unit, 56 * unit, 2.4 * unit, 1.2 * unit, accent, accentSoft, 0),
    gradientShape("theme-preview:orb", 125 * unit, 87 * unit, 28 * unit, 28 * unit, 14 * unit, accent, accentSoft, 135, {
      stroke: alpha(c(p.windowStroke, "#66FFFFFF"), 0.62 * a),
      strokeWidth: Math.max(0.32, 0.42 * unit),
    }),
    rounded("theme-preview:chip-accent", 28 * unit, 154 * unit, 52 * unit, 14 * unit, 7 * unit, alpha(c(p.accent, "#FFFFFFFF"), 0.20 * a), alpha(c(p.accentSoft, "#99FFFFFF"), 0.72 * a), Math.max(0.32, 0.42 * unit)),
    text("theme-preview:chip-accent-text", "ACCENT", 28 * unit, 157.8 * unit, 52 * unit, 7 * unit, accent, 8.1 * unit, "semibold", "text-align-center"),
    rounded("theme-preview:chip-muted", 87 * unit, 154 * unit, 48 * unit, 14 * unit, 7 * unit, alpha(c(p.cardDisabled, "#55212831"), 0.48 * a), alpha(c(p.strokeSoft, "#33FFFFFF"), 0.42 * a), Math.max(0.32, 0.38 * unit)),
    text("theme-preview:chip-muted-text", "MUTED", 87 * unit, 157.8 * unit, 48 * unit, 7 * unit, textMuted, 8.1 * unit, "semibold", "text-align-center"),
  ];

  children.push(
    gradientShape("theme-preview:palette-card", 188 * unit, 44 * unit, 118 * unit, 44 * unit, 9 * unit, cardG.a, alpha(c(p.cardDisabled, "#55212831"), 0.52 * a), cardG.angle, {
      stroke: alpha(c(p.strokeSoft, "#33FFFFFF"), 0.66 * a),
      strokeWidth: Math.max(0.32, 0.40 * unit),
    }),
    text("theme-preview:palette-title", "Palette", 199 * unit, 54 * unit, 72 * unit, 8 * unit, textMuted, 9.0 * unit, "semibold")
  );

  const colors = swatches(p);
  for (let i = 0; i < colors.length; i++) {
    const col = i % 6;
    const row = Math.floor(i / 6);
    const x = 200 * unit + col * 12 * unit;
    const y = 68 * unit + row * 12 * unit;
    children.push(rounded(`theme-preview:swatch:${i}`, x, y, 6 * unit, 6 * unit, 3 * unit, alpha(c(colors[i], "#FFFFFFFF"), a), strokeSoft, Math.max(0.25, 0.32 * unit)));
  }

  children.push(
    gradientShape("theme-preview:controls-card", 188 * unit, 96 * unit, 118 * unit, 38 * unit, 9 * unit, alpha(c(p.cardEnabled, "#66333A46"), 0.78 * a), alpha(c(p.cardDisabled, "#55212831"), 0.52 * a), 110, {
      stroke: alpha(c(p.strokeSoft, "#33FFFFFF"), 0.62 * a),
      strokeWidth: Math.max(0.32, 0.40 * unit),
    }),
    text("theme-preview:controls-title", "Controls", 199 * unit, 106 * unit, 72 * unit, 8 * unit, textMuted, 9.0 * unit, "semibold"),
    text("theme-preview:slider-label", "Slider", 199 * unit, 119 * unit, 34 * unit, 7 * unit, textPrimary, 7.74 * unit, "medium"),
    rounded("theme-preview:slider-track", 238 * unit, 121 * unit, 52 * unit, 2.2 * unit, 1.1 * unit, alpha(c(p.cardDisabled, "#55212831"), 0.72 * a)),
    gradientShape("theme-preview:slider-fill", 238 * unit, 121 * unit, 34 * unit, 2.2 * unit, 1.1 * unit, accent, accentSoft, 0),
    rounded("theme-preview:slider-knob", 269 * unit, 118.8 * unit, 6.5 * unit, 6.5 * unit, 3.25 * unit, accent, alpha(c(p.windowStroke, "#66FFFFFF"), 0.8 * a), Math.max(0.25, 0.32 * unit)),
    text("theme-preview:input-label", "Input", 199 * unit, 129 * unit, 34 * unit, 7 * unit, textPrimary, 7.74 * unit, "medium"),
    rounded("theme-preview:input-box", 238 * unit, 128 * unit, 52 * unit, 8 * unit, 4 * unit, alpha(c(p.surface, "#55242A34"), 0.54 * a), strokeSoft, Math.max(0.25, 0.32 * unit)),

    gradientShape("theme-preview:gradient-card", 188 * unit, 142 * unit, 118 * unit, 34 * unit, 9 * unit, alpha(c(p.cardEnabled, "#66333A46"), 0.70 * a), alpha(c(p.cardDisabled, "#55212831"), 0.50 * a), 100, {
      stroke: alpha(c(p.strokeSoft, "#33FFFFFF"), 0.62 * a),
      strokeWidth: Math.max(0.32, 0.40 * unit),
    }),
    text("theme-preview:gradient-title", "Gradients", 199 * unit, 151 * unit, 72 * unit, 8 * unit, textMuted, 9.0 * unit, "semibold"),
    gradientShape("theme-preview:gradient-strip-a", 199 * unit, 164 * unit, 44 * unit, 4.5 * unit, 2.25 * unit, rootG.a, rootG.b, rootG.angle),
    gradientShape("theme-preview:gradient-strip-b", 249 * unit, 164 * unit, 44 * unit, 4.5 * unit, 2.25 * unit, surfaceG.a, surfaceG.b, surfaceG.angle)
  );

  children.push(...specimenControls(p, unit, a));
  return ui.root({
    key: "theme-preview:root-node",
    class: cls(`w-${s(w)}`, `h-${s(h)}`),
    children,
  });
}

export function render(ctx) {
  return buildTemplate(ctx);
}
