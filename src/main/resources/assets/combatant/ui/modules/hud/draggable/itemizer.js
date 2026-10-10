/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const num = ui.num;
const str = ui.str;
const rgba = ui.color.alpha;
const value = ui.color.get;
const clamp = (v) => Math.max(0, Math.min(1, v));

function keyText(id, text, x, y, w, a, tint, size = 11) {
  const fontSize = Math.max(8, Math.round(size));
  return ui.clippedText({
    key: id, text, x, y, w, h: fontSize,
    color: rgba(tint, a),
    textClass: ui.cls("font-Inter", `font-size-${fontSize}`, `text-${rgba(tint, a)}`),
    measuredWidth: w, fade: false, centerWhenFits: false,
  });
}

function card(row, index, pal, isChat, hovered, blur) {
  const x = num(row.x, 0);
  const y = num(row.y, 0);
  const w = num(row.w, 30);
  const scale = num(row.scale, 1);
  const h = num(row.h, 16 * scale);
  const a = clamp(num(row.alpha, 1));
  const available = row.available !== false;
  const enabled = row.enabled !== false;
  const visible = row.visible !== false;
  const active = row.active === true;
  const aware = row.aware === true;
  const inProgress = row.inProgress === true;
  const hot = clamp(num(row.heat, aware || inProgress ? 1 : 0));
  const selected = isChat && hovered === index;
  const dim = available && enabled ? 1 : 0.57;
  const panelA = a * (visible ? 1 : 0.38);
  const bgTop = value(pal, "bgTop", "#96121314");
  const bgBottom = value(pal, "bgBottom", "#96000205");
  const accent = value(pal, "accent", "#FFAABBFF");
  const text = value(pal, "text", "#FFE1E1FF");
  const stroke = value(pal, "stroke", "#FF212121");
  const nodes = [];

  if (blur) {
    nodes.push(ui.blurSurface({
      key: `${index}:blur`, x, y, w, h, radius: 3 * scale,
      alpha: panelA * 0.85, brightness: 1.0,
    }));
  }
  nodes.push(ui.roundedGradient({
    key: `${index}:panel`, x, y, w, h, radius: 3 * scale,
    startColor: rgba(bgTop, panelA),
    endColor: rgba(bgBottom, panelA), angle: 90,
  }));
  nodes.push(ui.roundedGradient({
    key: `${index}:icon-bg`, x, y, w: 16 * scale, h, radius: 3 * scale,
    startColor: rgba(bgTop, panelA),
    endColor: rgba(bgBottom, panelA), angle: 90,
  }));
  if (!available || !enabled) {
    nodes.push(ui.roundedGradient({
      key: `${index}:unavailable`, x, y, w, h, radius: 3 * scale,
      startColor: rgba("#FFFF2D29", 0.11 * panelA),
      endColor: rgba("#FF760A0A", 0.05 * panelA), angle: 92,
    }));
  }
  if (hot > 0.01) {
    nodes.push(ui.roundedGradient({
      key: `${index}:aware`, x, y, w, h, radius: 3 * scale,
      startColor: rgba(accent, 0.12 * hot * panelA),
      endColor: rgba(accent, 0.018 * hot * panelA), angle: 95,
    }));
  }
  if (inProgress) {
    const remaining = clamp(1 - num(row.progress, 0));
    nodes.push(ui.shape({
      key: `${index}:consume-overlay`, shape: "rounded",
      style: ui.absolute(x + 0.6 * scale, y + h * (1 - remaining) + 0.5 * scale,
        14.8 * scale, Math.max(0, h * remaining - scale)),
      radius: 0.5 * scale, fill: rgba(accent, 0.13 * panelA),
    }));
  } else if (num(row.cooldown, 0) > 0) {
    const cd = clamp(num(row.cooldown, 0));
    nodes.push(ui.shape({
      key: `${index}:cooldown-fill`, shape: "rounded",
      style: ui.absolute(x + 0.7 * scale, y + h * (1 - cd), 14.6 * scale,
        Math.max(0, h * cd - 0.7 * scale)),
      radius: 0.6 * scale, fill: rgba("#FF101010", 0.3 * panelA),
    }));
  }
  nodes.push(ui.roundedStrokeGradient({
    key: `${index}:outline`, x, y, w, h, radius: 3 * scale, thickness: 0.4 * scale,
    startColor: rgba(stroke, 0.95 * panelA),
    endColor: rgba(stroke, 0.95 * panelA), angle: 90,
  }));
  nodes.push(keyText(`${index}:key`, str(row.bind, "—"),
    x + 19 * scale, y + 3 * scale, w - 22 * scale,
    dim * panelA, text, 11 * scale));
  if (selected) {
    nodes.push(ui.shape({
      key: `${index}:eye-bg`, shape: "rounded",
      style: ui.absolute(x + 0.8 * scale, y + 0.8 * scale, 14.4 * scale, 14.4 * scale),
      radius: 2.5 * scale, fill: rgba("#FF08090D", 0.8 * a),
    }));
    nodes.push(ui.svg({
      key: `${index}:eye`, asset: "eye", tint: rgba(visible ? text : accent, a),
      style: ui.absolute(x + 3.2 * scale, y + 3.5 * scale, 9.6 * scale, 9.2 * scale),
    }));
  } else if (!visible && isChat) {
    nodes.push(keyText(`${index}:hidden`, "×",
      x + 5 * scale, y + 3 * scale, 8 * scale, a, text, 11 * scale));
  }
  return nodes;
}

export function render(ctx) {
  const props = ctx.props || {};
  const palette = props.palette || {};
  const items = Array.isArray(props.items) ? props.items : [];
  const nodes = [];
  const chat = props.chat === true;
  const hovered = num(props.hovered, -1);
  for (let i = 0; i < items.length; i++) {
    nodes.push(...card(items[i], i, palette, chat, hovered, props.blur === true));
  }
  return ui.root({
    key: "itemizer",
    style: {width: num(props.width, ctx.width || 0), height: num(props.height, ctx.height || 0)},
  }, nodes);
}
