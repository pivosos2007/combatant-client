/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

const num = ui.num;
const fmt = ui.fmt;
const cls = ui.cls;
const abs = ui.abs;
const colorAlpha = ui.color.alpha;
const PROGRESS_DOTS = 32;

function displayShell(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, num(p.width, 126));
  const h = num(p.height, 35);
  const cut = num(p.bezelCut, 4.5);
  const alpha = num(p.alpha, 1);
  const nodes = [
    // Keep the shell on one analytic chamfer primitive. The same `chamfer` value drives
    // both the backdrop-blur mask and the painted surface, so the two silhouettes cannot drift.
    ui.shape({
      key: "shell:surface",
      shape: "primitive",
      preset: "chamfered",
      class: abs(x, 0, w, h),
      chamfer: cut,
      blur: p.blur === true,
      blurQuality: 8,
      blurBrightness: 1.0,
      blurAlpha: (p.blur === true ? num(p.blurAlpha, 0.45) : 0) * alpha,
      fill: colorAlpha(p.displayBg, alpha),
      startColor: colorAlpha(p.displayBgStart || p.displayBg, alpha),
      endColor: colorAlpha(p.displayBgEnd || p.displayBg, alpha),
      angle: num(p.displayBgAngle, 90),
    }),
    ui.shape({
      key: "shell:interaction",
      shape: "primitive",
      preset: "chamfered",
      class: abs(x, 0, w, h),
      chamfer: cut,
      fill: colorAlpha(p.phosphor, (0.025 * num(p.bodyHover, 0) + 0.045 * num(p.bodyPress, 0)) * alpha),
    }),
  ];
  return nodes;
}

function phosphorText(p, init, localAlpha = 1, strength = 0.20, width = 1.8) {
  const alpha = num(p.alpha, 1) * localAlpha;
  return ui.text({
    ...init,
    color: colorAlpha(p.phosphor, alpha),
    textGlowColor: colorAlpha(p.phosphor, alpha),
    textGlowWidth: width,
    textGlowStrength: strength,
  });
}

function artwork(p, key, x, y, size, alpha) {
  if (p.artworkTexture) {
    return ui.image({
      key,
      assetType: "texture",
      asset: p.artworkTexture,
      class: abs(x, y, size, size, "rounded-2.00"),
      tint: colorAlpha("#FFFFFFFF", alpha),
    });
  }
  return ui.shape({
    key: `${key}:fallback`,
    shape: "box",
    class: abs(x, y, size, size),
    corners: ui.corner.all(ui.corner.chamfered(2)),
    fill: colorAlpha(p.phosphorDim, 0.72 * alpha),
  });
}

function waveBars(ctx, p, x, centerY, alpha) {
  const out = [];
  for (let i = 0; i < 3; i++) {
    const h = p.playing ? 3 + Math.abs(Math.sin(ctx.time * 5.2 + i * 0.85)) * 7 : 3 + i;
    out.push(ui.shape({
      key: `wave:${i}`,
      shape: "rounded",
      class: abs(x + i * 4, centerY - h * 0.5, 1.7, h),
      radius: 0.85,
      fill: colorAlpha(p.phosphor, (0.64 + i * 0.10) * alpha),
    }));
  }
  return out;
}

function telemetryMode(p, isPvp) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, num(p.width, 82));
  const value = isPvp ? (p.pvpTelemetry || "PVP 00") : (p.time || "00:00");
  return [
    phosphorText(p, {
      key: isPvp ? "pvp:timer" : "time",
      text: value,
      class: cls(abs(x + 7, 7.5, Math.max(1, w - 14), 20), "font-MatrixSansPrint font-size-16.92 text-align-center"),
    }, 1, isPvp ? 0.30 : 0.24, 2.0),
  ];
}

