/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** Node kinds understood by the runtime layout/render/input layers. */
/** Runtime authoring contract loaded from ui.contract.json. Useful for tooling/diagnostics. */
export type UiAuthoringContract = {
  readonly version: number;
  readonly nodeTypes: readonly UiNodeType[];
  readonly styleKeys: readonly (keyof UiInlineStyle)[];
  /** Legacy/alternate authoring keys normalized to one canonical style key by ui.js. */
  readonly styleAliases: Readonly<Record<string, keyof UiInlineStyle>>;
};

export type UiNodeType =
  | "root"
  | "panel"
  | "row"
  | "column"
  | "stack"
  | "vector"
  | "text"
  | "image"
  | "svg"
  | "shape"
  | "connector"
  | "path"
  | "item"
  | "button"
  | "scroll"
  | "spacer"
  | "input"
  | "input_text"
  | "checkbox"
  | "slider"
  | "divider"
  | "canvas";

/** String reference dispatched through UiActionRegistry. */
export type UiActionRef = string;

/** Event map stored on a node spec. Values reference registered runtime actions. */
export type UiEvents = {
  /** Fired by pointer release on the same pressed node. */
  click?: UiActionRef;
  /** Value change event for host-backed controls. */
  change?: UiActionRef;
  /** Text/input event for host-backed controls. */
  input?: UiActionRef;
  /** Scroll event for scroll containers or host-backed controls. */
  scroll?: UiActionRef;
};

/** Asset kind name passed to UiAssetResolver and UiAssetRegistry. */
export type UiAssetKind =
  | "texture"
  | "gui-sprite"
  | "svg"
  | "player-head"
  | "media-artwork"
  | "item"
  | string;

/** Cross-axis alignment used by row/column/stack assignment. */
export type UiAlign = "start" | "center" | "end" | "stretch";
/** Main-axis free-space placement used by row/column/stack assignment. */
export type UiJustify = "start" | "center" | "end" | "between";
/** Overflow mode. Non-visible modes clip and may use scroll state. */
export type UiOverflow = "visible" | "hidden" | "scroll-x" | "scroll-y" | "scroll";

export type UiInlineStyle = {
  width?: number;
  height?: number;
  minWidth?: number;
  minHeight?: number;
  maxWidth?: number;
  maxHeight?: number;

  padding?: number;
  paddingX?: number;
  /** @deprecated Prefer paddingX. */
  paddingHorizontal?: number;
  paddingY?: number;
  /** @deprecated Prefer paddingY. */
  paddingVertical?: number;
  paddingLeft?: number;
  paddingTop?: number;
  paddingRight?: number;
  paddingBottom?: number;

  margin?: number;
  marginX?: number;
  /** @deprecated Prefer marginX. */
  marginHorizontal?: number;
  marginY?: number;
  /** @deprecated Prefer marginY. */
  marginVertical?: number;
  marginLeft?: number;
  marginTop?: number;
  marginRight?: number;
  marginBottom?: number;

  gap?: number;
  /** @deprecated Prefer flexGrow. */
  grow?: number;
  flexGrow?: number;
  /** Explicit main-axis shrink weight. Default 0 preserves legacy fixed-size behavior. */
  /** @deprecated Prefer flexShrink. */
  shrink?: number;
  flexShrink?: number;
  /** Browser-like container flow. display:flex defaults to row; block maps to vertical flow. */
  display?: "flex" | "block";
  flexDirection?: "row" | "column";

  position?: "absolute" | "relative" | "static" | "flow";
  /** @deprecated Prefer position: "absolute" or "relative". */
  absolute?: boolean;
  left?: number;
  top?: number;
  right?: number;
  bottom?: number;
  /** @deprecated Prefer left. */
  x?: number;
  /** @deprecated Prefer top. */
  y?: number;

  /** @deprecated Prefer alignItems. */
  align?: UiAlign;
  alignItems?: UiAlign;
  /** @deprecated Prefer justifyContent. */
  justify?: UiJustify;
  justifyContent?: UiJustify;
  overflow?: UiOverflow;

  /** @deprecated Prefer borderRadius for ordinary layout boxes. */
  radius?: number;
  borderRadius?: number;
  /** @deprecated Prefer backgroundColor. */
  background?: string | number;
  backgroundColor?: string | number;
  borderColor?: string | number;
  /** @deprecated Prefer borderColor. */
  strokeColor?: string | number;
  borderWidth?: number;
  /** @deprecated Prefer borderWidth. */
  strokeWidth?: number;

  /** @deprecated Prefer boxShadow. */
  shadow?: false | { color?: string | number; blur?: number; innerAlpha?: number };
  boxShadow?: false | { color?: string | number; blur?: number; innerAlpha?: number };
  shadowColor?: string | number;
  shadowBlur?: number;
  shadowInnerAlpha?: number;

  blur?: boolean;
  blurQuality?: number;
  blurBrightness?: number;
  blurAlpha?: number;
  liquidGlass?: boolean;
  clip?: boolean;
  marquee?: boolean;

  color?: string | number;
  /** @deprecated Prefer color. */
  textColor?: string | number;
  fontFamily?: string;
  fontWeight?: number | "normal" | "bold" | "semibold" | string;
  fontStyle?: "normal" | "italic";
  /** Preferred authored font size in logical UI units. 18 equals backend scale 1.0. */
  fontSize?: number;
  /** Optional line-box height in logical UI units. */
  lineHeight?: number;
  /** @deprecated Legacy multiplicative backend scale. Prefer fontSize. */
  fontScale?: number;
  /** @deprecated Legacy multiplicative backend scale. Prefer fontSize. */
  textScale?: number;
  textShadow?: boolean;
  textEffect?: string;
  textEffectSpeed?: number;
  textBackend?: string;
  maxTextWidth?: number;
  ellipsis?: boolean;
  /** Text wrapping mode. nowrap preserves legacy single-line behavior; pre-wrap preserves authored spaces and line breaks. */
  whiteSpace?: "nowrap" | "normal" | "pre-wrap";
  /** Long-token wrapping policy used when whiteSpace allows wrapping. */
  overflowWrap?: "normal" | "break-word" | "anywhere";
  /** Maximum rendered line count. 0/undefined means unlimited. */
  maxLines?: number;
  /** Overflow handling for constrained text. */
  textOverflow?: "clip" | "ellipsis";
  textAlign?: "left" | "center" | "right" | "end" | string;
  cursor?: string;
  blend?: string;
  /** Multiplies this node and its rendered subtree alpha. */
  opacity?: number;
};

