/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/** Reusable browser surface for JS-runtime UI consumers. */
export class SolidBrowserSurface {
  static tokens(overrides = {}) {
    return {
      designWidth: 976,
      designHeight: 636,
      minWidth: 720,
      minHeight: 470,
      screenInset: 24,
      baseInset: 16,

      navDesignWidth: 66,
      collectionDesignWidth: 202,
      headerDesignHeight: 56,

      rootRadius: 24,
      rootCornerSmoothness: 2,
      rootStrokeWidth: 1,
      rootBlurQuality: 45,
      rootBlurBrightness: 1,
      rootBlurAlpha: 1,

      separatorWidth: 2,
      separatorInset: 16,

      navLogoSize: 22,
      navLogoY: 17,
      navStackGap: 6,
      navHeaderToStackGap: 16,
      navFooterBottomInset: 16,
      navItemSize: 34,
      navItemPadding: 8,
      navIconSize: 18,
      navItemRadius: 8,

      collectionHeaderTop: 10,
      collectionHeaderRight: 16,
      collectionHeaderBottom: 2,
      collectionHeaderLeft: 16,
      collectionTitleFontSize: 0.96,
      collectionTopInset: 2,
      collectionRightInset: 10,
      collectionBottomInset: 10,
      collectionLeftInset: 8,
      collectionRowHeight: 30,
      collectionRowGap: 4,
      collectionRowHorizontalInset: 10,
      collectionRowRadius: 6,
      collectionRowFontSize: 0.84,

      toolbarLeftInset: 16,
      toolbarRightInset: 16,
      toolbarProfileWidth: 174,
      searchWidth: 160,
      searchHeight: 30,
      searchRadius: 6,
      searchTextInsetLeft: 8,
      searchTextInsetRight: 31,
      searchFontSize: 1.34,
      searchDividerX: 131,
      searchIconSize: 0.90,

      detailHorizontalInset: 16,
      detailTopInset: 16,
      detailBottomInset: 16,
      detailHeaderHeight: 44,
      detailHeaderGap: 2,
      detailTitleHeight: 24,
      detailTitleFontSize: 1.36,
      detailDescriptionFontSize: 0.84,
      detailContentTopInset: 55,
      detailContentRightInset: 0,
      detailColumnGap: 10,
      detailGroupGap: 10,
      detailMinViewportHeight: 160,
      detailCloseSize: 32,
      detailClosePadding: 7,
      detailCloseIconSize: 18,
      detailCloseRadius: 8,
      detailBackdropRadius: 3,
      detailBackdropOffset: 1,
      detailBackdropBrightness: 1,
      detailBackdropClipInset: 32,
      detailBackdropExtraBottom: 44,

      cardInset: 8,
      cardRadius: 14,
      shortcutWidth: 180,
      shortcutHeight: 34,
      shortcutLeftInset: 10,
      shortcutRightInset: 12,
      shortcutRadius: 8,
      shortcutIconSize: 14,
      shortcutFontSize: 0.84,
      shortcutGap: 10,
      emptyFontSize: 0.96,

      scrollbarWidth: 4,
      scrollbarMinThumb: 36,
      scrollbarLeadingInset: 4,
      scrollbarTrailingInset: 4,
      scrollbarOffset: -6,
      detailScrollbarOffset: -18,
      detailScrollbarTrailingInset: 10,
      scrollbarVisibilityMs: 1100,
      scrollbarBaseAlpha: 0.28,
      scrollbarHoverAlpha: 0.24,
      scrollbarDragAlpha: 0.28,
      wheelStep: 44,
      scrollSmoothingRate: 0.02,
      scrollSnapEpsilon: 0.1,

      stateMotionMs: 160,
      visibilityMotionMs: 220,
      hoverMotionMs: 300,
      appearMotionMs: 240,
      ...overrides,
    };
  }

  static palette(accent = "#FF906BFF", overrides = {}) {
    const p = {
      accent: ui.str(accent, "#FF906BFF"),
      surface: "#E618151D",
      surfaceWeak: "#6618151D",
      separator: "#403D3647",
      accentDeep: "#FF4D00FF",
      foreground: "#FFFFFFFF",
      card: "#FF1A171F",
      deep: "#FF050407",
    };
    p.cardWeak = ui.color.alpha(p.card, 0.4);
    p.scrollbar = p.foreground;
    return { ...p, ...overrides };
  }

  static frame(availableWidth, availableHeight, tokenOverrides = {}) {
    const t = SolidBrowserSurface.tokens(tokenOverrides);
    const aw = Math.max(0, ui.num(availableWidth, t.designWidth));
    const ah = Math.max(0, ui.num(availableHeight, t.designHeight));
    const width = Math.min(t.designWidth, Math.max(t.minWidth, aw - t.screenInset));
    const height = Math.min(t.designHeight, Math.max(t.minHeight, ah - t.screenInset));
    return {
      x: Math.round((aw - width) * 0.5),
      y: Math.round((ah - height) * 0.5),
      width,
      height,
      ...SolidBrowserSurface.layout(width, height, t),
    };
  }

