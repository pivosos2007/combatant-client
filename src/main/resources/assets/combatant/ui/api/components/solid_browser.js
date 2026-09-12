/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/**
 * Domain-free reusable port of Rockstar's Modern/Solid browser surface.
 *
 * The component is deliberately not a ClickGUI screen. It only describes
 * geometry, material, state-reactive visuals and slots. Runtime interpolation,
 * scroll, scrollbar and marquee are retained by the Java UI runtime so cached
 * JS trees remain interactive and animated.
 */
export class SolidBrowserSurface {
  static tokens(overrides = {}) {
    return {
      designWidth: 488,
      designHeight: 318,
      minWidth: 360,
      minHeight: 235,
      screenInset: 12,

      navDesignWidth: 33,
      collectionDesignWidth: 101,
      headerDesignHeight: 24,

      rootRadius: 12,
      rootCornerSmoothness: 2,
      rootStrokeWidth: 0.5,
      rootBlurQuality: 45,
      rootBlurBrightness: 1,
      rootBlurAlpha: 1,

      separatorWidth: 1,
      separatorInset: 1,

      navLogoSize: 11,
      navLogoY: 11,
      navStackGap: 3,
      navHeaderToStackGap: 9,
      navFooterBottomInset: 8,
      navItemSize: 17,
      navItemPadding: 4,
      navIconSize: 9,
      navItemRadius: 4,

      collectionHeaderTop: 5,
      collectionHeaderRight: 8,
      collectionHeaderBottom: 1,
      collectionHeaderLeft: 8,
      collectionTitleFontSize: 9,
      collectionTopInset: 1,
      collectionRightInset: 5,
      collectionBottomInset: 5,
      collectionLeftInset: 4,
      collectionRowHeight: 15,
      collectionRowGap: 2,
      collectionRowHorizontalInset: 5,
      collectionRowRadius: 3,
      collectionRowFontSize: 7,

      toolbarLeftInset: 5,
      toolbarRightInset: 7,
      toolbarProfileWidth: 87,
      searchWidth: 81,
      searchHeight: 12,
      searchRadius: 3,
      searchIconInset: 3.5,
      searchIconSize: 5,
      searchTextInsetLeft: 8.5,
      searchTextInsetRight: 2,

      detailHorizontalInset: 11,
      detailTopInset: 10.5,
      detailBottomInset: 2,
      detailHeaderHeight: 22,
      detailHeaderGap: 1,
      detailTitleHeight: 12,
      detailTitleFontSize: 12,
      detailDescriptionFontSize: 7,
      detailContentTopInset: 27.5,
      detailContentRightInset: 5,
      detailColumnGap: 5,
      detailGroupGap: 5,
      detailMinViewportHeight: 80,
      detailCloseSize: 16,
      detailClosePadding: 3.5,
      detailCloseIconSize: 9,
      detailCloseRadius: 4,
      detailBackdropRadius: 1.5,
      detailBackdropOffset: 0.5,
      detailBackdropBrightness: 1,
      detailBackdropClipInset: 16,
      detailBackdropExtraBottom: 22,

      cardInset: 4,
      cardRadius: 7,
      shortcutWidth: 90,
      shortcutHeight: 17,
      shortcutLeftInset: 5,
      shortcutRightInset: 6,
      shortcutRadius: 4,
      shortcutIconSize: 7,
      shortcutFontSize: 7,
      shortcutGap: 5,
      emptyFontSize: 9,

      scrollbarWidth: 2,
      scrollbarMinThumb: 18,
      scrollbarLeadingInset: 2,
      scrollbarTrailingInset: 2,
      scrollbarOffset: -3,
      detailScrollbarOffset: -9,
      detailScrollbarTrailingInset: 5,
      scrollbarVisibilityMs: 1100,
      scrollbarBaseAlpha: 0.28,
      scrollbarHoverAlpha: 0.24,
      scrollbarDragAlpha: 0.28,
      wheelStep: 22,
      scrollSmoothingRate: 0.02,
      scrollSnapEpsilon: 0.05,

      stateMotionMs: 160,
      visibilityMotionMs: 220,
      hoverMotionMs: 300,
      appearMotionMs: 240,
      marqueeSpeed: 35,
      marqueeHoldMs: 600,
      marqueeFadeWidth: 8,

      ...overrides,
    };
  }

