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

const layout = {
  iconX: 5.0,
  dividerX: 21.0,
  dividerXById: {
    game_time: 23.5,
    system_time: 23.5,
  },
  dividerY: 4.0,
  dividerW: 0.6,
  textX: 26.0,
  textXById: {
    game_time: 28.5,
    system_time: 28.5,
  },
  rightPad: 5.0,
  rightPadById: {
    speed_bps: 3.5,
    tps: 3.5,
  },
  valueSlots: {
    fps: [14.0, 21.0, 28.0, 36.0],
    ping: [16.0, 23.0, 31.0, 40.0],
    tps: [17.0, 24.0, 31.0, 39.0],
    memory: [24.0, 42.0, 58.0, 74.0],
    speed_bps: [18.0, 23.0, 29.0, 36.0],
    game_time: [10.0, 18.0, 27.0, 36.0, 45.0],
    system_time: [10.0, 18.0, 27.0, 36.0, 45.0, 52.0, 59.0, 66.0],
    default: [18.0, 26.0, 34.0, 42.0],
  },
  unitSlots: {
    fps: 17.0,
    ping: 16.0,
    tps: 15.0,
    memory: 17.0,
    speed_bps: 20.0,
    default: 18.0,
  },
};

function alpha(hex, amount) {
  const src = c(hex, "#00000000");
  if (!src.startsWith("#") || src.length !== 9) return src;
  const raw = Number.parseInt(src.slice(1), 16);
  const nextA = Math.max(0, Math.min(255, Math.round(((raw >>> 24) & 255) * amount)));
  return "#" + (((nextA << 24) | (raw & 0x00ffffff)) >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

function withAlpha(hex, amount) {
  const src = c(hex, "#00000000");
  if (!src.startsWith("#") || src.length !== 9) return src;
  const raw = Number.parseInt(src.slice(1), 16);
  const nextA = Math.max(0, Math.min(255, Math.round(255 * amount)));
  return "#" + (((nextA << 24) | (raw & 0x00ffffff)) >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

function alpha01(hex) {
  const src = c(hex, "#00000000");
  if (!src.startsWith("#") || src.length !== 9) return 1;
  const raw = Number.parseInt(src.slice(1), 16);
  return Math.max(0, Math.min(1, ((raw >>> 24) & 255) / 255));
}

function font(family, scale) {
  const name = c(family, "OnestMedium");
  const mapped = name === "OnestMedium" ? "OnestMedium" : name;
  return `font-${mapped}-${s(n(scale, 1))}`;
}

function textNode(key, text, x, y, w, h, family, scale, color, extra, props = {}) {
  return ui.text({
    key,
    text: text || "",
    color: c(color, "#FFFFFFFF"),
    class: cls(abs(x, y, w, h), font(family, scale), `text-${c(color, "#FFFFFFFF")}`, extra),
    ...props,
  });
}

function slot(map, id, measured, fallback) {
  const base = Object.prototype.hasOwnProperty.call(map, id) ? map[id] : fallback;
  return Math.max(n(measured, 0), n(base, fallback));
}

function valueSlot(p, measured, scale) {
  const slots = Object.prototype.hasOwnProperty.call(layout.valueSlots, p.id || "")
    ? layout.valueSlots[p.id]
    : layout.valueSlots.default;
  const text = String(p.valueText || "");
  const len = Math.max(1, text.length);
  const index = Math.min(slots.length - 1, Math.max(0, len - 1));
  return Math.max(n(measured, 0), n(slots[index], slots[slots.length - 1]));
}

function measuredValueW(p, scale) {
  return Math.max(1, n(p.valueW, 12) / Math.max(0.001, scale) * scale);
}

function layoutValue(map, id, fallback) {
  return Object.prototype.hasOwnProperty.call(map, id || "") ? map[id] : fallback;
}

function rightPad(p) {
  return layoutValue(layout.rightPadById, p.id || "", layout.rightPad);
}

function requiredWidth(p) {
  const scale = n(p.scale, 1);
  if (p.id === "xyz") return n(p.width, 92);
  const valueX = layoutValue(layout.textXById, p.id || "", layout.textX) * scale;
  const valueW = p.valueVisible === true
    ? valueSlot(p, n(p.valueW, 12) / Math.max(0.001, scale), scale) * scale
    : 0;
  const unitGap = p.unitVisible === true ? Math.max(1, 2 * scale) : 0;
  const unitW = p.unitVisible === true
    ? slot(layout.unitSlots, p.id || "", n(p.unitW, 10) / Math.max(0.001, scale), layout.unitSlots.default) * scale
    : 0;
  const extraGap = p.extraVisible === true ? Math.max(2, 4 * scale) : 0;
  const extraW = p.extraVisible === true ? n(p.extraW, 0) : 0;
  return valueX + valueW + unitGap + unitW + extraGap + extraW + rightPad(p) * scale;
}

function visualWidth(p, width) {
  const scale = n(p.scale, 1);
  const required = requiredWidth(p);
  if (p.id === "speed_bps") return Math.max(required, width - 1.5 * scale);
  if (p.id === "xyz" && p.showNether === true) return Math.max(1, width - 4.0 * scale);
  return Math.max(width, required);
}

function shell(p) {
  const w = n(p.width, 52);
  const h = n(p.height, 20);
  const r = n(p.radius, 5);
  const strokeW = Math.max(0.45, n(p.strokeWidth, 0.55));
  const softness = n(p.softness, 1);
  const bg1 = c(p.bgPrimary, "#E522242C");
  const bg2 = c(p.bgSecondary, "#E50E1015");
  const stroke = c(p.stroke, "#665A5A5A");
  const paintAlpha = Math.max(alpha01(bg1), alpha01(bg2));
  const hasStrokeControl = p.strokeControlled === true;
  const strokeEnabled = hasStrokeControl ? p.strokeEnabled === true : true;
  const configuredStrokeAlpha = Math.max(0, Math.min(1, n(p.strokeAlpha, 1)));
  const strokeAlpha = strokeEnabled
    ? (hasStrokeControl ? configuredStrokeAlpha : Math.max(alpha01(stroke), paintAlpha))
    : 0;
  const useStrokeGradient = hasStrokeControl && p.strokeGradient === true;
  const strokeStart = hasStrokeControl
    ? c(useStrokeGradient ? p.strokeStartColor : stroke, stroke)
    : "#FFFFFFFF";
  const strokeEnd = hasStrokeControl
    ? c(useStrokeGradient ? p.strokeEndColor : stroke, stroke)
    : stroke;
  return [
    ui.shape({
      key: "fill",
      shape: "rounded-gradient",
      class: abs(0, 0, w, h),
      radius: r,
      softness,
      startColor: bg1,
      endColor: bg2,
      angle: 92,
      stroke: hasStrokeControl ? withAlpha(stroke, strokeAlpha) : alpha(stroke, strokeAlpha),
      strokeWidth: strokeW,
      strokeStartColor: hasStrokeControl ? withAlpha(strokeStart, strokeAlpha) : alpha(strokeStart, 0.16 * strokeAlpha),
      strokeEndColor: hasStrokeControl ? withAlpha(strokeEnd, strokeAlpha) : alpha(strokeEnd, strokeAlpha),
      strokeAngle: 90,
    }),
    ui.shape({
      key: "top-glint",
      shape: "rounded-gradient",
      class: abs(1, 1, Math.max(0, w - 2), Math.max(1, h * 0.42)),
      radius: Math.max(0, r - 1),
      softness: 0.8,
      startColor: alpha("#FFFFFFFF", 0.065 * paintAlpha),
      endColor: alpha("#FFFFFFFF", 0),
      angle: 90,
    }),
  ].filter(Boolean);
}

function icon(p) {
  if (p.iconVisible !== true) return null;
  const w = n(p.iconW, 12);
  const h = n(p.iconH, w);
  const x = layout.iconX * n(p.scale, 1);
  const y = Math.max(0, (n(p.height, 20) - h) * 0.5);
  const color = c(p.iconColor, "#FFFFFFFF");
  if (p.iconKind === "glyph") {
    return textNode("icon:glyph", p.iconGlyph || "", x, y, w, h, p.iconFont || "WeatherIcons", n(p.iconScale, 1), color, "text-align-center");
  }
  return ui.image({
    key: "icon:texture",
    assetType: "texture",
    asset: p.iconId || "",
    class: abs(x, y, w, h, `rounded-${s(Math.min(w, h) * 0.22)}`),
    tint: color,
    fit: "contain",
    mask: true,
  });
}

function divider(p) {
  if (p.dividerVisible !== true) return null;
  const scale = n(p.scale, 1);
  const h = Math.max(0.5, n(p.height, 20) - 2 * layout.dividerY * scale);
  const w = Math.max(0.5, layout.dividerW * scale);
  const dividerX = layoutValue(layout.dividerXById, p.id || "", layout.dividerX);
  return ui.shape({
    key: "divider",
    shape: "rounded",
    class: abs(dividerX * scale, layout.dividerY * scale, w, h),
    radius: Math.max(0.5, w * 0.5),
    softness: 0.65,
    fill: c(p.dividerColor, "#44FFFFFF"),
  });
}

function compactTextRow(p) {
  const children = [];
  const scale = n(p.scale, 1);
  const valueX = layoutValue(layout.textXById, p.id || "", layout.textX) * scale;
  const valueY = n(p.valueY, 4);
  const valueW = measuredValueW(p, scale);
  const unitGap = Math.max(1, 2 * scale);
  const unitX = valueX + valueW + unitGap;
  const unitW = Math.max(1, slot(layout.unitSlots, p.id || "", n(p.unitW, 10) / Math.max(0.001, scale), layout.unitSlots.default) * scale);
  const extraX = unitX + unitW + Math.max(2, 4 * scale);
  if (p.valueVisible === true) {
    children.push(textNode("value", p.valueText || "", valueX, valueY, valueW, Math.max(8, n(p.height, 20)), p.valueFont, n(p.valueScale, 1), c(p.valueColor, "#FFFFFFFF")));
  }
  if (p.unitVisible === true) {
    children.push(textNode("unit", p.unitText || "", unitX, valueY, unitW, Math.max(8, n(p.height, 20)), p.unitFont, n(p.unitScale, n(p.valueScale, 1)), c(p.unitColor, "#99FFFFFF")));
  }
  if (p.extraVisible === true) {
    children.push(textNode("extra", p.extraText || "", extraX, valueY, Math.max(1, n(p.extraW, 10)), Math.max(8, n(p.height, 20)), p.extraFont, n(p.extraScale, n(p.valueScale, 1)), c(p.extraColor, "#77FFFFFF")));
  }
  return ui.stack({
    key: "text:layer",
    class: abs(0, 0, n(p.width, 52), n(p.height, 20)),
    children,
  });
}

function coordinatesRow(p) {
  const x = layoutValue(layout.textXById, p.id || "", layout.textX) * n(p.scale, 1);
  const y = n(p.valueY, 4);
  const h = Math.max(8, n(p.height, 20) - y);
  const scale = n(p.valueScale, n(p.scale, 1));
  const gap = Math.max(2, n(p.scale, 1) * 3.2);
  const pairGap = Math.max(1, n(p.scale, 1) * 1.6);
  const labelColor = c(p.labelColor, c(p.unitColor, "#99FFFFFF"));
  const valueColor = c(p.valueColor, "#FFFFFFFF");
  const children = [
    ui.text({ key: "x:l", text: "x", color: labelColor, class: cls(font("OnestMedium", scale), `text-${labelColor}`) }),
    ui.text({ key: "x:v", text: p.xText || "0", color: valueColor, class: cls(`ml-${s(pairGap)}`, font("Onest", scale), `text-${valueColor}`) }),
    ui.text({ key: "y:l", text: "y", color: labelColor, class: cls(`ml-${s(gap)}`, font("OnestMedium", scale), `text-${labelColor}`) }),
    ui.text({ key: "y:v", text: p.yText || "0", color: valueColor, class: cls(`ml-${s(pairGap)}`, font("Onest", scale), `text-${valueColor}`) }),
    ui.text({ key: "z:l", text: "z", color: labelColor, class: cls(`ml-${s(gap)}`, font("OnestMedium", scale), `text-${labelColor}`) }),
    ui.text({ key: "z:v", text: p.zText || "0", color: valueColor, class: cls(`ml-${s(pairGap)}`, font("Onest", scale), `text-${valueColor}`) }),
  ];
  if (p.showNether === true && p.netherText) {
    children.push(ui.text({
      key: "nether",
      text: p.netherText,
      color: c(p.extraColor, "#77FFFFFF"),
      class: cls(`ml-${s(gap)}`, font("Onest", scale), `text-${c(p.extraColor, "#77FFFFFF")}`),
    }));
  }
  return ui.row({
    key: "coords:row",
    class: cls(abs(x, y, Math.max(0, n(p.width, 92) - x - 4), h), "align-center"),
    children,
  });
}

function digitLayer(p) {
  if (p.digitAnimation !== true) return compactTextRow(p);
  const progress = Math.max(0, Math.min(1, n(p.digitProgress, 1)));
  const offset = n(p.digitOffset, 8) * (1 - progress);
  const scale = n(p.scale, 1);
  const valueX = layoutValue(layout.textXById, p.id || "", layout.textX) * scale;
  const valueY = n(p.valueY, 4);
  const valueW = measuredValueW(p, scale);
  const unitX = valueX + valueW + Math.max(1, 2 * scale);
  const currentY = -offset + 2;
  const previousY = n(p.digitOffset, 8) * progress + 2;
  const children = [
    ui.stack({
      key: "digit:clip",
      class: abs(valueX, valueY - 2, valueW, Math.max(8, n(p.height, 20)), "clip overflow-hidden"),
      children: [
        textNode("digit:prev", p.previousValue || "", 0, 0, valueW, Math.max(8, n(p.height, 20)), p.valueFont, n(p.valueScale, 1), alpha(c(p.valueColor, "#FFFFFFFF"), 1 - progress), "", { textOffsetY: previousY }),
        textNode("digit:current", p.valueText || "", 0, 0, valueW, Math.max(8, n(p.height, 20)), p.valueFont, n(p.valueScale, 1), alpha(c(p.valueColor, "#FFFFFFFF"), progress), "", { textOffsetY: currentY }),
      ],
    }),
  ];
  if (p.unitVisible === true) {
    children.push(textNode("unit", p.unitText || "", unitX, valueY, Math.max(1, slot(layout.unitSlots, p.id || "", n(p.unitW, 10) / Math.max(0.001, scale), layout.unitSlots.default) * scale), Math.max(8, n(p.height, 20)), p.unitFont, n(p.unitScale, n(p.valueScale, 1)), c(p.unitColor, "#99FFFFFF")));
  }
  if (p.extraVisible === true) {
    children.push(textNode("extra", p.extraText || "", n(p.extraX, 0), n(p.extraY, valueY), Math.max(1, n(p.extraW, 10)), Math.max(8, n(p.height, 20)), p.extraFont, n(p.extraScale, n(p.valueScale, 1)), c(p.extraColor, "#77FFFFFF")));
  }
  return ui.stack({
    key: "digit:layer",
    class: abs(0, 0, n(p.width, 52), n(p.height, 20)),
    children,
  });
}

export function buildTemplate(ctx) {
  const p = ctx.props || {};
  const w = n(p.width, 52);
  const h = n(p.height, 20);
  const visualW = visualWidth(p, w);
  const view = visualW === w ? p : { ...p, width: visualW };
  const blur = p.backgroundEffect === "Blur";
  const nodes = [];
  nodes.push(...shell(view));
  const ic = icon(view);
  const div = divider(view);
  if (ic) nodes.push(ic);
  if (div) nodes.push(div);
  nodes.push(p.id === "xyz" ? coordinatesRow(view) : digitLayer(view));

  return ui.root({
    key: `compact-stat:${p.id || "unknown"}`,
    class: cls(
      `w-${s(visualW)}`,
      `h-${s(h)}`,
      `rounded-${s(n(p.radius, 5))}`,
      p.shadowControlled === true ? "" : "shadow-compact",
      blur ? "blur" : "",
      blur ? `blur-alpha-${s(n(p.blurAlpha, 0.45))}` : ""
    ),
    children: [
      ui.stack({
        key: "clip",
        class: abs(0, 0, visualW, h, `rounded-${s(n(p.radius, 5))}`),
        children: nodes,
      }),
    ],
  });
}

export function render(ctx) {
  return buildTemplate(ctx);
}