  static layout(width, height, tokenOverrides = {}) {
    const t = tokenOverrides && tokenOverrides.designWidth ? tokenOverrides : SolidBrowserSurface.tokens(tokenOverrides);
    const w = Math.max(1, ui.num(width, t.designWidth));
    const h = Math.max(1, ui.num(height, t.designHeight));
    const navWidth = w * t.navDesignWidth / t.designWidth;
    const collectionWidth = w * t.collectionDesignWidth / t.designWidth;
    const detailWidth = Math.max(0, w - navWidth - collectionWidth);
    const headerHeight = Math.min(h, t.headerDesignHeight);
    const bodyHeight = Math.max(0, h - headerHeight);
    const detailViewportWidth = Math.max(0, detailWidth - t.baseInset * 2);
    const detailHeaderY = headerHeight + t.detailTopInset;
    const contentY = detailHeaderY + t.detailContentTopInset;
    return {
      width: w,
      height: h,
      navWidth,
      collectionWidth,
      detailWidth,
      headerHeight,
      collectionX: navWidth,
      detailX: navWidth + collectionWidth,
      bodyHeight,
      detailViewportWidth,
      detailViewportHeight: Math.max(t.detailMinViewportHeight, bodyHeight - t.detailTopInset - t.detailBottomInset),
      navX: t.baseInset,
      navStartY: headerHeight + t.navHeaderToStackGap,
      navRowWidth: Math.max(1, navWidth - t.baseInset * 2),
      navRowHeight: 46,
      navRowGap: 6,
      toolbarLeftX: t.toolbarLeftInset,
      toolbarY: (headerHeight - t.searchHeight) * 0.5,
      searchWidth: t.searchWidth,
      searchHeight: t.searchHeight,
      closeX: Math.max(0, detailWidth - t.toolbarRightInset - t.detailCloseSize),
      closeY: Math.max(0, (headerHeight - t.detailCloseSize) * 0.5),
      closeWidth: t.detailCloseSize,
      closeHeight: t.detailCloseSize,
      detailHeaderX: t.detailHorizontalInset,
      detailHeaderY,
      detailHeaderWidth: detailViewportWidth,
      detailHeaderHeight: t.detailHeaderHeight,
      contentX: t.detailHorizontalInset,
      contentY,
      contentWidth: Math.max(0, detailViewportWidth - t.detailContentRightInset),
      contentHeight: Math.max(0, h - contentY - t.detailBottomInset),
      navSeparatorX: navWidth - 1,
      navSeparatorY: t.separatorInset,
      navSeparatorHeight: Math.max(0, h - t.separatorInset * 2),
      headerSeparatorX: navWidth + collectionWidth + t.baseInset,
      headerSeparatorY: headerHeight - 1,
      headerSeparatorWidth: Math.max(0, detailWidth - t.baseInset * 2),
    };
  }