/** Layout fields that can be placed inline on node init objects. */
export type UiLayoutProps = {
  /** Explicit width in logical UI units. */
  width?: number;
  /** Explicit height in logical UI units. */
  height?: number;
  /** Minimum resolved width. */
  minWidth?: number;
  /** Minimum resolved height. */
  minHeight?: number;
  /** Maximum resolved width. */
  maxWidth?: number;
  /** Maximum resolved height. */
  maxHeight?: number;
  /** @deprecated Prefer style.flexGrow (or flexGrow while using legacy promoted props). */
  grow?: number;
  /** Main-axis grow weight inside row/column layout. */
  flexGrow?: number;
  /** @deprecated Prefer style.flexShrink (or flexShrink while using legacy promoted props). */
  shrink?: number;
  /** Explicit main-axis shrink weight. */
  flexShrink?: number;
  /** Browser-style flow aliases promoted into inline style. */
  display?: "flex" | "block";
  flexDirection?: "row" | "column";
  /** Spacing fields use canonical logical UI units and are scaled once at paint time. */
  padding?: number;
  paddingX?: number;
  paddingHorizontal?: number;
  paddingY?: number;
  paddingVertical?: number;
  paddingLeft?: number;
  paddingTop?: number;
  paddingRight?: number;
  paddingBottom?: number;
  margin?: number;
  marginX?: number;
  marginHorizontal?: number;
  marginY?: number;
  marginVertical?: number;
  marginLeft?: number;
  marginTop?: number;
  marginRight?: number;
  marginBottom?: number;
  gap?: number;
  /** Browser-style position. Canonical values are absolute or relative; static/flow remain legacy aliases. */
  position?: "absolute" | "relative" | "static" | "flow";
  /** Removes the node from normal parent flow and uses x/y offsets. */
  absolute?: boolean;
  /** @deprecated Legacy absolute x alias. Prefer left. */
  x?: number;
  /** @deprecated Legacy absolute y alias. Prefer top. */
  y?: number;
  /** Canonical inset aliases. left/top override x/y when both are supplied. */
  left?: number;
  top?: number;
  right?: number;
  bottom?: number;
  /** Parent-controlled cross-axis alignment. left/right/top/bottom are web-style aliases. */
  align?: UiAlign | "left" | "right" | "top" | "bottom";
  alignItems?: UiAlign | "left" | "right" | "top" | "bottom";
  /** Parent-controlled main-axis placement. */
  justify?: UiJustify;
  justifyContent?: UiJustify | "left" | "right" | "top" | "bottom" | "space-between";
  /** Overflow and scroll behavior. */
  overflow?: UiOverflow;
};

/** Common asset fields copied into node props. */
export type UiAsset = {
  /** Asset kind. Alias: kind. */
  assetType?: UiAssetKind;
  /** Asset id, usually a Minecraft identifier string. Alias: id. */
  asset?: string;
  /** Asset id alias. */
  id?: string;
  /** Intrinsic width used by layout when explicit width is absent. */
  intrinsicWidth?: number;
  /** Intrinsic height used by layout when explicit height is absent. */
  intrinsicHeight?: number;
};

