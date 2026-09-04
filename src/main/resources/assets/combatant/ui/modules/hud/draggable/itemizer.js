/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const n = ui.num;
const c = ui.str;
const s = ui.fmt;
const cls = ui.cls;
const abs = ui.abs;
const prop = ui.prop;
const arr = ui.arr;
const color = ui.color.get;
const alpha = ui.color.alpha;

function font(family, scale, type) {
  const suffix = type ? `-${type}` : "";
  return `font-${family}${suffix}-${s(scale)}`;
}

function entryPos(p, index) {
  const entryW = n(p.entryWidth, 24);
  const entryH = n(p.entryHeight, 24);
  const gap = n(p.gap, 4);
  if (c(p.direction, "horizontal") === "vertical") {
    return { x: 0, y: index * (entryH + gap), w: entryW, h: entryH };
  }
  return { x: index * (entryW + gap), y: 0, w: entryW, h: entryH };
}

function compactText(p, pal, pos, entry, index) {
  if (c(p.mode, "icons") !== "compact") return null;
  const bs = n(p.baseScale, 1);
  const fs = 0.78 * n(p.drawScale, 1);
  const text = c(prop(entry, "label", ""), "");
  const a = Math.max(0, Math.min(1, n(prop(entry, "alpha", 1), 1)));
  const accent = c(prop(entry, "accent", color(pal, "accent", "#FFFFFFFF")), color(pal, "accent", "#FFFFFFFF"));
  return ui.clippedText({
    key: `entry:${prop(entry, "key", index)}:label`,
    text,
    x: 24 * bs,
    y: 6.0 * bs,
    w: Math.max(1, pos.w - 29 * bs),
    h: Math.max(8, pos.h - 8 * bs),
    textClass: cls(font("Inter", fs, "bold"), `text-${alpha(color(pal, "text", "#FFFFFFFF"), a)}`),
    color: alpha(color(pal, "text", "#FFFFFFFF"), a),
    measuredWidth: Math.max(1, text.length * 6.4 * fs),
    fadeWidth: 7 * bs,
    fade: true,
    centerWhenFits: false,
  });
}

function entryNode(p, pal, entry, index) {
  const pos = entryPos(p, index);
  const children = [];
  const text = compactText(p, pal, pos, entry, index);
  if (text) children.push(text);
  return ui.stack({
    key: `entry:${prop(entry, "key", index)}`,
    class: abs(pos.x, pos.y, pos.w, pos.h, `rounded-${s(Math.min(pos.w, pos.h) * 0.24)}`),
    children,
  });
}

export function render(ctx) {
  const p = ctx.props || {};
  const pal = prop(p, "palette", {});
  const w = n(p.width, ctx.width || 24);
  const h = n(p.height, ctx.height || 24);
  const items = arr(p.items);
  return ui.root({
    key: "itemizer",
    class: cls(`w-${s(w)}`, `h-${s(h)}`),
    children: items.map((entry, index) => entryNode(p, pal, entry, index)),
  });
}
