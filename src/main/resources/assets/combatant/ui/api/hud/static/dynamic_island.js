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

function artwork(p, key, x, y, size, alpha) {
  if (p.artworkTexture) {
    return ui.image({
      key,
      assetType: "texture",
      asset: p.artworkTexture,
      class: abs(x, y, size, size, `rounded-${fmt(size * 0.5)}`),
      tint: colorAlpha("#FFFFFFFF", alpha),
    });
  }
  return ui.shape({
    key: `${key}:fallback`,
    shape: "circle",
    class: abs(x, y, size, size),
    fill: colorAlpha(p.accent, 0.92 * alpha),
    stroke: colorAlpha("#33FFFFFF", alpha),
    strokeWidth: 1.2,
  });
}

function waveBars(ctx, p, x, centerY, alpha) {
  const t = ctx.time;
  const out = [];
  for (let i = 0; i < 3; i++) {
    const h = p.playing ? 3 + Math.abs(Math.sin(t * 5.2 + i * 0.85)) * 8 : 4 + i;
    out.push(ui.shape({
      key: `wave:${i}`,
      shape: "rounded",
      class: abs(x + i * 5, centerY - h * 0.5, 2.5, h),
      radius: 1.25,
      fill: colorAlpha(p.accent, (0.75 + i * 0.08) * alpha),
    }));
  }
  return out;
}

function shell(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, num(p.width, 126));
  const h = num(p.height, 35);
  const r = num(p.radius, 17);
  const rootAlpha = num(p.alpha, 1);
  const nodes = [];
  if (p.blur === true) nodes.push(ui.blurSurface({ key: "shell:blur", x, y: 0, w, h, radius: r, alpha: num(p.blurAlpha, 0) * rootAlpha }));
  nodes.push(ui.roundedRect({ key: "shell:shadow", x, y: 0, w, h, radius: r, fill: p.shadow, strokeWidth: 0 }));
  nodes.push(ui.roundedGradient({ key: "shell:fill", x, y: 0, w, h, radius: r, startColor: p.fillTop, endColor: p.fillBottom, angle: 90 }));
  nodes.push(ui.roundedGradient({ key: "shell:tint", x, y: 0, w, h, radius: r, startColor: p.tintTop, endColor: p.tintBottom, angle: 90 }));
  nodes.push(ui.roundedRect({ key: "shell:stroke", x, y: 0, w, h, radius: r, fill: "#00000000", stroke: p.stroke, strokeWidth: 1 }));
  return nodes;
}

function contextChips(p) {
  if (p.mode !== "music" && p.mode !== "clickgui") return [];
  const mainX = num(p.mainX, 0);
  const mainW = num(p.mainWidth, num(p.width, 126));
  const leftW = Math.max(0, num(p.leftContextWidth, 0));
  const rightW = Math.max(0, num(p.rightContextWidth, 0));
  const gap = num(p.contextGap, 7);
  const alpha = num(p.contextAlpha, 0);
  const rightX = mainX + mainW + gap;
  const nodes = [];

  if (p.blur === true) nodes.push(ui.blurSurface({ key: "context:time:blur", x: 0, y: 7.5, w: leftW, h: 20, radius: 10, alpha: num(p.blurAlpha, 0) * alpha }));
  nodes.push(ui.roundedRect({ key: "context:time:box", x: 0, y: 7.5, w: leftW, h: 20, radius: 10, fill: colorAlpha(p.contextFill, alpha), stroke: colorAlpha(p.contextStroke, alpha), strokeWidth: 1 }));
  nodes.push(ui.text({
    key: "context:time",
    text: p.time || "",
    color: colorAlpha(p.textPrimary, alpha),
    class: cls(abs(0, 10.1, leftW, 14), "font-inter-bold-0.72 text-align-center", `text-${colorAlpha(p.textPrimary, alpha)}`),
  }));

  const pvpAlpha = p.pvpContextVisible === true ? alpha : 0;
  if (p.blur === true) nodes.push(ui.blurSurface({ key: "context:pvp:blur", x: rightX, y: 7.5, w: rightW, h: 20, radius: 10, alpha: num(p.blurAlpha, 0) * pvpAlpha }));
  nodes.push(ui.roundedRect({ key: "context:pvp:box", x: rightX, y: 7.5, w: rightW, h: 20, radius: 10, fill: colorAlpha(p.pvpFill, pvpAlpha), stroke: colorAlpha(p.pvpStroke, pvpAlpha), strokeWidth: 1 }));
  nodes.push(ui.text({
    key: "context:pvp",
    text: p.pvpTimer || "",
    color: colorAlpha(p.textPrimary, pvpAlpha),
    class: cls(abs(rightX, 10.1, rightW, 14), "font-inter-bold-0.74 text-align-center", `text-${colorAlpha(p.textPrimary, pvpAlpha)}`),
  }));

  return nodes;
}

