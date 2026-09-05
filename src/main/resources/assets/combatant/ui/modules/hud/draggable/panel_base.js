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

function withAlpha(hex, amount) {
  const src = c(hex, "#00000000");
  if (!src.startsWith("#") || src.length !== 9) return src;
  const raw = Number.parseInt(src.slice(1), 16);
  const nextA = Math.max(0, Math.min(255, Math.round(255 * amount)));
  return "#" + (((nextA << 24) | (raw & 0x00ffffff)) >>> 0).toString(16).padStart(8, "0").toUpperCase();
}

const alpha01 = ui.color.opacity;

function font(family, scale, type) {
  const suffix = type ? `-${type}` : "";
  return `font-${family}${suffix}-${s(scale)}`;
}

export class HudPanelLayout {
  constructor(ctx) {
    this.ctx = ctx || {};
    this.p = this.ctx.props || {};
    this.pal = prop(this.p, "palette", {});
    this.v = prop(this.p, "variant", {});
    this.base = {
      headerH: 15.5,
      bodyY: n(prop(this.v, "bodyY", 18.5), 18.5),
      radius: 4.0,
      stroke: 0.55,
      softness: 1.0,
      headerDividerH: 6.0,
      // One optical rail for headers and list rows.  The icon occupies a fixed
      // slot, the divider is centered after an equal visual gap, and text starts
      // from the same rail everywhere.  Do not reintroduce per-panel X magic.
      leftInsetX: 4.0,
      headerIconSlotW: 8.0,
      headerIconDividerGap: 3.0,
      headerDividerTitleGap: 2.75,
    };
  }

  bs() {
    return n(this.p.baseScale, 1);
  }

  fs() {
    return n(this.p.fontScale, 1);
  }

  w() {
    return n(this.p.width, this.ctx.width || 100);
  }

  h() {
    return n(this.p.height, this.ctx.height || 30);
  }

  horizontalLayout() {
    const bs = this.bs();
    const left = n(prop(this.v, "leftInsetX", this.base.leftInsetX), this.base.leftInsetX) * bs;
    const iconSlotW = n(prop(this.v, "headerIconSlotW", this.base.headerIconSlotW), this.base.headerIconSlotW) * bs;
    const iconDividerGap = n(
      prop(this.v, "headerIconDividerGap", this.base.headerIconDividerGap),
      this.base.headerIconDividerGap
    ) * bs;
    const dividerTitleGap = n(
      prop(this.v, "headerDividerTitleGap", this.base.headerDividerTitleGap),
      this.base.headerDividerTitleGap
    ) * bs;
    const dividerW = Math.max(0.5 * n(this.p.drawScale, 1), 0.5 * bs);
    const dividerCenterX = left + iconSlotW + iconDividerGap;
    const dividerX = dividerCenterX - dividerW * 0.5;
    const titleX = dividerCenterX + dividerW * 0.5 + dividerTitleGap;
    const contentInsetX = n(prop(this.v, "contentInsetX", this.base.leftInsetX), this.base.leftInsetX) * bs;
    return {
      left,
      iconSlotW,
      iconCenterX: left + iconSlotW * 0.5,
      dividerW,
      dividerCenterX,
      dividerX,
      titleX,
      contentInsetX,
    };
  }

  contentInsetX() {
    return this.horizontalLayout().contentInsetX;
  }

  panelShape(key, x, y, w, h, radii, colors, stroke, strokeW, extra = {}) {
    return ui.shape({
      key,
      shape: "rounded-corners",
      class: abs(x, y, w, h),
      radiusTL: n(radii[0], 0),
      radiusTR: n(radii[1], 0),
      radiusBR: n(radii[2], 0),
      radiusBL: n(radii[3], 0),
      softness: this.base.softness,
      topLeftColor: colors[0],
      topRightColor: colors[1],
      bottomRightColor: colors[2],
      bottomLeftColor: colors[3],
      stroke,
      strokeWidth: strokeW,
      ...extra,
    });
  }

  glintShape(key, x, y, w, h, radii, paintAlpha) {
    return this.panelShape(
      key,
      x,
      y,
      w,
      h,
      radii,
      [
        alpha("#FFFFFFFF", 0.065 * paintAlpha),
        alpha("#FFFFFFFF", 0.065 * paintAlpha),
        alpha("#FFFFFFFF", 0),
        alpha("#FFFFFFFF", 0),
      ],
      "#00000000",
      0
    );
  }

