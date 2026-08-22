/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import { HudPanelLayout } from "./panel_base.js";

class StatisticsPanelLayout extends HudPanelLayout {
  totalH() {
    return n(this.p.height, this.ctx.height || 64);
  }

  h() {
    return n(this.p.mainHeight, this.totalH());
  }

  statisticsHeader() {
    const bs = this.bs();
    const fs = this.fs();
    const w = this.w();
    const headerH = this.base.headerH * bs;
    const titleH = n(this.p.headerTextHeight, 8);
    const y = (headerH - titleH) * 0.5 + 0.25 * bs;

    return [
      ui.svg({
        key: "header:icon",
        asset: c(this.p.headerIconAsset, "chart-spline"),
        tint: c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF")),
        class: abs(5 * bs, (headerH - 9 * bs) * 0.5, 9 * bs, 9 * bs),
      }),
      ui.text({
        key: "header:title",
        text: c(this.p.title, "Statistics"),
        color: color(this.pal, "titleText", "#FFFFFFFF"),
        class: cls(abs(22 * bs, y, Math.max(20 * bs, w - 28 * bs), titleH + 4 * bs), font("Inter", fs, "bold"), `text-${color(this.pal, "titleText", "#FFFFFFFF")}`),
      }),
    ];
  }

  statRow(row, index, y) {
    const bs = this.bs();
    const fs = this.fs();
    const rowH = n(this.p.rowTextHeight, 8);
    const key = c(prop(row, "key", `row_${index}`), `row_${index}`);
    const label = c(prop(row, "label", ""), "");
    const value = c(prop(row, "value", ""), "");
    const valueColor = c(prop(row, "valueColor", color(this.pal, "text", "#FFFFFFFF")), color(this.pal, "text", "#FFFFFFFF"));
    const contentRight = this.p.showPlayTime === true ? this.w() - 62 * bs : this.w() - 6 * bs;
    const valueW = Math.max(24 * bs, Math.min(62 * bs, value.length * 6.4 * fs + 4 * bs));
    const valueX = contentRight - valueW;
    return [
      ui.shape({
        key: `stat:${key}:dot`,
        shape: "circle",
        class: abs(7 * bs, y + 3.2 * bs, 3 * bs, 3 * bs),
        radius: 1.5 * bs,
        fill: color(this.pal, "counter", "#FFFFFFFF"),
      }),
      ui.text({
        key: `stat:${key}:label`,
        text: label,
        color: color(this.pal, "muted", "#FFA5A5A5"),
        class: cls(abs(13 * bs, y, Math.max(10 * bs, valueX - 16 * bs), rowH + 4 * bs), font("OnestMedium", fs * 0.92), `text-${color(this.pal, "muted", "#FFA5A5A5")}`),
      }),
      ui.text({
        key: `stat:${key}:value`,
        text: value,
        color: valueColor,
        class: cls(abs(valueX, y, valueW, rowH + 4 * bs), font("OnestMedium", fs * 0.94), `text-${valueColor}`, "text-align-right"),
      }),
    ];
  }

