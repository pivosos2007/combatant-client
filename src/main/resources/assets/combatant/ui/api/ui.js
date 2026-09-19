/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const UI_FRAGMENT = Object.freeze({ __combatantUiFragment: true });

export const ui = {
  /**
   * React-like authoring entry point. Runtime node strings use Combatant node names
   * ("row", "column", "text", ...); function values are lightweight components.
   */
  h(type, init = null, ...children) {
    return createElement(type, init, children);
  },
  createElement(type, init = null, ...children) {
    return createElement(type, init, children);
  },
  /** Fragment can be used with ui.h/ui.createElement and may also be returned at module root. */
  Fragment: UI_FRAGMENT,
  fragment(...children) {
    return normalizeChildren(children);
  },
  /** Convenience conditional that composes naturally inside children arrays. */
  when(condition, child) {
    return condition ? child : null;
  },
  node(type, init = {}) {
    return normalize(type, init);
  },
  root(init = {}) { return normalize("root", init); },
  panel(init = {}) { return normalize("panel", init); },
  row(init = {}) { return normalize("row", init); },
  column(init = {}) { return normalize("column", init); },
  stack(init = {}) { return normalize("stack", init); },
  vector(init = {}) {
    return normalize("vector", { overflow: init.overflow ?? "hidden", ...init });
  },
  plot(init = {}) {
    const xDomain = Array.isArray(init.xDomain) ? init.xDomain : [0, 1];
    const yDomain = Array.isArray(init.yDomain) ? init.yDomain : [0, 1];
    const minX = ui.num(xDomain[0], 0);
    const maxX = ui.num(xDomain[1], minX + 1);
    const minY = ui.num(yDomain[0], 0);
    const maxY = ui.num(yDomain[1], minY + 1);
    const viewBox = init.viewBox ?? [minX, minY, Math.max(1e-9, maxX - minX), Math.max(1e-9, maxY - minY)];
    const { xDomain: _xDomain, yDomain: _yDomain, ...rest } = init;
    return ui.vector({ yAxis: "up", preserveAspectRatio: "none", ...rest, viewBox });
  },
  text(init = "") {
    if (typeof init === "string" || typeof init === "number") return normalize("text", { text: String(init) });
    const text = init?.text ?? primitiveText(init?.children);
    return normalize("text", {
      ...init,
      ...(text !== undefined ? { text, children: [] } : {}),
    });
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
  rect(init = {}) {
    const { x, y, width, height, ...rest } = init;
    return normalize("shape", {
      ...rest,
      shape: "rect",
      vectorX: init.vectorX ?? x,
      vectorY: init.vectorY ?? y,
      vectorWidth: init.vectorWidth ?? width,
      vectorHeight: init.vectorHeight ?? height,
    });
  },
  circle(init = {}) { return normalize("shape", { shape: "circle", ...init }); },
  point(init = {}) {
    const { x = init.cx ?? 0, y = init.cy ?? 0, ...rest } = init;
    return normalize("shape", { shape: "circle", cx: x, cy: y, radius: init.radius ?? init.r ?? 2, ...rest });
  },
  bar(init = {}) {
    const x = init.x ?? 0;
    const y = init.y ?? 0;
    const width = init.width ?? init.w ?? 0;
    const height = init.height ?? init.h ?? 0;
    return ui.rect({ ...init, x, y, width, height });
  },
  rounded(init = {}) {
    const { x, y, width, height, ...rest } = init;
    return normalize("shape", {
      ...rest,
      shape: "box",
      vectorX: init.vectorX ?? x,
      vectorY: init.vectorY ?? y,
      vectorWidth: init.vectorWidth ?? width,
      vectorHeight: init.vectorHeight ?? height,
      corners: ui.corner.all(ui.corner.rounded(init.radius ?? init.r ?? 0)),
    });
  },
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
  inset({ left = 0, top = 0, right, bottom, width, height, ...extra } = {}) {
    return ui.style({ position: "absolute", left, top, right, bottom, width, height }, extra);
  },
  /** @deprecated Utility-class positioning is retained for legacy scripts. Prefer style: ui.absolute(...). */
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
  roundedRect({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, fill, stroke, strokeWidth = 0, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      fill,
      stroke,
      strokeWidth,
      ...rest,
    });
  },
  squircle({ key, x = 0, y = 0, w = 0, h = 0, profile = "standard", power, exponent, fill, stroke, strokeWidth = 0, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "squircle",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      profile,
      power: power ?? exponent,
      fill,
      stroke,
      strokeWidth,
      ...rest,
    });
  },
  roundedGradient({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, startColor, endColor, angle = 90, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-gradient",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      startColor,
      endColor,
      angle,
      ...rest,
    });
  },
  roundedGradientQuad({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-gradient-quad",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      topLeftColor,
      topRightColor,
      bottomRightColor,
      bottomLeftColor,
      ...rest,
    });
  },
  roundedStrokeGradient({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, thickness = 1, startColor, endColor, angle = 90, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-stroke-gradient",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      thickness,
      startColor,
      endColor,
      angle,
      ...rest,
    });
  },
  roundedSoftShadow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, blur = 8, innerAlpha = 0.18, color = "#00000000", class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-soft-shadow",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      blur,
      innerAlpha,
      color,
      fill: color,
      ...rest,
    });
  },
  roundedShadow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, softness = 8, spread = 12, color = "#00000000", class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-shadow",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      softness,
      spread,
      color,
      fill: color,
      ...rest,
    });
  },
  roundedGlow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, softness = 0, glow = 8, color = "#00000000", class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded-glow",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      softness,
      glow,
      color,
      fill: color,
      ...rest,
    });
  },
  radialGlow({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, glowRadius = 32, cx = w * 0.5, cy = h * 0.5, color = "#00000000", class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "radial-glow-masked",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      glowRadius,
      cx,
      cy,
      color,
      fill: color,
      ...rest,
    });
  },
  circleSoftShadow({ key, x = 0, y = 0, size = 0, radius = size * 0.5, blur = 7, innerAlpha = 0.34, color = "#00000000", class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "circle-soft-shadow",
      class: extra,
      style: ui.absolute(x, y, size, size, style),
      radius,
      blur,
      innerAlpha,
      color,
      fill: color,
      ...rest,
    });
  },
  blurSurface({ key, x = 0, y = 0, w = 0, h = 0, radius = 0, r, alpha = 0, brightness = 1, class: extra = "", style = {}, ...rest } = {}) {
    return ui.shape({
      key,
      shape: "rounded",
      class: extra,
      style: ui.absolute(x, y, w, h, style),
      radius: r ?? radius,
      fill: "#00000000",
      blur: true,
      blurAlpha: ui.clamp(alpha, 0, 1),
      blurBrightness: brightness,
      ...rest,
    });
  },
  clip({ key, x = 0, y = 0, w = 0, h = 0, class: extra = "", style = {}, children = [], ...rest } = {}) {
    return ui.stack({
      key,
      class: extra,
      style: ui.absolute(x, y, w, h, ui.style({ overflow: "hidden" }, style)),
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
    const centered = centerWhenFits === true && !overflow;
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
          class: textClass,
          style: ui.absolute(0, 0, safeW, h, centered ? { textAlign: "center" } : {}),
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
    flexGrow,
    shrink,
    flexShrink,
    display,
    flexDirection,
    padding,
    paddingX,
    paddingHorizontal,
    paddingY,
    paddingVertical,
    paddingLeft,
    paddingTop,
    paddingRight,
    paddingBottom,
    margin,
    marginX,
    marginHorizontal,
    marginY,
    marginVertical,
    marginLeft,
    marginTop,
    marginRight,
    marginBottom,
    gap,
    position,
    absolute,
    x,
    y,
    left,
    top,
    right,
    bottom,
    align,
    alignItems,
    justify,
    justifyContent,
    overflow,
    fontSize,
    lineHeight,
    fontFamily,
    fontWeight,
    fontStyle,
    textAlign,
    maxTextWidth,
    ellipsis,
    whiteSpace,
    overflowWrap,
    maxLines,
    textOverflow,
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
  if (flexGrow !== undefined) layoutStyle.flexGrow = flexGrow;
  if (shrink !== undefined) layoutStyle.flexShrink = shrink;
  if (flexShrink !== undefined) layoutStyle.flexShrink = flexShrink;
  if (display !== undefined) layoutStyle.display = display;
  if (flexDirection !== undefined) layoutStyle.flexDirection = flexDirection;
  if (padding !== undefined) layoutStyle.padding = padding;
  if (paddingX !== undefined) layoutStyle.paddingX = paddingX;
  if (paddingHorizontal !== undefined) layoutStyle.paddingHorizontal = paddingHorizontal;
  if (paddingY !== undefined) layoutStyle.paddingY = paddingY;
  if (paddingVertical !== undefined) layoutStyle.paddingVertical = paddingVertical;
  if (paddingLeft !== undefined) layoutStyle.paddingLeft = paddingLeft;
  if (paddingTop !== undefined) layoutStyle.paddingTop = paddingTop;
  if (paddingRight !== undefined) layoutStyle.paddingRight = paddingRight;
  if (paddingBottom !== undefined) layoutStyle.paddingBottom = paddingBottom;
  if (margin !== undefined) layoutStyle.margin = margin;
  if (marginX !== undefined) layoutStyle.marginX = marginX;
  if (marginHorizontal !== undefined) layoutStyle.marginHorizontal = marginHorizontal;
  if (marginY !== undefined) layoutStyle.marginY = marginY;
  if (marginVertical !== undefined) layoutStyle.marginVertical = marginVertical;
  if (marginLeft !== undefined) layoutStyle.marginLeft = marginLeft;
  if (marginTop !== undefined) layoutStyle.marginTop = marginTop;
  if (marginRight !== undefined) layoutStyle.marginRight = marginRight;
  if (marginBottom !== undefined) layoutStyle.marginBottom = marginBottom;
  if (gap !== undefined) layoutStyle.gap = gap;
  if (position !== undefined) layoutStyle.position = position;
  if (absolute !== undefined) layoutStyle.absolute = absolute;
  if (x !== undefined) layoutStyle.left = x;
  if (y !== undefined) layoutStyle.top = y;
  if (left !== undefined) layoutStyle.left = left;
  if (top !== undefined) layoutStyle.top = top;
  if (right !== undefined) layoutStyle.right = right;
  if (bottom !== undefined) layoutStyle.bottom = bottom;
  if (align !== undefined) {
    if (type === "text" && (align === "left" || align === "right" || align === "center" || align === "end")) {
      layoutStyle.textAlign = align;
    } else {
      layoutStyle.alignItems = align;
    }
  }
  if (alignItems !== undefined) layoutStyle.alignItems = alignItems;
  if (justify !== undefined) layoutStyle.justifyContent = justify;
  if (justifyContent !== undefined) layoutStyle.justifyContent = justifyContent;
  if (overflow !== undefined) layoutStyle.overflow = overflow;
  if (fontSize !== undefined) layoutStyle.fontSize = fontSize;
  if (lineHeight !== undefined) layoutStyle.lineHeight = lineHeight;
  if (fontFamily !== undefined) layoutStyle.fontFamily = fontFamily;
  if (fontWeight !== undefined) layoutStyle.fontWeight = fontWeight;
  if (fontStyle !== undefined) layoutStyle.fontStyle = fontStyle;
  if (textAlign !== undefined) layoutStyle.textAlign = textAlign;
  if (maxTextWidth !== undefined) layoutStyle.maxTextWidth = maxTextWidth;
  if (ellipsis !== undefined) layoutStyle.ellipsis = ellipsis;
  if (whiteSpace !== undefined) layoutStyle.whiteSpace = whiteSpace;
  if (overflowWrap !== undefined) layoutStyle.overflowWrap = overflowWrap;
  if (maxLines !== undefined) layoutStyle.maxLines = maxLines;
  if (textOverflow !== undefined) layoutStyle.textOverflow = textOverflow;
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
    if (typeof child === "string" || typeof child === "number") {
      out.push(normalize("text", { text: String(child) }));
      return;
    }
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

function primitiveText(value) {
  if (typeof value === "string" || typeof value === "number") return String(value);
  if (!Array.isArray(value)) return undefined;
  let text = "";
  for (const child of value) {
    if (child === null || child === undefined || typeof child === "boolean") continue;
    if (typeof child !== "string" && typeof child !== "number") return undefined;
    text += String(child);
  }
  return text;
}

function createElement(type, init, childArgs) {
  const props = init && typeof init === "object" && !Array.isArray(init) ? { ...init } : {};
  const children = childArgs && childArgs.length > 0 ? childArgs : props.children;

  if (type === UI_FRAGMENT) {
    return normalizeChildren(children);
  }
  if (typeof type === "function") {
    return type({ ...props, children: normalizeChildren(children) });
  }
  if (typeof type !== "string" || type.length === 0) {
    throw new TypeError("ui.createElement(type, ...) expects a UI node type string, ui.Fragment, or component function.");
  }

  if (type === "text" && props.text === undefined) {
    const text = primitiveText(children);
    if (text !== undefined) {
      props.text = text;
      props.children = [];
      return normalize(type, props);
    }
  }
  props.children = children;
  return normalize(type, props);
}
