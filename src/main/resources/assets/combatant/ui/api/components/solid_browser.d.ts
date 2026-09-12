/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import type { UiActionRef, UiEvents, UiNode } from "../ui.js";

export type SolidSlot = UiNode | UiNode[] | null | undefined;

export type SolidBrowserTokens = {
  designWidth: number;
  designHeight: number;
  minWidth: number;
  minHeight: number;
  screenInset: number;

  navDesignWidth: number;
  collectionDesignWidth: number;
  headerDesignHeight: number;

  rootRadius: number;
  rootCornerSmoothness: number;
  rootStrokeWidth: number;
  rootBlurQuality: number;
  rootBlurBrightness: number;
  rootBlurAlpha: number;

  separatorWidth: number;
  separatorInset: number;

  navLogoSize: number;
  navLogoY: number;
  navStackGap: number;
  navHeaderToStackGap: number;
  navFooterBottomInset: number;
  navItemSize: number;
  navItemPadding: number;
  navIconSize: number;
  navItemRadius: number;

  collectionHeaderTop: number;
  collectionHeaderRight: number;
  collectionHeaderBottom: number;
  collectionHeaderLeft: number;
  collectionTitleFontSize: number;
  collectionTopInset: number;
  collectionRightInset: number;
  collectionBottomInset: number;
  collectionLeftInset: number;
  collectionRowHeight: number;
  collectionRowGap: number;
  collectionRowHorizontalInset: number;
  collectionRowRadius: number;
  collectionRowFontSize: number;

  toolbarLeftInset: number;
  toolbarRightInset: number;
  toolbarProfileWidth: number;
  searchWidth: number;
  searchHeight: number;
  searchRadius: number;
  searchIconInset: number;
  searchIconSize: number;
  searchTextInsetLeft: number;
  searchTextInsetRight: number;

  detailHorizontalInset: number;
  detailTopInset: number;
  detailBottomInset: number;
  detailHeaderHeight: number;
  detailHeaderGap: number;
  detailTitleHeight: number;
  detailTitleFontSize: number;
  detailDescriptionFontSize: number;
  detailContentTopInset: number;
  detailContentRightInset: number;
  detailColumnGap: number;
  detailGroupGap: number;
  detailMinViewportHeight: number;
  detailCloseSize: number;
  detailClosePadding: number;
  detailCloseIconSize: number;
  detailCloseRadius: number;
  detailBackdropRadius: number;
  detailBackdropOffset: number;
  detailBackdropBrightness: number;
  detailBackdropClipInset: number;
  detailBackdropExtraBottom: number;

  cardInset: number;
  cardRadius: number;
  shortcutWidth: number;
  shortcutHeight: number;
  shortcutLeftInset: number;
  shortcutRightInset: number;
  shortcutRadius: number;
  shortcutIconSize: number;
  shortcutFontSize: number;
  shortcutGap: number;
  emptyFontSize: number;

  scrollbarWidth: number;
  scrollbarMinThumb: number;
  scrollbarLeadingInset: number;
  scrollbarTrailingInset: number;
  scrollbarOffset: number;
  detailScrollbarOffset: number;
  detailScrollbarTrailingInset: number;
  scrollbarVisibilityMs: number;
  scrollbarBaseAlpha: number;
  scrollbarHoverAlpha: number;
  scrollbarDragAlpha: number;
  wheelStep: number;
  scrollSmoothingRate: number;
  scrollSnapEpsilon: number;

  stateMotionMs: number;
  visibilityMotionMs: number;
  hoverMotionMs: number;
  appearMotionMs: number;
  marqueeSpeed: number;
  marqueeHoldMs: number;
  marqueeFadeWidth: number;
};

export type SolidBrowserPalette = {
  accent: string;
  surface: string;
  surfaceWeak: string;
  separator: string;
  accentDeep: string;
  foreground: string;
  card: string;
  deep: string;
  cardWeak: string;
  scrollbar: string;
  [key: string]: string;
};

export type SolidBrowserLayout = {
  width: number;
  height: number;
  navWidth: number;
  collectionWidth: number;
  detailWidth: number;
  headerHeight: number;
  collectionX: number;
  detailX: number;
  bodyHeight: number;
  detailViewportWidth: number;
  detailViewportHeight: number;
};

