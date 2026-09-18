/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

export const ui = {
  node(type, init = {}) {
    return normalize(type, init);
  },
  root(init = {}) { return normalize("root", init); },
  panel(init = {}) { return normalize("panel", init); },
  row(init = {}) { return normalize("row", init); },
  column(init = {}) { return normalize("column", init); },
  stack(init = {}) { return normalize("stack", init); },
  text(init = "") {
    if (typeof init === "string") return normalize("text", { text: init });
    return normalize("text", init);
  },
  image(init = {}) {
    if (typeof init === "string") return normalize("image", { asset: init });
    return normalize("image", init);
  },
  svg(init = {}) {
    if (typeof init === "string") return normalize("svg", { asset: init, assetType: "svg" });
    return normalize("svg", { assetType: "svg", ...init });
  },
  shape(init = {}) { return normalize("shape", init); },
  box(init = {}) { return normalize("shape", { shape: "box", ...init }); },
  rect(init = {}) { return normalize("shape", { shape: "rect", ...init }); },
  circle(init = {}) { return normalize("shape", { shape: "circle", ...init }); },
  rounded(init = {}) { return normalize("shape", { shape: "box", corners: ui.corner.all(ui.corner.rounded(init.radius ?? init.r ?? 0)), ...init }); },
  chamfered(init = {}) { return normalize("shape", { shape: "box", corners: ui.corner.all(ui.corner.chamfered(init.cut ?? init.chamfer ?? 0)), ...init }); },
  connector(init = {}) { return normalize("connector", init); },
  path(init = {}) { return normalize("path", init); },
  line(init = {}) {
    const points = init.points ?? (
      Number.isFinite(init.x1) && Number.isFinite(init.y1) && Number.isFinite(init.x2) && Number.isFinite(init.y2)
        ? [init.x1, init.y1, init.x2, init.y2]
        : undefined
    );
    return normalize("path", { ...init, points, curve: "linear", strokeWidth: init.strokeWidth ?? 1 });
  },
  polyline(init = {}) { return normalize("path", { ...init, curve: "linear", strokeWidth: init.strokeWidth ?? 1 }); },
  spline(init = {}) { return normalize("path", { ...init, curve: "spline", strokeWidth: init.strokeWidth ?? 1 }); },
  area(init = {}) { return normalize("path", { ...init, curve: init.curve ?? "linear", area: init.area ?? true, strokeWidth: init.strokeWidth ?? 1 }); },
  item(init = {}) { return normalize("item", init); },
  button(init = {}) { return normalize("button", init); },
  scroll(init = {}) { return normalize("scroll", init); },
  spacer(init = {}) { return normalize("spacer", init); },
  inputText(init = {}) { return normalize("input_text", init); },
  checkbox(init = {}) { return normalize("checkbox", init); },
  slider(init = {}) { return normalize("slider", init); },
  divider(init = {}) { return normalize("divider", init); },
  canvas(init = {}) { return normalize("canvas", init); },

  num(value, fallback = 0) {
    return typeof value === "number" && Number.isFinite(value) ? value : fallback;
  },
  str(value, fallback = "") {
    return typeof value === "string" && value.length > 0 ? value : fallback;
  },
  bool(value, fallback = false) {
    return typeof value === "boolean" ? value : fallback;
  },
  clamp(value, min = 0, max = 1) {
    const v = ui.num(value, min);
    return Math.max(min, Math.min(max, v));
  },
  fmt(value, digits = 2, fallback = "0") {
    return Number.isFinite(value) ? value.toFixed(digits) : fallback;
  },
  cls(...parts) {
    return parts.filter(Boolean).join(" ");
  },
  style(...parts) {
    return Object.assign({}, ...parts.filter(part => part && typeof part === "object" && !Array.isArray(part)));
  },
  children(...parts) {
    return normalizeChildren(parts);
  },
  absolute(x = 0, y = 0, w = 0, h = 0, extra = {}) {
    return ui.style({ position: "absolute", left: x, top: y, width: w, height: h }, extra);
  },
  abs(x = 0, y = 0, w = 0, h = 0, extra = "") {
    return ui.cls("absolute", `x-${ui.fmt(x)}`, `y-${ui.fmt(y)}`, `w-${ui.fmt(w)}`, `h-${ui.fmt(h)}`, extra);
  },
  prop(value, key, fallback = undefined) {
    if (value === null || value === undefined) return fallback;
    try {
      const direct = value[key];
      if (direct !== undefined && direct !== null) return direct;
    } catch (_) {
    }
    try {
      if (typeof value.get === "function") {
        const resolved = value.get(key);
        if (resolved !== undefined && resolved !== null) return resolved;
      }
    } catch (_) {
    }
    return fallback;
  },
  arr(value) {
    if (value === null || value === undefined) return [];
    try {
      return Array.from(value);
    } catch (_) {
      return [];
    }
  },
  roundedRect({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, fill, stroke, strokeWidth = 0, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      fill,
      stroke,
      strokeWidth,
      ...rest,
    });
  },
  squircle({ key, x = 0, y = 0, w = 0, h = 0, profile = "standard", power, exponent, fill, stroke, strokeWidth = 0, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "squircle",
      class: ui.abs(x, y, w, h, extra),
      profile,
      power: power ?? exponent,
      fill,
      stroke,
      strokeWidth,
      ...rest,
    });
  },
  roundedGradient({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, startColor, endColor, angle = 90, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-gradient",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      startColor,
      endColor,
      angle,
      ...rest,
    });
  },
  roundedGradientQuad({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-gradient-quad",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      topLeftColor,
      topRightColor,
      bottomRightColor,
      bottomLeftColor,
      ...rest,
    });
  },
  roundedStrokeGradient({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, thickness = 1, startColor, endColor, angle = 90, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-stroke-gradient",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      thickness,
      startColor,
      endColor,
      angle,
      ...rest,
    });
  },
  roundedSoftShadow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, blur = 8, innerAlpha = 0.18, color = "#00000000", class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-soft-shadow",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      blur,
      innerAlpha,
      color,
      fill: color,
      ...rest,
    });
  },
  roundedShadow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, softness = 8, spread = 12, color = "#00000000", class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-shadow",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      softness,
      spread,
      color,
      fill: color,
      ...rest,
    });
  },
  roundedGlow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, softness = 0, glow = 8, color = "#00000000", class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-glow",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      softness,
      glow,
      color,
      fill: color,
      ...rest,
    });
  },
  radialGlow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, glowRadius = 32, cx = w * 0.5, cy = h * 0.5, color = "#00000000", class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "radial-glow-masked",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      glowRadius,
      cx,
      cy,
      color,
      fill: color,
      ...rest,
    });
  },
  circleSoftShadow({ key, x = 0, y = 0, size = 0, radius = size * 0.5, blur = 7, innerAlpha = 0.34, color = "#00000000", class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "circle-soft-shadow",
      class: ui.abs(x, y, size, size, extra),
      radius,
      blur,
      innerAlpha,
      color,
      fill: color,
      ...rest,
    });
  },
  blurSurface({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, alpha = 0, brightness = 1, class: extra = "", ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded",
      class: ui.abs(x, y, w, h, extra),
      radius: r ?? radius,
      fill: "#00000000",
      blur: true,
      blurAlpha: ui.clamp(alpha, 0, 1),
      blurBrightness: brightness,
      ...rest,
    });
  },
  clip({ key, x = 0, y = 0, w = 0, h = 0, class: extra = "", children = [], ...rest } = {}) {
    return ui.stack({
      key,
      class: ui.abs(x, y, w, h, ui.cls("clip overflow-hidden", extra)),
      children,
      ...rest,
    });
  },
  clippedText({
    key,
    text = "",
    x = 0,
    y = 0,
    w = 0,
    h = 0,
    textClass = "",
    color = "",
    measuredWidth = w,
    fadeWidth = 16,
    scrollTime = 0,
    scrollDelay = 1.0,
    scrollSpeed = 18,
    fade = true,
    centerWhenFits = false,
  } = {}) {
    const safeW = Math.max(0, w);
    const measured = ui.num(measuredWidth, safeW);
    const fadeW = ui.num(fadeWidth, 16);
    const overflow = measured > safeW + 1;
    const offset = overflow
      ? Math.min(Math.max(0, measured - Math.max(0, safeW - fadeW * 0.35)), Math.max(0, ui.num(scrollTime, 0) - ui.num(scrollDelay, 1.0)) * ui.num(scrollSpeed, 18))
      : 0;
    const align = centerWhenFits === true && !overflow ? "text-align-center" : "";
    return ui.clip({
      key: `${key}:clip`,
      x,
      y,
      w: safeW,
      h,
      children: [
        ui.text({
          key,
          text: text || "",
          color,
          class: ui.cls(ui.abs(0, 0, safeW, h), textClass, align),
          textOffsetX: -offset,
          textFade: fade === true && overflow,
          fadeLeft: offset > 0.5 ? fadeW : 0,
          fadeRight: fade === true && overflow ? fadeW : 0,
        }),
      ],
    });
  },
  color: {
    get(value, key, fallback = "#00000000") {
      return ui.str(ui.prop(value, key, fallback), fallback);
    },
    alpha(hex, alpha = 1, fallback = "#00000000") {
      const src = ui.str(hex, fallback);
      if (!src.startsWith("#") || src.length !== 9) return src;
      const raw = Number.parseInt(src.slice(1), 16);
      if (!Number.isFinite(raw)) return src;
      const nextA = Math.max(0, Math.min(255, Math.round(((raw >>> 24) & 255) * ui.clamp(alpha, 0, 1))));
      return "#" + (((nextA << 24) | (raw & 0x00ffffff)) >>> 0).toString(16).padStart(8, "0").toUpperCase();
    },
    opacity(hex, fallback = 1) {
      const src = ui.str(hex, "#FFFFFFFF");
      if (!src.startsWith("#") || src.length !== 9) return fallback;
      const raw = Number.parseInt(src.slice(1), 16);
      if (!Number.isFinite(raw)) return fallback;
      return ((raw >>> 24) & 255) / 255;
    },
  },
  corner: {
    square() { return { kind: "square" }; },
    rounded(radius = 0, radiusY = radius) { return { kind: "rounded", radius, radiusX: radius, radiusY }; },
    chamfered(cut = 0, cutY = cut) { return { kind: "chamfered", cut, cutX: cut, cutY }; },
    concave(radius = 0) { return { kind: "concave", radius }; },
    all(corner) { return { tl: corner, tr: corner, br: corner, bl: corner }; },
    mixed(tl, tr = tl, br = tr, bl = tl) { return { tl, tr, br, bl }; },
  },
  edge: {
    straight() { return { kind: "straight" }; },
    notch(width = 12, depth = 6, offset = "center") { return { kind: "notch", width, depth, offset }; },
    inset(depth = 0) { return { kind: "inset", depth }; },
  },
};