function clickGuiTabs(p) {
  const mainX = num(p.mainX, 0);
  const height = num(p.height, 34);
  const alpha = num(p.alpha, 1);
  const tabs = (() => {
    try { return Array.from(p.clickGuiTabs || []); } catch (_) { return []; }
  })();
  const nodes = [];
  tabs.forEach((tab, index) => {
    const active = tab && tab.active === true;
    const hovered = tab && tab.hovered === true;
    const tabX = mainX + num(tab && tab.x, 0);
    const tabW = Math.max(1, num(tab && tab.width, 1));
    const localAlpha = active ? 1 : (hovered ? 0.86 : 0.58);
    const categoryColor = tab && tab.color ? String(tab.color) : p.phosphor;
    nodes.push(ui.shape({
      key: `clickgui:wash:${index}`,
      shape: "box",
      class: abs(tabX + 2.5, 3, Math.max(1, tabW - 5), Math.max(1, height - 6)),
      corners: ui.corner.mixed(
        ui.corner.rounded(2.2),
        ui.corner.rounded(2.2),
        ui.corner.chamfered(2.2),
        ui.corner.chamfered(2.2),
      ),
      startColor: colorAlpha(categoryColor, (active ? 0.24 : (hovered ? 0.10 : 0)) * alpha),
      endColor: colorAlpha(p.phosphorDim, (active ? 0.10 : (hovered ? 0.035 : 0)) * alpha),
      angle: 0,
    }));
    nodes.push(ui.text({
      key: `clickgui:tab:${index}`,
      text: tab && tab.label ? String(tab.label) : "",
      color: colorAlpha(active || hovered ? categoryColor : p.textSecondary, localAlpha * alpha),
      textGlowColor: colorAlpha(categoryColor, alpha),
      textGlowWidth: active || hovered ? 1.7 : 0,
      textGlowStrength: active ? 0.24 : (hovered ? 0.14 : 0),
      interactive: false,
      class: cls(abs(tabX, (height - 17) * 0.5 - 1, tabW, 17), "font-OnestMedium font-size-14.4 text-align-center"),
    }));
    nodes.push(ui.shape({
      key: `clickgui:underline:${index}`,
      shape: "rounded",
      class: abs(tabX + tabW * 0.28, height - 6, tabW * 0.44, active ? 1.6 : 1.0),
      radius: 0.8,
      fill: colorAlpha(active || hovered ? categoryColor : p.matrixOff, (active ? 1 : (hovered ? 0.62 : 0.34)) * alpha),
    }));
  });
  return nodes;
}

function musicCompact(ctx, p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, 226);
  const alpha = num(p.compactAlpha, 0);
  const pvp = p.pvpActive === true;
  const statusW = pvp ? 50 : 24;
  const titleX = x + 82;
  const titleW = Math.max(0, w - 82 - statusW - 7);
  const nodes = [
    phosphorText(p, {
      key: "music:clock:compact",
      text: p.time || "00:00",
      class: cls(abs(x + 9, 10.3, 40, 14), "font-MatrixSansPrint font-size-12.96 text-align-center"),
    }, alpha, 0.20, 1.7),
    ui.shape({ key: "music:divider", shape: "rect", class: abs(x + 52, 8, 0.75, 19), fill: colorAlpha(p.phosphorDim, 0.46 * alpha) }),
    artwork(p, "artwork:compact", x + 58, 8.5, 18, alpha),
    ui.clippedText({
      key: "music:title:compact",
      text: p.title || "",
      x: titleX,
      y: 8.7,
      w: titleW,
      h: 18,
      textClass: cls("font-OnestMedium font-size-14.76", `text-${colorAlpha(p.textPrimary, alpha)}`),
      measuredWidth: p.titleWidthCompact,
      scrollTime: p.titleScrollTime,
      fade: true,
      centerWhenFits: false,
      color: colorAlpha(p.textPrimary, alpha),
    }),
  ];
  if (pvp) {
    nodes.push(phosphorText(p, {
      key: "music:pvp:compact",
      text: p.pvpTelemetry || "PVP 00",
      class: cls(abs(x + w - 55, 10.5, 48, 14), "font-MatrixSansPrint font-size-11.16 text-align-right"),
    }, alpha, 0.28, 1.8));
  } else {
    nodes.push(...waveBars(ctx, p, x + w - 19, 17.5, alpha));
  }
  return nodes;
}

