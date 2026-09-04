/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const SKIN = Object.freeze({
  shell: {
    veil: "#10141414",
    veilInset: 0.75,
  },
  shape: {
    fillInset: 0.95,
  },
  health: {
    alpha: 0x42,
    dynamicLow: "#FFFF0000",
    dynamicMid: "#FFFFFF00",
    dynamicHigh: "#FF00FF00",
    dynamicEndMixToWhite: 0.30,
    themedEndMixToText: 0.18,
    smokeMix: 0.48,
    smokeScale: 3.1,
    smokeIntensity: 1.08,
    smokeFlowX: 0.070,
    smokeFlowY: -0.035,
  },
  absorption: {
    alpha: 0x50,
    start: "#FFFFB400",
    end: "#FFFFE17A",
    smokeMix: 0.42,
    smokeScale: 3.6,
    smokeIntensity: 1.00,
    smokeFlowX: -0.045,
    smokeFlowY: -0.055,
    pulseMin: 0.86,
    pulseMax: 1.00,
    pulseSpeed: 4.2,
    flowSpeed: 3.4,
    flowPhaseScale: 0.35,
    flowMinPx: 3.0,
    flowWidthFactor: 0.22,
  },
});

const n = ui.num;
const c = ui.str;
const s = ui.fmt;
const cls = ui.cls;
const abs = ui.abs;

function clamp01(value) {
  return Math.max(0, Math.min(1, n(value, 0)));
}

function colorInt(value, fallback) {
  const src = c(value, fallback);
  if (!src.startsWith("#")) return colorInt(fallback, "#00000000");
  if (src.length === 9) return Number.parseInt(src.slice(1), 16) >>> 0;
  if (src.length === 7) return (0xff000000 | Number.parseInt(src.slice(1), 16)) >>> 0;
  return colorInt(fallback, "#00000000");
}

