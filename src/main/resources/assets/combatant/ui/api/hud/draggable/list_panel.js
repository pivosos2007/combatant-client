/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import { HudPanelLayout } from "./panel_base.js";

class HudListPanelLayout extends HudPanelLayout {
  constructor(ctx) {
    super(ctx);
    this.base.bodyInsetY = 6.5;
    this.base.rowStep = 11.0;
  }

  iconNode(row, rowCenterY) {
    const bs = this.bs();
    const kind = c(prop(row, "iconKind", ""), "");
    const icon = c(prop(row, "icon", ""), "");
    const size = n(prop(this.v, "rowIconSize", 8), 8) * bs;
    const x = n(prop(this.v, "rowIconX", 3.5), 3.5) * bs;
    const y = rowCenterY - size * 0.5;
    const tint = c(prop(row, "iconTint", "#FFFFFFFF"), "#FFFFFFFF");
    const radius = Math.min(1.15 * bs, size * 0.24);
    if (kind === "item") {
      return ui.item({
        key: `row:${prop(row, "key", "")}:item`,
        item: icon,
        overlay: false,
        class: abs(x, y, size, size),
      });
    }
    if (kind === "head") {
      if (icon) {
        return ui.image({
          key: `row:${prop(row, "key", "")}:head`,
          assetType: "player-head",
          asset: icon,
          secondLayer: true,
          tint,
          class: abs(x, y, size, size, `rounded-${s(radius)}`),
        });
      }
      return ui.shape({
        key: `row:${prop(row, "key", "")}:head-fallback`,
        shape: "rounded",
        class: abs(x, y, size, size),
        radius,
        fill: "#22FFFFFF",
        stroke: "#88FFFFFF",
        strokeWidth: Math.max(0.45, 0.55 * bs),
      });
    }
    if (kind === "texture" && icon) {
      return ui.image({
        key: `row:${prop(row, "key", "")}:texture`,
        assetType: "texture",
        asset: icon,
        tint,
        class: abs(x, y, size, size, `rounded-${s(Math.min(1.15 * bs, size * 0.18))}`),
      });
    }
    if (kind === "svg" && icon) {
      return ui.svg({
        key: `row:${prop(row, "key", "")}:svg`,
        asset: icon,
        tint,
        class: abs(x, y, size, size),
      });
    }
    return null;
  }

  nameParts(row, rowCenterY) {
    const bs = this.bs();
    const fs = this.fs();
    const rowTextH = n(this.p.rowTextHeight, 8);
    const x = n(prop(this.v, "rowTextX", 18), 18) * bs;
    const y = rowCenterY - rowTextH * 0.5;
    const parts = arr(prop(row, "nameParts", []));
    const nodes = [];
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const text = c(prop(part, "text", ""), "");
      const px = n(prop(part, "x", 0), 0);
      const partColor = c(prop(part, "color", "#FFFFFFFF"), "#FFFFFFFF");
      nodes.push(ui.text({
        key: `row:${prop(row, "key", "")}:name:${i}`,
        text,
        color: partColor,
        class: cls(abs(x + px, y, Math.max(10, text.length * 8 * fs), rowTextH + 4 * bs), font("OnestMedium", fs), `text-${partColor}`),
      }));
    }
    return nodes;
  }

  rowParts(row, index, cursorY) {
    const bs = this.bs();
    const fs = this.fs();
    const rowTextH = n(this.p.rowTextHeight, 8);
    const w = this.w();
    const rowCenterY = cursorY + n(prop(this.v, "rowCenterOffset", 2), 2) * bs;
    const key = c(prop(row, "key", `row:${index}`), `row:${index}`);
    const rightText = c(prop(row, "rightText", ""), "");
    const rightColor = c(prop(row, "rightColor", color(this.pal, "counter", "#FFFFFFFF")), color(this.pal, "counter", "#FFFFFFFF"));
    const rightW = Math.max(14 * bs, rightText.length * 7.5 * fs);
    const rightX = w - rightW - n(prop(this.v, "rowRightPad", 8), 8) * bs;
    const decorations = [];
    const icons = [];
    const text = [];
    const icon = this.iconNode(row, rowCenterY);
    if (icon) icons.push(icon);
    decorations.push(ui.shape({
      key: `row:${key}:divider`,
      shape: "rounded",
      class: abs(n(prop(this.v, "rowDividerX", 15), 15) * bs, rowCenterY - n(prop(this.v, "rowDividerH", 6), 6) * bs * 0.5, Math.max(0.5 * n(this.p.drawScale, 1), 0.5 * bs), n(prop(this.v, "rowDividerH", 6), 6) * bs),
      radius: 0.5 * bs,
      fill: c(prop(row, "dividerColor", color(this.pal, "divider", "#66FFFFFF")), color(this.pal, "divider", "#66FFFFFF")),
    }));
    text.push(...this.nameParts(row, rowCenterY));
    text.push(ui.text({
      key: `row:${key}:right`,
      text: rightText,
      color: rightColor,
      class: cls(abs(rightX, rowCenterY - rowTextH * 0.5, rightW, rowTextH + 4 * bs), font("OnestMedium", fs), `text-${rightColor}`, "text-align-right"),
    }));
    return { decorations, icons, text };
  }

  renderContent() {
    const bs = this.bs();
    const rows = arr(this.p.rows);
    const decorations = [];
    const icons = [];
    const text = [];
    let cursorY = this.base.bodyY * bs + this.base.bodyInsetY * bs;
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const parts = this.rowParts(row, i, cursorY);
      decorations.push(...parts.decorations);
      icons.push(...parts.icons);
      text.push(...parts.text);
      cursorY += this.base.rowStep * bs * Math.max(0, n(prop(row, "alpha", 1), 1));
    }
    const children = [...decorations, ...icons, ...text];
    return [
      ui.stack({
        key: "rows",
        class: abs(0, 0, this.w(), this.h()),
        children,
      }),
    ];
  }
}

export function render(ctx) {
  return new HudListPanelLayout(ctx).render();
}