function control(p, key, icon, x, y, hover, tint, active) {
  const size = num(p.controlSize, 27);
  const alpha = num(p.expandedAlpha, 0);
  const glow = Math.max(0, hover);
  return [
    ui.radialGlow({
      key: `${key}:hover`,
      x: x - 1,
      y: y - 1,
      w: size + 2,
      h: size + 2,
      radius: 4,
      glowRadius: size * 0.48,
      color: colorAlpha(p.phosphor, glow * 0.13 * alpha),
    }),
    ui.text({
      key,
      text: icon || "",
      color: colorAlpha(tint || p.textPrimary, (0.76 + glow * 0.24) * alpha),
      textGlowColor: colorAlpha(p.phosphor, alpha),
      textGlowWidth: 1.5 + glow * 0.7,
      textGlowStrength: (0.12 + glow * 0.24) * alpha,
      class: cls(abs(x, y + 4, size, size - 4), "font-mediaplayer font-size-21.96 text-align-center"),
    }),
    ui.shape({
      key: `${key}:active`,
      shape: "rounded",
      class: abs(x + size * 0.43, y + size - 2.5, size * 0.14, 1.5),
      radius: 0.75,
      fill: colorAlpha(p.phosphor, (active ? 0.92 : 0) * alpha),
    }),
  ];
}

function svgControl(p, key, asset, tint, x, y, hover, active) {
  const size = num(p.controlSize, 27);
  const alpha = num(p.expandedAlpha, 0);
  const iconSize = size * 0.70;
  return [
    ui.radialGlow({
      key: `${key}:hover`, x: x - 1, y: y - 1, w: size + 2, h: size + 2,
      radius: 4, glowRadius: size * 0.48, color: colorAlpha(p.phosphor, Math.max(0, hover) * 0.13 * alpha),
    }),
    ui.svg({
      key,
      asset: asset || "repeat-off",
      tint: colorAlpha(tint || p.textSecondary, alpha),
      class: abs(x + (size - iconSize) * 0.5, y + (size - iconSize) * 0.5, iconSize, iconSize),
    }),
    ui.shape({
      key: `${key}:active`, shape: "rounded",
      class: abs(x + size * 0.43, y + size - 2.5, size * 0.14, 1.5), radius: 0.75,
      fill: colorAlpha(p.phosphor, (active ? 0.92 : 0) * alpha),
    }),
  ];
}

function progressDots(p, x, w, alpha) {
  const progress = ui.clamp(num(p.progress, 0), 0, 1);
  const focus = Math.max(num(p.progressHover, 0), num(p.progressPress, 0));
  const dotStart = x + 13;
  const dotWidth = Math.max(1, w - 26);
  const currentIndex = Math.max(0, Math.min(PROGRESS_DOTS - 1, Math.round(progress * (PROGRESS_DOTS - 1))));
  const nodes = [];
  for (let i = 0; i < PROGRESS_DOTS; i++) {
    const played = i <= currentIndex;
    const current = i === currentIndex;
    const size = current ? 3.2 + focus * 0.8 : (played ? 2.05 : 1.45);
    const cx = dotStart + (dotWidth * i) / (PROGRESS_DOTS - 1);
    nodes.push(ui.shape({
      key: `music:progress:dot:${i}`,
      shape: "circle",
      class: abs(cx - size * 0.5, 57 - size * 0.5, size, size),
      fill: colorAlpha(current ? p.textPrimary : (played ? p.phosphor : p.matrixOff), alpha),
    }));
  }
  const focusSize = 7 + focus * 2;
  const focusX = dotStart + dotWidth * progress;
  nodes.push(ui.roundedGlow({
    key: "music:progress:focus",
    x: focusX - focusSize * 0.5,
    y: 57 - focusSize * 0.5,
    w: focusSize,
    h: focusSize,
    radius: focusSize * 0.5,
    glow: 2.4 + focus,
    color: colorAlpha(p.phosphor, (0.10 + focus * 0.12) * alpha),
  }));
  return nodes;
}