  blurShape(key, x, y, w, h, radii) {
    return ui.shape({
      key,
      shape: "rounded-corners",
      class: abs(x, y, w, h),
      radiusTL: n(radii[0], 0),
      radiusTR: n(radii[1], 0),
      radiusBR: n(radii[2], 0),
      radiusBL: n(radii[3], 0),
      fill: "#00000000",
      blur: true,
      blurAlpha: n(this.p.blurAlpha, 0.55),
      blurBrightness: 1,
    });
  }

  isUnifiedDivider() {
    return c(this.p.layout, "Split Header") === "Unified Divider";
  }

  chrome() {
    return this.isUnifiedDivider() ? this.chromeUnifiedDivider() : this.chromeSplitHeader();
  }

  chromeSplitHeader() {
    const w = this.w();
    const h = this.h();
    const bs = this.bs();
    const headerH = this.base.headerH * bs;
    const bodyY = this.base.bodyY * bs;
    const bodyH = Math.max(0, h - bodyY);
    const r = this.base.radius * bs;
    const strokeW = Math.max(0.35 * n(this.p.drawScale, 1), 0.42 * bs);
    const headerPrimary = color(this.pal, "headerLeft", "#E522242C");
    const headerSecondary = color(this.pal, "headerRight", "#E50E1015");
    const bodyPrimary = color(this.pal, "bodyLeft", "#E522242C");
    const bodySecondary = color(this.pal, "bodyRight", "#E50E1015");
    const outline = color(this.pal, "outline", "#665A5A5A");
    const headerPaintAlpha = Math.max(alpha01(headerPrimary), alpha01(headerSecondary));
    const bodyPaintAlpha = Math.max(alpha01(bodyPrimary), alpha01(bodySecondary));
    const legacyStrokeAlpha = Math.min(0.38, Math.max(alpha01(outline) * 0.48, Math.max(headerPaintAlpha, bodyPaintAlpha) * 0.30));
    const hasStrokeControl = typeof this.p.strokeEnabled === "boolean";
    const strokeEnabled = hasStrokeControl ? this.p.strokeEnabled === true : true;
    const configuredStrokeAlpha = Math.max(0, Math.min(1, n(this.p.strokeAlpha, legacyStrokeAlpha)));
    const strokeAlpha = strokeEnabled ? (hasStrokeControl ? configuredStrokeAlpha : legacyStrokeAlpha) : 0;
    const stroke = hasStrokeControl ? withAlpha(outline, strokeAlpha) : alpha(outline, strokeAlpha);
    const useStrokeGradient = hasStrokeControl && this.p.strokeGradient === true;
    const strokeStart = useStrokeGradient
      ? withAlpha(c(this.p.strokeStartColor, outline), strokeAlpha)
      : (hasStrokeControl ? stroke : alpha("#FFFFFFFF", 0.10 * strokeAlpha));
    const strokeEnd = useStrokeGradient
      ? withAlpha(c(this.p.strokeEndColor, outline), strokeAlpha)
      : stroke;
    const nodes = [];

    if (this.p.blur === true) {
      nodes.push(this.blurShape("blur:header", 0, 0, w, headerH, [r, r, 0, 0]));
      if (bodyH > 0) {
        nodes.push(this.blurShape("blur:body", 0, bodyY, w, bodyH, [0, 0, r, r]));
      }
    }

    nodes.push(this.panelShape(
      "header",
      0,
      0,
      w,
      headerH,
      [r, r, 0, 0],
      [headerPrimary, headerPrimary, headerSecondary, headerSecondary],
      stroke,
      strokeW,
      {
        strokeStartColor: strokeStart,
        strokeEndColor: strokeEnd,
        strokeAngle: 90,
      }
    ));
    nodes.push(this.glintShape(
      "header:top-glint",
      1,
      1,
      Math.max(0, w - 2),
      Math.max(1, headerH * 0.42),
      [Math.max(0, r - 1), Math.max(0, r - 1), 0, 0],
      headerPaintAlpha
    ));
    if (bodyH > 0) {
      nodes.push(this.panelShape(
        "body",
        0,
        bodyY,
        w,
        bodyH,
        [0, 0, r, r],
        [bodyPrimary, bodyPrimary, bodySecondary, bodySecondary],
        stroke,
        strokeW,
        {
          strokeStartColor: strokeStart,
          strokeEndColor: strokeEnd,
          strokeAngle: 90,
        }
      ));
      nodes.push(this.glintShape(
        "body:top-glint",
        1,
        bodyY + 1,
        Math.max(0, w - 2),
        Math.max(1, bodyH * 0.18),
        [0, 0, 0, 0],
        bodyPaintAlpha
      ));
    }

    nodes.push(this.headerIconDivider());

    return nodes;
  }