/** Plain object shape returned by script render functions. */
export type UiNode = {
  /** Runtime node kind. */
  type: UiNodeType;
  /** Stable reconciliation key. Use keys for dynamic lists. */
  key?: string;
  /** Utility token class string parsed by UiStyleParser. */
  class?: string;
  /** React-style alias merged with class. */
  className?: string;
  /** Sparse inline style applied after class tokens. */
  style?: UiInlineStyle;
  /** Convenience event aliases normalized into events. */
  onClick?: UiActionRef;
  onChange?: UiActionRef;
  onInput?: UiActionRef;
  onScroll?: UiActionRef;
  /** Raw data props visible to renderers, input, and asset providers. */
  props?: Record<string, unknown>;
  /** Event bindings by event name. */
  events?: UiEvents;
  /** Extra debug/tooling metadata. */
  meta?: Record<string, unknown>;
  /** Child nodes. */
  children?: UiNode[];
} & UiLayoutProps;


export type UiViewBox =
  | string
  | readonly [number, number, number, number]
  | { x?: number; y?: number; minX?: number; minY?: number; width: number; height: number };

/** Shared SVG/data coordinate container. Child vector primitives resolve against this viewBox. */
export type UiVectorNode = UiNode & {
  type: "vector";
  viewBox?: UiViewBox;
  viewBoxX?: number;
  viewBoxY?: number;
  viewBoxWidth?: number;
  viewBoxHeight?: number;
  /** SVG-like y-down by default; "up"/"cartesian" makes positive y point upward for charts. */
  yAxis?: "down" | "up" | "cartesian" | "math";
  coordinateSystem?: "down" | "up" | "cartesian" | "math";
  /** ViewBox fit mode. "meet" fits uniformly, "slice" covers uniformly, "none" stretches X/Y independently. */
  preserveAspectRatio?: string;
  viewBoxAlign?: string;
};

/** Text node shape. */
export type UiTextNode = Omit<UiNode, "align"> & {
  type: "text";
  /** Text content. */
  text?: string;
  /** ViewBox-space text position inside ui.vector/ui.plot. Explicit by design: top-level x/y remain legacy layout aliases. */
  vectorX?: number;
  vectorY?: number;
  /** Horizontal anchor for vector-positioned text. */
  textAnchor?: "start" | "middle" | "center" | "end" | "right";
  /** Vertical anchor for vector-positioned text. */
  verticalAnchor?: "top" | "middle" | "center" | "bottom" | "end";
  coordinateSpace?: "viewBox" | "local" | "bounds";
  /** Convenience alias promoted into style.fontSize. Uses logical UI units. */
  fontSize?: number;
  /** Convenience alias promoted into style.lineHeight. */
  lineHeight?: number;
  /** Convenience alias promoted into style.fontFamily. */
  fontFamily?: string;
  /** Convenience alias promoted into style.fontWeight. */
  fontWeight?: UiInlineStyle["fontWeight"];
  /** Convenience alias promoted into style.fontStyle. */
  fontStyle?: UiInlineStyle["fontStyle"];
  /** Maximum rendered text width in logical UI units. Layout maxWidth remains available through style/maxWidth. */
  maxTextWidth?: number;
  /** Legacy text-alignment alias accepted by normalize(). */
  align?: "left" | "center" | "right" | "end";
  /** Text alignment inside the node bounds. */
  textAlign?: "left" | "center" | "right" | "end";
  /** Enables text shortening when maxTextWidth is set. */
  ellipsis?: boolean;
  whiteSpace?: UiInlineStyle["whiteSpace"];
  overflowWrap?: UiInlineStyle["overflowWrap"];
  maxLines?: number;
  textOverflow?: UiInlineStyle["textOverflow"];
  /** Enables clipping mode for scrolling/fading text behavior. */
  marquee?: boolean;
  /** Color of the compact phosphor bloom rendered behind the sharp glyphs. */
  textGlowColor?: string | number;
  /** Bloom spread in logical pixels; intended range is 1.5 to 2.5. */
  textGlowWidth?: number;
  /** Bloom strength in the 0..1 range. */
  textGlowStrength?: number;
};

/** Image-like node shape. */
export type UiImageNode = UiNode & UiAsset & {
  type: "image" | "svg";
  /** Image fit mode. */
  fit?: "contain" | "cover" | "stretch";
  /** Optional tint value consumed by renderer bridges. */
  tint?: string;
};


export type UiCornerKind = "square" | "rounded" | "chamfered" | "concave" | "notched" | "custom";