function musicExpanded(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, 338);
  const alpha = num(p.expandedAlpha, 0);
  const nodes = [
    phosphorText(p, {
      key: "music:label:expanded",
      text: "NOW PLAYING",
      class: cls(abs(x + 13, 7, 90, 12), "font-MatrixSansPrint font-size-9.72"),
    }, alpha, 0.14, 1.5),
    phosphorText(p, {
      key: "music:elapsed:header",
      text: p.elapsed || "0:00",
      class: cls(abs(x + w - 61, 7, 48, 12), "font-MatrixSansPrint font-size-11.16 text-align-right"),
    }, alpha, 0.20, 1.7),
    ui.shape({
      key: "artwork:expanded:frame", shape: "box", class: abs(x + 12, 23, 30, 30),
      corners: ui.corner.all(ui.corner.chamfered(2.5)), fill: colorAlpha(p.phosphorDim, 0.16 * alpha),
    }),
    artwork(p, "artwork:expanded", x + 14, 25, 26, alpha),
    ui.clippedText({
      key: "music:title:expanded", text: p.title || "", x: x + 50, y: 22.5,
      w: Math.max(0, w - 63), h: 16,
      textClass: cls("font-OnestMedium font-size-15.48", `text-${colorAlpha(p.textPrimary, alpha)}`),
      measuredWidth: p.titleWidthExpanded, scrollTime: p.titleScrollTime, fade: true,
      color: colorAlpha(p.textPrimary, alpha),
    }),
    ui.clippedText({
      key: "music:artist:expanded", text: p.artist || "", x: x + 50, y: 38,
      w: Math.max(0, w - 63), h: 14,
      textClass: cls("font-Onest font-size-12.6", `text-${colorAlpha(p.textSecondary, alpha)}`),
      measuredWidth: p.artistWidthExpanded, scrollTime: p.titleScrollTime,
      scrollDelay: 1.4, scrollSpeed: 14, fade: true,
      color: colorAlpha(p.textSecondary, alpha),
    }),
    ...progressDots(p, x, w, alpha),
    phosphorText(p, {
      key: "music:elapsed:expanded", text: p.elapsed || "0:00",
      class: cls(abs(x + 13, 63, 44, 12), "font-MatrixSansPrint font-size-10.44"),
    }, alpha, 0.14, 1.5),
    phosphorText(p, {
      key: "music:total:expanded", text: p.total || "0:00",
      class: cls(abs(x + w - 57, 63, 44, 12), "font-MatrixSansPrint font-size-10.44 text-align-right"),
    }, alpha, 0.14, 1.5),
    ...control(p, "music:prev", p.iconPrev, p.prevX, p.prevY, num(p.prevHover, 0), p.textPrimary, false),
    ...control(p, "music:play", p.iconPlay, p.playX, p.playY, num(p.playHover, 0), p.phosphor, true),
    ...control(p, "music:next", p.iconNext, p.nextX, p.nextY, num(p.nextHover, 0), p.textPrimary, false),
  ];
  if (p.showShuffle) {
    nodes.push(...control(p, "music:shuffle", p.iconShuffle, p.shuffleX, p.shuffleY,
      num(p.shuffleHover, 0), p.shuffleColor, p.shuffleActive === true));
  }
  if (p.showRepeat) {
    nodes.push(...svgControl(p, "music:repeat", p.repeatAsset, p.repeatColor, p.repeatX, p.repeatY,
      num(p.repeatHover, 0), p.repeatActive === true));
  }
  return nodes;
}

export function render(ctx) {
  const p = ctx.props || {};
  const content = [];
  if (p.mode === "music") {
    content.push(...musicCompact(ctx, p));
    content.push(...musicExpanded(p));
  } else if (p.mode === "clickgui") {
    content.push(...clickGuiTabs(p));
  } else {
    content.push(...telemetryMode(p, p.mode === "pvp"));
  }

  return ui.root({
    key: "dynamic-island",
    class: cls(`w-${fmt(p.width)}`, `h-${fmt(p.height)}`),
    children: [
      ...displayShell(p),
      ui.stack({
        key: "content",
        class: cls("absolute x-0 y-0", `w-${fmt(p.width)}`, `h-${fmt(p.height)}`, "clip overflow-hidden"),
        children: content,
      }),
    ],
  });
}