function hexColor(argb) {
  return "#" + (argb >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

function withAlpha(hex, alpha) {
  const rgb = colorInt(hex, "#00000000") & 0x00ffffff;
  const a = Math.max(0, Math.min(255, Math.round(n(alpha, 255))));
  return hexColor(((a << 24) | rgb) >>> 0);
}

function alpha(hex, amount) {
  const raw = colorInt(hex, "#00000000");
  const nextA = Math.max(0, Math.min(255, Math.round(((raw >>> 24) & 255) * clamp01(amount))));
  return hexColor(((nextA << 24) | (raw & 0x00ffffff)) >>> 0);
}

function mixRgb(a, b, t) {
  const ca = colorInt(a, "#FF000000");
  const cb = colorInt(b, "#FF000000");
  const k = clamp01(t);
  const ar = (ca >>> 16) & 255;
  const ag = (ca >>> 8) & 255;
  const ab = ca & 255;
  const br = (cb >>> 16) & 255;
  const bg = (cb >>> 8) & 255;
  const bb = cb & 255;
  return hexColor((0xff000000
    | ((ar + (br - ar) * k + 0.5) << 16)
    | ((ag + (bg - ag) * k + 0.5) << 8)
    | ((ab + (bb - ab) * k + 0.5))) >>> 0);
}

function healthDynamicColor(ratio) {
  const t = clamp01(ratio);
  if (t >= 0.5) {
    return mixRgb(SKIN.health.dynamicMid, SKIN.health.dynamicHigh, (t - 0.5) / 0.5);
  }
  return mixRgb(SKIN.health.dynamicLow, SKIN.health.dynamicMid, t / 0.5);
}

function themedHealthColors(p) {
  const start = c(p.themeAccent, "#FFFFFFFF");
  const soft = c(p.themeAccentSoft, start);
  const text = c(p.themeTextPrimary, "#FFFFFFFF");
  return {
    start,
    end: mixRgb(soft, text, SKIN.health.themedEndMixToText),
  };
}

function healthColors(p, ratio) {
  if (p.colorMode === "dynamic") {
    const start = healthDynamicColor(ratio);
    return {
      start,
      end: mixRgb(start, "#FFFFFFFF", SKIN.health.dynamicEndMixToWhite),
    };
  }
  return themedHealthColors(p);
}

function rounded(key, x, y, w, h, radius, fill, extra = {}) {
  return ui.shape({
    key,
    shape: "rounded",
    class: abs(x, y, w, h),
    radius,
    fill,
    ...extra,
  });
}

function roundedSmokeFill(key, x, y, w, h, radius, fillRatio, firstColor, secondColor, thirdColor, extra = {}) {
  return ui.shape({
    key,
    shape: "rounded-smoke-fill",
    class: abs(x, y, w, h),
    radius,
    fillRatio: clamp01(fillRatio),
    firstColor,
    secondColor,
    thirdColor,
    ...extra,
  });
}

function fills(ctx, p, w, h, r) {
  const globalAlpha = clamp01(n(p.alpha, 1));
  const healthRatio = clamp01(n(p.healthRatio, 0));
  const absorbRatio = clamp01(n(p.absorbRatio, 0));
  const inset = Math.max(0, Math.min(SKIN.shape.fillInset, Math.min(w, h) * 0.30));
  const innerW = Math.max(0, w - inset * 2.0);
  const innerH = Math.max(0, h - inset * 2.0);
  const innerR = Math.max(0, r - inset);
  const phase = n(p.phase, 0);
  const smokeTime = n(ctx.time, phase) + phase * 0.37;
  const pulseT = 0.5 + 0.5 * Math.sin(phase * SKIN.absorption.pulseSpeed);
  const absorbPulse = SKIN.absorption.pulseMin + (SKIN.absorption.pulseMax - SKIN.absorption.pulseMin) * pulseT;
  const flowOffset = Math.sin(ctx.time * SKIN.absorption.flowSpeed + phase * SKIN.absorption.flowPhaseScale)
    * Math.max(SKIN.absorption.flowMinPx, w * SKIN.absorption.flowWidthFactor);

  const nodes = [];
  if (innerW > 0.01 && innerH > 0.01 && healthRatio > 0.0001) {
    const colors = healthColors(p, healthRatio);
    const first = alpha(withAlpha(colors.start, SKIN.health.alpha), globalAlpha);
    const second = alpha(withAlpha(colors.end, SKIN.health.alpha), globalAlpha);
    const third = alpha(withAlpha(mixRgb(colors.end, "#FFFFFFFF", 0.22), SKIN.health.alpha), globalAlpha);
    nodes.push(roundedSmokeFill(
      "health:fill",
      inset,
      inset,
      innerW,
      innerH,
      innerR,
      healthRatio,
      first,
      second,
      third,
      {
        time: smokeTime,
        smokeMix: SKIN.health.smokeMix,
        smokeScale: SKIN.health.smokeScale,
        intensity: SKIN.health.smokeIntensity,
        octaves: 4,
        flowX: SKIN.health.smokeFlowX,
        flowY: SKIN.health.smokeFlowY,
      }
    ));
  }
  if (innerW > 0.01 && innerH > 0.01 && absorbRatio > 0.0001) {
    const first = alpha(withAlpha(SKIN.absorption.start, SKIN.absorption.alpha), absorbPulse * globalAlpha);
    const second = alpha(withAlpha(SKIN.absorption.end, SKIN.absorption.alpha), absorbPulse * globalAlpha);
    const third = alpha(withAlpha(mixRgb(SKIN.absorption.end, "#FFFFFFFF", 0.16), SKIN.absorption.alpha), absorbPulse * globalAlpha);
    nodes.push(roundedSmokeFill(
      "absorb:fill",
      inset,
      inset,
      innerW,
      innerH,
      innerR,
      absorbRatio,
      first,
      second,
      third,
      {
        time: smokeTime + flowOffset * 0.015,
        smokeMix: SKIN.absorption.smokeMix,
        smokeScale: SKIN.absorption.smokeScale,
        intensity: SKIN.absorption.smokeIntensity,
        octaves: 4,
        flowX: SKIN.absorption.smokeFlowX,
        flowY: SKIN.absorption.smokeFlowY,
      }
    ));
  }
  return nodes;
}

export function render(ctx) {
  const p = ctx.props || {};
  const w = Math.max(0, n(p.width, ctx.width));
  const h = Math.max(0, n(p.height, ctx.height));
  const r = Math.max(0, n(p.radius, h * 0.5));
  const globalAlpha = clamp01(n(p.alpha, 1));

  return ui.root({
    key: "custom-health-bar",
    class: cls(`w-${s(w)}`, `h-${s(h)}`, `rounded-${s(r)}`, "clip"),
    renderRadius: r,
    children: [
      rounded(
        "shell:veil",
        SKIN.shell.veilInset,
        SKIN.shell.veilInset,
        Math.max(0, w - SKIN.shell.veilInset * 2.0),
        Math.max(0, h - SKIN.shell.veilInset * 2.0),
        Math.max(0, r - SKIN.shell.veilInset),
        alpha(SKIN.shell.veil, globalAlpha)
      ),
      ui.stack({
        key: "fills",
        class: cls(abs(0, 0, w, h), "clip"),
        children: fills(ctx, p, w, h, r),
      }),
    ],
  });
}