export type UiCornerSpec =
  | UiCornerKind
  | number
  | {
      kind?: UiCornerKind;
      type?: UiCornerKind;
      radius?: number;
      radiusX?: number;
      radiusY?: number;
      cut?: number;
      chamfer?: number;
      cutX?: number;
      cutY?: number;
    };

export type UiCornerMap = {
  tl?: UiCornerSpec;
  tr?: UiCornerSpec;
  br?: UiCornerSpec;
  bl?: UiCornerSpec;
  topLeft?: UiCornerSpec;
  topRight?: UiCornerSpec;
  bottomRight?: UiCornerSpec;
  bottomLeft?: UiCornerSpec;
};

export type UiEdgeKind = "straight" | "notch" | "notched" | "inset" | "cut" | "custom";

export type UiEdgeSpec =
  | UiEdgeKind
  | {
      kind?: UiEdgeKind;
      type?: UiEdgeKind;
      width?: number;
      size?: number;
      depth?: number;
      offset?: number | "center";
    };

export type UiEdgeMap = {
  top?: UiEdgeSpec;
  right?: UiEdgeSpec;
  bottom?: UiEdgeSpec;
  left?: UiEdgeSpec;
};

export type UiCompoundCircleSource = {
  /** Local center x inside the shape node. */
  x: number;
  /** Local center y inside the shape node. */
  y: number;
  radius?: number;
  r?: number;
};

export type UiCompoundBoxSource = {
  /** Local box origin inside the shape node. */
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  w?: number;
  h?: number;
};

/** Code-drawn rectangle/implicit primitive. */
export type UiShapeNode = UiNode & {
  type: "shape";
  shape?:
    | "rect"
    | "quad"
    | "gradient"
    | "rect-gradient"
    | "quad-gradient"
    | "rounded"
    | "rounded-rect"
    | "rounded-gradient"
    | "rounded-gradient-quad"
    | "rounded-stroke-gradient"
    | "rounded-soft-shadow"
    | "rounded-shadow"
    | "rounded-glow"
    | "radial-glow-masked"
    | "rounded-rect-gradient"
    | "rounded-corners"
    | "rounded-rect-corners"
    | "chamfered"
    | "beveled"
    | "cut-corner"
    | "notched"
    | "circle"
    | "circle-stroke"
    | "circle-soft-shadow"
    | "ring"
    | "arc"
    | "arc-stroke"
    | "arc-flat"
    | "arc-gradient"
    | "box"
    | "mixed"
    | "flex"
    | "flex-box"
    | "primitive"
    | "island-blob"
    | "island_blob"
    | "metaball"
    | "metaballs"
    | "smooth-box-union"
    | "smooth_box_union"
    | "compound-sdf"
    | "compound_sdf"
    | "chamfered";
  /** Optional viewBox-space rectangle geometry when this shape is inside ui.vector/ui.plot. */
  vectorX?: number;
  vectorY?: number;
  vectorWidth?: number;
  vectorHeight?: number;
  coordinateSpace?: "viewBox" | "local" | "bounds";
  /** Local center x for circle and arc shapes. Inside a vector container this is a viewBox coordinate. */
  cx?: number;
  /** Local center y for circle and arc shapes. Inside a vector container this is a viewBox coordinate. */
  cy?: number;
  /** Radius for rounded rectangles, circles, and arcs. */
  radius?: number;
  /** Top-left radius for rounded-corners shapes. */
  radiusTL?: number;
  /** Top-right radius for rounded-corners shapes. */
  radiusTR?: number;
  /** Bottom-right radius for rounded-corners shapes. */
  radiusBR?: number;
  /** Bottom-left radius for rounded-corners shapes. */
  radiusBL?: number;
  /** Stroke thickness for circle-stroke/ring and arc shapes. */
  thickness?: number;
  /** Arc start angle in degrees. */
  startAngle?: number;
  /** Arc end angle in degrees. */
  endAngle?: number;
  /** Linear gradient start color for gradient shapes and arc-gradient. */
  startColor?: string | number;
  /** Linear gradient end color for gradient shapes and arc-gradient. */
  endColor?: string | number;
  /** Linear gradient angle in degrees. 0 is left-to-right, 90 is top-to-bottom. */
  angle?: number;
  /** Linear gradient offset in pixels. */
  offset?: number;
  /** Explicit per-corner fill color. Overrides start/end interpolation for rect and rounded gradients. */
  topLeftColor?: string | number;
  topRightColor?: string | number;
  bottomRightColor?: string | number;
  bottomLeftColor?: string | number;
  cTopLeft?: string | number;
  cTopRight?: string | number;
  cBottomRight?: string | number;
  cBottomLeft?: string | number;
  /** Linear stroke gradient. Falls back to startColor/endColor when omitted. */
  strokeStartColor?: string | number;
  strokeEndColor?: string | number;
  strokeAngle?: number;
  strokeOffset?: number;
  strokeTopLeftColor?: string | number;
  strokeTopRightColor?: string | number;
  strokeBottomRightColor?: string | number;
  strokeBottomLeftColor?: string | number;
  /** Flexible per-corner geometry. Allows mixed rounded/chamfered/square corners. */
  corners?: UiCornerMap;
  cornerTL?: UiCornerSpec;
  cornerTR?: UiCornerSpec;
  cornerBR?: UiCornerSpec;
  cornerBL?: UiCornerSpec;
  cornerTopLeft?: UiCornerSpec;
  cornerTopRight?: UiCornerSpec;
  cornerBottomRight?: UiCornerSpec;
  cornerBottomLeft?: UiCornerSpec;
  /** Flexible per-edge modifiers. */
  edges?: UiEdgeMap;
  edgeTop?: UiEdgeSpec;
  edgeRight?: UiEdgeSpec;
  edgeBottom?: UiEdgeSpec;
  edgeLeft?: UiEdgeSpec;
  cut?: number;
  chamfer?: number;
  bevel?: number;
  notchWidth?: number;
  notchDepth?: number;
  /** Primary shape fill color. */
  fill?: string | number;
  stroke?: string | number;
  strokeWidth?: number;
  softness?: number;
  spread?: number;
  glow?: number;
  glowRadius?: number;
  blur?: number;
  innerAlpha?: number;
  /** Smooth-min radius used by IslandBlob and smooth-box-union. */
  smoothing?: number;
  smoothness?: number;
  /** Up to four circle sources for IslandBlob/metaball shapes. Coordinates are local to the node. */
  sources?: UiCompoundCircleSource[];
  /** First/second local rounded boxes for smooth-box-union. */
  first?: UiCompoundBoxSource;
  second?: UiCompoundBoxSource;
  firstRadius?: number;
  secondRadius?: number;
  /** Default two-circle IslandBlob separation when sources are omitted. */
  separation?: number;
  /** Enables the existing liquid-glass material on compatible analytic primitives. */
  liquidGlass?: boolean;
  glassPreset?: "light" | "balanced" | "heavy" | "hud-small" | "hud-large" | "health" | string;
  glassTint?: string | number;
  glassAlpha?: number;
  blurAlpha?: number;
  /** Enables a stable per-surface frosted refraction jitter. */
  glassFrosted?: boolean;
  /** Frosted jitter amplitude in logical pixels, clamped to 0..4. */
  glassFrostedJitter?: number;
  /** Inner-glow strength, clamped to 0..1. */
  glassInnerGlow?: number;
  /** Inner-glow falloff distance in logical pixels. */
  glassInnerGlowSize?: number;
  /** Inner-glow ARGB or #RRGGBB/#AARRGGBB color. */
  glassInnerGlowColor?: string | number;
  color?: string | number;
  highlight?: string | number;
  shadow?: string | number;
};