  statisticsBody() {
    const bs = this.bs();
    const fs = this.fs();
    const w = this.w();
    const nodes = [];
    const rows = arr(this.p.rows);
    for (let i = 0; i < rows.length; i++) {
      nodes.push(...this.statRow(rows[i], i, (23 + 12 * i) * bs));
    }

    if (this.p.showPlayTime !== true) return nodes;

    const ringCx = w - 31 * bs;
    const ringCy = 39.5 * bs;
    const ringR = 16 * bs;
    const ringBoxX = ringCx - ringR - 2 * bs;
    const ringBoxY = ringCy - ringR - 2 * bs;
    const ringBox = (ringR + 2 * bs) * 2;
    nodes.push(ui.shape({
      key: "playtime:ring",
      shape: "ring",
      class: abs(ringBoxX, ringBoxY, ringBox, ringBox),
      cx: ringBox * 0.5,
      cy: ringBox * 0.5,
      radius: ringR,
      thickness: Math.max(1.1 * bs, 1.1),
      stroke: alpha(color(this.pal, "muted", "#FFA5A5A5"), 0.28),
    }));
    nodes.push(ui.shape({
      key: "playtime:arc",
      shape: "arc-gradient",
      class: abs(ringBoxX, ringBoxY, ringBox, ringBox),
      cx: ringBox * 0.5,
      cy: ringBox * 0.5,
      radius: ringR,
      thickness: Math.max(1.55 * bs, 1.25),
      startAngle: -90,
      endAngle: n(this.p.arcEndAngle, -90),
      startColor: c(this.p.accentStartColor, color(this.pal, "counter", "#FFFFFFFF")),
      endColor: c(this.p.accentEndColor, color(this.pal, "text", "#FFFFFFFF")),
      angle: 0,
      softness: 0.65,
    }));
    nodes.push(ui.text({
      key: "playtime:value",
      text: c(this.p.playTime, "00:00"),
      color: color(this.pal, "text", "#FFFFFFFF"),
      class: cls(abs(ringCx - 17 * bs, ringCy - 4.8 * bs, 34 * bs, 9 * bs), font("OnestMedium", fs * 0.80), `text-${color(this.pal, "text", "#FFFFFFFF")}`, "text-align-center"),
    }));
    nodes.push(ui.text({
      key: "playtime:label",
      text: "Play Time",
      color: color(this.pal, "muted", "#FFA5A5A5"),
      class: cls(abs(ringCx - 18 * bs, ringCy + 5 * bs, 36 * bs, 8 * bs), font("OnestMedium", fs * 0.66), `text-${color(this.pal, "muted", "#FFA5A5A5")}`, "text-align-center"),
    }));
    return nodes;
  }

  graphCard(y, h) {
    const bs = this.bs();
    const w = this.w();
    const r = this.base.radius * bs;
    const primary = color(this.pal, "bodyLeft", "#E522242C");
    const secondary = color(this.pal, "bodyRight", "#E50E1015");
    const outline = color(this.pal, "outline", "#665A5A5A");
    const strokeAlpha = this.p.strokeEnabled === true ? Math.max(0, Math.min(1, n(this.p.strokeAlpha, 0.4))) : 0;
    const stroke = withAlpha(outline, strokeAlpha);
    const nodes = [];
    if (this.p.blur === true) {
      nodes.push(this.blurShape("graph:blur", 0, y, w, h, [r, r, r, r]));
    }
    nodes.push(this.panelShape(
      "graph:plate",
      0,
      y,
      w,
      h,
      [r, r, r, r],
      [primary, primary, secondary, secondary],
      stroke,
      Math.max(0.35 * n(this.p.drawScale, 1), 0.42 * bs),
      {
        strokeStartColor: this.p.strokeGradient === true ? withAlpha(c(this.p.strokeStartColor, outline), strokeAlpha) : stroke,
        strokeEndColor: this.p.strokeGradient === true ? withAlpha(c(this.p.strokeEndColor, outline), strokeAlpha) : stroke,
        strokeAngle: 90,
      }
    ));
    nodes.push(this.glintShape("graph:top-glint", 1, y + 1, Math.max(0, w - 2), Math.max(1, h * 0.18), [Math.max(0, r - 1), Math.max(0, r - 1), 0, 0], 1));
    return nodes;
  }

  graphNodes() {
    if (this.p.showGraph !== true) return [];
    const bs = this.bs();
    const fs = this.fs();
    const w = this.w();
    const graphH = n(this.p.graphHeight, 54 * bs);
    const separate = this.p.separateGraph === true;
    const graphY = separate ? this.h() + n(this.p.graphGap, 5 * bs) : this.h() - graphH;
    const nodes = [];
    if (separate) {
      nodes.push(...this.graphCard(graphY, graphH));
    } else {
      nodes.push(ui.connector({
        key: "graph:separator",
        connector: "line",
        class: abs(6 * bs, graphY + 0.5 * bs, Math.max(1, w - 12 * bs), 1),
        x1: 0,
        y1: 0.5,
        x2: Math.max(1, w - 12 * bs),
        y2: 0.5,
        stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.45),
        strokeWidth: Math.max(0.5, 0.55 * bs),
      }));
    }