function normalize(type, init) {
  const {
    key = "",
    class: classValue = "",
    className = "",
    style = {},
    width,
    height,
    minWidth,
    minHeight,
    maxWidth,
    maxHeight,
    grow,
    display,
    flexDirection,
    absolute,
    x,
    y,
    align,
    justify,
    overflow,
    textAlign,
    maxTextWidth,
    ellipsis,
    marquee,
    props = {},
    events = {},
    onClick,
    onChange,
    onInput,
    onScroll,
    meta = {},
    children = [],
    ...rest
  } = init ?? {};
  const layoutStyle = {};
  if (width !== undefined) layoutStyle.width = width;
  if (height !== undefined) layoutStyle.height = height;
  if (minWidth !== undefined) layoutStyle.minWidth = minWidth;
  if (minHeight !== undefined) layoutStyle.minHeight = minHeight;
  if (maxWidth !== undefined) layoutStyle.maxWidth = maxWidth;
  if (maxHeight !== undefined) layoutStyle.maxHeight = maxHeight;
  if (grow !== undefined) layoutStyle.flexGrow = grow;
  if (display !== undefined) layoutStyle.display = display;
  if (flexDirection !== undefined) layoutStyle.flexDirection = flexDirection;
  if (absolute !== undefined) layoutStyle.absolute = absolute;
  if (x !== undefined) layoutStyle.left = x;
  if (y !== undefined) layoutStyle.top = y;
  if (align !== undefined) {
    if (type === "text" && (align === "left" || align === "right" || align === "center" || align === "end")) {
      layoutStyle.textAlign = align;
    } else {
      layoutStyle.alignItems = align;
    }
  }
  if (justify !== undefined) layoutStyle.justifyContent = justify;
  if (overflow !== undefined) layoutStyle.overflow = overflow;
  if (textAlign !== undefined) layoutStyle.textAlign = textAlign;
  if (maxTextWidth !== undefined) layoutStyle.maxTextWidth = maxTextWidth;
  if (ellipsis !== undefined) layoutStyle.ellipsis = ellipsis;
  if (marquee !== undefined) layoutStyle.marquee = marquee;

  const normalizedEvents = { ...events };
  if (onClick !== undefined && onClick !== null) normalizedEvents.click = onClick;
  if (onChange !== undefined && onChange !== null) normalizedEvents.change = onChange;
  if (onInput !== undefined && onInput !== null) normalizedEvents.input = onInput;
  if (onScroll !== undefined && onScroll !== null) normalizedEvents.scroll = onScroll;
  return {
    type,
    key,
    class: ui.cls(className, classValue),
    style: ui.style(layoutStyle, style && typeof style === "object" && !Array.isArray(style) ? style : {}),
    props: { ...rest, ...props },
    events: normalizedEvents,
    meta,
    children: normalizeChildren(children),
  };
}

function normalizeChildren(value) {
  const out = [];
  const append = (child) => {
    if (child === null || child === undefined || typeof child === "boolean") return;
    if (Array.isArray(child)) {
      for (const nested of child) append(nested);
      return;
    }
    if (typeof child !== "string" && child) {
      try {
        const iterator = child[Symbol.iterator];
        if (typeof iterator === "function") {
          for (const nested of child) append(nested);
          return;
        }
      } catch (_) {
      }
    }
    out.push(child);
  };
  append(value);
  return out;
}
