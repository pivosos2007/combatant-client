/*
 * Combatant Client GPL-3.0 — native shared-navigation component.
 * Shared selection/hover geometry, no per-row card/stroke duplication.
 */
export class NavigationGroup {
  static create(options = {}) {
    const key = ui.str(options.key, "navigation-group");
    const items = Array.isArray(options.items) ? options.items : [];
    const width = Math.max(1, ui.num(options.width, 236));
    const rowHeight = Math.max(1, ui.num(options.rowHeight, 42));
    const gap = Math.max(0, ui.num(options.gap, 3));
    const inset = Math.max(0, ui.num(options.inset, 3));
    const height = items.length * (rowHeight + gap) - (items.length ? gap : 0) + inset * 2;
    const selected = ui.str(options.selected, items.length ? ui.str(items[0].id, "") : "");
    const accent = ui.str(options.accent, "#FF906BFF");
    const base = ui.str(options.surface, "#5416141E");
    const foreground = ui.str(options.foreground, "#FFFFFFFF");
    const selectedIndex = Math.max(0, items.findIndex(v => ui.str(v.id, "") === selected));
    const rowInnerWidth = Math.max(1, width - 2 * inset);
    const rowInnerHeight = Math.max(1, rowHeight - 2);
    const children = [
      ui.roundedRect({key: `${key}:surface`, x: 0, y: 0, w: width, h: height,
        radius: 12, fill: base, interactive: false}),
      // The two only moving surfaces in the entire group. Host may patch their
      // bounds and opacity every frame without rebuilding the JS component tree.
      ui.roundedRect({key: `${key}:hover`, x: inset, y: inset, w: rowInnerWidth, h: rowInnerHeight,
        radius: 8, fill: "#00FFFFFF", interactive: false}),
      ui.roundedRect({key: `${key}:selected`, x: inset,
        y: inset + selectedIndex * (rowHeight + gap), w: rowInnerWidth, h: rowInnerHeight,
        radius: 8, fill: ui.color.alpha(accent, 0.16), interactive: false}),
    ];
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const itemKey = `${key}:item:${ui.str(item.id, String(i))}`;
      const icon = ui.str(item.icon, "");
      const active = ui.str(item.id, "") === selected;
      const visual = [];
      if (icon) visual.push(ui.svg({
        key: `${itemKey}:icon`, asset: icon, interactive: false,
        tintReactive: {
          base: foreground, mix: accent, mixTerms: {selected: 1},
          alphaBase: 0.62, alphaTerms: {selected: 0.38, hover: 0.16},
        }, stateSource: "parent",
        class: ui.abs(12, (rowHeight - 18) / 2, 18, 18),
      }));
      visual.push(ui.text({
        key: `${itemKey}:label`, text: ui.str(item.label, ""), interactive: false,
        colorReactive: {
          base: foreground, mix: accent, mixTerms: {selected: 0.66, hover: 0.12},
          alphaBase: 0.72, alphaTerms: {selected: 0.28, hover: 0.15},
        }, stateSource: "parent",
        class: ui.abs(icon ? 42 : 12, 0, Math.max(1, rowInnerWidth - (icon ? 52 : 24)), rowHeight,
          `font-${ui.str(options.font, "OnestMedium")}-${ui.fmt(ui.num(options.fontSize, 1.12))} text-align-left`),
      }));
      children.push(ui.button({
        key: itemKey, class: ui.abs(inset, inset + i * (rowHeight + gap), rowInnerWidth, rowHeight, "cursor-pointer"),
        stateValues: {selected: active},
        stateMotions: {selected: {durationMs: 190, easing: "out-cubic"}, hover: {durationMs: 190, easing: "out-cubic"}},
        children: visual,
      }));
    }
    return ui.stack({key, class: ui.abs(0, 0, width, height), children});
  }
}