  chromeUnifiedDivider() {
    const w = this.w();
    const h = this.h();
    const bs = this.bs();
    const headerH = this.base.headerH * bs;
    const r = this.base.radius * bs;
    const strokeW = Math.max(0.35 * n(this.p.drawScale, 1), 0.42 * bs);
    const primary = color(this.pal, "bodyLeft", color(this.pal, "headerLeft", "#E522242C"));
    const secondary = color(this.pal, "bodyRight", color(this.pal, "headerRight", "#E50E1015"));
    const outline = color(this.pal, "outline", "#665A5A5A");
    const paintAlpha = Math.max(alpha01(primary), alpha01(secondary));
    const legacyStrokeAlpha = Math.min(0.34, Math.max(alpha01(outline) * 0.42, paintAlpha * 0.26));
    const hasStrokeControl = typeof this.p.strokeEnabled === "boolean";
    const strokeEnabled = hasStrokeControl ? this.p.strokeEnabled === true : true;
    const configuredStrokeAlpha = Math.max(0, Math.min(1, n(this.p.strokeAlpha, legacyStrokeAlpha)));
    const strokeAlpha = strokeEnabled ? (hasStrokeControl ? configuredStrokeAlpha : legacyStrokeAlpha) : 0;
    const stroke = hasStrokeControl ? withAlpha(outline, strokeAlpha) : alpha(outline, strokeAlpha);
    const useStrokeGradient = hasStrokeControl && this.p.strokeGradient === true;
    const strokeStart = useStrokeGradient
      ? withAlpha(c(this.p.strokeStartColor, outline), strokeAlpha)
      : (hasStrokeControl ? stroke : alpha("#FFFFFFFF", 0.09 * strokeAlpha));
    const strokeEnd = useStrokeGradient
      ? withAlpha(c(this.p.strokeEndColor, outline), strokeAlpha)
      : stroke;
    const dividerAlpha = Math.min(0.42, Math.max(0.12, paintAlpha * 0.38));
    const nodes = [];

    if (this.p.blur === true) {
      nodes.push(this.blurShape("blur:plate", 0, 0, w, h, [r, r, r, r]));
    }

    nodes.push(this.panelShape(
      "plate",
      0,
      0,
      w,
      h,
      [r, r, r, r],
      [primary, primary, secondary, secondary],
      stroke,
      strokeW,
      {
        strokeStartColor: strokeStart,
        strokeEndColor: strokeEnd,
        strokeAngle: 90,
      }
    ));
    nodes.push(this.glintShape(
      "plate:top-glint",
      1,
      1,
      Math.max(0, w - 2),
      Math.max(1, Math.min(h * 0.34, headerH * 0.52)),
      [Math.max(0, r - 1), Math.max(0, r - 1), 0, 0],
      paintAlpha
    ));
    nodes.push(ui.connector({
      key: "header:separator",
      connector: "line",
      class: abs(6 * bs, headerH + 1.4 * bs, Math.max(1, w - 12 * bs), 1),
      x1: 0,
      y1: 0.5,
      x2: Math.max(1, w - 12 * bs),
      y2: 0.5,
      stroke: alpha(color(this.pal, "divider", "#66FFFFFF"), dividerAlpha),
      strokeWidth: Math.max(0.5 * n(this.p.drawScale, 1), 0.55 * bs),
    }));
    nodes.push(this.headerIconDivider(alpha(color(this.pal, "divider", "#66FFFFFF"), dividerAlpha * 0.85)));

    return nodes;
  }

  headerIconDivider(fillOverride) {
    const bs = this.bs();
    const horizontal = this.horizontalLayout();
    const headerH = this.base.headerH * bs;
    const dividerH = this.base.headerDividerH * bs;
    const dividerY = (headerH - dividerH) * 0.5;
    return ui.shape({
      key: "header:divider",
      shape: "rounded",
      class: abs(horizontal.dividerX, dividerY, horizontal.dividerW, dividerH),
      radius: Math.min(0.5 * bs, horizontal.dividerW * 0.5),
      fill: fillOverride || color(this.pal, "divider", "#66FFFFFF"),
    });
  }

