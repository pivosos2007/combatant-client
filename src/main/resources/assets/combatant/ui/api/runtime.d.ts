/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import type { UiNode } from "./ui";

/** Data passed to script render(ctx). */
export type UiScriptRenderContext<P extends Record<string, unknown> = Record<string, unknown>> = {
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
  props: P;
};

/** Script module/component shape accepted by the runtime. */
export type UiScriptComponent<P extends Record<string, unknown> = Record<string, unknown>> = {
  /** Optional metadata for tooling. */
  meta?: Record<string, unknown>;
  /** Produces a root UI node object. */
  render(ctx: UiScriptRenderContext<P>): UiNode;
  /** Optional cleanup hook for hosts that keep component instances. */
  dispose?(): void;
};
