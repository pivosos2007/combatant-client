/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import { HudPanelLayout } from "./panel_base.js";

class HudInventoryPanelLayout extends HudPanelLayout {
  constructor(ctx) {
    super(ctx);
    this.base.bodyY = 18.4;
  }

  counterLabel() {
    return "Items:";
  }

  gridLine(key, x, y, w, h) {
    return ui.shape({
      key,
      shape: "rounded",
      class: abs(x, y, w, h),
      radius: Math.max(0.25, Math.min(w, h) * 0.5),
      fill: c(this.p.gridDivider, "#33FFFFFF"),
    });
  }

  slotFrame(row, col) {
    const bs = this.bs();
    const gx = prop(this.p, "gridStartX", null) == null
      ? this.contentInsetX()
      : n(this.p.gridStartX, this.base.leftInsetX) * bs;
    const gy = n(this.p.gridStartY, 20.0) * bs;
    const step = n(this.p.gridStep, 13.0) * bs;
    const itemX = gx + col * step;
    const itemY = gy + row * step;
    const lineT = Math.max(0.5 * n(this.p.drawScale, 1), n(this.p.gridLineThickness, 0.5) * bs);
    const lineLen = n(this.p.gridLineLength, 9.0) * bs;
    const nodes = [];
    if (col < n(this.p.cols, 9) - 1) {
      nodes.push(this.gridLine(`grid:v:${row}:${col}`, itemX + n(this.p.gridLineOffsetX, 11.0) * bs, itemY + bs, lineT, lineLen));
    }
    if (row < n(this.p.rowsCount, 3) - 1) {
      nodes.push(this.gridLine(`grid:h:${row}:${col}`, itemX + 0.5 * bs, itemY + n(this.p.gridLineOffsetY, 10.0) * bs, lineLen, lineT));
    }
    return nodes;
  }

  itemNode(cell, index) {
    const bs = this.bs();
    const gx = prop(this.p, "gridStartX", null) == null
      ? this.contentInsetX()
      : n(this.p.gridStartX, this.base.leftInsetX) * bs;
    const gy = n(this.p.gridStartY, 20.0) * bs;
    const step = n(this.p.gridStep, 13.0) * bs;
    const slot = Math.max(0, Math.round(n(prop(cell, "slot", index), index)));
    const cols = Math.max(1, Math.round(n(this.p.cols, 9)));
    const row = Math.floor(slot / cols);
    const col = slot % cols;
    const size = 16.0 * n(this.p.itemRenderScale, 0.5) * bs;
    const x = gx + col * step;
    const y = gy + row * step;
    return ui.item({
      key: `item:${slot}`,
      stack: prop(cell, "stack", null),
      item: c(prop(cell, "item", ""), ""),
      count: n(prop(cell, "count", 1), 1),
      damage: n(prop(cell, "damage", 0), 0),
      maxDamage: n(prop(cell, "maxDamage", 0), 0),
      overlay: true,
      class: abs(x, y, size, size),
    });
  }

  renderContent() {
    const cols = Math.max(1, Math.round(n(this.p.cols, 9)));
    const rows = Math.max(1, Math.round(n(this.p.rowsCount, 3)));
    const grid = [];
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < cols; col++) {
        grid.push(...this.slotFrame(row, col));
      }
    }
    const itemNodes = arr(this.p.items).map((cell, index) => this.itemNode(cell, index));
    return [
      ui.stack({
        key: "inventory:grid",
        class: abs(0, 0, this.w(), this.h()),
        children: [...grid, ...itemNodes],
      }),
    ];
  }
}

export function render(ctx) {
  return new HudInventoryPanelLayout(ctx).render();
}