/** Code-drawn connector primitive. Coordinates are local to node bounds. */
export type UiConnectorNode = UiNode & {
  type: "connector";
  connector?:
    | "line"
    | "cable"
    | "bezier"
    | "orthogonal"
    | "node-edge"
    | "spline"
    | "spline-area"
    | "rounded-edge"
    | "rounded-node-edge"
    | "rounded-orthogonal";
  x1?: number;
  y1?: number;
  x2?: number;
  y2?: number;
  cx1?: number;
  cy1?: number;
  cx2?: number;
  cy2?: number;
  /** Source rounded rectangle x coordinate, local to connector node bounds. */
  sourceX?: number;
  /** Source rounded rectangle y coordinate, local to connector node bounds. */
  sourceY?: number;
  /** Source rounded rectangle width. Alias: sourceW. */
  sourceWidth?: number;
  /** Source rounded rectangle width alias. */
  sourceW?: number;
  /** Source rounded rectangle height. Alias: sourceH. */
  sourceHeight?: number;
  /** Source rounded rectangle height alias. */
  sourceH?: number;
  /** Source rounded rectangle radius used to resolve the border anchor. */
  sourceRadius?: number;
  /** Target rounded rectangle x coordinate, local to connector node bounds. */
  targetX?: number;
  /** Target rounded rectangle y coordinate, local to connector node bounds. */
  targetY?: number;
  /** Target rounded rectangle width. Alias: targetW. */
  targetWidth?: number;
  /** Target rounded rectangle width alias. */
  targetW?: number;
  /** Target rounded rectangle height. Alias: targetH. */
  targetHeight?: number;
  /** Target rounded rectangle height alias. */
  targetH?: number;
  /** Target rounded rectangle radius used to resolve the border anchor. */
  targetRadius?: number;
  /** Local x coordinate for orthogonal connector middle column. */
  midX?: number;
  points?: Array<{ x: number; y: number }> | number[];
  /** Local y coordinate used as the bottom edge of a spline area fill. Defaults to node height. */
  baseline?: number;
  fillStartColor?: string | number;
  fillEndColor?: string | number;
  fillBottomStartColor?: string | number;
  fillBottomEndColor?: string | number;
  stroke?: string | number;
  strokeWidth?: number;
  /** Path gradient start/end. Connectors use path progress; rounded anchors are handled before gradient draw. */
  startColor?: string | number;
  endColor?: string | number;
  strokeStartColor?: string | number;
  strokeEndColor?: string | number;
  outerStartColor?: string | number;
  outerEndColor?: string | number;
  innerStartColor?: string | number;
  innerEndColor?: string | number;
  outer?: string | number;
  inner?: string | number;
  segments?: number;
  closed?: boolean;
};