    const headerY = graphY + 4 * bs;
    nodes.push(ui.text({
      key: "graph:title",
      text: "Speed",
      color: color(this.pal, "titleText", "#FFFFFFFF"),
      class: cls(abs(6 * bs, headerY, 36 * bs, 9 * bs), font("Inter", fs * 0.86, "bold"), `text-${color(this.pal, "titleText", "#FFFFFFFF")}`),
    }));
    nodes.push(ui.text({
      key: "graph:average",
      text: `Average: ${c(this.p.averageSpeed, "0.00 BPS")}`,
      color: color(this.pal, "muted", "#FFA5A5A5"),
      class: cls(abs(w - 78 * bs, headerY + 0.2 * bs, 72 * bs, 9 * bs), font("OnestMedium", fs * 0.73), `text-${color(this.pal, "muted", "#FFA5A5A5")}`, "text-align-right"),
    }));

    const plotX = 5 * bs;
    const plotY = graphY + 16 * bs;
    const plotW = w - 10 * bs;
    const plotH = Math.max(8 * bs, graphH - 21 * bs);
    nodes.push(ui.shape({
      key: "graph:background",
      shape: "rounded",
      class: abs(plotX, plotY, plotW, plotH),
      radius: 3 * bs,
      fill: alpha("#FF000000", 0.20),
      stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.20),
      strokeWidth: Math.max(0.4, 0.42 * bs),
    }));
    for (let i = 1; i <= 2; i++) {
      const gy = plotY + (plotH * i) / 3;
      nodes.push(ui.connector({
        key: `graph:grid:${i}`,
        connector: "line",
        class: abs(plotX + 2 * bs, gy, Math.max(1, plotW - 4 * bs), 1),
        x1: 0,
        y1: 0.5,
        x2: Math.max(1, plotW - 4 * bs),
        y2: 0.5,
        stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.13),
        strokeWidth: Math.max(0.35, 0.4 * bs),
      }));
    }
    const graphClass = abs(plotX + 2 * bs, plotY + 2 * bs, Math.max(1, plotW - 4 * bs), Math.max(1, plotH - 4 * bs));
    const accentStart = c(this.p.accentStartColor, color(this.pal, "counter", "#FFFFFFFF"));
    const accentEnd = c(this.p.accentEndColor, color(this.pal, "text", "#FFFFFFFF"));
    const graphPoints = arr(this.p.graphPoints);
    nodes.push(ui.connector({
      key: "graph:area",
      connector: "spline-area",
      class: graphClass,
      points: graphPoints,
      fillStartColor: alpha(accentStart, 0.34),
      fillEndColor: alpha(accentEnd, 0.28),
      fillBottomStartColor: alpha(accentStart, 0.015),
      fillBottomEndColor: alpha(accentEnd, 0.01),
    }));
    nodes.push(ui.connector({
      key: "graph:glow",
      connector: "spline",
      class: graphClass,
      points: graphPoints,
      stroke: alpha(accentStart, 0.16),
      strokeStartColor: alpha(accentStart, 0.18),
      strokeEndColor: alpha(accentEnd, 0.14),
      strokeWidth: Math.max(3.1, 3.5 * bs),
      closed: false,
    }));
    nodes.push(ui.connector({
      key: "graph:spline",
      connector: "spline",
      class: graphClass,
      points: graphPoints,
      stroke: accentStart,
      strokeStartColor: accentStart,
      strokeEndColor: accentEnd,
      strokeWidth: Math.max(1.25, 1.35 * bs),
      closed: false,
    }));
    return nodes;
  }

  render() {
    const w = this.w();
    const totalH = this.totalH();
    return ui.root({
      key: "panel:statistics",
      class: cls(
        `w-${s(w)}`,
        `h-${s(totalH)}`,
        `rounded-${s(this.base.radius * this.bs())}`,
        this.p.shadowControlled === true ? "" : "shadow-panel"
      ),
      children: [
        ...this.chrome(),
        ...this.statisticsHeader(),
        ...this.statisticsBody(),
        ...this.graphNodes(),
      ],
    });
  }
}

export function render(ctx) {
  return new StatisticsPanelLayout(ctx).render();
}