function clickGuiMode(p) {
  return [];
}

function timeMode(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, num(p.width, 74));
  return [
    ui.roundedRect({ key: "time:box", x, y: 7.5, w, h: 20, radius: 10, fill: p.timeFill, stroke: p.timeStroke, strokeWidth: 1 }),
    ui.text({
      key: "time",
      text: p.time || "",
      color: p.textPrimary,
      class: cls(abs(x, 7.8, w, 20), "font-inter-bold-0.96 text-align-center", `text-${p.textPrimary}`),
    }),
  ];
}

function pvpMode(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, num(p.width, 72));
  const chipW = Math.min(w - 16, Math.max(42, 54));
  const chipX = x + (w - chipW) * 0.5;
  return [
    ui.roundedRect({ key: "pvp:chip", x: chipX, y: 7.5, w: chipW, h: 20, radius: 10, fill: p.pvpFill, stroke: p.pvpStroke, strokeWidth: 1 }),
    ui.text({
      key: "pvp:timer",
      text: p.pvpTimer || "0s",
      color: p.textPrimary,
      class: cls(abs(chipX, 10.1, chipW, 14), "font-inter-bold-0.84 text-align-center shadow-text", `text-${p.textPrimary}`),
    }),
  ];
}

function musicCompact(ctx, p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, 226);
  const alpha = num(p.compactAlpha, 0);
  const centerY = 17.5;
  const waveX = x + w - 26;
  return [
    ui.text({
      key: "music:elapsed:compact",
      text: p.elapsed || "0:00",
      color: colorAlpha(p.textSecondary, alpha),
      class: cls(abs(x + 11, 10.5, 38, 13), "font-inter-medium-0.72", `text-${colorAlpha(p.textSecondary, alpha)}`),
    }),
    ui.shape({ key: "music:divider", shape: "rect", class: abs(x + 49, 7, 1, 18), fill: colorAlpha(p.textMuted, 0.4 * alpha) }),
    artwork(p, "artwork:compact", x + 58, 6.5, 22, alpha),
    ui.clippedText({
      key: "music:title:compact",
      text: p.title || "",
      x: x + 89,
      y: 8.5,
      w: Math.max(0, w - 124),
      h: 18,
      textClass: cls("font-inter-medium-0.82", `text-${colorAlpha(p.textPrimary, alpha)}`),
      measuredWidth: p.titleWidthCompact,
      scrollTime: p.titleScrollTime,
      fade: true,
      centerWhenFits: true,
      color: colorAlpha(p.textPrimary, alpha),
    }),
    ...waveBars(ctx, p, waveX, centerY, alpha),
  ];
}

function control(p, key, icon, x, y, hover) {
  const size = num(p.controlSize, 27);
  const alpha = num(p.expandedAlpha, 0);
  return [
    ui.shape({
      key: `${key}:hover`,
      shape: "circle",
      class: abs(x, y, size, size),
      fill: colorAlpha(p.accentSoft, Math.max(0, hover) * 0.3 * alpha),
    }),
    ui.text({
      key,
      text: icon || "",
      color: colorAlpha(p.textPrimary, alpha),
      class: cls(abs(x, y + 4, size, size - 4), "font-mediaplayer-1.28 text-align-center", `text-${colorAlpha(p.textPrimary, alpha)}`),
    }),
  ];
}

function svgControl(p, key, asset, tint, x, y, hover) {
  const size = num(p.controlSize, 27);
  const alpha = num(p.expandedAlpha, 0);
  const iconSize = size * 0.74;
  const iconX = x + (size - iconSize) * 0.5;
  const iconY = y + (size - iconSize) * 0.5;
  return [
    ui.shape({
      key: `${key}:hover`,
      shape: "circle",
      class: abs(x, y, size, size),
      fill: colorAlpha(p.accentSoft, Math.max(0, hover) * 0.3 * alpha),
    }),
    ui.svg({
      key,
      asset: asset || "repeat-off",
      tint: colorAlpha(tint || "#99000000", alpha),
      class: abs(iconX, iconY, iconSize, iconSize),
    }),
  ];
}