  /** Exact baseline palette entries from the Modern source, accent-shifted by hue/saturation. */
  static palette(accent = "#FF906BFF", overrides = {}) {
    const baseline = SolidBrowserSurface._parseColor("#FF906BFF");
    const target = SolidBrowserSurface._parseColor(accent) || baseline;
    const baseHsv = SolidBrowserSurface._rgbToHsv(baseline.r, baseline.g, baseline.b);
    const targetHsv = SolidBrowserSurface._rgbToHsv(target.r, target.g, target.b);
    const hueDelta = targetHsv.h - baseHsv.h;
    const saturationRatio = baseHsv.s <= 0.000001 ? 1 : targetHsv.s / baseHsv.s;

    const transform = (hex) => {
      const src = SolidBrowserSurface._parseColor(hex);
      if (!src) return hex;
      const hsv = SolidBrowserSurface._rgbToHsv(src.r, src.g, src.b);
      if (hsv.s <= 0.000001) return SolidBrowserSurface._colorHex(src.a, src.r, src.g, src.b);
      const rgb = SolidBrowserSurface._hsvToRgb(
        SolidBrowserSurface._wrap01(hsv.h + hueDelta),
        SolidBrowserSurface._clamp01(hsv.s * saturationRatio),
        hsv.v
      );
      return SolidBrowserSurface._colorHex(src.a, rgb.r, rgb.g, rgb.b);
    };

    const p = {
      accent: SolidBrowserSurface._colorHex(255, target.r, target.g, target.b),
      surface: transform("#E618151D"),       // palette index 1: 24,21,29,229.5
      surfaceWeak: transform("#6618151D"),   // palette index 2: 24,21,29,102
      separator: transform("#403D3647"),     // palette index 3: 61,54,71,63.75
      accentDeep: transform("#FF4D00FF"),
      foreground: "#FFFFFFFF",
      card: transform("#FF1A171F"),          // palette index 6
      deep: transform("#FF050407"),          // palette index 7
    };
    p.cardWeak = SolidBrowserSurface._withAlpha(p.card, 0.4);
    p.scrollbar = p.foreground;
    return { ...p, ...overrides };
  }