/** Generic vector path/series primitive. Inside ui.vector/ui.plot, coordinates resolve in the shared viewBox. */
export type UiPathNode = UiNode & {
  type: "path";
  /** Explicit local points. Flat [x,y,...] and object point arrays are both accepted. */
  points?: Array<{ x: number; y: number }> | number[];
  /** Numeric series mapped across this node's bounds. Useful for charts/sparklines. */
  values?: Array<number | { x?: number; value?: number; y?: number }>;
  /** Data-space alias for values. Points with x use a proportional x-axis instead of equal slots. */
  data?: Array<number | { x?: number; value?: number; y?: number }>;
  /** Treat explicit points as normalized 0..1 coordinates inside node bounds. This opts out of parent viewBox coordinates. */
  normalized?: boolean;
  /** Force path coordinates back to local node bounds even inside a vector container. */
  coordinateSpace?: "viewBox" | "local" | "bounds";
  curve?: "linear" | "spline" | "smooth";
  closed?: boolean;
  /** Backward-compatible y-domain aliases. Omitted bounds are inferred from observed data. */
  domainMin?: number;
  domainMax?: number;
  /** Explicit data-space domains for irregular line/area charts. */
  xDomainMin?: number;
  xDomainMax?: number;
  yDomainMin?: number;
  yDomainMax?: number;
  /** Virtual x slots. If larger than values.length, the series is right-aligned. */
  historySlots?: number;
  clampValues?: boolean;
  /** Enables area-to-baseline fill. */
  area?: boolean;
  /** Local-pixel baseline override. */
  baseline?: number;
  /** Data-space baseline; defaults to zero and clamps to the visible y-domain when clampValues is enabled. */
  baselineValue?: number;
  fill?: string | number;
  fillStartColor?: string | number;
  fillEndColor?: string | number;
  fillBottomColor?: string | number;
  fillBottomStartColor?: string | number;
  fillBottomEndColor?: string | number;
  stroke?: string | number;
  strokeStartColor?: string | number;
  strokeEndColor?: string | number;
  strokeWidth?: number;
  glow?: string | number;
  glowStartColor?: string | number;
  glowEndColor?: string | number;
  glowWidth?: number;
  strokeLinecap?: "butt" | "round" | "square";
  strokeLinejoin?: "miter" | "round" | "bevel";
};

/**
 * Authoring-side child input. Nested iterables are flattened, booleans/null/undefined are dropped,
 * and string/number children become ordinary text nodes.
 */
export type UiChildInput = UiNode | string | number | readonly UiChildInput[] | Iterable<UiChildInput> | boolean | null | undefined;

/** Fragment token accepted by ui.h/ui.createElement. */
export type UiFragment = Readonly<{ __combatantUiFragment: true }>;
/** Lightweight function component. Components return the same declarative nodes as render(ctx). */
export type UiComponent<P = Record<string, unknown>> = (props: P & { children?: UiNode[] }) => UiChildInput;
export type UiElementType<P = Record<string, unknown>> = UiNodeType | UiComponent<P> | UiFragment;
/** Root render output. A fragment/iterable is wrapped by the runtime in a transparent ROOT node. */
export type UiRenderOutput = UiNode | readonly UiChildInput[] | (Iterable<UiChildInput> & object);

export type NodeInit = Omit<UiNode, "type" | "children"> & { children?: UiChildInput };
export type TextInit = (Omit<UiTextNode, "type" | "children"> & { children?: UiChildInput }) | string;
export type ImageInit = (Omit<UiImageNode, "type" | "children"> & { children?: UiChildInput }) | string;
export type ShapeInit = Omit<UiShapeNode, "type" | "children"> & { children?: UiChildInput };
export type VectorInit = Omit<UiVectorNode, "type" | "children"> & { children?: UiChildInput };
export type PlotInit = Omit<VectorInit, "viewBox" | "yAxis" | "coordinateSystem"> & {
  viewBox?: UiViewBox;
  xDomain?: readonly [number, number];
  yDomain?: readonly [number, number];
};
export type PathInit = Omit<UiPathNode, "type" | "children"> & { children?: UiChildInput };
export type LineInit = PathInit & { x1?: number; y1?: number; x2?: number; y2?: number };

