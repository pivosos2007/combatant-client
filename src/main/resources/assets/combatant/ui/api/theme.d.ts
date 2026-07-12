/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** Theme color token or direct color string. */
export type UiColor = `#${string}` | string;

/** Text effect name consumed by UiTextEffectRenderer. */
export type UiTextEffect = "none" | "flow" | "pulse" | "stripe" | string;

/** Utility-token class string parsed by UiStyleParser. */
export type UiStyleClass = string;