  static frame(availableWidth, availableHeight, tokenOverrides = {}) {
    const t = SolidBrowserSurface.tokens(tokenOverrides);
    const aw = Math.max(0, SolidBrowserSurface._num(availableWidth, t.designWidth));
    const ah = Math.max(0, SolidBrowserSurface._num(availableHeight, t.designHeight));
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
    const w = Math.max(1, SolidBrowserSurface._num(width, t.designWidth));
    const h = Math.max(1, SolidBrowserSurface._num(height, t.designHeight));
    const navWidth = w * t.navDesignWidth / t.designWidth;
    const collectionWidth = w * t.collectionDesignWidth / t.designWidth;
    const detailWidth = Math.max(0, w - navWidth - collectionWidth);
    const headerHeight = h * t.headerDesignHeight / t.designHeight;
    const bodyHeight = Math.max(0, h - headerHeight);
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
      detailViewportWidth: Math.max(0, detailWidth - 22),
      detailViewportHeight: Math.max(t.detailMinViewportHeight, bodyHeight - t.detailTopInset - t.detailBottomInset),
    };
  }

  /** Build the reusable browser shell. No consumer is registered here. */
  static surface(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const width = Math.max(1, SolidBrowserSurface._num(props.width, t.designWidth));
    const height = Math.max(1, SolidBrowserSurface._num(props.height, t.designHeight));
    const l = SolidBrowserSurface.layout(width, height, t);
    const key = SolidBrowserSurface._str(props.key, "solid-browser");
    const x = SolidBrowserSurface._num(props.x, 0);
    const y = SolidBrowserSurface._num(props.y, 0);

    const children = [
      // Source calls this a squircle, but CornerSmoothness=2 with radius=12 is the ordinary L2 rounded-box SDF.
      ui.roundedRect({
        key: `${key}:surface`,
        x: 0, y: 0, w: l.width, h: l.height,
        radius: t.rootRadius,
        fill: p.surface,
        blur: props.blur !== false,
        blurQuality: SolidBrowserSurface._num(props.blurQuality, t.rootBlurQuality),
        blurBrightness: SolidBrowserSurface._num(props.blurBrightness, t.rootBlurBrightness),
        blurAlpha: SolidBrowserSurface._clamp01(props.blurAlpha === undefined ? t.rootBlurAlpha : props.blurAlpha),
        interactive: false,
      }),
      // Source explicitly draws a rounded 0.5 border after drawClientRect.
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
      SolidBrowserSurface._collectionPane(key, l, t, p, props),
      SolidBrowserSurface._detailPane(key, l, t, p, props),
    ];

    return ui.stack({
      key,
      class: ui.abs(x, y, width, height, "clip overflow-hidden"),
      children,
    });
  }

  /** Source-faithful 17x17 navigation entry with a caller-owned 9x9 icon. */
  static navigationItem(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-nav-item");
    const size = SolidBrowserSurface._num(props.size, t.navItemSize);
    const icon = SolidBrowserSurface._slotNode(props.icon);
    const stateValues = { selected: !!props.selected };
    const stateMotions = SolidBrowserSurface._motions(t, ["selected"]);

    return ui.button({
      key,
      class: ui.abs(0, 0, size, size, "cursor-pointer"),
      stateValues,
      stateMotions,
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: size, h: size, radius: t.navItemRadius,
          fill: p.surfaceWeak,
          fillReactive: {
            base: p.surfaceWeak,
            mix: p.foreground,
            mixTerms: { hover: 0.025 },
          },
          stateSource: "parent",
          interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(icon, t.navItemPadding, t.navItemPadding, t.navIconSize, t.navIconSize, {
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
      ],
    });
  }

  /** Bottom rail action from the same source, with hover-only icon emphasis. */
  static navigationFooterItem(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-nav-footer");
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

  /** Generic source-metric collection row. selected and active are semantic, not module-specific. */
  static collectionRow(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-row");
    const width = Math.max(1, SolidBrowserSurface._num(props.width, 92));
    const label = SolidBrowserSurface._str(props.label, "");
    const font = SolidBrowserSurface._str(props.font, "OnestMedium");
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
        runtimeMarquee: props.marquee !== false,
        marqueeSpeed: t.marqueeSpeed,
        marqueeHoldMs: t.marqueeHoldMs,
        marqueeFadeWidth: t.marqueeFadeWidth,
        interactive: false,
        class: ui.abs(
          t.collectionRowHorizontalInset, 0,
          Math.max(0, width - t.collectionRowHorizontalInset * 2), t.collectionRowHeight,
          `font-${font}-${SolidBrowserSurface._fmt(t.collectionRowFontSize)} text-align-left`
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

  /** Visual search primitive. Input ownership remains with the eventual consumer/host. */
  static searchField(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-search");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    const text = SolidBrowserSurface._str(props.value, SolidBrowserSurface._str(props.placeholder, ""));
    const textAlpha = props.value ? 0.72 : 0.52;
    return ui.inputText({
      key,
      class: ui.abs(0, 0, t.searchWidth, t.searchHeight, "cursor-text"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: [
        ui.roundedRect({
          key: `${key}:bg`, x: 0, y: 0, w: t.searchWidth, h: t.searchHeight, radius: t.searchRadius,
          fill: SolidBrowserSurface._withAlpha(p.surface, 173.4 / 255),
          fillReactive: {
            base: SolidBrowserSurface._withAlpha(p.surface, 173.4 / 255),
            mix: p.foreground,
            mixBase: 0.035,
            mixTerms: { hover: 0.035 },
          },
          stateSource: "parent", interactive: false,
        }),
        ...(icon ? [SolidBrowserSurface._placeVisual(icon, t.searchIconInset, t.searchIconInset, t.searchIconSize, t.searchIconSize, {
          tintReactive: { base: p.foreground, alphaBase: 0.48 },
          stateSource: "parent", interactive: false,
        })] : []),
        ui.text({
          key: `${key}:text`, text,
          color: SolidBrowserSurface._withAlpha(p.foreground, textAlpha),
          interactive: false,
          class: ui.abs(
            t.searchTextInsetLeft, 0,
            Math.max(0, t.searchWidth - t.searchTextInsetLeft - t.searchTextInsetRight), t.searchHeight,
            `font-${SolidBrowserSurface._str(props.font, "OnestMedium")}-6.00 text-align-left`
          ),
        }),
      ],
    });
  }

  static closeButton(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-close");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    return ui.button({
      key,
      class: ui.abs(0, 0, t.detailCloseSize, t.detailCloseSize, "cursor-pointer"),
      stateMotions: SolidBrowserSurface._motions(t),
      events: SolidBrowserSurface._events(props),
      children: icon ? [SolidBrowserSurface._placeVisual(
        icon, t.detailClosePadding, t.detailClosePadding, t.detailCloseIconSize, t.detailCloseIconSize,
        {
          tintReactive: { base: p.foreground, alphaBase: 0.8, alphaTerms: { hover: 0.2 } },
          stateSource: "parent", interactive: false,
        }
      )] : [],
    });
  }

  static card(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-card");
    const width = Math.max(1, SolidBrowserSurface._num(props.width, 90));
    const height = Math.max(1, SolidBrowserSurface._num(props.height, 32));
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
    const key = SolidBrowserSurface._str(props.key, "solid-section");
    return ui.column({
      key,
      class: props.class || "",
      children: SolidBrowserSurface._nodes(props.children),
    });
  }

  static shortcut(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    const key = SolidBrowserSurface._str(props.key, "solid-shortcut");
    const icon = SolidBrowserSurface._slotNode(props.icon);
    const label = SolidBrowserSurface._str(props.label, "");
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
          stateSource: "parent", runtimeMarquee: true,
          marqueeSpeed: t.marqueeSpeed, marqueeHoldMs: t.marqueeHoldMs,
          interactive: false,
          class: ui.abs(
            t.shortcutLeftInset + t.shortcutIconSize + 4, 0,
            Math.max(0, t.shortcutWidth - (t.shortcutLeftInset + t.shortcutIconSize + 4) - t.shortcutRightInset), t.shortcutHeight,
            `font-${SolidBrowserSurface._str(props.font, "OnestMedium")}-${SolidBrowserSurface._fmt(t.shortcutFontSize)} text-align-left`
          ),
        }),
      ],
    });
  }

  static emptyState(props = {}) {
    const t = SolidBrowserSurface.tokens(props.tokens || {});
    const p = SolidBrowserSurface.palette(props.accent || "#FF906BFF", props.palette || {});
    return ui.text({
      key: SolidBrowserSurface._str(props.key, "solid-empty"),
      text: SolidBrowserSurface._str(props.text, ""),
      color: SolidBrowserSurface._withAlpha(p.foreground, 0.55),
      interactive: false,
      class: ui.cls(
        props.class || "",
        `font-${SolidBrowserSurface._str(props.font, "OnestMedium")}-${SolidBrowserSurface._fmt(t.emptyFontSize)} text-align-center`
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
        t.navLogoY,
        t.navLogoSize,
        t.navLogoSize,
        { interactive: false }
      ));
    }

    const nav = SolidBrowserSurface._nodes(props.navigationItems);
    const navX = (l.navWidth - t.navItemSize) * 0.5;
    let navY = l.headerHeight + t.navHeaderToStackGap;
    for (let i = 0; i < nav.length; i++) {
      out.push(SolidBrowserSurface._placeVisual(nav[i], navX, navY, t.navItemSize, t.navItemSize));
      navY += t.navItemSize + t.navStackGap;
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
    const title = SolidBrowserSurface._str(props.collectionTitle, "");
    const rows = SolidBrowserSurface._nodes(props.collectionRows);
    const contentWidth = Math.max(0, l.collectionWidth - t.collectionLeftInset - t.collectionRightInset);
    const headerChildren = title ? [ui.text({
      key: `${key}:collection-title`, text: title, color: p.foreground, interactive: false,
      class: ui.abs(
        t.collectionHeaderLeft, t.collectionHeaderTop,
        Math.max(0, l.collectionWidth - t.collectionHeaderLeft - t.collectionHeaderRight),
        Math.max(0, l.headerHeight - t.collectionHeaderTop - t.collectionHeaderBottom),
        `font-${SolidBrowserSurface._str(props.collectionTitleFont, "OnestMedium")}-${SolidBrowserSurface._fmt(t.collectionTitleFontSize)} text-align-left`
      ),
    })] : [];

    const column = ui.column({
      key: `${key}:collection-content`,
      class: `absolute x-${SolidBrowserSurface._fmt(t.collectionLeftInset)} y-${SolidBrowserSurface._fmt(t.collectionTopInset)} w-${SolidBrowserSurface._fmt(contentWidth)} gap-${SolidBrowserSurface._fmt(t.collectionRowGap)}`,
      children: rows,
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
      children: [column],
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
    const leftW = Math.max(0, SolidBrowserSurface._num(props.toolbarLeftWidth, t.searchWidth));
    const rightW = Math.max(0, SolidBrowserSurface._num(props.toolbarRightWidth, t.toolbarProfileWidth));
    if (left) out.push(SolidBrowserSurface._placeVisual(left, t.toolbarLeftInset, (l.headerHeight - t.searchHeight) * 0.5, leftW, t.searchHeight));
    if (right) out.push(SolidBrowserSurface._placeVisual(right, Math.max(0, l.detailWidth - t.toolbarRightInset - rightW), 0, rightW, l.headerHeight));

    const detailActive = props.detailActive !== undefined
      ? !!props.detailActive
      : !!(props.detailTitle || SolidBrowserSurface._nodes(props.detailContent).length);

    if (detailActive && props.detailBackdrop !== false) {
      // i_method_f5052edf: x extends 11px around inner header, y extends 10.5 up,
      // height adds 10.5 + 22, zero corner radius, blur radius/offset 1.5/0.5.
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
        blurAlpha: SolidBrowserSurface._clamp01(props.detailBackdropAlpha === undefined ? 1 : props.detailBackdropAlpha),
        interactive: false,
      }));
    }

    const viewportW = Math.max(0, l.detailWidth - 22);
    const viewportH = Math.max(t.detailMinViewportHeight, l.bodyHeight - t.detailTopInset - t.detailBottomInset);
    const detailNodes = SolidBrowserSurface._nodes(props.detailContent);
    const contentColumn = ui.column({
      key: `${key}:detail-content`,
      class: `absolute x-0.00 y-${SolidBrowserSurface._fmt(t.detailContentTopInset)} w-${SolidBrowserSurface._fmt(Math.max(0, viewportW - t.detailContentRightInset))} gap-${SolidBrowserSurface._fmt(t.detailGroupGap)}`,
      children: detailNodes,
    });

    out.push(ui.scroll({
      key: `${key}:detail-scroll`,
      class: ui.abs(t.detailHorizontalInset, l.headerHeight + t.detailTopInset, viewportW, viewportH, "clip overflow-hidden"),
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
      const headerW = viewportW;
      const close = SolidBrowserSurface._slotNode(props.detailClose);
      const titleActions = SolidBrowserSurface._nodes(props.detailTitleActions);
      const titleRightReserve = (close ? t.detailCloseSize + t.detailGroupGap : 0) + Math.max(0, SolidBrowserSurface._num(props.detailTitleActionsWidth, 0));
      const textW = Math.max(0, headerW - titleRightReserve);
      const title = SolidBrowserSurface._str(props.detailTitle, "");
      const description = SolidBrowserSurface._str(props.detailDescription, "");
      const headerChildren = [];
      if (title) {
        headerChildren.push(ui.text({
          key: `${key}:detail-title`, text: title, color: p.foreground, interactive: false,
          class: ui.abs(0, 0, textW, t.detailTitleHeight,
            `font-${SolidBrowserSurface._str(props.detailTitleFont, "OnestMedium")}-${SolidBrowserSurface._fmt(t.detailTitleFontSize)} text-align-left`),
        }));
      }
      if (description) {
        headerChildren.push(ui.text({
          key: `${key}:detail-description`, text: description,
          color: SolidBrowserSurface._withAlpha(p.foreground, 0.58), interactive: false,
          runtimeMarquee: true, marqueeSpeed: t.marqueeSpeed, marqueeHoldMs: t.marqueeHoldMs,
          class: ui.abs(0, t.detailTitleHeight + t.detailHeaderGap, textW,
            Math.max(0, t.detailHeaderHeight - t.detailTitleHeight - t.detailHeaderGap),
            `font-${SolidBrowserSurface._str(props.detailDescriptionFont, "OnestMedium")}-${SolidBrowserSurface._fmt(t.detailDescriptionFontSize)} text-align-left`),
        }));
      }
      if (titleActions.length) {
        const actionsW = Math.max(0, SolidBrowserSurface._num(props.detailTitleActionsWidth, 0));
        for (const node of titleActions) {
          headerChildren.push(SolidBrowserSurface._placeVisual(node, Math.max(0, headerW - titleRightReserve), 0, actionsW, t.detailTitleHeight));
        }
      }
      if (close) {
        headerChildren.push(SolidBrowserSurface._placeVisual(close, Math.max(0, headerW - t.detailCloseSize), 0, t.detailCloseSize, t.detailCloseSize));
      }
      out.push(ui.stack({
        key: `${key}:detail-header`,
        class: ui.abs(t.detailHorizontalInset, l.headerHeight + t.detailTopInset, headerW, t.detailHeaderHeight),
        children: headerChildren,
      }));
    } else {
      out.push(...SolidBrowserSurface._nodes(props.detailEmpty).map((node) => SolidBrowserSurface._placeVisual(
        node,
        t.detailHorizontalInset,
        l.headerHeight + t.detailTopInset,
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
    const x1 = l.navWidth - t.separatorWidth;
    const x2 = l.navWidth + l.collectionWidth - t.separatorWidth;
    return [
      ui.shape({ key: `${key}:sep-nav`, shape: "rect", class: ui.abs(x1, t.separatorInset, 1, l.height - t.separatorInset * 2), fill: p.separator, interactive: false }),
      ui.shape({ key: `${key}:sep-collection`, shape: "rect", class: ui.abs(x2, t.separatorInset, 1, l.height - t.separatorInset * 2), fill: p.separator, interactive: false }),
      ui.shape({ key: `${key}:sep-header`, shape: "rect", class: ui.abs(x2 + 1, l.headerHeight - 1, l.width - l.navWidth - l.collectionWidth - 1, 1), fill: p.separator, interactive: false }),
    ];
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

  static _num(value, fallback) {
    return typeof value === "number" && Number.isFinite(value) ? value : fallback;
  }

  static _str(value, fallback = "") {
    return typeof value === "string" && value.length ? value : fallback;
  }

  static _fmt(value) {
    return SolidBrowserSurface._num(value, 0).toFixed(2);
  }

  static _clamp01(value) {
    return Math.max(0, Math.min(1, SolidBrowserSurface._num(value, 0)));
  }

  static _withAlpha(hex, alpha) {
    const c = SolidBrowserSurface._parseColor(hex);
    if (!c) return hex;
    return SolidBrowserSurface._colorHex(Math.round(255 * SolidBrowserSurface._clamp01(alpha)), c.r, c.g, c.b);
  }

  static _parseColor(hex) {
    if (typeof hex !== "string" || !/^#[0-9a-fA-F]{8}$/.test(hex.trim())) return null;
    const raw = Number.parseInt(hex.trim().slice(1), 16) >>> 0;
    return { a: (raw >>> 24) & 255, r: (raw >>> 16) & 255, g: (raw >>> 8) & 255, b: raw & 255 };
  }

  static _colorHex(a, r, g, b) {
    const aa = Math.max(0, Math.min(255, Math.round(a)));
    const rr = Math.max(0, Math.min(255, Math.round(r)));
    const gg = Math.max(0, Math.min(255, Math.round(g)));
    const bb = Math.max(0, Math.min(255, Math.round(b)));
    return `#${((((aa << 24) | (rr << 16) | (gg << 8) | bb) >>> 0).toString(16).padStart(8, "0")).toUpperCase()}`;
  }

  static _wrap01(value) {
    const v = value - Math.floor(value);
    return v < 0 ? v + 1 : v;
  }

  static _rgbToHsv(r, g, b) {
    const rr = r / 255, gg = g / 255, bb = b / 255;
    const max = Math.max(rr, gg, bb), min = Math.min(rr, gg, bb), d = max - min;
    let h = 0;
    if (d > 0.000001) {
      if (max === rr) h = ((gg - bb) / d) % 6;
      else if (max === gg) h = (bb - rr) / d + 2;
      else h = (rr - gg) / d + 4;
      h /= 6;
      if (h < 0) h += 1;
    }
    return { h, s: max <= 0 ? 0 : d / max, v: max };
  }

  static _hsvToRgb(h, s, v) {
    const hh = SolidBrowserSurface._wrap01(h) * 6;
    const c = v * s;
    const x = c * (1 - Math.abs((hh % 2) - 1));
    const m = v - c;
    let rr = 0, gg = 0, bb = 0;
    if (hh < 1) { rr = c; gg = x; }
    else if (hh < 2) { rr = x; gg = c; }
    else if (hh < 3) { gg = c; bb = x; }
    else if (hh < 4) { gg = x; bb = c; }
    else if (hh < 5) { rr = x; bb = c; }
    else { rr = c; bb = x; }
    return { r: Math.round((rr + m) * 255), g: Math.round((gg + m) * 255), b: Math.round((bb + m) * 255) };
  }
}
