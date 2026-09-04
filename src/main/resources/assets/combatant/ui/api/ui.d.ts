/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** Node kinds understood by the runtime layout/render/input layers. */
export type UiNodeType =
  | "root"
  | "panel"
  | "row"
  | "column"
  | "stack"
  | "text"
  | "image"
  | "svg"
  | "shape"
  | "connector"
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

/** Event map stored on a node spec. Function callbacks are authoring-side only. */
export type UiEvents = {
  /** Fired by pointer release on the same pressed node. */
  click?: UiActionRef | (() => void);
  /** Value change event for host-backed controls. */
  change?: UiActionRef | ((value: unknown) => void);
  /** Text/input event for host-backed controls. */
  input?: UiActionRef | ((value: string) => void);
  /** Scroll event for scroll containers or host-backed controls. */
  scroll?: UiActionRef | ((amount: number) => void);
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

/** Layout fields that can be placed inline on node init objects. */
export type UiLayoutProps = {
  /** Explicit width in UI pixels. */
  width?: number;
  /** Explicit height in UI pixels. */
  height?: number;
  /** Minimum resolved width. */
  minWidth?: number;
  /** Minimum resolved height. */
  minHeight?: number;
  /** Maximum resolved width. */
  maxWidth?: number;
  /** Maximum resolved height. */
  maxHeight?: number;
  /** Main-axis grow weight inside row/column layout. */
  grow?: number;
  /** Removes the node from normal parent flow and uses x/y offsets. */
  absolute?: boolean;
  /** Absolute x offset inside parent content area. */
  x?: number;
  /** Absolute y offset inside parent content area. */
  y?: number;
  /** Parent-controlled cross-axis alignment. */
  align?: UiAlign;
  /** Parent-controlled main-axis placement. */
  justify?: UiJustify;
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
  /** Raw data props visible to renderers, input, and asset providers. */
  props?: Record<string, unknown>;
  /** Event bindings by event name. */
  events?: UiEvents;
  /** Extra debug/tooling metadata. */
  meta?: Record<string, unknown>;
  /** Child nodes. */
  children?: UiNode[];
} & UiLayoutProps;

/** Text node shape. */
export type UiTextNode = UiNode & {
  type: "text";
  /** Text content. */
  text?: string;
  /** Maximum text width in UI pixels. */
  maxWidth?: number;
  /** Text alignment inside the node bounds. */
  align?: "left" | "center" | "right";
  /** Enables text shortening when maxWidth is set. */
  ellipsis?: boolean;
  /** Enables clipping mode for scrolling/fading text behavior. */
  marquee?: boolean;
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

/** Code-drawn rectangle primitive. */
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
    | "chamfered";
  /** Local center x for circle and arc shapes. Defaults to half node width. */
  cx?: number;
  /** Local center y for circle and arc shapes. Defaults to half node height. */
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
  fill?: string | number;
  stroke?: string | number;
  strokeWidth?: number;
  softness?: number;
  spread?: number;
  glow?: number;
  glowRadius?: number;
  blur?: number;
  innerAlpha?: number;
  color?: string | number;
  highlight?: string | number;
  shadow?: string | number;
};

/** Code-drawn connector primitive. Coordinates are local to node bounds. */
export type UiConnectorNode = UiNode & {
  type: "connector";
  connector?:
    | "line"
    | "wire"
    | "cable"
    | "bezier"
    | "orthogonal"
    | "node-edge"
    | "spline"
    | "wave"
    | "waveform"
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
  /** Local center line for analytic wave/waveform connectors. */
  centerY?: number;
  /** Analytic wave amplitude in logical pixels. */
  amplitude?: number;
  /** Wave phase in radians. */
  phase?: number;
  /** Wave wavelength in logical pixels. */
  wavelength?: number;
  /** Secondary harmonic strength [0..0.45]. */
  harmonic?: number;
  /** Endpoint settling distance in logical pixels. */
  edgeFade?: number;
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

export type NodeInit = Omit<UiNode, "type">;
export type TextInit = Omit<UiTextNode, "type"> | string;
export type ImageInit = Omit<UiImageNode, "type"> | string;
export type ShapeInit = Omit<UiShapeNode, "type">;

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
  iconScale: number;
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
  valueScale: number;
  valueX: number;
  valueY: number;
  valueW: number;
  valueColor: string;
  unitVisible: boolean;
  unitText: string;
  unitFont: string;
  unitScale: number;
  unitX: number;
  unitY: number;
  unitW: number;
  unitColor: string;
  extraVisible: boolean;
  extraText: string;
  extraFont: string;
  extraScale: number;
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

/** Factory available to JS/TS authors and mirrored inside the embedded UI script runtime. */
export interface UiFactory {
  node(type: UiNodeType, init?: NodeInit): UiNode;
  root(init?: NodeInit): UiNode;
  panel(init?: NodeInit): UiNode;
  row(init?: NodeInit): UiNode;
  column(init?: NodeInit): UiNode;
  stack(init?: NodeInit): UiNode;
  text(init: TextInit): UiTextNode;
  image(init: ImageInit): UiImageNode;
  svg(init: ImageInit): UiImageNode;
  shape(init?: ShapeInit): UiShapeNode;
  box(init?: ShapeInit): UiShapeNode;
  rounded(init?: ShapeInit & { radius?: number; r?: number }): UiShapeNode;
  chamfered(init?: ShapeInit & { cut?: number; chamfer?: number }): UiShapeNode;
  squircle(init?: ShapeInit & { x?: number; y?: number; w?: number; h?: number; profile?: "soft" | "standard" | "tight"; power?: number; exponent?: number }): UiShapeNode;
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
  abs(x?: number, y?: number, w?: number, h?: number, extra?: string): string;
  /** Safe property lookup supporting plain objects and host map-like values. */
  prop<T = unknown>(value: unknown, key: string, fallback?: T): T | unknown;
  /** Converts arrays/iterables/host collections to a plain JS array, or [] on failure. */
  arr<T = unknown>(value: unknown): T[];
  color: {
    /** Reads a string color from a plain object or host map-like value. */
    get(value: unknown, key: string, fallback?: string): string;
    alpha(hex: string, alpha?: number, fallback?: string): string;
    opacity(hex: string, fallback?: number): number;
  };
  connector(init?: Omit<UiConnectorNode, "type">): UiConnectorNode;
  item(init?: NodeInit): UiNode;
  button(init?: NodeInit): UiNode;
  scroll(init?: NodeInit): UiNode;
  spacer(init?: NodeInit): UiNode;
  inputText(init?: NodeInit): UiNode;
  checkbox(init?: NodeInit): UiNode;
  slider(init?: NodeInit): UiNode;
  divider(init?: NodeInit): UiNode;
  canvas(init?: NodeInit): UiNode;
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
  /** Host surface width in UI pixels. */
  width: number;
  /** Host surface height in UI pixels. */
  height: number;
  /** Host-provided snapshot props. */
  props: Record<string, unknown>;
};

/** Module shape accepted by the script runtime. */
export type UiComponentModule = {
  /** Optional metadata for tooling. */
  meta?: Record<string, unknown>;
  /** Produces a root UI node object. */
  render(ctx: UiRenderContext): UiNode;
};
