/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** Asset kind consumed by UiAssetResolver and optional dynamic providers. */
export type UiAssetKind =
  | "texture"
  | "gui-sprite"
  | "svg"
  | "player-head"
  | "media-artwork"
  | "item"
  | string;

/** Asset fields copied into node props and resolved by UiAssetResolver. */
export type UiAssetRef = {
  /** Asset kind. Alias: kind. */
  assetType?: UiAssetKind;
  /** Asset kind alias. */
  kind?: UiAssetKind;
  /** Asset id, usually a Minecraft identifier string. Alias: id. */
  asset?: string;
  /** Asset id alias. */
  id?: string;
  /** Intrinsic width used when explicit layout width is absent. */
  intrinsicWidth?: number;
  /** Intrinsic height used when explicit layout height is absent. */
  intrinsicHeight?: number;
};
