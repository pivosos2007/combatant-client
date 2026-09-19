/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import { HudPanelLayout } from "./panel_base.js";

class TriangulatorLayout extends HudPanelLayout {
  constructor(ctx) {
    super(ctx);
    this.base.bodyY = 16.5;
    this.base.headerH = 15.5;
    this.base.rowStep = 11.0;
    this.base.bodyInsetY = 6.5;
  }

  counterLabel() {
    return "Eyes:";
  }

  header() {
    const fs = this.fs();
    const w = this.w();
    const headerH = this.base.headerH;
    const rowTextH = n(this.p.rowTextHeight, 8);
    const titleH = n(this.p.headerTextHeight, 8);
    const count = String(Math.max(0, Math.round(n(this.p.activeCount, 0))));
    const clearVisible = this.p.clearVisible === true;
    const copyVisible = this.p.copyVisible === true;
    const buttonSize = 9.0;
    const buttonGap = 3.0;
    let buttonsRight = 8.0;
    let clearX = 0;
    let copyX = 0;
    if (clearVisible) {
      clearX = w - buttonsRight - buttonSize;
      buttonsRight += buttonSize + buttonGap;
    }
    if (copyVisible) {
      copyX = w - buttonsRight - buttonSize;
      buttonsRight += buttonSize + buttonGap;
    }
    const measuredCountValueW = n(this.p.countValueWidth, 0);
    const measuredCountLabelW = n(this.p.countLabelWidth, 0);
    const countValueW = Math.max(8, measuredCountValueW > 0 ? measuredCountValueW + 1.5 : count.length * 6.5 * fs + 5.5);
    const countLabel = this.counterLabel();
    const countLabelW = Math.max(28 * fs, measuredCountLabelW > 0 ? measuredCountLabelW + 1.5 : countLabel.length * 5.9 * fs + 1.5);
    const countGap = Math.max(3.0, 3.0 * fs);
    const countValueX = w - countValueW - 4.0 - (buttonsRight - 8.0);
    const countLabelX = countValueX - countLabelW - countGap;
    const countY = (headerH - rowTextH) * 0.5 + 0.8;
    const titleX = 22.0;
    const titleW = Math.max(0, countLabelX - titleX - 4.0);
    const titleY = (headerH - titleH) * 0.5 + 0.25;
    const iconSize = 8.2;
    const iconY = (headerH - iconSize) * 0.5;
    const iconColor = c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"));
    const nodes = [
      ui.text({
        key: "header:count-label",
        text: countLabel,
        color: color(this.pal, "text", "#FFFFFFFF"),
        class: cls(abs(countLabelX, countY, countLabelW, rowTextH + 4), font("OnestMedium", this.fontSize(0.92)), `text-${color(this.pal, "text", "#FFFFFFFF")}`),
      }),
      ui.text({
        key: "header:count-value",
        text: count,
        color: color(this.pal, "counter", "#FFFFFFFF"),
        class: cls(abs(countValueX, countY, countValueW, rowTextH + 4), font("OnestMedium", this.fontSize(0.92)), `text-${color(this.pal, "counter", "#FFFFFFFF")}`),
      }),
      ui.image({
        key: "header:icon",
        assetType: "texture",
        asset: c(this.p.headerIcon, "combatant:textures/hud/elements/coords.png"),
        tint: iconColor,
        gradientEnabled: this.p.headerIconGradient === true,
        gradientStartColor: c(this.p.headerIconGradientStart, iconColor),
        gradientEndColor: c(this.p.headerIconGradientEnd, iconColor),
        gradientAngle: n(this.p.headerIconGradientAngle, 45),
        fit: "contain",
        mask: true,
        class: abs(4.5, iconY, iconSize, iconSize, `rounded-${s(Math.min(1.15, iconSize * 0.18))}`),
      }),
      ui.text({
        key: "header:title",
        text: c(this.p.title, "Triangulator"),
        color: color(this.pal, "titleText", "#FFFFFFFF"),
        class: cls(abs(titleX, titleY, titleW, titleH + 4), font("Inter", this.fontSize(), "bold"), `text-${color(this.pal, "titleText", "#FFFFFFFF")}`),
      }),
    ];
    const buttonY = (headerH - buttonSize) * 0.5;
    if (copyVisible) nodes.push(...this.headerButton("copy", copyX, buttonY, buttonSize, color(this.pal, "counter", "#FFFFFFFF"), c(this.p.copyIcon, "clipboard"), c(this.p.copyIconColor, color(this.pal, "counter", "#FFFFFFFF"))));
    if (clearVisible) nodes.push(...this.headerButton("clear", clearX, buttonY, buttonSize, color(this.pal, "danger", "#FFFF6A6A"), c(this.p.clearIcon, "x"), c(this.p.clearIconColor, color(this.pal, "danger", "#FFFF6A6A"))));
    return nodes;
  }

  headerButton(key, x, y, size, baseColor, iconAsset, iconColor) {
    const fill = alpha(baseColor, 0.18);
    const stroke = alpha(baseColor, 0.42);
    const icon = Math.max(4.2, size * 0.52);
    return [
      ui.shape({
        key: `${key}:bg`,
        shape: "rounded-gradient",
        class: abs(x, y, size, size),
        radius: Math.min(2.2, size * 0.28),
        softness: 0.9,
        startColor: fill,
        endColor: alpha(baseColor, 0.08),
        angle: 90,
        stroke,
        strokeWidth: 0.45,
        strokeStartColor: alpha("#FFFFFFFF", 0.08),
        strokeEndColor: stroke,
        strokeAngle: 90,
      }),
      ui.svg({
        key: `${key}:icon`,
        asset: iconAsset,
        tint: iconColor,
        class: abs(x + (size - icon) * 0.5, y + (size - icon) * 0.5, icon, icon),
      }),
    ];
  }