  header(counterLabel = "Active:") {
    const bs = this.bs();
    const fs = this.fs();
    const w = this.w();
    const headerH = this.base.headerH * bs;
    const rowTextH = n(this.p.rowTextHeight, 8);
    const titleH = n(this.p.headerTextHeight, 8);
    const iconH = n(this.p.headerIconHeight, 8);
    const iconScale = Math.max(0.25, n(prop(this.v, "headerIconScale", 1), 1));
    const horizontal = this.horizontalLayout();
    const count = String(Math.max(0, Math.round(n(this.p.activeCount, 0))));
    const counterLabelText = c(counterLabel, "Active:");
    const measuredCountValueW = n(this.p.countValueWidth, 0);
    const measuredCountLabelW = n(this.p.countLabelWidth, 0);
    const countValueW = Math.max(8 * bs, measuredCountValueW > 0 ? measuredCountValueW + 1.5 * bs : count.length * 6.5 * fs + 5.5 * bs);
    const countLabelW = Math.max(26 * fs, measuredCountLabelW > 0 ? measuredCountLabelW + 1.5 * bs : counterLabelText.length * 5.9 * fs + 1.5 * bs);
    const countGap = Math.max(3.2 * bs, 3.0 * fs);
    const countValueX = w - countValueW - n(prop(this.v, "countValueOffset", 3), 3) * bs;
    const countLabelX = countValueX - countLabelW - countGap;
    const countY = (headerH - rowTextH) * 0.5 + 0.8 * bs;
    const titleX = horizontal.titleX;
    const titleW = Math.max(0, countLabelX - titleX - 4 * bs);
    const titleY = (headerH - titleH) * 0.5;
    const iconY = (headerH - iconH) * 0.5;

    return [
      ui.text({
        key: "header:count-label",
        text: counterLabelText,
        color: color(this.pal, "text", "#FFFFFFFF"),
        class: cls(abs(countLabelX, countY, countLabelW, rowTextH + 4 * bs), font("OnestMedium", fs * 0.92), `text-${color(this.pal, "text", "#FFFFFFFF")}`),
      }),
      ui.text({
        key: "header:count-value",
        text: count,
        color: color(this.pal, "counter", "#FFFFFFFF"),
        class: cls(abs(countValueX, countY, countValueW, rowTextH + 4 * bs), font("OnestMedium", fs * 0.92), `text-${color(this.pal, "counter", "#FFFFFFFF")}`),
      }),
      ui.text({
        key: "header:icon",
        text: c(this.p.headerIcon, ""),
        color: c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF")),
        textGradient: this.p.headerIconGradient === true,
        gradientStartColor: c(this.p.headerIconGradientStart, c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"))),
        gradientEndColor: c(this.p.headerIconGradientEnd, c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"))),
        gradientAngle: n(this.p.headerIconGradientAngle, 45),
        class: cls(abs(horizontal.left, iconY, horizontal.iconSlotW, iconH + 4 * bs), font(c(prop(this.v, "headerIconFont", "IconsNur"), "IconsNur"), fs * iconScale), `text-${c(this.p.headerIconColor, color(this.pal, "counter", "#FFFFFFFF"))}`, "text-align-center"),
      }),
      ui.text({
        key: "header:title",
        text: c(this.p.title, ""),
        color: color(this.pal, "titleText", "#FFFFFFFF"),
        class: cls(abs(titleX, titleY, titleW, titleH + 4 * bs), font("Inter", fs, "bold"), `text-${color(this.pal, "titleText", "#FFFFFFFF")}`),
      }),
    ];
  }

  renderContent() {
    return [];
  }

  counterLabel() {
    return "Active:";
  }

  render() {
    const w = this.w();
    const h = this.h();
    return ui.root({
      key: `panel:${c(this.p.id, "panel")}`,
      class: cls(
        `w-${s(w)}`,
        `h-${s(h)}`,
        `rounded-${s(this.base.radius * this.bs())}`,
        this.p.shadowControlled === true ? "" : "shadow-panel"
      ),
      children: [
        ...this.chrome(),
        ...this.header(this.counterLabel()),
        ...this.renderContent(),
      ],
    });
  }
}
