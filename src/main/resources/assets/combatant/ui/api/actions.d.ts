/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** String reference dispatched through UiActionRegistry. */
export type UiActionRef = string;

/** Event name stored in UiNode.events. */
export type UiActionEvent =
  | "click"
  | "change"
  | "input"
  | "scroll"
  | "focus"
  | "blur"
  | string;

/** Convenience map for node event bindings. */
export type UiActionMap = Partial<Record<UiActionEvent, UiActionRef>>;