export type SolidBrowserFrame = SolidBrowserLayout & {
  x: number;
  y: number;
};

export type SolidInteraction = {
  events?: UiEvents;
  onClick?: UiActionRef;
  onSecondaryClick?: UiActionRef;
  onAuxiliaryClick?: UiActionRef;
  onChange?: UiActionRef;
  onInput?: UiActionRef;
};

export type SolidStyled = {
  tokens?: Partial<SolidBrowserTokens>;
  palette?: Partial<SolidBrowserPalette>;
  accent?: string;
};

export type SolidNavigationItemProps = SolidInteraction & SolidStyled & {
  key?: string;
  size?: number;
  selected?: boolean;
  icon?: SolidSlot;
};

export type SolidCollectionRowProps = SolidInteraction & SolidStyled & {
  key?: string;
  width: number;
  label?: string;
  font?: string;
  selected?: boolean;
  active?: boolean;
  marquee?: boolean;
  content?: SolidSlot;
};

export type SolidSearchFieldProps = SolidInteraction & SolidStyled & {
  key?: string;
  value?: string;
  placeholder?: string;
  font?: string;
  icon?: SolidSlot;
};

export type SolidCardProps = SolidStyled & {
  key?: string;
  width?: number;
  height?: number;
  children?: SolidSlot;
};

export type SolidSectionProps = {
  key?: string;
  class?: string;
  children?: SolidSlot;
};

export type SolidShortcutProps = SolidInteraction & SolidStyled & {
  key?: string;
  label?: string;
  font?: string;
  icon?: SolidSlot;
};

export type SolidEmptyStateProps = SolidStyled & {
  key?: string;
  text?: string;
  font?: string;
  class?: string;
};

export type SolidBrowserProps = SolidStyled & {
  key?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  blur?: boolean;
  blurQuality?: number;
  blurBrightness?: number;
  blurAlpha?: number;

  navigationHeader?: SolidSlot;
  navigationItems?: SolidSlot;
  navigationFooter?: SolidSlot;

  collectionTitle?: string;
  collectionTitleFont?: string;
  collectionRows?: SolidSlot;
  collectionOverlay?: SolidSlot;

  toolbarLeft?: SolidSlot;
  toolbarLeftWidth?: number;
  toolbarRight?: SolidSlot;
  toolbarRightWidth?: number;

  detailActive?: boolean;
  detailBackdrop?: boolean;
  detailBackdropAlpha?: number;
  detailTitle?: string;
  detailTitleFont?: string;
  detailDescription?: string;
  detailDescriptionFont?: string;
  detailTitleActions?: SolidSlot;
  detailTitleActionsWidth?: number;
  detailClose?: SolidSlot;
  detailContent?: SolidSlot;
  detailEmpty?: SolidSlot;
  detailOverlay?: SolidSlot;
};

export declare class SolidBrowserSurface {
  static tokens(overrides?: Partial<SolidBrowserTokens>): SolidBrowserTokens;
  static palette(accent?: string, overrides?: Partial<SolidBrowserPalette>): SolidBrowserPalette;
  static frame(availableWidth: number, availableHeight: number, tokenOverrides?: Partial<SolidBrowserTokens>): SolidBrowserFrame;
  static layout(width: number, height: number, tokenOverrides?: Partial<SolidBrowserTokens> | SolidBrowserTokens): SolidBrowserLayout;
  static surface(props?: SolidBrowserProps): UiNode;
  static navigationItem(props?: SolidNavigationItemProps): UiNode;
  static navigationFooterItem(props?: SolidNavigationItemProps): UiNode;
  static collectionRow(props: SolidCollectionRowProps): UiNode;
  static searchField(props?: SolidSearchFieldProps): UiNode;
  static closeButton(props?: SolidNavigationItemProps): UiNode;
  static card(props?: SolidCardProps): UiNode;
  static section(props?: SolidSectionProps): UiNode;
  static shortcut(props?: SolidShortcutProps): UiNode;
  static emptyState(props?: SolidEmptyStateProps): UiNode;
}
