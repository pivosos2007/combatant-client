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
    const w = this.w();
    const headerH = this.base.headerH;
    const titleH = n(this.p.headerTextHeight, 8);
    const y = (headerH - titleH) * 0.5 + 0.25;

    return [
      ui.svg({
        key: "header:icon",
        asset: c(this.p.headerIconAsset, "chart-spline"),
        tint: c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF")),
        gradientEnabled: this.p.headerIconGradient === true,
        gradientStartColor: c(this.p.headerIconGradientStart, c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"))),
        gradientEndColor: c(this.p.headerIconGradientEnd, c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"))),
        gradientAngle: n(this.p.headerIconGradientAngle, 45),
        style: ui.absolute(5, (headerH - 9) * 0.5, 9, 9),
      }),
      ui.text({
        key: "header:title",
        text: c(this.p.title, "Statistics"),
        color: color(this.pal, "titleText", "#FFFFFFFF"),
        style: ui.absolute(22, y, Math.max(20, w - 28), titleH + 4, {
          fontFamily: "Inter", fontWeight: "bold", fontSize: this.fontSize(),
        }),
      }),
    ];
  }

  statRow(row, index, y) {
    const rowH = n(this.p.rowTextHeight, 8);
    const key = c(prop(row, "key", `row_${index}`), `row_${index}`);
    const label = c(prop(row, "label", ""), "");
    const value = c(prop(row, "value", ""), "");
    const valueColor = c(prop(row, "valueColor", color(this.pal, "text", "#FFFFFFFF")), color(this.pal, "text", "#FFFFFFFF"));
    const progress = Math.max(0, Math.min(1, n(prop(row, "animation", 1), 1)));
    const slideX = (1 - progress) * 5;
    const rowLeft = 7;
    const contentRight = this.p.showPlayTime === true ? this.w() - 62 : this.w() - 6;
    const rowWidth = Math.max(0, contentRight - rowLeft);

    return [ui.row({
      key: `stat:${key}`,
      style: ui.absolute(rowLeft, y, rowWidth, rowH + 4, {
        alignItems: "center",
        gap: 3,
        overflow: "hidden",
      }),
      children: [
        ui.shape({
          key: `stat:${key}:dot`,
          shape: "circle",
          fill: alpha(color(this.pal, "counter", "#FFFFFFFF"), progress),
          style: {
            width: 3,
            height: 3,
            marginLeft: slideX,
            flexShrink: 0,
          },
        }),
        ui.text({
          key: `stat:${key}:label`,
          text: label,
          color: alpha(color(this.pal, "muted", "#FFA5A5A5"), progress),
          style: {
            fontFamily: "OnestMedium",
            fontSize: this.fontSize(0.92),
            flexGrow: 1,
            flexShrink: 1,
            minWidth: 0,
            overflow: "hidden",
          },
        }),
        ui.text({
          key: `stat:${key}:value`,
          text: value,
          color: alpha(valueColor, progress),
          style: {
            fontFamily: "OnestMedium",
            fontSize: this.fontSize(0.94),
            flexShrink: 0,
            marginRight: slideX,
            textAlign: "right",
          },
        }),
      ],
    })];
  }

  statisticsBody() {
    const w = this.w();
    const nodes = [];
    const rows = arr(this.p.rows);
    let rowY = 23;
    for (let i = 0; i < rows.length; i++) {
      const progress = Math.max(0, Math.min(1, n(prop(rows[i], "animation", 1), 1)));
      nodes.push(...this.statRow(rows[i], i, rowY));
      rowY += 12 * progress;
    }

    if (this.p.showPlayTime !== true) return nodes;

    const ringCx = w - 31;
    const ringCy = 39.5;
    const ringR = 16;
    const ringBoxX = ringCx - ringR - 2;
    const ringBoxY = ringCy - ringR - 2;
    const ringBox = (ringR + 2) * 2;
    nodes.push(ui.shape({
      key: "playtime:ring",
      shape: "ring",
      style: ui.absolute(ringBoxX, ringBoxY, ringBox, ringBox),
      cx: ringBox * 0.5,
      cy: ringBox * 0.5,
      radius: ringR,
      thickness: 1.1,
      stroke: alpha(color(this.pal, "muted", "#FFA5A5A5"), 0.28),
    }));
    nodes.push(ui.shape({
      key: "playtime:arc",
      shape: "arc-hash",
      style: ui.absolute(ringBoxX, ringBoxY, ringBox, ringBox),
      cx: ringBox * 0.5,
      cy: ringBox * 0.5,
      radius: ringR,
      thickness: 1.55,
      startAngle: -90,
      endAngle: n(this.p.arcEndAngle, -90),
      hashTime: n(this.p.arcHashTime, 0),
      startColor: c(this.p.accentStartColor, color(this.pal, "counter", "#FFFFFFFF")),
      endColor: c(this.p.accentEndColor, color(this.pal, "text", "#FFFFFFFF")),
      angle: 0,
      softness: 0.65,
    }));
    nodes.push(ui.text({
      key: "playtime:value",
      text: c(this.p.playTime, "00:00"),
      color: color(this.pal, "text", "#FFFFFFFF"),
      style: ui.absolute(ringCx - 17, ringCy - 4.8, 34, 9, { fontFamily: "OnestMedium", fontSize: this.fontSize(0.80), textAlign: "center" }),
    }));
    nodes.push(ui.text({
      key: "playtime:label",
      text: "Play Time",
      color: color(this.pal, "muted", "#FFA5A5A5"),
      style: ui.absolute(ringCx - 18, ringCy + 5, 36, 8, { fontFamily: "OnestMedium", fontSize: this.fontSize(0.66), textAlign: "center" }),
    }));
    return nodes;
  }

  graphCard(y, h) {
    const w = this.w();
    const r = this.base.radius;
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
      0.42,
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
    const w = this.w();
    const graphH = n(this.p.graphHeight, 54);
    const separate = this.p.separateGraph === true;
    const graphY = separate ? this.h() + n(this.p.graphGap, 5) : this.h() - graphH;
    const nodes = [];
    if (separate) {
      nodes.push(...this.graphCard(graphY, graphH));
    } else {
      nodes.push(ui.connector({
        key: "graph:separator",
        connector: "line",
        style: ui.absolute(6, graphY + 0.5, Math.max(1, w - 12), 1),
        x1: 0,
        y1: 0.5,
        x2: Math.max(1, w - 12),
        y2: 0.5,
        stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.45),
        strokeWidth: 0.55,
      }));
    }

    const headerY = graphY + 4;
    nodes.push(ui.text({
      key: "graph:title",
      text: "Speed",
      color: color(this.pal, "titleText", "#FFFFFFFF"),
      style: ui.absolute(6, headerY, 36, 9, { fontFamily: "Inter", fontWeight: "bold", fontSize: this.fontSize(0.86) }),
    }));
    nodes.push(ui.text({
      key: "graph:average",
      text: `Average: ${c(this.p.averageSpeed, "0.00 BPS")}`,
      color: color(this.pal, "muted", "#FFA5A5A5"),
      style: ui.absolute(w - 78, headerY + 0.2, 72, 9, { fontFamily: "OnestMedium", fontSize: this.fontSize(0.73), textAlign: "right" }),
    }));

    const plotX = 5;
    const plotY = graphY + 16;
    const plotW = w - 10;
    const plotH = Math.max(8, graphH - 21);
    nodes.push(ui.shape({
      key: "graph:background",
      shape: "rounded",
      style: ui.absolute(plotX, plotY, plotW, plotH),
      radius: 3,
      fill: alpha("#FF000000", 0.20),
      stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.20),
      strokeWidth: 0.42,
    }));
    const graphStyle = ui.absolute(plotX + 2, plotY + 2, Math.max(1, plotW - 4), Math.max(1, plotH - 4));
    const accentStart = c(this.p.accentStartColor, color(this.pal, "counter", "#FFFFFFFF"));
    const accentEnd = c(this.p.accentEndColor, color(this.pal, "text", "#FFFFFFFF"));
    const graphValues = arr(this.p.graphValues);
    const domainMax = Math.max(0.001, n(this.p.graphDomainMax, 24));
    const plotChildren = [];

    for (let i = 1; i <= 2; i++) {
      const value = (domainMax * i) / 3;
      plotChildren.push(ui.line({
        key: `graph:grid:${i}`,
        x1: 0,
        y1: value,
        x2: 99,
        y2: value,
        stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), 0.13),
        strokeWidth: 0.4,
      }));
    }

    const lineWidth = 1.5;
    const glowWidth = Math.max(3, Math.round(lineWidth * 2.25 * 2) / 2);
    plotChildren.push(ui.area({
      key: "graph:series",
      curve: "spline",
      values: graphValues,
      historySlots: 100,
      baselineValue: 0,
      fillStartColor: alpha(accentStart, 0.34),
      fillEndColor: alpha(accentEnd, 0.28),
      fillBottomStartColor: alpha(accentStart, 0.015),
      fillBottomEndColor: alpha(accentEnd, 0.01),
      glowStartColor: alpha(accentStart, 0.18),
      glowEndColor: alpha(accentEnd, 0.14),
      glowWidth,
      strokeStartColor: accentStart,
      strokeEndColor: accentEnd,
      strokeWidth: lineWidth,
      closed: false,
    }));

    nodes.push(ui.plot({
      key: "graph:plot",
      style: graphStyle,
      xDomain: [0, 99],
      yDomain: [0, domainMax],
      children: plotChildren,
    }));
    return nodes;
  }

  render() {
    const w = this.w();
    const totalH = this.totalH();
    return ui.root({
      key: "panel:statistics",
      class: this.p.shadowControlled === true ? "" : "shadow-panel",
      style: { width: w, height: totalH, borderRadius: this.base.radius },
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