  renderSummary() {
    const fs = this.fs();
    const w = this.w();
    const rowTextH = n(this.p.rowTextHeight, 8);
    const y = this.base.bodyY + this.base.bodyInsetY;
    const statusText = c(this.p.statusText, "");
    const statusColor = c(this.p.statusColor, color(this.pal, "muted", "#FFFFFFFF"));
    const confidence = c(this.p.confidenceText, "");
    const confidenceW = Math.max(18, confidence.length * 7.4 * fs);
    const nodes = [
      ui.text({
        key: "summary:status",
        text: statusText,
        color: statusColor,
        class: cls(abs(8.0, y, Math.max(30, statusText.length * 7.4 * fs), rowTextH + 4), font("OnestMedium", this.fontSize()), `text-${statusColor}`),
      }),
      ui.text({
        key: "summary:primary",
        text: c(this.p.primaryLine, ""),
        color: color(this.pal, "text", "#FFFFFFFF"),
        class: cls(abs(8.0, y + 11.0, Math.max(40, w - 16), rowTextH + 4), font("OnestMedium", this.fontSize()), `text-${color(this.pal, "text", "#FFFFFFFF")}`),
      }),
      ui.text({
        key: "summary:secondary",
        text: c(this.p.secondaryLine, ""),
        color: color(this.pal, "muted", "#FFFFFFFF"),
        class: cls(abs(8.0, y + 20.5, Math.max(40, w - 16), rowTextH + 4), font("OnestMedium", this.fontSize(0.92)), `text-${color(this.pal, "muted", "#FFFFFFFF")}`),
      }),
    ];
    if (confidence) {
      nodes.push(ui.text({
        key: "summary:confidence",
        text: confidence,
        color: statusColor,
        class: cls(abs(w - confidenceW - 8.0, y, confidenceW, rowTextH + 4), font("OnestMedium", this.fontSize()), `text-${statusColor}`, "text-align-right"),
      }));
    }
    return nodes;
  }

  row(row, index, cursorY) {
    const fs = this.fs();
    const rowTextH = n(this.p.rowTextHeight, 8);
    const w = this.w();
    const key = c(prop(row, "key", `eye:${index}`), `eye:${index}`);
    const centerY = cursorY + 1.95;
    const marker = c(prop(row, "markerColor", color(this.pal, "counter", "#FFFFFFFF")), color(this.pal, "counter", "#FFFFFFFF"));
    const divider = c(prop(row, "dividerColor", color(this.pal, "divider", "#66FFFFFF")), color(this.pal, "divider", "#66FFFFFF"));
    const label = c(prop(row, "label", ""), "");
    const value = c(prop(row, "value", ""), "");
    const labelColor = c(prop(row, "labelColor", color(this.pal, "text", "#FFFFFFFF")), color(this.pal, "text", "#FFFFFFFF"));
    const valueColor = c(prop(row, "valueColor", marker), marker);
    const valueW = Math.max(24, value.length * 7.4 * fs);
    const textY = centerY - rowTextH * 0.5;
    return [
      ui.shape({
        key: `row:${key}:marker`,
        shape: "rounded",
        class: abs(4.0, centerY - 3.75, 7.5, 7.5),
        radius: Math.min(1.4, 1.8),
        softness: 0.85,
        fill: marker,
      }),
      ui.shape({
        key: `row:${key}:divider`,
        shape: "rounded",
        class: abs(14.5, centerY - 3.25, 0.5, 6.5),
        radius: 0.5,
        fill: divider,
      }),
      ui.text({
        key: `row:${key}:label`,
        text: label,
        color: labelColor,
        class: cls(abs(19.0, textY, Math.max(28, label.length * 7.6 * fs), rowTextH + 4), font("OnestMedium", this.fontSize()), `text-${labelColor}`),
      }),
      ui.text({
        key: `row:${key}:value`,
        text: value,
        color: valueColor,
        class: cls(abs(w - valueW - 8.0, textY, valueW, rowTextH + 4), font("OnestMedium", this.fontSize()), `text-${valueColor}`, "text-align-right"),
      }),
    ];
  }

  renderContent() {
    const w = this.w();
    const rows = arr(this.p.rows);
    const nodes = [...this.renderSummary()];
    if (rows.length === 0) {
      return [ui.stack({ key: "content", class: abs(0, 0, this.w(), this.h()), children: nodes })];
    }
    const dividerY = this.base.bodyY + this.base.bodyInsetY + 34.5;
    nodes.push(ui.shape({
      key: "summary:divider",
      shape: "rounded",
      class: abs(8.0, dividerY, Math.max(0, w - 16.0), 0.55),
      radius: 0.5,
      fill: color(this.pal, "divider", "#66FFFFFF"),
    }));
    let cursorY = dividerY + 5.0;
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      nodes.push(...this.row(row, i, cursorY));
      cursorY += this.base.rowStep * Math.max(0, n(prop(row, "alpha", 1), 1));
    }
    return [ui.stack({ key: "content", class: abs(0, 0, this.w(), this.h()), children: nodes })];
  }
}

export function render(ctx) {
  return new TriangulatorLayout(ctx).render();
}