  static surface(props = {}) {
    const tokenInput = props.tokens || {};
    const t = SolidBrowserSurface.tokens(props.combinedNavigation
      ? { ...tokenInput, navDesignWidth: 268, collectionDesignWidth: 0 }
      : tokenInput);
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const width = Math.max(1, ui.num(props.width, t.designWidth));
    const height = Math.max(1, ui.num(props.height, t.designHeight));
    const calculatedLayout = SolidBrowserSurface.layout(width, height, t);
    const suppliedLayout = props.layout && typeof props.layout === "object" ? props.layout : null;
    const l = suppliedLayout ? {
      width: ui.num(suppliedLayout.width, calculatedLayout.width),
      height: ui.num(suppliedLayout.height, calculatedLayout.height),
      navWidth: ui.num(suppliedLayout.navWidth, calculatedLayout.navWidth),
      collectionWidth: ui.num(suppliedLayout.collectionWidth, calculatedLayout.collectionWidth),
      detailWidth: ui.num(suppliedLayout.detailWidth, calculatedLayout.detailWidth),
      headerHeight: ui.num(suppliedLayout.headerHeight, calculatedLayout.headerHeight),
      collectionX: ui.num(suppliedLayout.collectionX, calculatedLayout.collectionX),
      detailX: ui.num(suppliedLayout.detailX, calculatedLayout.detailX),
      bodyHeight: ui.num(suppliedLayout.bodyHeight, calculatedLayout.bodyHeight),
      detailViewportWidth: ui.num(suppliedLayout.detailViewportWidth, calculatedLayout.detailViewportWidth),
      detailViewportHeight: ui.num(suppliedLayout.detailViewportHeight, calculatedLayout.detailViewportHeight),
      navStartY: ui.num(suppliedLayout.navStartY, calculatedLayout.headerHeight + t.navHeaderToStackGap),
      navX: ui.num(suppliedLayout.navX, calculatedLayout.navX),
      navRowWidth: ui.num(suppliedLayout.navRowWidth, calculatedLayout.navRowWidth),
      navRowHeight: ui.num(suppliedLayout.navRowHeight, 46),
      navRowGap: ui.num(suppliedLayout.navRowGap, 6),
      toolbarLeftX: ui.num(suppliedLayout.toolbarLeftX, calculatedLayout.toolbarLeftX),
      toolbarY: ui.num(suppliedLayout.toolbarY, calculatedLayout.toolbarY),
      searchWidth: ui.num(suppliedLayout.searchWidth, calculatedLayout.searchWidth),
      searchHeight: ui.num(suppliedLayout.searchHeight, calculatedLayout.searchHeight),
      closeX: ui.num(suppliedLayout.closeX, calculatedLayout.closeX),
      closeY: ui.num(suppliedLayout.closeY, calculatedLayout.closeY),
      closeWidth: ui.num(suppliedLayout.closeWidth, calculatedLayout.closeWidth),
      closeHeight: ui.num(suppliedLayout.closeHeight, calculatedLayout.closeHeight),
      detailHeaderX: ui.num(suppliedLayout.detailHeaderX, calculatedLayout.detailHeaderX),
      detailHeaderY: ui.num(suppliedLayout.detailHeaderY, calculatedLayout.detailHeaderY),
      detailHeaderWidth: ui.num(suppliedLayout.detailHeaderWidth, calculatedLayout.detailHeaderWidth),
      detailHeaderHeight: ui.num(suppliedLayout.detailHeaderHeight, calculatedLayout.detailHeaderHeight),
      contentX: ui.num(suppliedLayout.contentX, calculatedLayout.contentX),
      contentY: ui.num(suppliedLayout.contentY, calculatedLayout.contentY),
      contentWidth: ui.num(suppliedLayout.contentWidth, calculatedLayout.contentWidth),
      contentHeight: ui.num(suppliedLayout.contentHeight, calculatedLayout.contentHeight),
      navSeparatorX: ui.num(suppliedLayout.navSeparatorX, calculatedLayout.navSeparatorX),
      navSeparatorY: ui.num(suppliedLayout.navSeparatorY, calculatedLayout.navSeparatorY),
      navSeparatorHeight: ui.num(suppliedLayout.navSeparatorHeight, calculatedLayout.navSeparatorHeight),
      headerSeparatorX: ui.num(suppliedLayout.headerSeparatorX, calculatedLayout.headerSeparatorX),
      headerSeparatorY: ui.num(suppliedLayout.headerSeparatorY, calculatedLayout.headerSeparatorY),
      headerSeparatorWidth: ui.num(suppliedLayout.headerSeparatorWidth, calculatedLayout.headerSeparatorWidth),
    } : calculatedLayout;
    const key = ui.str(props.key, "solid-browser");
    const x = ui.num(props.x, 0);
    const y = ui.num(props.y, 0);

    const children = [
      ui.roundedRect({
        key: `${key}:surface`,
        x: 0, y: 0, w: l.width, h: l.height,
        radius: t.rootRadius,
        fill: p.surface,
        blur: props.blur !== false,
        blurQuality: ui.num(props.blurQuality, t.rootBlurQuality),
        blurBrightness: ui.num(props.blurBrightness, t.rootBlurBrightness),
        blurAlpha: ui.clamp(props.blurAlpha === undefined ? t.rootBlurAlpha : props.blurAlpha),
        interactive: false,
      }),
      ui.roundedRect({
        key: `${key}:border`,
        x: 0, y: 0, w: l.width, h: l.height,
        radius: t.rootRadius,
        fill: "#00000000",
        stroke: p.separator,
        strokeWidth: t.rootStrokeWidth,
        interactive: false,
      }),
      ...SolidBrowserSurface._separators(key, l, t, p),
      SolidBrowserSurface._navigationPane(key, l, t, p, props),
      ...(props.combinedNavigation ? [] : [SolidBrowserSurface._collectionPane(key, l, t, p, props)]),
      SolidBrowserSurface._detailPane(key, l, t, p, props),
    ];

    return ui.stack({
      key,
      class: ui.abs(x, y, width, height, "clip overflow-hidden"),
      children,
    });
  }

