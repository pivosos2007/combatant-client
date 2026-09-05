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
    const horizontal = this.horizontalLayout();
    const x = horizontal.iconCenterX - size * 0.5;
    const y = rowCenterY - size * 0.5;
    const tint = c(prop(row, "iconTint", "#FFFFFFFF"), "#FFFFFFFF");
    const radius = Math.min(1.15 * bs, size * 0.24);
    if (kind === "item") {
      return ui.item({
        key: `row:${prop(row, "key", "")}:item`,
        item: icon,
        overlay: false,
        alpha: Math.max(0, Math.min(1, n(prop(row, "alpha", 1), 1))),
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
    const x = this.horizontalLayout().titleX;
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

  timePillParts(row, rowCenterY, key, rightText, rightColor) {
    const bs = this.bs();
    const fs = this.fs();
    const rowTextH = n(this.p.rowTextHeight, 8);
    const w = this.w();
    const rightPad = n(prop(this.v, "rowRightPad", 8), 8) * bs;
    const pillH = n(prop(this.v, "timePillHeight", 8.4), 8.4) * bs;
    const ringBox = n(prop(this.v, "timePillRingBox", 6.5), 6.5) * bs;
    // Java measures the real glyph advances with the same TextRenderer and reserves
    // the widest width for the lifetime of the row. Do not infer width from string length.
    const textW = Math.max(0, n(prop(row, "rightTextWidth", 0), 0));
    const leftPad = n(prop(this.v, "timePillLeftPad", 2.0), 2.0) * bs;
    const ringTextGap = n(prop(this.v, "timePillRingTextGap", 1.35), 1.35) * bs;
    const rightInnerPad = n(prop(this.v, "timePillRightPad", 2.3), 2.3) * bs;
    const pillW = leftPad + ringBox + ringTextGap + textW + rightInnerPad;
    const pillX = w - pillW - rightPad;
    const pillY = rowCenterY - pillH * 0.5;
    const ringX = pillX + leftPad;
    const ringY = rowCenterY - ringBox * 0.5;
    const ringRadius = Math.max(1.0 * bs, ringBox * 0.5 - 0.72 * bs);
    const ringThickness = Math.max(0.62, 0.72 * bs);
    const textX = ringX + ringBox + ringTextGap;
    const textBoxW = Math.max(1, textW);

    const fillStart = c(prop(row, "timeFillStart", "#38282828"), "#38282828");
    const fillEnd = c(prop(row, "timeFillEnd", fillStart), fillStart);
    const strokeStart = c(prop(row, "timeStrokeStart", "#66808080"), "#66808080");
    const strokeEnd = c(prop(row, "timeStrokeEnd", strokeStart), strokeStart);
    const baseArc = c(prop(row, "timeArcBase", "#4AFFFFFF"), "#4AFFFFFF");
    const arcStartColor = c(prop(row, "timeArcStartColor", rightColor), rightColor);
    const arcEndColor = c(prop(row, "timeArcEndColor", arcStartColor), arcStartColor);
    const arcStart = n(prop(row, "timeArcStart", 0), 0);
    const arcEnd = n(prop(row, "timeArcEnd", 360), 360);

    const decorations = [
      ui.shape({
        key: `row:${key}:time-pill`,
        shape: "rounded-gradient",
        class: abs(pillX, pillY, pillW, pillH),
        radius: pillH * 0.5,
        startColor: fillStart,
        endColor: fillEnd,
        angle: 12,
        stroke: strokeStart,
        strokeWidth: Math.max(0.45, 0.50 * bs),
        strokeStartColor: strokeStart,
        strokeEndColor: strokeEnd,
        strokeAngle: 22,
      }),
      ui.shape({
        key: `row:${key}:time-arc-base`,
        shape: "ring",
        class: abs(ringX, ringY, ringBox, ringBox),
        cx: ringBox * 0.5,
        cy: ringBox * 0.5,
        radius: ringRadius,
        thickness: ringThickness,
        stroke: baseArc,
      }),
      ui.shape({
        key: `row:${key}:time-arc`,
        shape: "arc-gradient",
        class: abs(ringX, ringY, ringBox, ringBox),
        cx: ringBox * 0.5,
        cy: ringBox * 0.5,
        radius: ringRadius,
        thickness: ringThickness,
        startAngle: arcStart,
        endAngle: arcEnd,
        stroke: arcStartColor,
        startColor: arcStartColor,
        endColor: arcEndColor,
        angle: 45,
      }),
    ];

    const text = [ui.text({
      key: `row:${key}:time-text`,
      text: rightText,
      color: rightColor,
      class: cls(abs(textX, rowCenterY - rowTextH * 0.5, textBoxW, rowTextH + 4 * bs), font("OnestMedium", fs), `text-${rightColor}`, "text-align-center"),
    })];
    return { decorations, text };
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
    const rightMode = c(prop(row, "rightMode", "text"), "text");
    // Keep the legacy text-mode contract bit-for-bit: only timed pills consume
    // Java-measured glyph width. Generic/Admins/list values retain the old
    // right-side box sizing so adding timed pills cannot perturb unrelated HUDs.
    const rightW = rightMode === "time-pill"
      ? Math.max(1, n(prop(row, "rightTextWidth", 0), 0))
      : Math.max(14 * bs, rightText.length * 7.5 * fs);
    const rightX = w - rightW - n(prop(this.v, "rowRightPad", 8), 8) * bs;
    const decorations = [];
    const icons = [];
    const text = [];
    const icon = this.iconNode(row, rowCenterY);
    if (icon) icons.push(icon);
    const horizontal = this.horizontalLayout();
    const dividerH = n(prop(this.v, "rowDividerH", 6), 6) * bs;
    decorations.push(ui.shape({
      key: `row:${key}:divider`,
      shape: "rounded",
      class: abs(horizontal.dividerX, rowCenterY - dividerH * 0.5, horizontal.dividerW, dividerH),
      radius: Math.min(0.5 * bs, horizontal.dividerW * 0.5),
      fill: c(prop(row, "dividerColor", color(this.pal, "divider", "#66FFFFFF")), color(this.pal, "divider", "#66FFFFFF")),
    }));
    text.push(...this.nameParts(row, rowCenterY));
    if (rightMode === "time-pill") {
      const pill = this.timePillParts(row, rowCenterY, key, rightText, rightColor);
      decorations.push(...pill.decorations);
      text.push(...pill.text);
    } else {
      text.push(ui.text({
        key: `row:${key}:right`,
        text: rightText,
        color: rightColor,
        class: cls(abs(rightX, rowCenterY - rowTextH * 0.5, rightW, rowTextH + 4 * bs), font("OnestMedium", fs), `text-${rightColor}`, "text-align-right"),
      }));
    }
    return { decorations, icons, text };
  }

  renderContent() {
    const bs = this.bs();
    const rows = arr(this.p.rows);
    const bodyY = this.base.bodyY * bs;
    const bodyH = Math.max(0, this.h() - bodyY);
    const rowStep = this.base.rowStep * bs;
    const rowCenterOffset = n(prop(this.v, "rowCenterOffset", 2), 2) * bs;
    const rowStacks = [];
    let cursorY = this.base.bodyInsetY * bs;

    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const key = c(prop(row, "key", `row:${i}`), `row:${i}`);
      const layoutProgress = Math.max(0, Math.min(1, n(
        prop(row, "layoutProgress", prop(row, "alpha", 1)),
        1
      )));
      const rowCenterY = cursorY + rowCenterOffset;
      const clipH = Math.max(0, rowStep * layoutProgress);
      const clipY = rowCenterY - clipH * 0.5;
      const parts = this.rowParts(row, i, clipH * 0.5 - rowCenterOffset);

      if (clipH > 0.01) {
        rowStacks.push(ui.stack({
          key: `row:${key}:clip`,
          class: abs(0, clipY, this.w(), clipH, "clip overflow-hidden"),
          children: [...parts.decorations, ...parts.icons, ...parts.text],
        }));
      }

      cursorY += rowStep * layoutProgress;
    }

    return [
      // Body-level scissor prevents residual fragments from escaping the shrinking panel;
      // each row also owns a centered shrinking scissor so opaque item icons disappear
      // continuously instead of being guillotined by the panel bottom edge.
      ui.stack({
        key: "rows:clip",
        class: abs(0, bodyY, this.w(), bodyH, "clip overflow-hidden"),
        children: rowStacks,
      }),
    ];
  }

}

export function render(ctx) {
  return new HudListPanelLayout(ctx).render();
}