/** Host snapshot used by compact HUD stat widgets before Java rendering is replaced. */
export type CompactHudStatProps = {
  [key: string]: unknown;
  id: string;
  visible: boolean;
  rootX: number;
  rootY: number;
  width: number;
  height: number;
  radius: number;
  scale: number;
  backgroundEffect: "None" | "Blur" | "Glass" | string;
  backgroundTheme: boolean;
  blurAlpha: number;
  bgPrimary: string;
  bgSecondary: string;
  stroke: string;
  strokeWidth: number;
  softness: number;
  iconVisible: boolean;
  iconKind: "texture" | "glyph" | string;
  iconId: string;
  iconGlyph: string;
  iconFont: string;
  iconX: number;
  iconY: number;
  iconW: number;
  iconH: number;
  iconFontSize: number;
  iconColor: string;
  dividerVisible: boolean;
  dividerX: number;
  dividerY: number;
  dividerW: number;
  dividerH: number;
  dividerColor: string;
  valueVisible: boolean;
  valueText: string;
  valueFont: string;
  valueFontSize: number;
  valueX: number;
  valueY: number;
  valueW: number;
  valueColor: string;
  unitVisible: boolean;
  unitText: string;
  unitFont: string;
  unitFontSize: number;
  unitX: number;
  unitY: number;
  unitW: number;
  unitColor: string;
  extraVisible: boolean;
  extraText: string;
  extraFont: string;
  extraFontSize: number;
  extraX: number;
  extraY: number;
  extraW: number;
  extraColor: string;
  labelEffect: "NONE" | "MIX" | "FLOW" | "PULSE" | "STRIPE" | string;
  labelEffectSpeed: number;
  effectTime: number;
  digitAnimation: boolean;
  previousValue: string;
  digitProgress: number;
  digitOffset: number;
};