  static navigationItem(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-nav-item");
    const size = ui.num(props.size, t.navItemSize);
    const width = Math.max(size, ui.num(props.width, size));
    const height = Math.max(size, ui.num(props.height, size));
    const label = ui.str(props.label, "");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    const settingsCategory = props.appearance === "settings-category";
    const stateValues = { selected: !!props.selected };
    const stateMotions = SolidBrowserSurface._motions(t, ["selected"]);

    return ui.button({
      key,
      class: ui.abs(0, 0, width, height, "cursor-pointer"),
      stateValues,
      stateMotions,
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: width, h: height,
          radius: t.navItemRadius,
          fill: settingsCategory ? p.surface : p.surfaceWeak,
          fillReactive: {
            base: settingsCategory ? p.surface : p.surfaceWeak,
            mix: p.foreground,
            mixTerms: { hover: settingsCategory ? 0.045 : 0.025, selected: settingsCategory ? 0.035 : 0 },
          },
          stateSource: "parent",
          interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(icon, t.navItemPadding, (height - t.navIconSize) * 0.5, t.navIconSize, t.navIconSize, {
          tintReactive: {
            base: p.foreground,
            mix: p.accent,
            mixTerms: { selected: 1.0 },
            alphaBase: 0.62,
            alphaTerms: { selected: 0.38 },
          },
          stateSource: "parent",
          interactive: false,
        })] : []),
        ...(label ? [ui.text({
          key: `${key}:label`, text: label, color: p.foreground,
          colorReactive: {
            base: p.foreground, mix: p.accent, mixTerms: { selected: 0.72, hover: 0.16 },
            alphaBase: 0.66, alphaTerms: { selected: 0.34, hover: 0.16 },
          },
          stateSource: "parent", interactive: false,
          class: ui.abs(t.navItemPadding + t.navIconSize + 12, (height - 16) * 0.5,
            Math.max(0, width - t.navItemPadding * 2 - t.navIconSize - 12), 16,
            `font-${ui.str(props.font, "OnestMedium")}-1.00 text-align-left`),
        })] : []),
      ],
    });
  }

  static navigationFooterItem(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-nav-footer");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    return ui.button({
      key,
      class: ui.abs(0, 0, t.navItemSize, t.navItemSize, "cursor-pointer"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: t.navItemSize, h: t.navItemSize, radius: t.navItemRadius,
          fill: p.surfaceWeak,
          fillReactive: { base: p.surfaceWeak, mix: p.foreground, mixTerms: { hover: 0.025 } },
          stateSource: "parent", interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(icon, t.navItemPadding, t.navItemPadding, t.navIconSize, t.navIconSize, {
          tintReactive: { base: p.foreground, alphaBase: 0.58, alphaTerms: { hover: 0.35 } },
          stateSource: "parent", interactive: false,
        })] : []),
      ],
    });
  }

  static collectionRow(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-row");
    const width = Math.max(1, ui.num(props.width, 92));
    const label = ui.str(props.label, "");
    const font = ui.str(props.font, "OnestMedium");
    const custom = SolidBrowserSurface._nodes(props.content);

    const children = [
      ui.roundedRect({
        key: `${key}:bg`, x: 0, y: 0, w: width, h: t.collectionRowHeight, radius: t.collectionRowRadius,
        fill: p.cardWeak,
        fillReactive: {
          base: p.cardWeak,
          mix: p.accent,
          mixTerms: { active: 0.08, selected: 0.018, hover: 0.025 },
        },
        stateSource: "parent", interactive: false,
      }),
    ];

    if (custom.length) {
      children.push(...custom.map((node) => SolidBrowserSurface._placeVisual(
        node, t.collectionRowHorizontalInset, 0,
        Math.max(0, width - t.collectionRowHorizontalInset * 2), t.collectionRowHeight,
        { stateSource: "parent", interactive: false }
      )));
    } else {
      children.push(ui.text({
        key: `${key}:label`,
        text: label,
        color: p.foreground,
        colorReactive: {
          base: p.foreground,
          mix: p.accent,
          mixTerms: { active: 0.5 },
          alphaBase: 0.60,
          alphaTerms: { active: 0.30, selected: 0.10 },
        },
        stateSource: "parent",
        interactive: false,
        class: ui.abs(
          t.collectionRowHorizontalInset, 0,
          Math.max(0, width - t.collectionRowHorizontalInset * 2), t.collectionRowHeight,
          `font-${font}-${ui.fmt(t.collectionRowFontSize)} text-align-left`
        ),
      }));
    }

    return ui.button({
      key,
      class: ui.abs(0, 0, width, t.collectionRowHeight, "cursor-pointer"),
      stateValues: { selected: !!props.selected, active: !!props.active },
      stateMotions: SolidBrowserSurface._motions(t, ["selected", "active"]),
      events: SolidBrowserSurface._events(props),
      children,
    });
  }

  static searchField(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const key = ui.str(props.key, "solid-search");
    const text = ui.str(props.value, ui.str(props.placeholder, ""));
    const textColor = props.value ? "#FFFFFFFF" : "#FF878894";
    return ui.inputText({
      key,
      class: ui.abs(0, 0, t.searchWidth, t.searchHeight, "cursor-text"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: [
        ui.blurSurface({
          key: `${key}:blur`, x: 0, y: 0, w: t.searchWidth, h: t.searchHeight,
          radius: t.searchRadius, alpha: 135 / 255, brightness: 1, blurQuality: 27,
          interactive: false,
        }),
        ui.shape({
          key: `${key}:surface`, shape: "quad-gradient",
          class: ui.abs(0, 0, t.searchWidth, t.searchHeight),
          startColor: "#6C121314", endColor: "#6C050607", angle: 90,
          interactive: false,
        }),
        ui.roundedRect({
          key: `${key}:stroke`, x: 0.5, y: 0.5,
          w: t.searchWidth - 1, h: t.searchHeight - 1, radius: t.searchRadius,
          fill: "#00000000", stroke: "#96121314", strokeWidth: 1,
          stateSource: "parent", interactive: false,
        }),
        ui.shape({
          key: `${key}:divider`, shape: "rect",
          class: ui.abs(t.searchDividerX, 8, 1, t.searchHeight - 16),
          fill: "#269B9B9B", interactive: false,
        }),
        ui.text({
          key: `${key}:text`, text,
          color: textColor,
          colorReactive: { base: textColor, mix: "#FFFFFFFF", mixTerms: { focus: 1 } },
          stateSource: "parent",
          interactive: false,
          class: ui.abs(
            t.searchTextInsetLeft, 0,
            Math.max(0, t.searchWidth - t.searchTextInsetLeft - t.searchTextInsetRight), t.searchHeight,
            `font-${ui.str(props.font, "Onest")}-${ui.fmt(t.searchFontSize)} text-align-left`
          ),
        }),
        ui.text({
          key: `${key}:icon`, text: "s", color: "#FF878894",
          colorReactive: { base: "#FF878894", mix: "#FFF5F5FF", mixTerms: { focus: 1, hover: 0.35 } },
          stateSource: "parent", interactive: false,
          class: ui.abs(t.searchDividerX + 1, 0, t.searchWidth - t.searchDividerX - 1, t.searchHeight,
            `font-Icons-${ui.fmt(t.searchIconSize)} text-align-center`),
        }),
      ],
    });
  }

  static closeButton(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-close");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    return ui.button({
      key,
      class: ui.abs(0, 0, t.detailCloseSize, t.detailCloseSize, "cursor-pointer"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: t.detailCloseSize, h: t.detailCloseSize,
          radius: t.detailCloseRadius, fill: p.surfaceWeak,
          fillReactive: { base: p.surfaceWeak, mix: p.foreground, mixTerms: { hover: 0.07 } },
          stateSource: "parent", interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(
          icon, t.detailClosePadding, t.detailClosePadding, t.detailCloseIconSize, t.detailCloseIconSize,
          {
            tintReactive: { base: p.foreground, alphaBase: 0.68, alphaTerms: { hover: 0.32 } },
            stateSource: "parent", interactive: false,
          }
        )] : []),
      ],
    });
  }

  static card(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-card");
    const width = Math.max(1, ui.num(props.width, 90));
    const height = Math.max(1, ui.num(props.height, 32));
    return ui.stack({
      key,
      class: ui.abs(0, 0, width, height),
      children: [
        ui.roundedRect({ key: `${key}:bg`, x: 0, y: 0, w: width, h: height, radius: t.cardRadius, fill: p.cardWeak, interactive: false }),
        ...SolidBrowserSurface._nodes(props.children).map((node) => SolidBrowserSurface._placeVisual(
          node, t.cardInset, t.cardInset, Math.max(0, width - t.cardInset * 2), Math.max(0, height - t.cardInset * 2), { interactive: false }
        )),
      ],
    });
  }

  static section(props = {}) {
    const key = ui.str(props.key, "solid-section");
    return ui.column({
      key,
      class: props.class || "",
      children: SolidBrowserSurface._nodes(props.children),
    });
  }

  static shortcut(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = ui.str(props.key, "solid-shortcut");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    const label = ui.str(props.label, "");
    return ui.button({
      key,
      class: ui.abs(0, 0, t.shortcutWidth, t.shortcutHeight, "cursor-pointer"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: t.shortcutWidth, h: t.shortcutHeight, radius: t.shortcutRadius,
          fill: p.cardWeak,
          fillReactive: { base: p.cardWeak, mix: p.accent, mixTerms: { hover: 0.05 } },
          stateSource: "parent", interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(icon, t.shortcutLeftInset, 5, t.shortcutIconSize, t.shortcutIconSize, {
          tintReactive: { base: p.foreground, alphaBase: 0.62, alphaTerms: { hover: 0.38 } },
          stateSource: "parent", interactive: false,
        })] : []),
        ui.text({
          key: `${key}:label`, text: label,
          colorReactive: { base: p.foreground, alphaBase: 0.72, alphaTerms: { hover: 0.28 } },
          stateSource: "parent",
          interactive: false,
          class: ui.abs(
            t.shortcutLeftInset + t.shortcutIconSize + 4, 0,
            Math.max(0, t.shortcutWidth - (t.shortcutLeftInset + t.shortcutIconSize + 4) - t.shortcutRightInset), t.shortcutHeight,
            `font-${ui.str(props.font, "OnestMedium")}-${ui.fmt(t.shortcutFontSize)} text-align-left`
          ),
        }),
      ],
    });
  }

  static emptyState(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    return ui.text({
      key: ui.str(props.key, "solid-empty"),
      text: ui.str(props.text, ""),
      color: ui.color.alpha(p.foreground, 0.55),
      interactive: false,
      class: ui.cls(
        props.class || "",
        `font-${ui.str(props.font, "OnestMedium")}-${ui.fmt(t.emptyFontSize)} text-align-center`
      ),
    });
  }

  static _navigationPane(key, l, t, p, props) {
    const out = [];
    const logo = SolidBrowserSurface._slotNode(props.navigationHeader);
    if (logo) {
      out.push(SolidBrowserSurface._placeVisual(
        logo,
        (l.navWidth - t.navLogoSize) * 0.5,
        (l.headerHeight - t.navLogoSize) * 0.5,
        t.navLogoSize,
        t.navLogoSize,
        { interactive: false }
      ));
    }

    const nav = SolidBrowserSurface._nodes(props.navigationItems);
    const combined = !!props.combinedNavigation;
    const navX = combined ? l.navX : (l.navWidth - t.navItemSize) * 0.5;
    const navW = combined ? l.navRowWidth : t.navItemSize;
    const navH = combined ? ui.num(l.navRowHeight, 46) : t.navItemSize;
    const navGap = combined ? ui.num(l.navRowGap, 6) : t.navStackGap;
    let navY = combined ? ui.num(l.navStartY, l.headerHeight + t.navHeaderToStackGap)
      : l.headerHeight + t.navHeaderToStackGap;
    for (let i = 0; i < nav.length; i++) {
      out.push(SolidBrowserSurface._placeVisual(nav[i], navX, navY, navW, navH));
      navY += navH + navGap;
    }

    const footer = SolidBrowserSurface._slotNode(props.navigationFooter);
    if (footer) {
      out.push(SolidBrowserSurface._placeVisual(
        footer,
        navX,
        l.height - t.navFooterBottomInset - t.navItemSize,
        t.navItemSize,
        t.navItemSize
      ));
    }

    return ui.stack({ key: `${key}:navigation`, class: ui.abs(0, 0, l.navWidth, l.height), children: out });
  }

  static _collectionPane(key, l, t, p, props) {
    const title = ui.str(props.collectionTitle, "");
    const rows = SolidBrowserSurface._nodes(props.collectionRows);
    const contentWidth = Math.max(0, l.collectionWidth - t.collectionLeftInset - t.collectionRightInset);
    const headerChildren = title ? [ui.text({
      key: `${key}:collection-title`, text: title, color: p.foreground, interactive: false,
      class: ui.abs(
        t.collectionHeaderLeft, t.collectionHeaderTop,
        Math.max(0, l.collectionWidth - t.collectionHeaderLeft - t.collectionHeaderRight),
        Math.max(0, l.headerHeight - t.collectionHeaderTop - t.collectionHeaderBottom),
        `font-${ui.str(props.collectionTitleFont, "OnestMedium")}-${ui.fmt(t.collectionTitleFontSize)} text-align-left`
      ),
    })] : [];

    let rowY = 0;
    const placedRows = rows.map((row) => {
      const placed = SolidBrowserSurface._placeVisual(row, 0, rowY, contentWidth, t.collectionRowHeight);
      rowY += t.collectionRowHeight + t.collectionRowGap;
      return placed;
    });
    const contentHeight = Math.max(0, rowY - (placedRows.length ? t.collectionRowGap : 0));
    const content = ui.stack({
      key: `${key}:collection-content`,
      class: ui.abs(t.collectionLeftInset, t.collectionTopInset, contentWidth, contentHeight),
      children: placedRows,
    });

    const scroll = ui.scroll({
      key: `${key}:collection-scroll`,
      class: ui.abs(0, l.headerHeight, l.collectionWidth, Math.max(0, l.height - l.headerHeight), "clip overflow-hidden"),
      smoothScroll: true,
      scrollSmoothingRate: t.scrollSmoothingRate,
      scrollSnapEpsilon: t.scrollSnapEpsilon,
      wheelStep: t.wheelStep,
      scrollbar: true,
      scrollbarColor: p.scrollbar,
      scrollbarWidth: t.scrollbarWidth,
      scrollbarMinThumb: t.scrollbarMinThumb,
      scrollbarLeadingInset: t.scrollbarLeadingInset,
      scrollbarTrailingInset: t.scrollbarTrailingInset,
      scrollbarOffset: t.scrollbarOffset,
      scrollbarVisibilityMs: t.scrollbarVisibilityMs,
      scrollbarBaseAlpha: t.scrollbarBaseAlpha,
      scrollbarHoverAlpha: t.scrollbarHoverAlpha,
      scrollbarDragAlpha: t.scrollbarDragAlpha,
      children: [content],
    });

    return ui.stack({
      key: `${key}:collection`,
      class: ui.abs(l.collectionX, 0, l.collectionWidth, l.height, "clip overflow-hidden"),
      children: [...headerChildren, scroll, ...SolidBrowserSurface._nodes(props.collectionOverlay)],
    });
  }

  static _detailPane(key, l, t, p, props) {
    const out = [];
    const left = SolidBrowserSurface._slotNode(props.toolbarLeft);
    const right = SolidBrowserSurface._slotNode(props.toolbarRight);
    const close = SolidBrowserSurface._slotNode(props.detailClose);
    const leftW = Math.max(0, ui.num(props.toolbarLeftWidth, l.searchWidth));
    const rightW = Math.max(0, ui.num(props.toolbarRightWidth, t.toolbarProfileWidth));
    if (left) out.push(SolidBrowserSurface._placeVisual(left, l.toolbarLeftX, l.toolbarY, leftW, l.searchHeight));
    if (right) out.push(SolidBrowserSurface._placeVisual(right, Math.max(0, l.detailWidth - t.toolbarRightInset - rightW), 0, rightW, l.headerHeight));
    if (close) out.push(SolidBrowserSurface._placeVisual(
      close,
      l.closeX,
      l.closeY,
      l.closeWidth,
      l.closeHeight
    ));

    const detailActive = props.detailActive !== undefined
      ? !!props.detailActive
      : !!(props.detailTitle || SolidBrowserSurface._nodes(props.detailContent).length);

    if (detailActive && props.detailBackdrop !== false) {
      out.push(ui.roundedRect({
        key: `${key}:detail-backdrop`,
        x: 0,
        y: l.headerHeight,
        w: l.detailWidth,
        h: t.detailHeaderHeight + t.detailTopInset + t.detailBackdropExtraBottom,
        radius: 0,
        fill: "#00000000",
        blur: true,
        blurQuality: t.detailBackdropRadius,
        blurBrightness: t.detailBackdropBrightness,
        blurAlpha: ui.clamp(props.detailBackdropAlpha === undefined ? 1 : props.detailBackdropAlpha),
        interactive: false,
      }));
    }

    const viewportW = l.detailViewportWidth;
    const viewportH = l.detailViewportHeight;
    const detailNodes = SolidBrowserSurface._nodes(props.detailContent);
    const contentColumn = ui.column({
      key: `${key}:detail-content`,
      class: `absolute x-${ui.fmt(l.contentX - l.detailHeaderX)} y-${ui.fmt(l.contentY - l.detailHeaderY)} w-${ui.fmt(l.contentWidth)} gap-${ui.fmt(t.detailGroupGap)}`,
      children: detailNodes,
    });

    out.push(ui.scroll({
      key: `${key}:detail-scroll`,
      class: ui.abs(l.detailHeaderX, l.detailHeaderY, viewportW, viewportH, "clip overflow-hidden"),
      smoothScroll: true,
      scrollSmoothingRate: t.scrollSmoothingRate,
      scrollSnapEpsilon: t.scrollSnapEpsilon,
      wheelStep: t.wheelStep,
      scrollbar: true,
      scrollbarColor: p.scrollbar,
      scrollbarWidth: t.scrollbarWidth,
      scrollbarMinThumb: t.scrollbarMinThumb,
      scrollbarLeadingInset: t.scrollbarLeadingInset,
      scrollbarTrailingInset: t.detailScrollbarTrailingInset,
      scrollbarOffset: t.detailScrollbarOffset,
      scrollbarVisibilityMs: t.scrollbarVisibilityMs,
      scrollbarBaseAlpha: t.scrollbarBaseAlpha,
      scrollbarHoverAlpha: t.scrollbarHoverAlpha,
      scrollbarDragAlpha: t.scrollbarDragAlpha,
      children: [contentColumn],
    }));

    if (detailActive) {
      const headerW = l.detailHeaderWidth;
      const titleActions = SolidBrowserSurface._nodes(props.detailTitleActions);
      const titleRightReserve = Math.max(0, ui.num(props.detailTitleActionsWidth, 0));
      const textW = Math.max(0, headerW - titleRightReserve);
      const title = ui.str(props.detailTitle, "");
      const description = ui.str(props.detailDescription, "");
      const headerChildren = [];
      if (title) {
        headerChildren.push(ui.text({
          key: `${key}:detail-title`, text: title, color: p.foreground, interactive: false,
          class: ui.abs(0, 0, textW, t.detailTitleHeight,
            `font-${ui.str(props.detailTitleFont, "OnestMedium")}-${ui.fmt(t.detailTitleFontSize)} text-align-left`),
        }));
      }
      if (description) {
        headerChildren.push(ui.text({
          key: `${key}:detail-description`, text: description,
          color: ui.color.alpha(p.foreground, 0.58), interactive: false,
          class: ui.abs(0, t.detailTitleHeight + t.detailHeaderGap, textW,
            Math.max(0, l.detailHeaderHeight - t.detailTitleHeight - t.detailHeaderGap),
            `font-${ui.str(props.detailDescriptionFont, "OnestMedium")}-${ui.fmt(t.detailDescriptionFontSize)} text-align-left`),
        }));
      }
      if (titleActions.length) {
        const actionsW = Math.max(0, ui.num(props.detailTitleActionsWidth, 0));
        for (const node of titleActions) {
          headerChildren.push(SolidBrowserSurface._placeVisual(node, Math.max(0, headerW - titleRightReserve), 0, actionsW, t.detailTitleHeight));
        }
      }
      out.push(ui.stack({
        key: `${key}:detail-header`,
        class: ui.abs(l.detailHeaderX, l.detailHeaderY, headerW, l.detailHeaderHeight),
        children: headerChildren,
      }));
    } else {
      out.push(...SolidBrowserSurface._nodes(props.detailEmpty).map((node) => SolidBrowserSurface._placeVisual(
        node,
        l.detailHeaderX,
        l.detailHeaderY,
        viewportW,
        viewportH
      )));
    }

    out.push(...SolidBrowserSurface._nodes(props.detailOverlay));
    return ui.stack({
      key: `${key}:detail`,
      class: ui.abs(l.detailX, 0, l.detailWidth, l.height, "clip overflow-hidden"),
      children: out,
    });
  }

  static _separators(key, l, t, p) {
    const x2 = l.navWidth + l.collectionWidth - 1;
    const out = [ui.shape({ key: `${key}:sep-nav`, shape: "rect", class: ui.abs(l.navSeparatorX, l.navSeparatorY, 1, l.navSeparatorHeight), fill: p.separator, interactive: false })];
    if (l.collectionWidth > 0.5) {
      out.push(ui.shape({ key: `${key}:sep-collection`, shape: "rect", class: ui.abs(x2, t.separatorInset, 1, l.height - t.separatorInset * 2), fill: p.separator, interactive: false }));
    }
    out.push(ui.shape({ key: `${key}:sep-header`, shape: "rect", class: ui.abs(l.headerSeparatorX, l.headerSeparatorY, l.headerSeparatorWidth, 1), fill: p.separator, interactive: false }));
    return out;
  }

  static _motions(t, custom = []) {
    const motions = {
      hover: { durationMs: t.hoverMotionMs, easing: "out-cubic" },
      press: { durationMs: t.stateMotionMs, easing: "out-cubic" },
      focus: { durationMs: t.stateMotionMs, easing: "out-cubic" },
    };
    for (const name of custom) motions[name] = { durationMs: t.stateMotionMs, easing: "out-cubic" };
    return motions;
  }

  static _events(props) {
    const out = props && props.events && typeof props.events === "object" ? { ...props.events } : {};
    if (props && props.onClick) out.click = props.onClick;
    if (props && props.onSecondaryClick) out.secondaryClick = props.onSecondaryClick;
    if (props && props.onAuxiliaryClick) out.auxiliaryClick = props.onAuxiliaryClick;
    if (props && props.onInput) out.input = props.onInput;
    if (props && props.onChange) out.change = props.onChange;
    return out;
  }

  static _nodes(value) {
    if (value === null || value === undefined) return [];
    if (Array.isArray(value)) return value.filter((v) => v && typeof v === "object" && typeof v.type === "string");
    if (value && typeof value === "object" && typeof value.type === "string") return [value];
    try { return Array.from(value).filter((v) => v && typeof v === "object" && typeof v.type === "string"); } catch (_) { return []; }
  }

  static _slotNode(value) {
    const nodes = SolidBrowserSurface._nodes(value);
    return nodes.length ? nodes[0] : null;
  }

  static _placeVisual(node, x, y, w, h, propPatch = {}) {
    if (!node) return null;
    return {
      ...node,
      class: ui.cls(node.class || "", ui.abs(x, y, w, h)),
      props: { ...(node.props || {}), ...propPatch },
    };
  }

}