function musicExpanded(p) {
  const x = num(p.mainX, 0);
  const w = num(p.mainWidth, 338);
  const alpha = num(p.expandedAlpha, 0);
  const progressW = Math.max(0, w - 26);
  const nodes = [
    ui.clippedText({
      key: "music:title:expanded",
      text: p.title || "",
      x: x + 59,
      y: 11,
      w: Math.max(0, w - 72),
      h: 16,
      textClass: cls("font-inter-medium-0.86", `text-${colorAlpha(p.textPrimary, alpha)}`),
      measuredWidth: p.titleWidthExpanded,
      scrollTime: p.titleScrollTime,
      fade: true,
      centerWhenFits: true,
      color: colorAlpha(p.textPrimary, alpha),
    }),
    ui.clippedText({
      key: "music:artist:expanded",
      text: p.artist || "",
      x: x + 59,
      y: 28,
      w: Math.max(0, w - 72),
      h: 14,
      textClass: cls("font-inter-0.72", `text-${colorAlpha(p.textSecondary, alpha)}`),
      measuredWidth: p.artistWidthExpanded,
      scrollTime: p.titleScrollTime,
      scrollDelay: 1.4,
      scrollSpeed: 14,
      fade: true,
      color: colorAlpha(p.textSecondary, alpha),
    }),
    ui.roundedRect({ key: "music:progress:bg", x: x + 13, y: 52, w: progressW, h: 5, radius: 2.5, fill: colorAlpha(p.progressBg, alpha), strokeWidth: 0 }),
    ui.roundedRect({ key: "music:progress:fill", x: x + 13, y: 52, w: progressW * ui.clamp(p.progress, 0, 1), h: 5, radius: 2.5, fill: colorAlpha(p.accent, alpha), strokeWidth: 0 }),
    ui.text({ key: "music:elapsed:expanded", text: p.elapsed || "0:00", color: colorAlpha(p.textSecondary, alpha), class: cls(abs(x + 13, 65, 44, 14), "font-inter-medium-0.80", `text-${colorAlpha(p.textSecondary, alpha)}`) }),
    ui.text({ key: "music:total:expanded", text: p.total || "0:00", color: colorAlpha(p.textSecondary, alpha), class: cls(abs(x + w - 57, 65, 44, 14), "font-inter-medium-0.80 text-align-right", `text-${colorAlpha(p.textSecondary, alpha)}`) }),
    ...control(p, "music:prev", p.iconPrev, p.prevX, p.prevY, num(p.prevHover, 0)),
    ...control(p, "music:play", p.iconPlay, p.playX, p.playY, num(p.playHover, 0)),
    ...control(p, "music:next", p.iconNext, p.nextX, p.nextY, num(p.nextHover, 0)),
  ];
  if (p.showShuffle) {
    nodes.push(...control(p, "music:shuffle", p.iconShuffle, p.shuffleX, p.shuffleY, num(p.shuffleHover, 0)));
  }
  if (p.showRepeat) {
    nodes.push(...svgControl(p, "music:repeat", p.repeatAsset, p.repeatColor, p.repeatX, p.repeatY, num(p.repeatHover, 0)));
  }
  nodes.unshift(artwork(p, "artwork:expanded", x + 13, 11, 36, alpha));
  return nodes;
}

export function render(ctx) {
  const p = ctx.props || {};
  const content = [];
  let background = [];

  if (p.mode === "music") {
    background = shell(p);
    content.push(...contextChips(p));
    content.push(...musicCompact(ctx, p));
    content.push(...musicExpanded(p));
  } else if (p.mode === "clickgui") {
    background = shell(p);
    content.push(...contextChips(p));
    content.push(...clickGuiMode(p));
  } else if (p.mode === "pvp") {
    content.push(...pvpMode(p));
  } else {
    content.push(...timeMode(p));
  }

  return ui.root({
    key: "dynamic-island",
    class: cls(`w-${fmt(p.width)}`, `h-${fmt(p.height)}`, `rounded-${fmt(p.radius)}`),
    children: [
      ...background,
      ui.stack({
        key: "content",
        class: cls("absolute x-0 y-0", `w-${fmt(p.width)}`, `h-${fmt(p.height)}`, "clip overflow-hidden"),
        children: content,
      }),
    ],
  });
}