/** Factory available to JS/TS authors; the runtime loads this API from the canonical ui.js resource. */
export interface UiFactory {
  /** Canonical runtime contract; mirrors ui.contract.json. */
  readonly contract: UiAuthoringContract;
  /** React-like element construction without a second runtime: everything normalizes to UiNode. */
  h<P = Record<string, unknown>>(type: UiElementType<P>, init?: P | null, ...children: UiChildInput[]): UiChildInput;
  createElement<P = Record<string, unknown>>(type: UiElementType<P>, init?: P | null, ...children: UiChildInput[]): UiChildInput;
  /** Fragment token for ui.h/ui.createElement. */
  readonly Fragment: UiFragment;
  /** Flattens children and returns a fragment array. */
  fragment(...children: UiChildInput[]): UiNode[];
  /** Returns child when condition is truthy, otherwise null. */
  when(condition: unknown, child: UiChildInput): UiChildInput;
  node(type: UiNodeType, init?: NodeInit, ...children: UiChildInput[]): UiNode;
  root(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  panel(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  row(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  column(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  stack(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  vector(init?: VectorInit, ...children: UiChildInput[]): UiVectorNode;
  plot(init?: PlotInit, ...children: UiChildInput[]): UiVectorNode;
  /** Text can be authored as ui.text("hello") or ui.text({ style: ... }, "hello"). */
  text(init?: TextInit | number, ...children: Array<string | number | boolean | null | undefined>): UiTextNode;
  image(init: ImageInit): UiImageNode;
  svg(init: ImageInit): UiImageNode;
  shape(init?: ShapeInit): UiShapeNode;
  box(init?: ShapeInit): UiShapeNode;
  /** ViewBox/data-space rectangle. x/y/width/height are geometry, never layout; use style.left/top for layout. */
  rect(init?: ShapeInit): UiShapeNode;
  circle(init?: ShapeInit): UiShapeNode;
  /** Chart/scatter point. x/y are viewBox coordinates inside ui.vector/ui.plot; radius is a logical UI size. */
  point(init?: ShapeInit & { x?: number; y?: number; r?: number; radius?: number }): UiShapeNode;
  /** ViewBox/data-space rectangle helper for bars and ranges. */
  bar(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; width?: number; height?: number }): UiShapeNode;
  /** ViewBox/data-space rounded rectangle. x/y/width/height are geometry, never layout. */
  rounded(init?: ShapeInit & { radius?: number; r?: number }): UiShapeNode;
  chamfered(init?: ShapeInit & { cut?: number; chamfer?: number }): UiShapeNode;
  squircle(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; profile?: "soft" | "standard" | "tight"; power?: number; exponent?: number }): UiShapeNode;
  /** Absolute-layout rounded surface helper. x/y/w/h are logical UI layout coordinates; prefer style for new reusable components. */
  roundedRect(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedGradient(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedGradientQuad(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedStrokeGradient(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedSoftShadow(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedShadow(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  roundedGlow(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  radialGlow(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number }): UiShapeNode;
  circleSoftShadow(init?: ShapeInit & { x?: number; y?: number; size?: number; radius?: number }): UiShapeNode;
  blurSurface(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; radius?: number; r?: number; alpha?: number; brightness?: number }): UiShapeNode;
  clip(init?: NodeInit & { x?: number; y?: number; w?: number; h?: number }): UiNode;
  clippedText(init?: NodeInit & { text?: string; x?: number; y?: number; w?: number; h?: number; textClass?: string; color?: string | number; measuredWidth?: number; fadeWidth?: number; scrollTime?: number; scrollDelay?: number; scrollSpeed?: number; fade?: boolean; centerWhenFits?: boolean }): UiNode;
  num(value: unknown, fallback?: number): number;
  str(value: unknown, fallback?: string): string;
  bool(value: unknown, fallback?: boolean): boolean;
  clamp(value: unknown, min?: number, max?: number): number;
  fmt(value: number, digits?: number, fallback?: string): string;
  cls(...parts: Array<string | false | null | undefined>): string;
  style(...parts: Array<UiInlineStyle | false | null | undefined>): UiInlineStyle;
  children(...parts: UiChildInput[]): UiNode[];
  absolute(x?: number, y?: number, w?: number, h?: number, extra?: UiInlineStyle): UiInlineStyle;
  /** Absolute inset helper using canonical left/top/right/bottom style names. */
  inset(init?: { left?: number; top?: number; right?: number; bottom?: number; width?: number; height?: number } & UiInlineStyle): UiInlineStyle;
  /** @deprecated Utility-class positioning is legacy-only. Prefer style: ui.absolute(...). */
  abs(x?: number, y?: number, w?: number, h?: number, extra?: string): string;
  /** @deprecated ctx.props is deep-converted to plain JS objects; use ordinary property access. */
  prop<T = unknown>(value: unknown, key: string, fallback?: T): T | unknown;
  /** @deprecated ctx.props collections are deep-converted to plain JS arrays. */
  arr<T = unknown>(value: unknown): T[];
  color: {
    /** Reads a string color from a plain object or host map-like value. */
    get(value: unknown, key: string, fallback?: string): string;
    alpha(hex: string, alpha?: number, fallback?: string): string;
    opacity(hex: string, fallback?: number): number;
  };
  connector(init?: Omit<UiConnectorNode, "type">): UiConnectorNode;
  path(init?: PathInit): UiPathNode;
  line(init?: LineInit): UiPathNode;
  polyline(init?: PathInit): UiPathNode;
  spline(init?: PathInit): UiPathNode;
  area(init?: PathInit): UiPathNode;
  item(init?: NodeInit & { /** Multiplies Renderer2D item alpha for this node. */ alpha?: number }): UiNode;
  button(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  scroll(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  spacer(init?: NodeInit): UiNode;
  inputText(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  checkbox(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  slider(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  divider(init?: NodeInit): UiNode;
  canvas(init?: NodeInit, ...children: UiChildInput[]): UiNode;
  corner: {
    square(): UiCornerSpec;
    rounded(radius?: number, radiusY?: number): UiCornerSpec;
    chamfered(cut?: number, cutY?: number): UiCornerSpec;
    concave(radius?: number): UiCornerSpec;
    all(corner: UiCornerSpec): UiCornerMap;
    mixed(tl: UiCornerSpec, tr?: UiCornerSpec, br?: UiCornerSpec, bl?: UiCornerSpec): UiCornerMap;
  };
  edge: {
    straight(): UiEdgeSpec;
    notch(width?: number, depth?: number, offset?: number | "center"): UiEdgeSpec;
    inset(depth?: number): UiEdgeSpec;
  };
}

export declare const ui: UiFactory;

/** Data passed to script render(ctx). */
export type UiRenderContext = {
  /** Host frame counter. */
  frame: number;
  /** Host time value in seconds. */
  time: number;
  /** Delta time in seconds. */
  delta: number;
  /** Host surface width in logical UI units. */
  width: number;
  /** Host surface height in logical UI units. */
  height: number;
  /** Host-provided snapshot props, recursively converted to ordinary JS objects/arrays. */
  props: Record<string, unknown>;
};

/** Module shape accepted by the script runtime. */
export type UiComponentModule = {
  /** Optional metadata for tooling. */
  meta?: Record<string, unknown>;
  /** Produces one root node or a fragment/iterable of nodes. */
  render(ctx: UiRenderContext): UiRenderOutput;
};
